[![Kotlin](https://img.shields.io/badge/Kotlin-2.3.10-blue.svg?style=flat&logo=kotlin)](http://kotlinlang.org)
![Github Actions](https://github.com/ashbornelabs/bitcoin-kmp/actions/workflows/test.yml/badge.svg)
[![License](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](https://github.com/ashbornelabs/bitcoin-kmp/blob/master/LICENSE)

# Kotlin Multiplatform Bitcoin XBT Library

Fork of [ACINQ/bitcoin-kmp](https://github.com/ACINQ/bitcoin-kmp) for the Bitcoin BLAKE2b (XBT) chain.

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

## Install

`bitcoin-xbt-kmp` is not published to a public repository yet. Run `./gradlew publishToMavenLocal` and add `mavenLocal()` to your repositories.

* **Multiplatform**: Add the `com.ashbornelabs:bitcoin-xbt-kmp` dependency to your common source set dependencies.
* **JVM**: Add the `com.ashbornelabs:bitcoin-xbt-kmp-jvm` dependency to your project.

## libscp256k1 support

**You need to add a JVM implementation of Secp256k1** to your project in order to use bitcoin-xbt-kmp with JVM.

Please refer the [Secp256k1 installation section](https://github.com/ACINQ/secp256k1-kmp#installation).

## Usage

Please have a look at unit tests, more samples will be added soon.
