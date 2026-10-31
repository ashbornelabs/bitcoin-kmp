// Modified by Ashborne Labs (2026) for the Bitcoin BLAKE2b (XBT) chain. See NOTICE.
package com.ashbornelabs.bitcoin.xbt.crypto

internal actual fun Sha1(): Digest = Sha1Native()

internal actual fun Sha256(): Digest = Sha256Native()

internal actual fun Sha512(): Digest = Sha512Native()
