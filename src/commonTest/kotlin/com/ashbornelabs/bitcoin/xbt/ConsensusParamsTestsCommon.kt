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
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ConsensusParamsTestsCommon {

    @Test // values from Bitcoin Knots src/kernel/chainparams.cpp (tag v29.4.3.knots20260508)
    fun `chain parameters`() {
        assertEquals(ConsensusParams.Mainnet, Chain.Mainnet.consensus)
        assertEquals(961_640, Chain.Mainnet.consensus.blake2bHeight)
        assertEquals(22, Chain.Mainnet.consensus.blake2bTargetShift)
        assertEquals(1_819_756_800, Chain.Mainnet.consensus.rdtsExpiryTime)

        assertEquals(ConsensusParams.Testnet4, Chain.Testnet4.consensus)
        assertEquals(150_308, Chain.Testnet4.consensus.blake2bHeight)
        assertEquals(20, Chain.Testnet4.consensus.blake2bTargetShift)
        assertEquals(1_806_537_600, Chain.Testnet4.consensus.rdtsExpiryTime)

        // The fork is not scheduled on regtest unless requested.
        assertEquals(ConsensusParams.Regtest, Chain.Regtest.consensus)
        assertFalse(Chain.Regtest.consensus.isBlake2bHeight(Int.MAX_VALUE - 1))
        assertFalse(Chain.Regtest.consensus.rdtsActiveAt(Int.MAX_VALUE, Long.MIN_VALUE))

        assertEquals(Chain.Mainnet, Chain.fromChainHash(Block.LivenetGenesisBlock.hash))
        assertEquals(Chain.Testnet4, Chain.fromChainHash(Block.Testnet4GenesisBlock.hash))
        assertEquals(Chain.Regtest, Chain.fromChainHash(Block.RegtestGenesisBlock.hash))
        assertNull(Chain.fromChainHash(BlockHash(ByteVector32.Zeroes)))
    }

    @Test
    fun `activation boundaries`() {
        val params = Chain.Mainnet.consensus
        assertFalse(params.isBlake2bHeight(961_639))
        assertTrue(params.isBlake2bHeight(961_640))
        // RDTS applies from the BLAKE2b height until the median-time-past of the previous block reaches the expiry time.
        assertFalse(params.rdtsActiveAt(961_639, 0))
        assertTrue(params.rdtsActiveAt(961_640, params.rdtsExpiryTime - 1))
        assertFalse(params.rdtsActiveAt(961_640, params.rdtsExpiryTime))
    }

    @Test
    fun `blake2b target shift`() {
        // Block 961640 is not a retarget block: its target is the target of block 961639 shifted by 22 bits.
        assertEquals(0x1702353dL, XbtFixtures.header961639.bits)
        assertEquals(0x1a008d4fL, XbtFixtures.header961640.bits)
        assertEquals(XbtFixtures.header961640.bits, BlockHeader.applyBlake2bTargetShift(XbtFixtures.header961639.bits, Chain.Mainnet.consensus))
        // The shifted target cannot exceed the proof-of-work limit.
        assertEquals(0x1d00ffffL, BlockHeader.applyBlake2bTargetShift(0x1d00ffffL, Chain.Mainnet.consensus))
        assertEquals(0x1d00ffffL, BlockHeader.applyBlake2bTargetShift(0x1b00ffffL, Chain.Mainnet.consensus))
        // Below the limit, shifting by 20 bits multiplies the target by 2^20.
        assertEquals(0x19100000L, BlockHeader.applyBlake2bTargetShift(0x17010000L, Chain.Testnet4.consensus))
    }

    @Test
    fun `check real headers`() {
        val params = Chain.Mainnet.consensus
        assertNull(XbtFixtures.header961639.checkHeader(params, 961_639))
        assertNull(XbtFixtures.header961640.checkHeader(params, 961_640))
        assertNull(XbtFixtures.header961641.checkHeader(params, 961_641))
    }

    @Test
    fun `header validation errors`() {
        val params = Chain.Mainnet.consensus
        val v1 = XbtFixtures.header961639
        val v2 = XbtFixtures.header961640
        val v2Fields = assertNotNull(v2.v2)
        // A version 1 header is not accepted from the BLAKE2b height onwards.
        assertEquals(HeaderValidationError.BadVersionBlake2b, v1.checkHeader(params, 961_640))
        // The height committed in a version 2 header must be the height of the block.
        assertEquals(HeaderValidationError.BadHeaderHeight, v2.checkHeader(params, 961_641))
        // A version 2 header cannot claim a height before the BLAKE2b height.
        assertEquals(HeaderValidationError.BadVersionSha256d, v2.copy(v2 = v2Fields.copy(height = 961_639)).checkHeader(params, 961_639))
        // The two high bits of the flags are reserved.
        assertEquals(HeaderValidationError.BadFlagsHighBits, v2.copy(v2 = v2Fields.copy(flags = 0x40)).checkHeader(params, 961_640))
        assertEquals(HeaderValidationError.BadFlagsHighBits, v2.copy(v2 = v2Fields.copy(flags = 0x80)).checkHeader(params, 961_640))
        // Changing the nonce invalidates the proof of work.
        assertEquals(HeaderValidationError.HighHash, v2.setNonce(v2.nonce + 1).checkHeader(params, 961_640))
        assertEquals(HeaderValidationError.HighHash, v1.setNonce(v1.nonce + 1).checkHeader(params, 961_639))
        assertEquals("bad-version-blake2b", HeaderValidationError.BadVersionBlake2b.rejectReason)
    }

    @Test
    fun `custom regtest activation height`() {
        // Equivalent to Knots' -testactivationheight=blake2b@200.
        val params = Chain.Regtest.consensus.copy(blake2bHeight = 200)
        assertFalse(params.isBlake2bHeight(199))
        assertTrue(params.isBlake2bHeight(200))
        // The regtest genesis block is a valid version 1 header at height 0.
        assertNull(Block.RegtestGenesisBlock.header.checkHeader(params, 0))
        assertEquals(HeaderValidationError.BadVersionBlake2b, Block.RegtestGenesisBlock.header.checkHeader(params, 200))
    }
}
