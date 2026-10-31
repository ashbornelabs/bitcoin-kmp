// Modified by Ashborne Labs (2026) for the Bitcoin BLAKE2b (XBT) chain. See NOTICE.
package com.ashbornelabs.bitcoin.xbt

import platform.posix.*
import kotlinx.cinterop.*

@OptIn(ExperimentalForeignApi::class)
actual fun readEnvironmentVariable(name: String): String? {
    return getenv(name)?.toKString()
}