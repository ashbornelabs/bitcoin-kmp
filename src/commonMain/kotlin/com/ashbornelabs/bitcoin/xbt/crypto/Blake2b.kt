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

/**
 * Unkeyed BLAKE2b, as specified in RFC 7693.
 * Bitcoin BLAKE2b (XBT) uses its 32-byte variant for block header proof-of-work.
 *
 * @param digestSize output length in bytes, between 1 and 64.
 */
internal class Blake2b(private val digestSize: Int = 32) : Digest {

    private val h = LongArray(8)
    private val v = LongArray(16)
    private val m = LongArray(16)
    private val buffer = ByteArray(BLOCK_SIZE)
    private var bufferLength = 0

    // 128-bit counter of the number of bytes hashed so far.
    private var t0 = 0L
    private var t1 = 0L

    init {
        require(digestSize in 1..MAX_DIGEST_SIZE) { "BLAKE2b digest size must be between 1 and $MAX_DIGEST_SIZE bytes" }
        reset()
    }

    override fun getAlgorithmName(): String = "BLAKE2b-${digestSize * 8}"

    override fun getDigestSize(): Int = digestSize

    override fun reset() {
        IV.copyInto(h)
        // Parameter block: digest length, no key, fanout = 1, depth = 1.
        h[0] = h[0] xor (0x01010000L or digestSize.toLong())
        buffer.fill(0)
        bufferLength = 0
        t0 = 0
        t1 = 0
    }

    override fun update(input: Byte) {
        if (bufferLength == BLOCK_SIZE) {
            incrementCounter(BLOCK_SIZE)
            compress(buffer, 0, isLastBlock = false)
            bufferLength = 0
        }
        buffer[bufferLength++] = input
    }

    override fun update(input: ByteArray, inputOffset: Int, len: Int) {
        var offset = inputOffset
        var remaining = len
        while (remaining > 0) {
            // The last block must be compressed with the finalization flag, so a full buffer is only
            // compressed once we know more data follows it.
            if (bufferLength == BLOCK_SIZE) {
                incrementCounter(BLOCK_SIZE)
                compress(buffer, 0, isLastBlock = false)
                bufferLength = 0
            }
            val n = minOf(BLOCK_SIZE - bufferLength, remaining)
            input.copyInto(buffer, bufferLength, offset, offset + n)
            bufferLength += n
            offset += n
            remaining -= n
        }
    }

    override fun doFinal(out: ByteArray, outOffset: Int): Int {
        incrementCounter(bufferLength)
        buffer.fill(0, bufferLength)
        compress(buffer, 0, isLastBlock = true)
        val result = ByteArray(MAX_DIGEST_SIZE)
        for (i in 0 until 8) Pack.writeInt64LE(h[i], result, i * 8)
        result.copyInto(out, outOffset, 0, digestSize)
        reset()
        return digestSize
    }

    private fun incrementCounter(n: Int) {
        t0 += n
        // Unsigned overflow of the low word carries into the high word.
        if (t0.toULong() < n.toULong()) t1++
    }

    private fun compress(block: ByteArray, offset: Int, isLastBlock: Boolean) {
        for (i in 0 until 16) m[i] = Pack.int64LE(block, offset + i * 8)
        h.copyInto(v, 0)
        IV.copyInto(v, 8)
        v[12] = v[12] xor t0
        v[13] = v[13] xor t1
        if (isLastBlock) v[14] = v[14].inv()
        for (round in 0 until ROUNDS) {
            val s = SIGMA[round % 10]
            g(0, 4, 8, 12, m[s[0]], m[s[1]])
            g(1, 5, 9, 13, m[s[2]], m[s[3]])
            g(2, 6, 10, 14, m[s[4]], m[s[5]])
            g(3, 7, 11, 15, m[s[6]], m[s[7]])
            g(0, 5, 10, 15, m[s[8]], m[s[9]])
            g(1, 6, 11, 12, m[s[10]], m[s[11]])
            g(2, 7, 8, 13, m[s[12]], m[s[13]])
            g(3, 4, 9, 14, m[s[14]], m[s[15]])
        }
        for (i in 0 until 8) h[i] = h[i] xor v[i] xor v[i + 8]
    }

    /** BLAKE2b mixing function. */
    private fun g(a: Int, b: Int, c: Int, d: Int, x: Long, y: Long) {
        v[a] = v[a] + v[b] + x
        v[d] = (v[d] xor v[a]).rotateRight(32)
        v[c] = v[c] + v[d]
        v[b] = (v[b] xor v[c]).rotateRight(24)
        v[a] = v[a] + v[b] + y
        v[d] = (v[d] xor v[a]).rotateRight(16)
        v[c] = v[c] + v[d]
        v[b] = (v[b] xor v[c]).rotateRight(63)
    }

    private companion object {
        const val BLOCK_SIZE = 128
        const val MAX_DIGEST_SIZE = 64
        const val ROUNDS = 12

        val IV = longArrayOf(
            0x6a09e667f3bcc908uL.toLong(), 0xbb67ae8584caa73buL.toLong(),
            0x3c6ef372fe94f82buL.toLong(), 0xa54ff53a5f1d36f1uL.toLong(),
            0x510e527fade682d1uL.toLong(), 0x9b05688c2b3e6c1fuL.toLong(),
            0x1f83d9abfb41bd6buL.toLong(), 0x5be0cd19137e2179uL.toLong(),
        )

        val SIGMA = arrayOf(
            intArrayOf(0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15),
            intArrayOf(14, 10, 4, 8, 9, 15, 13, 6, 1, 12, 0, 2, 11, 7, 5, 3),
            intArrayOf(11, 8, 12, 0, 5, 2, 15, 13, 10, 14, 3, 6, 7, 1, 9, 4),
            intArrayOf(7, 9, 3, 1, 13, 12, 11, 14, 2, 6, 5, 10, 4, 0, 15, 8),
            intArrayOf(9, 0, 5, 7, 2, 4, 10, 15, 14, 1, 11, 12, 6, 8, 3, 13),
            intArrayOf(2, 12, 6, 10, 0, 11, 8, 3, 4, 13, 7, 5, 15, 14, 1, 9),
            intArrayOf(12, 5, 1, 15, 14, 13, 4, 10, 0, 7, 6, 3, 9, 2, 8, 11),
            intArrayOf(13, 11, 7, 14, 12, 1, 3, 9, 5, 0, 15, 4, 8, 6, 2, 10),
            intArrayOf(6, 15, 14, 9, 11, 3, 0, 8, 12, 2, 13, 7, 1, 4, 10, 5),
            intArrayOf(10, 2, 8, 4, 7, 6, 1, 5, 15, 11, 9, 14, 3, 12, 13, 0),
        )
    }
}
