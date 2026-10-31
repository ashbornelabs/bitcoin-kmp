package com.ashbornelabs.bitcoin.xbt

actual fun readEnvironmentVariable(name: String): String? {
    return System.getenv(name)
}