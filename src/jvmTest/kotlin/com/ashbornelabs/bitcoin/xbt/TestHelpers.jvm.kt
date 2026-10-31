// Modified by Ashborne Labs (2026) for the Bitcoin BLAKE2b (XBT) chain. See NOTICE.
package com.ashbornelabs.bitcoin.xbt

actual fun readEnvironmentVariable(name: String): String? {
    return System.getenv(name)
}