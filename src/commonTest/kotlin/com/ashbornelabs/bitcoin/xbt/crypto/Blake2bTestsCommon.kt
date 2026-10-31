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

package com.ashbornelabs.bitcoin.xbt.crypto

import com.ashbornelabs.bitcoin.xbt.ByteVector
import com.ashbornelabs.bitcoin.xbt.Crypto
import fr.acinq.secp256k1.Hex
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertFailsWith

class Blake2bTestsCommon {
    private fun sequence(n: Int): ByteArray = ByteArray(n) { it.toByte() }

    @Test // expected values computed with python's hashlib.blake2b(data, digest_size=32)
    fun `reference tests`() {
        val vectors = listOf(
            "".encodeToByteArray() to "0e5751c026e543b2e8ab2eb06099daa1d1e5df47778f7787faab45cdf12fe3a8",
            "abc".encodeToByteArray() to "bddd813c634239723171ef3fee98579b94964e3bb1cb3e427262c8c068d52319",
            sequence(1) to "03170a2e7597b7b7e3d84c05391d139a62b157e78786d8c082f29dcf4c111314",
            sequence(127) to "f2fe67ff342e21b8f45e8f2e0bcd1d9243245d50ee6c78042e9c491388791c72",
            sequence(128) to "c3582f71ebb2be66fa5dd750f80baae97554f3b015663c8be377cfcb2488c1d1",
            sequence(129) to "f7f3c46ba2564ff4c4c162da1f5b605f9f1c4aa6a20652a9f9a337c1a2f5b9c9",
            sequence(255) to "1d0850ee9bca0abc9601e9deabe1418fedec2fb6ac4150bd5302d2430f9be943",
            sequence(256) to "39a7eb9fedc19aabc83425c6755dd90e6f9d0c804964a1f4aaeea3b9fb599835",
            sequence(1000) to "c636324d47d89f2b2434dc2c994100663fbbaea880ff020fc5de89dd0f77a1ec",
            ByteArray(1_000_000) { 0x61 } to "0741850f36cba4259628355d1073e24ddb9ca0e1bfac36fd39ae5dc2101e23a4",
        )
        vectors.forEach { (input, expected) ->
            assertContentEquals(Hex.decode(expected), Digest.blake2b256().hash(input))
        }
    }

    @Test // BLAKE2b-512 values from RFC 7693 appendix A and the BLAKE2 reference implementation
    fun `other digest sizes`() {
        assertContentEquals(
            Hex.decode("786a02f742015903c6c6fd852552d272912f4740e15847618a86e217f71f5419d25e1031afee585313896444934eb04b903a685b1448b755d56f701afe9be2ce"),
            Blake2b(64).hash("".encodeToByteArray())
        )
        assertContentEquals(
            Hex.decode("ba80a53f981c4d0d6a2797b69f12f6e94c212f14685ac4b74b12bb6fdbffa2d17d87c5392aab792dc252d5de4533cc9518d38aa8dbf1925ab92386edd4009923"),
            Blake2b(64).hash("abc".encodeToByteArray())
        )
        assertContentEquals(Hex.decode("384264f676f39536840523f284921cdc68b6846b"), Blake2b(20).hash("abc".encodeToByteArray()))
    }

    @Test
    fun `incremental updates`() {
        val input = sequence(1000)
        val expected = Digest.blake2b256().hash(input)
        listOf(1, 7, 64, 127, 128, 129).forEach { chunkSize ->
            val digest = Digest.blake2b256()
            input.toList().chunked(chunkSize).forEach { chunk -> digest.update(chunk.toByteArray(), 0, chunk.size) }
            val output = ByteArray(32)
            digest.doFinal(output, 0)
            assertContentEquals(expected, output, "chunk size $chunkSize")
        }
        val digest = Digest.blake2b256()
        input.forEach { digest.update(it) }
        val output = ByteArray(32)
        digest.doFinal(output, 0)
        assertContentEquals(expected, output, "byte by byte")
    }

    @Test
    fun `digest can be reused after doFinal`() {
        val digest = Digest.blake2b256()
        val first = digest.hash(sequence(300))
        val second = digest.hash(sequence(300))
        assertContentEquals(first, second)
    }

    @Test
    fun `crypto helpers`() {
        val input = sequence(200)
        val expected = Digest.blake2b256().hash(input)
        assertContentEquals(expected, Crypto.blake2b256(input))
        assertContentEquals(expected, Crypto.blake2b256(ByteVector(input)))
        val padded = ByteArray(10) + input + ByteArray(10)
        assertContentEquals(expected, Crypto.blake2b256(padded, 10, input.size))
    }

    @Test
    fun `invalid digest size`() {
        assertFailsWith<IllegalArgumentException> { Blake2b(0) }
        assertFailsWith<IllegalArgumentException> { Blake2b(65) }
    }
}
