// Modified by Ashborne Labs (2026) for the Bitcoin BLAKE2b (XBT) chain. See NOTICE.
package com.ashbornelabs.bitcoin.xbt.io

import java.io.InputStream


public class InputStreamInput(private val stream: InputStream) : Input {

    override val availableBytes: Int get() = stream.available()

    override fun read(): Int = stream.read()

    override fun read(b: ByteArray, offset: Int, length: Int): Int = stream.read(b, offset, length)
}
