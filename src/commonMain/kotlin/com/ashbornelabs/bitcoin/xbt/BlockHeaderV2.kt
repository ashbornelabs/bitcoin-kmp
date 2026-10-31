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
import com.ashbornelabs.bitcoin.xbt.io.Input
import com.ashbornelabs.bitcoin.xbt.io.Output
import kotlin.jvm.JvmField

/**
 * Extra fields of a version 2 block header, used by the Bitcoin BLAKE2b (XBT) chain from the proof-of-work change onwards.
 * A version 2 header is 164 bytes long: the 80 bytes of a version 1 header, with bit 31 of the version set, followed by these fields.
 * Byte fields are stored in the same order as on the wire.
 *
 * See Bitcoin Knots `src/primitives/block.h` (`CBlockHeader`).
 *
 * @param nonce2 additional nonce for mining hardware (uint32).
 * @param nonce3 additional nonce for mining hardware (uint32).
 * @param extraNonce stratum v1 extranonce (16 bytes).
 * @param timeOffset when [useTimeOffset] is set, the time sent on the wire is the block time minus this offset (uint32).
 * @param txCount number of transactions in the block (uint16).
 * @param flags bits 0-1 select the [asicProfile], bit 2 enables [useTimeOffset] (uint8).
 * @param xorKeyMaskClearBits number of leading bits of the proof-of-work XOR mask that are cleared (uint8).
 * @param xorKey key from which the proof-of-work XOR mask is derived, all zeros for no mask (16 bytes).
 * @param height height of this block.
 * @param mmRhs merge-mining commitment (32 bytes).
 */
public data class BlockHeaderV2(
    @JvmField val nonce2: Long,
    @JvmField val nonce3: Long,
    @JvmField val extraNonce: ByteVector,
    @JvmField val timeOffset: Long,
    @JvmField val txCount: Int,
    @JvmField val flags: Int,
    @JvmField val xorKeyMaskClearBits: Int,
    @JvmField val xorKey: ByteVector,
    @JvmField val height: Int,
    @JvmField val mmRhs: ByteVector32
) {
    init {
        require(nonce2 in 0..UINT32_MAX) { "nonce2 must be a uint32" }
        require(nonce3 in 0..UINT32_MAX) { "nonce3 must be a uint32" }
        require(extraNonce.size() == 16) { "extraNonce must be 16 bytes" }
        require(timeOffset in 0..UINT32_MAX) { "timeOffset must be a uint32" }
        require(txCount in 0..0xffff) { "txCount must be a uint16" }
        require(flags in 0..0xff) { "flags must be a uint8" }
        require(xorKeyMaskClearBits in 0..0xff) { "xorKeyMaskClearBits must be a uint8" }
        require(xorKey.size() == 16) { "xorKey must be 16 bytes" }
    }

    /** Selects the layout of the data hashed by mining hardware, see [BlockHeaderV2Pow.asicInput]. */
    public val asicProfile: Int get() = flags and 3

    public val useTimeOffset: Boolean get() = (flags and FLAG_USE_TIME_OFFSET) != 0

    public companion object {
        /** Bit of the version field that marks a version 2 header. */
        public const val VERSION_FLAG: Long = 0x80000000L
        public const val FLAG_USE_TIME_OFFSET: Int = 4
        private const val UINT32_MAX: Long = 0xffffffffL

        internal fun read(input: Input): BlockHeaderV2 = BlockHeaderV2(
            nonce2 = BtcSerializer.uint32(input).toLong(),
            nonce3 = BtcSerializer.uint32(input).toLong(),
            extraNonce = BtcSerializer.bytes(input, 16).byteVector(),
            timeOffset = BtcSerializer.uint32(input).toLong(),
            txCount = BtcSerializer.uint16(input).toInt(),
            flags = BtcSerializer.uint8(input).toInt(),
            xorKeyMaskClearBits = BtcSerializer.uint8(input).toInt(),
            xorKey = BtcSerializer.bytes(input, 16).byteVector(),
            height = BtcSerializer.uint32(input).toInt(),
            mmRhs = BtcSerializer.bytes(input, 32).byteVector32()
        )

        internal fun write(message: BlockHeaderV2, output: Output) {
            BtcSerializer.writeUInt32(message.nonce2.toUInt(), output)
            BtcSerializer.writeUInt32(message.nonce3.toUInt(), output)
            BtcSerializer.writeBytes(message.extraNonce, output)
            BtcSerializer.writeUInt32(message.timeOffset.toUInt(), output)
            BtcSerializer.writeUInt16(message.txCount.toUShort(), output)
            BtcSerializer.writeUInt8(message.flags.toUByte(), output)
            BtcSerializer.writeUInt8(message.xorKeyMaskClearBits.toUByte(), output)
            BtcSerializer.writeBytes(message.xorKey, output)
            BtcSerializer.writeUInt32(message.height.toUInt(), output)
            BtcSerializer.writeBytes(message.mmRhs, output)
        }
    }
}

