package cacheops.cache.definition

import cacheops.cache.buffer.read.Reader

interface Recolourable {
    var originalColours: ShortArray?
    var modifiedColours: ShortArray?
    var originalTextureColours: ShortArray?
    var modifiedTextureColours: ShortArray?

    fun readColours(buffer: Reader) {
        val length = buffer.readUnsignedByte()
        originalColours = ShortArray(length)
        modifiedColours = ShortArray(length)
        repeat(length) { count ->
            originalColours!![count] = buffer.readUnsignedShort().toShort()
            modifiedColours!![count] = buffer.readUnsignedShort().toShort()
        }
    }

    fun readTextures(buffer: Reader) {
        val length = buffer.readUnsignedByte()
        originalTextureColours = ShortArray(length)
        modifiedTextureColours = ShortArray(length)
        repeat(length) { count ->
            originalTextureColours!![count] = buffer.readUnsignedShort().toShort()
            modifiedTextureColours!![count] = buffer.readUnsignedShort().toShort()
        }
    }
}