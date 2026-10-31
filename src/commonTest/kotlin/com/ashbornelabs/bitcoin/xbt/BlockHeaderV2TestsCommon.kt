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

import com.ashbornelabs.bitcoin.xbt.crypto.Digest
import com.ashbornelabs.bitcoin.xbt.io.ByteArrayOutput
import fr.acinq.secp256k1.Hex
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class BlockHeaderV2TestsCommon {

    /**
     * Test vectors from Bitcoin Knots `src/test/data/block_header_v2.json` (tag v29.4.1.knots20260508).
     * As in Knots' `block_header_tests.cpp`, 16 and 32 bytes fields and the block hash are encoded in reverse byte order.
     */
    @Test
    fun `knots test vectors`() {
        val vectors = TestHelpers.readResourceAsJson("data/block_header_v2.json").jsonObject["headers"]!!.jsonArray
        assertEquals(5, vectors.size)
        vectors.forEach { vector ->
            val test = vector.jsonObject
            val name = test["name"]!!.jsonPrimitive.content
            val fields = test["fields"]!!.jsonObject
            fun long(key: String) = fields[key]!!.jsonPrimitive.long
            fun int(key: String) = fields[key]!!.jsonPrimitive.int
            fun reversed(key: String) = ByteVector(fields[key]!!.jsonPrimitive.content).reversed()
            fun expected(key: String) = test[key]!!.jsonPrimitive.content

            val v2 = BlockHeaderV2(
                nonce2 = long("m_nonce2"),
                nonce3 = long("m_nonce3"),
                extraNonce = reversed("m_extranonce"),
                timeOffset = long("m_time_offset"),
                txCount = int("m_txcount"),
                flags = int("m_flags"),
                xorKeyMaskClearBits = int("m_xor_key_mask_clear_bits"),
                xorKey = reversed("m_xor_key"),
                height = int("m_height"),
                mmRhs = ByteVector32(reversed("m_mm_rhs"))
            )
            val header = BlockHeader(
                version = long("nVersion"),
                hashPreviousBlock = BlockHash(ByteVector32(reversed("hashPrevBlock"))),
                hashMerkleRoot = ByteVector32(reversed("hashMerkleRoot")),
                time = long("nTime"),
                bits = long("nBits"),
                nonce = long("nNonce"),
                v2 = v2
            )
            assertEquals(test["asic_profile"]!!.jsonPrimitive.int, v2.asicProfile, name)

            val serialized = BlockHeader.write(header)
            assertEquals(164, serialized.size, name)
            assertEquals(expected("serialized"), Hex.encode(serialized), name)

            // Intermediate values, so that a mismatch points to the step that differs.
            assertEquals(expected("xor_key_hash"), BlockHeaderV2Pow.xorKeyHash(v2).toHex(), "$name: xor_key_hash")
            val h1 = BlockHeaderV2Pow.h1(header, v2)
            assertEquals(expected("h1"), h1.toHex(), "$name: h1")
            val h2 = BlockHeaderV2Pow.h2(h1, v2)
            assertEquals(expected("h2"), h2.toHex(), "$name: h2")
            val blake2b1 = BlockHeaderV2Pow.blake2b1(h2, v2)
            assertEquals(expected("blake2b_1"), Hex.encode(blake2b1), "$name: blake2b_1")
            val asicInput = BlockHeaderV2Pow.asicInput(header, v2, h2, blake2b1)
            assertEquals(expected("asic_input"), Hex.encode(asicInput), "$name: asic_input")
            assertEquals(expected("blake2b_2"), Hex.encode(Digest.blake2b256().hash(asicInput)), "$name: blake2b_2")
            assertEquals(expected("mask"), Hex.encode(BlockHeaderV2Pow.mask(v2)), "$name: mask")
            assertEquals(expected("block_hash"), header.blockId.toString(), "$name: block_hash")

            val decoded = BlockHeader.read(serialized)
            assertEquals(header, decoded, name)
            assertEquals(header.time, decoded.time, name)
            assertEquals(expected("block_hash"), decoded.blockId.toString(), name)
        }
    }

    @Test
    fun `real headers across the blake2b activation`() {
        // Headers of mainnet blocks 961639 (last SHA256d block), 961640 (first BLAKE2b block) and 961641.
        val headers = listOf(
            "10000a205fca17a6566978303e989d163e1aa9dc6715eef5542e0000000000000000000080fe52c98f1c1f8484213dff5a88315f7c334d0705f7d79579b289781868c0dff5c1916a3d350217510c87ed",
            "000000a0657e02138733654183a2c7320d85ca9d743fe139c4bb01000000000000000000c137a8515a0f6b3aaf6049cc7611787c022ad523d51094be0a0363d0dc0bc7684dca936a4f8d001a5671798c84daeb494dca936a00000000b1ccf00d0300000000000000000000001e0300000000000000000000000000000000000068ac0e000000000000000000000000000000000000000000000000000000000000000000",
            "000000a0eb8ef1961354c8a8e794a4e5464fe13b2959f47296f6e5c150000000000000006639b797171dbe9c48f7d32714d7a3c7d850bfee30772864e4214ed9d80282fea5ce936a4f8d001a7c31a7570cb5e64aa5ce936a00000000b1ccf00d020000000000000000000000120200000000000000000000000000000000000069ac0e000000000000000000000000000000000000000000000000000000000000000000",
        ).map { BlockHeader.read(it) }
        val blockIds = listOf(
            "00000000000000000001bbc439e13f749dca850d32c7a2834165338713027e65",
            "0000000000000050c1e5f69672f459293be14f46e5a494e7a8c8541396f18eeb",
            "0000000000000010ef13157db08c138ea82aa1ac0ec360bdb9f101ce3ed7f7b6",
        )
        assertEquals(blockIds, headers.map { it.blockId.toString() })
        assertNull(headers[0].v2)
        assertEquals(961640, assertNotNull(headers[1].v2).height)
        assertEquals(961641, assertNotNull(headers[2].v2).height)
        assertEquals(headers[0].hash, headers[1].hashPreviousBlock)
        assertEquals(headers[1].hash, headers[2].hashPreviousBlock)
        headers.forEach { assertTrue(it.checkProofOfWork(Chain.Mainnet.chainHash)) }
        headers.zip(listOf(80, 164, 164)).forEach { (header, size) -> assertEquals(size, BlockHeader.write(header).size) }
    }

    @Test
    fun `block with v2 header`() {
        val header = BlockHeader.read(
            "000000a0657e02138733654183a2c7320d85ca9d743fe139c4bb01000000000000000000c137a8515a0f6b3aaf6049cc7611787c022ad523d51094be0a0363d0dc0bc7684dca936a4f8d001a5671798c84daeb494dca936a00000000b1ccf00d0300000000000000000000001e0300000000000000000000000000000000000068ac0e000000000000000000000000000000000000000000000000000000000000000000"
        )
        val block = Block(header, Block.LivenetGenesisBlock.tx)
        val decoded = Block.read(Block.write(block))
        assertEquals(block, decoded)
        assertEquals(header.hash, decoded.hash)
    }

    @Test
    fun `tx out proof with v2 header`() {
        val txid = Block.LivenetGenesisBlock.tx.first().hash.value
        // With a single transaction, the merkle root is the transaction hash.
        val header = BlockHeader.read(
            "000000a0657e02138733654183a2c7320d85ca9d743fe139c4bb01000000000000000000c137a8515a0f6b3aaf6049cc7611787c022ad523d51094be0a0363d0dc0bc7684dca936a4f8d001a5671798c84daeb494dca936a00000000b1ccf00d0300000000000000000000001e0300000000000000000000000000000000000068ac0e000000000000000000000000000000000000000000000000000000000000000000"
        ).setHashMerkleRoot(txid)
        val out = ByteArrayOutput()
        BlockHeader.write(header, out)
        BtcSerializer.writeUInt32(1u, out) // number of transactions in the block
        BtcSerializer.writeVarint(1, out) // number of merkle node hashes
        BtcSerializer.writeBytes(txid, out)
        BtcSerializer.writeScript(byteArrayOf(1), out) // flag bits
        val (decodedHeader, matched) = Block.verifyTxOutProof(out.toByteArray())
        assertEquals(header, decodedHeader)
        assertEquals(listOf(txid to 0), matched)
    }

    @Test
    fun `invalid v2 fields`() {
        val valid = BlockHeaderV2(0, 0, ByteVector(ByteArray(16)), 0, 0, 0, 0, ByteVector(ByteArray(16)), 0, ByteVector32.Zeroes)
        assertFailsWith<IllegalArgumentException> { valid.copy(extraNonce = ByteVector(ByteArray(15))) }
        assertFailsWith<IllegalArgumentException> { valid.copy(xorKey = ByteVector(ByteArray(17))) }
        assertFailsWith<IllegalArgumentException> { valid.copy(nonce2 = -1) }
        assertFailsWith<IllegalArgumentException> { valid.copy(nonce3 = 0x1_0000_0000) }
        assertFailsWith<IllegalArgumentException> { valid.copy(timeOffset = -1) }
        assertFailsWith<IllegalArgumentException> { valid.copy(txCount = 0x10000) }
        assertFailsWith<IllegalArgumentException> { valid.copy(flags = 256) }
        assertFailsWith<IllegalArgumentException> { valid.copy(xorKeyMaskClearBits = -1) }
    }
}
