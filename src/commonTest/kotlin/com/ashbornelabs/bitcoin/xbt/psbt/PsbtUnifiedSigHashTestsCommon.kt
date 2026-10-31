/*
 * Copyright 2026 Ashborne Labs
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.ashbornelabs.bitcoin.xbt.psbt

import com.ashbornelabs.bitcoin.xbt.*
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFails
import kotlin.test.assertIs
import kotlin.test.assertNotNull

class PsbtUnifiedSigHashTestsCommon {
    private val priv = PrivateKey(ByteVector32("0101010101010101010101010101010101010101010101010101010101010101"))
    private val pub = priv.publicKey()
    private val p2wshScript = listOf(OP_PUSHDATA(pub), OP_CHECKSIG)

    /** A transaction with one output per script type: P2PKH, P2WPKH, P2WSH and P2TR key path. */
    private val parentTx = Transaction(
        version = 2,
        txIn = listOf(TxIn(OutPoint(Block.LivenetGenesisBlock.tx.first(), 0), TxIn.SEQUENCE_FINAL)),
        txOut = listOf(
            TxOut(10_000.sat(), Script.pay2pkh(pub)),
            TxOut(20_000.sat(), Script.pay2wpkh(pub)),
            TxOut(30_000.sat(), Script.pay2wsh(p2wshScript)),
            TxOut(40_000.sat(), Script.pay2tr(priv.xOnlyPublicKey(), Crypto.TaprootTweak.KeyPathTweak)),
        ),
        lockTime = 0
    )
    private val unsignedTx = Transaction(
        version = 2,
        txIn = parentTx.txOut.indices.map { TxIn(OutPoint(parentTx, it.toLong()), TxIn.SEQUENCE_FINAL) },
        txOut = listOf(TxOut(90_000.sat(), Script.pay2wpkh(pub))),
        lockTime = 0
    )

    @Test
    fun `sign a psbt with the unified sighash`() {
        val sighashType = SigHash.SIGHASH_ALL_UNIFIED
        val updated = Psbt(unsignedTx)
            .updateNonWitnessInput(parentTx, 0, sighashType = sighashType).right!!
            .updateWitnessInput(OutPoint(parentTx, 1), parentTx.txOut[1], sighashType = sighashType).right!!
            .updateWitnessInput(OutPoint(parentTx, 2), parentTx.txOut[2], witnessScript = p2wshScript, sighashType = sighashType).right!!
            .updateWitnessInput(OutPoint(parentTx, 3), parentTx.txOut[3], sighashType = sighashType, taprootInternalKey = priv.xOnlyPublicKey()).right!!

        val sign0 = assertNotNull(updated.sign(priv, 0).right)
        val sign1 = assertNotNull(sign0.psbt.sign(priv, 1).right)
        val sign2 = assertNotNull(sign1.psbt.sign(priv, 2).right)
        val sign3 = assertNotNull(sign2.psbt.sign(priv, 3).right)
        // Every signature carries the unified hash type byte.
        listOf(sign0, sign1, sign2, sign3).forEach { assertEquals(sighashType.toByte(), it.sig.toByteArray().last()) }

        val finalized = sign3.psbt
            .finalizeNonWitnessInput(0, listOf(OP_PUSHDATA(sign0.sig), OP_PUSHDATA(pub))).right!!
            .finalizeWitnessInput(1, Script.witnessPay2wpkh(pub, sign1.sig)).right!!
            .finalizeWitnessInput(2, ScriptWitness(listOf(sign2.sig, Script.write(p2wshScript).byteVector()))).right!!
            .finalizeWitnessInput(3, ScriptWitness(listOf(sign3.sig))).right!!
        // Extracting the transaction verifies its signatures with the standard script flags.
        val signedTx = assertNotNull(finalized.extract().right)
        Transaction.correctlySpends(signedTx, parentTx, ScriptFlags.STANDARD_SCRIPT_VERIFY_FLAGS)
        // On the SHA256d chain, these signatures are invalid: this is the replay protection.
        assertFails { Transaction.correctlySpends(signedTx, parentTx, ScriptFlags.STANDARD_SCRIPT_VERIFY_FLAGS and ScriptFlags.SCRIPT_VERIFY_UNIFIED_SIGHASH.inv()) }
    }

    @Test
    fun `unified sighash requires every spent utxo`() {
        // Only the input being signed is updated: the unified sighash cannot be computed without the other utxos.
        val psbt = Psbt(unsignedTx).updateWitnessInput(OutPoint(parentTx, 1), parentTx.txOut[1], sighashType = SigHash.SIGHASH_ALL_UNIFIED).right!!
        assertIs<UpdateFailure.CannotSignInput>(psbt.sign(priv, 1).left)
        // Legacy signatures only commit to the utxo being spent.
        val legacy = Psbt(unsignedTx).updateWitnessInput(OutPoint(parentTx, 1), parentTx.txOut[1], sighashType = SigHash.SIGHASH_ALL).right!!
        assertNotNull(legacy.sign(priv, 1).right)
    }
}
