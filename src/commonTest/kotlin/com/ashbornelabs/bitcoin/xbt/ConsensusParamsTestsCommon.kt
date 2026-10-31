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
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ConsensusParamsTestsCommon {

    @Test // values from Bitcoin Knots src/kernel/chainparams.cpp (tag v29.4.1.knots20260508)
    fun `chain parameters`() {
        assertEquals(ConsensusParams.Mainnet, Chain.Mainnet.consensus)
        assertEquals(961_640, Chain.Mainnet.consensus.blake2bHeight)
        assertEquals(22, Chain.Mainnet.consensus.blake2bTargetShift)
        assertEquals(1_819_756_800, Chain.Mainnet.consensus.rdtsExpiryTime)

        assertEquals(ConsensusParams.Testnet4, Chain.Testnet4.consensus)
        assertEquals(150_308, Chain.Testnet4.consensus.blake2bHeight)
        assertEquals(20, Chain.Testnet4.consensus.blake2bTargetShift)
        assertEquals(1_791_903_600, Chain.Testnet4.consensus.rdtsExpiryTime)

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
}
