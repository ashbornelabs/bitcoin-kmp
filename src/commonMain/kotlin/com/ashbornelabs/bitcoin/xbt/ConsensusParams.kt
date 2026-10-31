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
 * (tag v29.4.3.knots20260508). Use [Chain.consensus] to get the parameters of a chain; on regtest, other values can be
 * used with [copy], like Knots' `-testactivationheight=blake2b@<height>`.
 *
 * @param powLimit maximum (i.e. easiest) proof-of-work target, big-endian.
 * @param blake2bHeight first block that must use a version 2 header and BLAKE2b proof-of-work.
 * @param blake2bTargetShift number of bits the target is shifted left by at [blake2bHeight], see [BlockHeader.applyBlake2bTargetShift].
 * @param rdtsExpiryTime median-time-past (in seconds) at which the BIP 110 reduced data rules (RDTS) stop applying.
 * @param coinbaseMaturity coinbase maturity rules.
 */
public data class ConsensusParams(
    @JvmField val powLimit: ByteVector32,
    @JvmField val blake2bHeight: Int,
    @JvmField val blake2bTargetShift: Int,
    @JvmField val rdtsExpiryTime: Long,
    @JvmField val coinbaseMaturity: CoinbaseMaturityParams = CoinbaseMaturityParams.Default
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
            // Extended in Knots v29.4.2 from 1_791_903_600 (October 13th, 2026 15:00 UTC) after that time had passed.
            rdtsExpiryTime = 1_806_537_600 // April 1st, 2027 00:00 UTC
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

/**
 * Coinbase maturity rules, ported from Bitcoin Knots `Consensus::CheckTxInputs` in `src/consensus/tx_verify.cpp`
 * (tag v29.4.3.knots20260508). Coinbase outputs normally need [COINBASE_MATURITY] confirmations; a long maturity
 * schedule requires [long] confirmations for coinbase outputs mined from [longStartHeight], and a historical schedule
 * (mainnet only) required more confirmations for coinbase outputs mined between [historicalStartHeight] and
 * [historicalReleaseHeight], until that release height.
 *
 * @param long confirmations required by the long maturity schedule.
 * @param longStartHeight first coinbase height covered by the long maturity schedule.
 * @param longEnforceHeight first spending height where the long maturity schedule is enforced.
 * @param longReleaseHeight first spending height where it is no longer enforced, unless [longUntilRdts].
 * @param longUntilRdts the long maturity schedule covers coinbase outputs mined until the BIP 110 rules expire, and
 * keeps applying to them afterwards.
 * @param historicalStartHeight first coinbase height covered by the historical schedule.
 * @param historicalReleaseHeight first spending height where the historical schedule no longer applies.
 */
public data class CoinbaseMaturityParams(
    @JvmField val long: Int = COINBASE_MATURITY,
    @JvmField val longStartHeight: Int = Int.MAX_VALUE,
    @JvmField val longEnforceHeight: Int = Int.MAX_VALUE,
    @JvmField val longReleaseHeight: Int = Int.MAX_VALUE,
    @JvmField val longUntilRdts: Boolean = false,
    @JvmField val historicalStartHeight: Int = Int.MAX_VALUE,
    @JvmField val historicalReleaseHeight: Int = Int.MAX_VALUE
) {
    private fun historicalActiveAt(spendHeight: Int): Boolean = spendHeight >= historicalStartHeight && spendHeight < historicalReleaseHeight

    private fun longActiveAt(spendHeight: Int): Boolean = spendHeight >= longEnforceHeight && (longUntilRdts || spendHeight < longReleaseHeight)

    /**
     * @param coinbaseHeight height of the block containing the coinbase transaction.
     * @param spendHeight height of the block spending its outputs.
     * @param longMaturityEndHeight when [longUntilRdts] is set: the first height, on the spending branch, whose previous
     * block's median-time-past reached [ConsensusParams.rdtsExpiryTime]. Coinbase outputs mined from that height only
     * need [COINBASE_MATURITY] confirmations. [Int.MAX_VALUE] while that time has not been reached.
     * @return the minimum difference between [spendHeight] and [coinbaseHeight] for the spend to be valid.
     */
    public fun requiredDepth(coinbaseHeight: Int, spendHeight: Int, longMaturityEndHeight: Int = Int.MAX_VALUE): Int {
        val historical = when {
            historicalActiveAt(spendHeight) && coinbaseHeight >= historicalStartHeight -> historicalReleaseHeight - historicalStartHeight
            else -> COINBASE_MATURITY
        }
        val longEndHeight = if (longUntilRdts) longMaturityEndHeight else Int.MAX_VALUE
        val rolling = when {
            longActiveAt(spendHeight) && coinbaseHeight >= longStartHeight && coinbaseHeight < longEndHeight -> long
            else -> COINBASE_MATURITY
        }
        return maxOf(historical, rolling)
    }

    public companion object {
        public const val COINBASE_MATURITY: Int = 100

        /** Only the default maturity of [COINBASE_MATURITY] confirmations, as on regtest. */
        @JvmField
        public val Default: CoinbaseMaturityParams = CoinbaseMaturityParams()
    }
}

/** Reasons a block header is rejected, named after the reject reasons used by Bitcoin Knots. */
public enum class HeaderValidationError(public val rejectReason: String) {
    /** A version 2 header claims a height before [ConsensusParams.blake2bHeight]. */
    BadVersionSha256d("bad-version-sha256d"),

    /** A version 2 header sets the two high bits of its flags, which are reserved for future hard forks. */
    BadFlagsHighBits("bad-flags-highbits"),

    /** The height in a version 2 header does not match the height of the block. */
    BadHeaderHeight("bad-header-height"),

    /** A version 1 header is used at or after [ConsensusParams.blake2bHeight]. */
    BadVersionBlake2b("bad-version-blake2b"),

    /** The header does not satisfy its proof-of-work target. */
    HighHash("high-hash")
}
