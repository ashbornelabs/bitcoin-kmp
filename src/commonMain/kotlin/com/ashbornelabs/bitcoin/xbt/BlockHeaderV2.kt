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
