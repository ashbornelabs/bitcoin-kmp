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
import fr.acinq.secp256k1.Hex
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

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
