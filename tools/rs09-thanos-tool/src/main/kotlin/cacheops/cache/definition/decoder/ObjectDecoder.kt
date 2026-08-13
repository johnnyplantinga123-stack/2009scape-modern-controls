package cacheops.cache.definition.decoder

import cacheops.cache.Cache
import cacheops.cache.DefinitionDecoder
import cacheops.cache.buffer.read.Reader
import cacheops.cache.definition.data.ObjectDefinition
import const.Indices

open class ObjectDecoder(cache: Cache, val member: Boolean, val configReplace: Boolean) : DefinitionDecoder<ObjectDefinition>(cache, Indices.CONFIGURATION_OBJECT) {

    val DEFINITIONS = HashMap<Int,ObjectDefinition>()

    fun forId(id: Int): ObjectDefinition{
        if(DEFINITIONS[id] != null) return DEFINITIONS[id]!!
        val def = readData(id)
        DEFINITIONS[id] = def ?: ObjectDefinition(id,null,null,"null")
        return DEFINITIONS[id]!!
    }

    override fun create() = ObjectDefinition()

    override fun getFile(id: Int) = id and 0xff

    override fun getArchive(id: Int) = id ushr 8

    override fun readData(id: Int): ObjectDefinition? {
        val def = super.readData(id)
        val replacement = def?.getReplacementId() ?: return def
        return super.readData(replacement)
    }

    private fun ObjectDefinition.getReplacementId(): Int? {
        if (!configReplace) {
            return null
        }
        val configIndex = 0
        val configs = configObjectIds ?: return null
        val config = if (configIndex < 0 || (configIndex >= configs.size - 1 || configs[configIndex] == -1)) {
            configs[configs.size - 1]
        } else {
            configs.getOrNull(configIndex)
        }
        return if (config != -1) config else null
    }

