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

package com.ashbornelabs.bitcoin.xbt

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** BIP 110 reduced data rules (RDTS), as enforced by Bitcoin Knots. Each rule is checked with and without the rules. */
class ReducedDataTestsCommon {
    private val withRdts = ScriptFlags.MANDATORY_SCRIPT_VERIFY_FLAGS or ScriptFlags.REDUCED_DATA_MANDATORY_VERIFY_FLAGS
    private val withoutRdts = ScriptFlags.MANDATORY_SCRIPT_VERIFY_FLAGS
    private val internalKey = PrivateKey(ByteVector32("0101010101010101010101010101010101010101010101010101010101010101")).xOnlyPublicKey()

    private fun spends(scriptPubKey: List<ScriptElt>, flags: Int, scriptSig: List<ScriptElt> = listOf(), witness: ScriptWitness = ScriptWitness.empty): Boolean {
        val spent = TxOut(1_000.sat(), scriptPubKey)
        val tx = Transaction(
            version = 2,
            txIn = listOf(TxIn(OutPoint(Block.LivenetGenesisBlock.tx.first(), 0), Script.write(scriptSig).byteVector(), TxIn.SEQUENCE_FINAL, witness)),
            txOut = listOf(TxOut(500.sat(), Script.pay2wpkh(internalKey.publicKey))),
            lockTime = 0
        )
        return runCatching {
            Script.Runner(Script.Context(tx, 0, spent.amount, listOf(spent)), flags).verifyScripts(tx.txIn[0].signatureScript, spent.publicKeyScript, tx.txIn[0].witness)
        }.getOrDefault(false)
    }

    /** Spends a P2WSH output: the last witness item is the witness script, the others are its arguments. */
    private fun spendsP2wsh(witnessScript: List<ScriptElt>, arguments: List<ByteVector>, flags: Int): Boolean =
        spends(Script.pay2wsh(witnessScript), flags, witness = ScriptWitness(arguments + Script.write(witnessScript).byteVector()))

    /** Spends a leaf of a taproot script tree with no arguments, optionally adding an annex. */
    private fun spendsTapscript(leaf: ScriptTree.Leaf, tree: ScriptTree, flags: Int, annex: ByteVector? = null): Boolean {
        val witness = Script.witnessScriptPathPay2tr(internalKey, leaf, ScriptWitness.empty, tree)
        return spends(Script.pay2tr(internalKey, tree), flags, witness = annex?.let { ScriptWitness(witness.stack + it) } ?: witness)
    }

    private fun data(size: Int): ByteVector = ByteVector(ByteArray(size) { 0x42 })

    @Test
    fun `push and script argument size`() {
        val dropTrue = listOf(OP_DROP, OP_1)
        assertTrue(spendsP2wsh(dropTrue, listOf(data(256)), withRdts))
        assertFalse(spendsP2wsh(dropTrue, listOf(data(257)), withRdts))
        assertTrue(spendsP2wsh(dropTrue, listOf(data(257)), withoutRdts))
        // Pushes in the executed script are limited too.
        val bigPush = listOf(OP_PUSHDATA(data(257)), OP_DROP, OP_1)
        assertFalse(spendsP2wsh(bigPush, listOf(), withRdts))
        assertTrue(spendsP2wsh(bigPush, listOf(), withoutRdts))
        // The witness script itself is not a script argument: it can be larger than 256 bytes.
        val bigScript = listOf(OP_PUSHDATA(data(150)), OP_PUSHDATA(data(150)), OP_2DROP, OP_1)
        assertTrue(Script.write(bigScript).size > 256)
        assertTrue(spendsP2wsh(bigScript, listOf(), withRdts))
    }

    @Test
    fun `p2sh redeem script is exempt`() {
        val bigRedeemScript = listOf(OP_PUSHDATA(data(150)), OP_PUSHDATA(data(150)), OP_2DROP, OP_1)
        assertTrue(Script.write(bigRedeemScript).size > 256)
        assertTrue(spends(Script.pay2sh(bigRedeemScript), withRdts, scriptSig = listOf(OP_PUSHDATA(Script.write(bigRedeemScript)))))
        // The other items pushed by the scriptSig are script arguments.
        val dropTrue = listOf(OP_DROP, OP_1)
        val scriptSig = { size: Int -> listOf(OP_PUSHDATA(data(size)), OP_PUSHDATA(Script.write(dropTrue))) }
        assertTrue(spends(Script.pay2sh(dropTrue), withRdts, scriptSig = scriptSig(256)))
        assertFalse(spends(Script.pay2sh(dropTrue), withRdts, scriptSig = scriptSig(257)))
        assertTrue(spends(Script.pay2sh(dropTrue), withoutRdts, scriptSig = scriptSig(257)))
    }

    @Test
    fun `taproot annex`() {
        val leaf = ScriptTree.Leaf(listOf(OP_1))
        assertTrue(spendsTapscript(leaf, leaf, withRdts))
        assertFalse(spendsTapscript(leaf, leaf, withRdts, annex = ByteVector("50")))
        assertTrue(spendsTapscript(leaf, leaf, withoutRdts, annex = ByteVector("50")))
    }

    @Test
    fun `taproot control block size`() {
        val leaf = ScriptTree.Leaf(listOf(OP_1))
        // Builds a tree where our leaf has the given depth, i.e. a control block of 33 + 32 * depth bytes.
        fun tree(depth: Int): ScriptTree = (1..depth).fold(leaf as ScriptTree) { acc, i -> ScriptTree.Branch(acc, ScriptTree.Leaf(listOf(OP_PUSHDATA(byteArrayOf(i.toByte())), OP_DROP, OP_1))) }
        assertTrue(spendsTapscript(leaf, tree(7), withRdts))
        assertFalse(spendsTapscript(leaf, tree(8), withRdts))
        assertTrue(spendsTapscript(leaf, tree(8), withoutRdts))
    }

    @Test
    fun `OP_IF in tapscript`() {
        val ifScript = listOf(OP_0, OP_IF, OP_ENDIF, OP_1)
        val leaf = ScriptTree.Leaf(ifScript)
        assertFalse(spendsTapscript(leaf, leaf, withRdts))
        assertTrue(spendsTapscript(leaf, leaf, withoutRdts))
        val notIfLeaf = ScriptTree.Leaf(listOf(OP_1, OP_NOTIF, OP_ENDIF, OP_1))
        assertFalse(spendsTapscript(notIfLeaf, notIfLeaf, withRdts))
        assertTrue(spendsTapscript(notIfLeaf, notIfLeaf, withoutRdts))
        // The rule only applies to tapscript.
        assertTrue(spendsP2wsh(ifScript, listOf(), withRdts))
    }
}