/**
 * BLAKE2b proof-of-work hash of version 2 block headers, ported from Bitcoin Knots `CBlockHeader::GetHash()`
 * in `src/primitives/block.cpp`. Each step is exposed separately so that it can be checked against Knots' test vectors.
 */
internal object BlockHeaderV2Pow {
    private val zeros16 = ByteArray(16)

    fun xorKeyHash(v2: BlockHeaderV2): ByteVector32 = Crypto.taggedHash(v2.xorKey.toByteArray(), "Bitcoin block hash PoW XOR key")

    fun mask(v2: BlockHeaderV2): ByteArray {
        if (v2.xorKey.toByteArray().all { it == 0.toByte() }) return ByteArray(32)
        val mask = Crypto.taggedHash(v2.xorKey.toByteArray(), "Bitcoin block hash PoW XOR mask").toByteArray()
        val clearBytes = v2.xorKeyMaskClearBits / 8
        mask.fill(0, 0, clearBytes)
        mask[clearBytes] = (mask[clearBytes].toInt() and (0xff ushr (v2.xorKeyMaskClearBits % 8))).toByte()
        return mask
    }

    /** Commits to the fields that mining hardware never sees (119 bytes). */
    fun h1(header: BlockHeader, v2: BlockHeaderV2): ByteVector32 {
        val out = ByteArrayOutput()
        BtcSerializer.writeUInt32(header.versionOnWire().toUInt(), out)
        BtcSerializer.writeBytes(header.hashPreviousBlock.value.reversed(), out)
        BtcSerializer.writeUInt32(v2.height.toUInt(), out)
        BtcSerializer.writeBytes(header.hashMerkleRoot, out)
        BtcSerializer.writeUInt32(header.timeOnWire().toUInt(), out)
        BtcSerializer.writeUInt8(0u, out) // reserved for extended 40-bit time
        BtcSerializer.writeUInt32(header.bits.toUInt(), out)
        BtcSerializer.writeUInt32(v2.txCount.toUInt(), out)
        BtcSerializer.writeUInt8(v2.flags.toUByte(), out)
        BtcSerializer.writeUInt8(v2.xorKeyMaskClearBits.toUByte(), out)
        BtcSerializer.writeBytes(xorKeyHash(v2), out)
        return Crypto.taggedHash(out.toByteArray(), "Bitcoin block header 1")
    }

    /** Merge-mining hook (96 bytes). */
    fun h2(h1: ByteVector32, v2: BlockHeaderV2): ByteVector32 =
        Crypto.taggedHash(h1.toByteArray() + zeros16 + zeros16 + v2.mmRhs.toByteArray(), "Merge-mining hook")

    /** First BLAKE2b, over the data sent to miners over stratum v1 (52 bytes). */
    fun blake2b1(h2: ByteVector32, v2: BlockHeaderV2): ByteArray =
        Digest.blake2b256().hash(ByteArray(4) + h2.toByteArray() + v2.extraNonce.toByteArray())

    /** Data hashed by mining hardware, whose layout depends on [BlockHeaderV2.asicProfile]. */
    fun asicInput(header: BlockHeader, v2: BlockHeaderV2, h2: ByteVector32, blake2b1: ByteArray): ByteArray {
        val nonce = BtcSerializer.writeUInt32(header.nonce.toUInt())
        val nonce2 = BtcSerializer.writeUInt32(v2.nonce2.toUInt())
        val nonce3 = BtcSerializer.writeUInt32(v2.nonce3.toUInt())
        val timeOffset = BtcSerializer.writeUInt32(v2.timeOffset.toUInt())
        return when (v2.asicProfile) {
            0 -> {
                val prevBlockHidden = Crypto.taggedHash(header.hashPreviousBlock.value.reversed().toByteArray(), "Bitcoin prevblock header, hashed").toByteArray()
                prevBlockHidden.fill(0, 0, 6)
                prevBlockHidden + nonce + nonce2 + timeOffset + nonce3 + blake2b1
            }
            1 -> nonce + nonce2 + nonce3 + timeOffset + blake2b1 + h2.toByteArray()
            2 -> ByteArray(48) + h2.toByteArray() + nonce + nonce2 + timeOffset + nonce3 + blake2b1
            else -> ByteArray(32 + 48) + h2.toByteArray() + nonce + nonce2 + timeOffset + nonce3 + blake2b1
        }
    }

    fun hash(header: BlockHeader, v2: BlockHeaderV2): BlockHash {
        val h2 = h2(h1(header, v2), v2)
        val blake2b2 = Digest.blake2b256().hash(asicInput(header, v2, h2, blake2b1(h2, v2)))
        val mask = mask(v2)
        // The masked hash is in display order: reverse it to get the internal byte order used for block hashes.
        val masked = ByteArray(32) { i -> (blake2b2[i].toInt() xor mask[i].toInt()).toByte() }
        return BlockHash(masked.reversedArray())
    }
}
