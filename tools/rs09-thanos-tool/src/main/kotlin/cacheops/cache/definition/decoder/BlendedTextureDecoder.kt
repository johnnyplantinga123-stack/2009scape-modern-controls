package cacheops.cache.definition.decoder

import cacheops.cache.Cache
import cacheops.cache.DefinitionDecoder
import cacheops.cache.buffer.read.BufferReader
import cacheops.cache.buffer.read.Reader
import cacheops.cache.definition.data.BlendedTextureDefinition
import const.Indices

class BlendedTextureDecoder(cache: Cache) : DefinitionDecoder<BlendedTextureDefinition>(cache, Indices.MATERIALS) {

    companion object {
        val blendedTextureDef = HashMap<Int, BlendedTextureDefinition>()
    }

    override fun create() = BlendedTextureDefinition()

    var metricsCount = 0

    override val last: Int
        get() = metricsCount

    init {
        val data = getData(0, 0)
        if (data != null) {
            decode(BufferReader(data))
        }
    }

    fun decode(buffer: Reader) {
        metricsCount = buffer.readShort()
        for (id in 0 until metricsCount) {
            if (buffer.readUnsignedBoolean()) {
                blendedTextureDef[id] = BlendedTextureDefinition(id = id)
            }
        }

        for (texture in blendedTextureDef.values) { // J
            texture.renderOnMap = !buffer.readUnsignedBoolean() // 0
        }
        for (texture in blendedTextureDef.values) {
            buffer.readUnsignedBoolean() // 1
        }
        for (texture in blendedTextureDef.values) {
            buffer.readUnsignedBoolean() // 1
        }
        for (texture in blendedTextureDef.values) {
            buffer.readByte().toByte() // 1
        }
        for (texture in blendedTextureDef.values) {
            buffer.readByte().toByte()
        }
        for (texture in blendedTextureDef.values) {
            buffer.readByte().toByte()
        }
        for (texture in blendedTextureDef.values) {
            buffer.readByte().toByte()
        }
        for (texture in blendedTextureDef.values) {
            texture.blendedColor = buffer.readUnsignedShort()
            println(texture.blendedColor)
        }
        for (texture in blendedTextureDef.values) {
            buffer.readByte().toByte()
        }
        for (texture in blendedTextureDef.values) {
            buffer.readByte().toByte()
        }
        for (texture in blendedTextureDef.values) {
            buffer.readUnsignedBoolean()
        }
        for (texture in blendedTextureDef.values) {
            buffer.readUnsignedBoolean()
        }
        for (texture in blendedTextureDef.values) {
            buffer.readByte().toByte()
        }
        for (texture in blendedTextureDef.values) {
            buffer.readUnsignedBoolean()
        }
        for (texture in blendedTextureDef.values) {
            buffer.readUnsignedBoolean()
        }
        for (texture in blendedTextureDef.values) {
            buffer.readUnsignedBoolean()
        }
    }

    override fun clear() {
    }

    override fun BlendedTextureDefinition.read(opcode: Int, buffer: Reader) {
        throw IllegalStateException("Shouldn't be used.")
    }
}