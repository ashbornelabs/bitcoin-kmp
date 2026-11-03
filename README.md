[![Kotlin](https://img.shields.io/badge/Kotlin-2.3.10-blue.svg?style=flat&logo=kotlin)](http://kotlinlang.org)
![Github Actions](https://github.com/ashbornelabs/bitcoin-kmp/actions/workflows/test.yml/badge.svg)
[![License](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](https://github.com/ashbornelabs/bitcoin-kmp/blob/master/LICENSE)

# Kotlin Multiplatform Bitcoin XBT Library

> Modified by Ashborne Labs (2026) for the Bitcoin BLAKE2b (XBT) chain. See NOTICE.

Fork of [ACINQ/bitcoin-kmp](https://github.com/ACINQ/bitcoin-kmp) for the Bitcoin BLAKE2b (XBT) chain.

## About XBT

Bitcoin BLAKE2b (XBT) shares Bitcoin's history up to block 961,639 and then follows the consensus rules of [Bitcoin Knots](https://github.com/bitcoinknots/bitcoin) with the BLAKE2b fork. From block 961,640 on mainnet:

* blocks are mined with BLAKE2b proof-of-work instead of SHA256d, and use a 164-byte version 2 header (bit 31 of the version set);
* the proof-of-work target is shifted left by 22 bits at the fork height;
* signatures can opt in to a unified signature hash (`SIGHASH_UNIFIED`, `0x20`): they are not valid on the SHA256d chain, which protects transactions against replay;
* the BIP 110 reduced data rules (RDTS) apply until they expire.

Addresses, key derivation paths, BIP 39 mnemonics and secp256k1 signatures are the same as in Bitcoin.

This library follows Bitcoin Knots tag [`v29.4.3.knots20260508`](https://github.com/bitcoinknots/bitcoin/tree/v29.4.3.knots20260508): the XBT rules are ported from its source code and tested against its test vectors.

## Changes from bitcoin-kmp

| Area | Change |
|---|---|
| Package | `fr.acinq.bitcoin` is now `com.ashbornelabs.bitcoin.xbt`. |
| Coordinates | `com.ashbornelabs:bitcoin-xbt-kmp`. |
| Chains | `Chain.Mainnet`, `Chain.Testnet4` and `Chain.Regtest`. Testnet3 and signet are removed. |
| Consensus parameters | `Chain.consensus` returns the chain's `ConsensusParams`: fork height, target shift, RDTS expiry and coinbase maturity. |
| Block headers | `BlockHeader.v2` holds the extra fields of a version 2 header. `BlockHeader.read` reads 80-byte and 164-byte headers, `hash` is BLAKE2b for version 2 headers, and `checkHeader` applies the header rules of Knots. |
| Hashing | `Crypto.blake2b256`. |
| Signatures | `SigHash.SIGHASH_UNIFIED` and `SigHash.SIGHASH_ALL_UNIFIED`, `Transaction.hashForSigningUnified`, and PSBT signing with the unified signature hash. |
| Scripts | `ScriptFlags.SCRIPT_VERIFY_REDUCED_DATA` and `SCRIPT_VERIFY_UNIFIED_SIGHASH`, part of the standard flags, and `Transaction.checkOutputSizes` for the BIP 110 output rule. |
| Coinbase maturity | `CoinbaseMaturityParams.requiredDepth` returns the confirmations a coinbase output needs. |

The rest of the API keeps the names and signatures of bitcoin-kmp. The library still uses ACINQ's [secp256k1-kmp](https://github.com/ACINQ/secp256k1-kmp).

## Network parameters

| | Mainnet | Testnet4 | Regtest |
|---|---|---|---|
| BLAKE2b fork height | 961,640 | 150,308 | not scheduled |
| Target shift at the fork | 22 bits | 20 bits | 20 bits |
| RDTS expiry (median time past) | 1,819,756,800 (2027-09-01 00:00 UTC) | 1,806,537,600 (2027-04-01 00:00 UTC) | not active |
| Coinbase maturity | 25,920 blocks (about 180 days) for coinbases mined from height 979,919 while RDTS applies | 6,705 blocks for coinbases mined from height 151,406, enforced for spends at heights 151,550 to 158,110 | 100 blocks |

Other coinbase outputs need 100 confirmations, as in Bitcoin. On mainnet, spends at heights 973,440 to 979,919 also required 6,480 confirmations for coinbases mined from height 973,440. On regtest, `ConsensusParams.Regtest.copy(...)` schedules the fork at any height, like `-testactivationheight=blake2b@<height>` in Knots.

## Overview

This is a simple Kotlin Multiplatform library which implements most of the bitcoin protocol:

* base58 encoding/decoding
* block headers, block and tx parsing
* tx creation, signature and verification
* script parsing and execution (including OP_CLTV and OP_CSV)
* pay to public key tx
* pay to script tx / multisig tx
* BIP 32 (deterministic wallets)
* BIP 39 (mnemonic code for generating deterministic keys)
* BIP 86 (key derivation for p2tr outputs)
* BIP 173 (Base32 address format for native v0-16 witness outputs)
* BIP 174 (Partially Signed Bitcoin Transaction Format v0)
* BIP 341 and 342 (taproot and tapscript transactions)
* BIP 350 (Bech32m format)
* XBT version 2 block headers and BLAKE2b proof-of-work
* XBT unified signature hash, including PSBT signing
* BIP 110 reduced data rules

## Objectives

Our goal is not to re-implement a full Bitcoin node but to build a library that implements all the primitives that you need to create bitcoin applications: building and signing transactions, verifying transactions, working with custom bitcoin scripts, parsing blocks, headers, transactions, building BIP39 wallets...

ACINQ uses the original library in its multiplatform lightning engine https://github.com/ACINQ/lightning-kmp.

Our runtime targets are:

* JVM
* Android
* iOS
* Linux 64 bits (for testing/prototyping only, you should use the JVM for production applications)

## Status

* [X] Message parsing (blocks, transactions, inv, ...)
* [X] Building transactions (P2PK, P2PKH, P2SH, P2WPK, P2WSH)
* [X] Signing transactions
* [X] Verifying signatures
* [X] Passing core reference tests (scripts & transactions)
* [X] Passing core reference segwit tests
* [X] Passing core reference psbt v0 tests
* [X] Passing Knots reference tests for version 2 block headers and the unified signature hash
* [X] Passing Knots reference script and transaction tests with the BIP 110 rules
* [X] Verifying real XBT mainnet headers around the fork and transactions signed with the unified signature hash

## Install

`bitcoin-xbt-kmp` is not published to a public Maven repository yet. Run `./gradlew publishToMavenLocal` and add `mavenLocal()` to your repositories. The current version is `0.1.0-SNAPSHOT`.

* **Multiplatform**: Add the `com.ashbornelabs:bitcoin-xbt-kmp:0.1.0-SNAPSHOT` dependency to your common source set dependencies.
* **JVM**: Add the `com.ashbornelabs:bitcoin-xbt-kmp-jvm:0.1.0-SNAPSHOT` dependency to your project.

## libscp256k1 support

**You need to add a JVM implementation of Secp256k1** to your project in order to use bitcoin-xbt-kmp with JVM.

Please refer the [Secp256k1 installation section](https://github.com/ACINQ/secp256k1-kmp#installation).

## Usage

Please have a look at unit tests, more samples will be added soon.

### Read and check block headers

`BlockHeader.read` reads a version 1 header (80 bytes) or a version 2 header (164 bytes), depending on bit 31 of the version, so a stream that mixes both can be read header by header:

```kotlin
val input = ByteArrayInput(bytes)
val headers = buildList { while (input.availableBytes > 0) add(BlockHeader.read(input)) }
val header = headers.last()
val isV2 = header.v2 != null // true from block 961,640 on mainnet
val error = header.checkHeader(Chain.Mainnet.consensus, height = 961_640) // null when the header is valid
```

### Sign with the unified signature hash

The unified signature hash commits to every output spent by the transaction, so signing takes all of them:

```kotlin
val privateKey = PrivateKey.fromHex("0101010101010101010101010101010101010101010101010101010101010101")
val publicKey = privateKey.publicKey()
val spent = TxOut(100_000.sat(), Script.pay2wpkh(publicKey))
val outPoint = OutPoint(TxHash("8f2e1e7c0d8b6a5c4b3a29180716f5e4d3c2b1a09f8e7d6c5b4a392817160504"), 0)
val unsigned = Transaction(
    version = 2,
    txIn = listOf(TxIn(outPoint, sequence = 0xfffffffdL)),
    txOut = listOf(TxOut(99_000.sat(), Script.pay2wpkh(publicKey))),
    lockTime = 0
)
val sig = unsigned.signInput(0, Script.pay2pkh(publicKey), SigHash.SIGHASH_ALL_UNIFIED, listOf(spent), SigVersion.SIGVERSION_WITNESS_V0, privateKey)
val signed = unsigned.updateWitness(0, Script.witnessPay2wpkh(publicKey, sig.byteVector()))
signed.correctlySpends(mapOf(outPoint to spent), ScriptFlags.STANDARD_SCRIPT_VERIFY_FLAGS)
```

The library signs with the legacy signature hash unless `SIGHASH_UNIFIED` is set. To sign a PSBT with it, set the input's sighash type to `SIGHASH_ALL_UNIFIED` and include the utxo spent by every input.

## License

bitcoin-xbt-kmp is released under the [Apache License 2.0](LICENSE). It is a fork of [bitcoin-kmp](https://github.com/ACINQ/bitcoin-kmp), Copyright 2020-2021 ACINQ SAS, and includes code ported from, and test data taken from, [Bitcoin Knots](https://github.com/bitcoinknots/bitcoin), released under the MIT License. [NOTICE](NOTICE) holds these attributions and the license of Bitcoin Knots. Source files modified by Ashborne Labs carry a notice saying so.

This fork is maintained by Ashborne Labs. It is not affiliated with or endorsed by ACINQ or the Bitcoin Knots project.
