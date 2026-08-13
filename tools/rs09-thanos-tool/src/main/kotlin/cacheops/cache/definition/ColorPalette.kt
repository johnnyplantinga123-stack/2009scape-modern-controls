package cacheops.cache.definition

import cacheops.cache.buffer.read.Reader

interface ColorPalette {
    var recolourPalette: ByteArray?

    fun readColourPalette(buffer: Reader) {
        val length = buffer.readUnsignedByte()
        recolourPalette = ByteArray(length)
        repeat(length) { count ->
            recolourPalette!![count] = buffer.readByte().toByte()
        }
    }
}