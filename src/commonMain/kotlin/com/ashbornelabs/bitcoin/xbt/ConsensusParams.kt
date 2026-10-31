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

import kotlin.jvm.JvmField

/**
 * Consensus parameters of a Bitcoin BLAKE2b (XBT) chain, as defined in Bitcoin Knots `src/kernel/chainparams.cpp`
 * (tag v29.4.1.knots20260508). Use [Chain.consensus] to get the parameters of a chain; on regtest, other values can be
 * used with [copy], like Knots' `-testactivationheight=blake2b@<height>`.
 *
 * @param powLimit maximum (i.e. easiest) proof-of-work target, big-endian.
 * @param blake2bHeight first block that must use a version 2 header and BLAKE2b proof-of-work.
 * @param blake2bTargetShift number of bits the target is shifted left by at [blake2bHeight], see [BlockHeader.applyBlake2bTargetShift].
 * @param rdtsExpiryTime median-time-past (in seconds) at which the BIP 110 reduced data rules (RDTS) stop applying.
 */
public data class ConsensusParams(
    @JvmField val powLimit: ByteVector32,
    @JvmField val blake2bHeight: Int,
    @JvmField val blake2bTargetShift: Int,
    @JvmField val rdtsExpiryTime: Long
) {
    /** @return a new instance of the proof-of-work limit: some [UInt256] operations mutate their receiver. */
    public fun powLimitTarget(): UInt256 = UInt256(powLimit.toByteArray())

    /** @return true if the block at this height must use BLAKE2b proof-of-work. */
    public fun isBlake2bHeight(height: Int): Boolean = height >= blake2bHeight

    /**
     * RDTS rules apply from [blake2bHeight] until the median-time-past of the previous block reaches [rdtsExpiryTime].
     *
     * @param height height of the block.
     * @param medianTimePastOfPrevious median-time-past of the previous block.
     */
    public fun rdtsActiveAt(height: Int, medianTimePastOfPrevious: Long): Boolean =
        isBlake2bHeight(height) && medianTimePastOfPrevious < rdtsExpiryTime

    public companion object {
        private val PowLimitMainnet = ByteVector32("00000000ffffffffffffffffffffffffffffffffffffffffffffffffffffffff")
        private val PowLimitRegtest = ByteVector32("7fffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffff")

        @JvmField
        public val Mainnet: ConsensusParams = ConsensusParams(
            powLimit = PowLimitMainnet,
            blake2bHeight = 961_640,
            blake2bTargetShift = 22,
            rdtsExpiryTime = 1_819_756_800 // September 1st, 2027 00:00 UTC
        )

        @JvmField
        public val Testnet4: ConsensusParams = ConsensusParams(
            powLimit = PowLimitMainnet,
            blake2bHeight = 150_308,
            blake2bTargetShift = 20,
            rdtsExpiryTime = 1_791_903_600 // October 13th, 2026 15:00 UTC
        )

        /** As in Knots, the BLAKE2b fork and RDTS are not scheduled on regtest by default. */
        @JvmField
        public val Regtest: ConsensusParams = ConsensusParams(
            powLimit = PowLimitRegtest,
            blake2bHeight = Int.MAX_VALUE,
            blake2bTargetShift = 20,
            rdtsExpiryTime = Long.MIN_VALUE
        )
    }
}