    override fun ObjectDefinition.read(opcode: Int, buffer: Reader) {
        when (opcode) {
            1 -> {
                val count = buffer.readUnsignedByte()
                if (count > 0) {
                    if (modelIds == null) {
                        modelIds = IntArray(count)
                        modelTypes = IntArray(count)
                        var i = 0
                        while (count > i) {
                            modelIds!![i] = buffer.readUnsignedShort()
                            modelTypes!![i] = buffer.readUnsignedByte()
                            i++
                        }
                    } else {
                        buffer.position(buffer.position() + count * 3)
                    }
                }
            }
            2 -> name = buffer.readString()
            5 -> {
                val i_30_ = buffer.readUnsignedByte()
                if (i_30_ > 0) {
                    if (modelIds == null) {
                        modelIds = IntArray(i_30_)
                        modelTypes = null
                        var i_31_ = 0
                        while (i_30_ > i_31_) {
                            modelIds!![i_31_] = buffer.readUnsignedShort()
                            i_31_++
                        }
                    } else {
                        buffer.position(buffer.position() + i_30_ * 2)
                    }
                }
            }
            14 -> sizeX = buffer.readUnsignedByte()
            15 -> sizeY = buffer.readUnsignedByte()
            17 -> {
                blocksSky = false
                solid = 0
            }
            18 -> blocksSky = false
            19 -> interactive = buffer.readUnsignedByte()
            21 -> contouredGround = 1
            22 -> delayShading = true
            23 -> culling = 1
            24 -> {
                val length = buffer.readUnsignedShort()
                if (length != 65535) {
                    animations = intArrayOf(length)
                }
            }
            27 -> solid = 1
            28 -> offsetMultiplier = buffer.readUnsignedByte()
            29 -> brightness = buffer.readByte()
            in 30..34 -> options[opcode - 30] = buffer.readString()
            39 -> contrast = buffer.readByte() * 5
            40 -> readColours(buffer)
            41 -> readTextures(buffer)
            42 -> readColourPalette(buffer)
            62 -> mirrored = true
            64 -> castsShadow = false
            65 -> modelSizeX = buffer.readUnsignedShort()
            66 -> modelSizeZ = buffer.readUnsignedShort()
            67 -> modelSizeY = buffer.readUnsignedShort()
            69 -> blockFlag = buffer.readUnsignedByte()
            70 -> offsetX = buffer.readUnsignedShort() shl 2
            71 -> offsetZ = buffer.readUnsignedShort() shl 2
            72 -> offsetY = buffer.readUnsignedShort() shl 2
            73 -> blocksLand = true
            74 -> ignoreOnRoute = true
            75 -> supportItems = buffer.readUnsignedByte()
            77, 92 -> {
                varbitIndex = buffer.readUnsignedShort()
                if (varbitIndex == 65535) {
                    varbitIndex = -1
                }
                configId = buffer.readUnsignedShort()
                if (configId == 65535) {
                    configId = -1
                }
                var last = -1
                if (opcode == 92) {
                    last = buffer.readUnsignedShort()
                    if (last == 65535) {
                        last = -1
                    }
                }
                val length = buffer.readUnsignedByte()
                configObjectIds = IntArray(length + 2)
                for (count in 0..length) {
                    configObjectIds!![count] = buffer.readUnsignedShort()
                    if (configObjectIds!![count] == 65535) {
                        configObjectIds!![count] = -1
                    }
                }
                configObjectIds!![length + 1] = last
            }
            78 -> {
                anInt3015 = buffer.readUnsignedShort()
                anInt3012 = buffer.readUnsignedByte()
            }
            79 -> {
                anInt2989 = buffer.readUnsignedShort()
                anInt2971 = buffer.readUnsignedShort()
                anInt3012 = buffer.readUnsignedByte()
                val length = buffer.readUnsignedByte()
                anIntArray3036 = IntArray(length)
                repeat(length) { count ->
                    anIntArray3036!![count] = buffer.readUnsignedShort()
                }
            }
            81 -> {
                contouredGround = 2.toByte()
                anInt3023 = buffer.readUnsignedByte() * 256
            }
            82 -> hideMinimap = true
            88 -> aBoolean2972 = false
            89 -> animateImmediately = false
            90 -> aBoolean1502 = true
            91 -> isMembers = true
            93 -> {
                contouredGround = 3
                anInt3023 = buffer.readUnsignedShort()
            }
            94 -> contouredGround = 4
            95 -> {
                contouredGround = 5
            }
            96 -> aBoolean1507 = true
            97 -> adjustMapSceneRotation = true
            98 -> hasAnimation = true
            99 -> {
                anInt2987 = buffer.readUnsignedByte()
                anInt3008 = buffer.readUnsignedShort()
            }
            100 -> {
                anInt3038 = buffer.readUnsignedByte()
                anInt3013 = buffer.readUnsignedShort()
            }
            101 -> mapSceneRotation = buffer.readUnsignedByte()
            102 -> mapscene = buffer.readUnsignedShort()
            103 -> culling = 0
            104 -> anInt3024 = buffer.readUnsignedByte()
            105 -> invertMapScene = true
            106 -> {
                val length = buffer.readUnsignedByte()
                var total = 0
                animations = IntArray(length)
                percents = IntArray(length)
                repeat(length) { count ->
                    animations!![count] = buffer.readUnsignedShort()
                    if (animations!![count] == 65535) {
                        animations!![count] = -1
                    }
                    percents!![count] = buffer.readUnsignedByte()
                    total += percents!![count]
                }
                repeat(length) { count ->
                    percents!![count] = 65535 * percents!![count] / total
                }
            }
            107 -> mapDefinitionId = buffer.readUnsignedShort()
            in 150..154 -> {
                options[-150 + opcode] = buffer.readString()
                if (!member) {
                    options[-150 + opcode] = null
                }
            }
            160 -> {
                val length = buffer.readUnsignedByte()
                anIntArray2981 = IntArray(length)
                repeat(length) { count ->
                    anIntArray2981!![count] = buffer.readUnsignedShort()
                }
            }
            249 -> readParameters(buffer)
        }
    }

    companion object {
        private fun skip(buffer: Reader) {
            val length = buffer.readUnsignedByte()
            repeat(length) {
                buffer.skip(1)
                val amount = buffer.readUnsignedByte()
                buffer.skip(amount * 2)
            }
        }
    }
}