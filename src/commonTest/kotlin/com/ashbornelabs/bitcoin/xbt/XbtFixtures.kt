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

/** Real Bitcoin BLAKE2b (XBT) mainnet data shared by tests. */
object XbtFixtures {
    /** Mainnet block 961639: last block with a version 1 header and SHA256d proof-of-work. */
    val header961639: BlockHeader = BlockHeader.read(
        "10000a205fca17a6566978303e989d163e1aa9dc6715eef5542e0000000000000000000080fe52c98f1c1f8484213dff5a88315f7c334d0705f7d79579b289781868c0dff5c1916a3d350217510c87ed"
    )

    /** Mainnet block 961640: first block with a version 2 header and BLAKE2b proof-of-work. */
    val header961640: BlockHeader = BlockHeader.read(
        "000000a0657e02138733654183a2c7320d85ca9d743fe139c4bb01000000000000000000c137a8515a0f6b3aaf6049cc7611787c022ad523d51094be0a0363d0dc0bc7684dca936a4f8d001a5671798c84daeb494dca936a00000000b1ccf00d0300000000000000000000001e0300000000000000000000000000000000000068ac0e000000000000000000000000000000000000000000000000000000000000000000"
    )

    /** Mainnet block 961641. */
    val header961641: BlockHeader = BlockHeader.read(
        "000000a0eb8ef1961354c8a8e794a4e5464fe13b2959f47296f6e5c150000000000000006639b797171dbe9c48f7d32714d7a3c7d850bfee30772864e4214ed9d80282fea5ce936a4f8d001a7c31a7570cb5e64aa5ce936a00000000b1ccf00d020000000000000000000000120200000000000000000000000000000000000069ac0e000000000000000000000000000000000000000000000000000000000000000000"
    )
}
