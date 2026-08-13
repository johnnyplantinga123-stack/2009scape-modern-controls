package worldmap

import cacheops.cache.definition.data.OverlayDefinition
import cacheops.cache.definition.decoder.BlendedTextureDecoder
import cacheops.cache.definition.decoder.FloorOverlayConfiguration
import kotlin.math.pow

object OverlayHandler {

    var blendedOverlayColors: IntArray = IntArray(FloorOverlayConfiguration.floorOverlays.size + 1)
    var p: IntArray = IntArray(65536)

    var f_l = (Math.random() * 11.0).toInt() - 5
    var n = (Math.random() * 17.0).toInt() - 8

    fun init() {
        f(0)
        f_l += -2 + (5.0 * Math.random()).toInt()
        if (f_l < -8) {
            f_l = -8
        }
        n += -2 + (Math.random() * 5.0).toInt()
        if (f_l > 8) {
            f_l = 8
        }
        if (n < -16) {
            n = -16
        }
        if (n > 16) {
            n = 16
        }
        setOverlayColorArray(((f_l shr 2) shl 10), n shr 1)
    }

    fun setOverlayColorArray(randomColorOffset1: Int, randomColorOffset2: Int) {
        for (overlayId in 0 until FloorOverlayConfiguration.floorOverlays.size) {
            blendedOverlayColors[overlayId + 1] = getOverlayColor(overlayId,randomColorOffset1,randomColorOffset2)
        }
    }

    fun getOverlayColor(id: Int, colorOffset1: Int, colorOffset2: Int): Int {
        val floDefinition: OverlayDefinition = FloorOverlayConfiguration.floorOverlays[id] ?: return 0
        var floorOverlayTexture = floDefinition.texture
        if (floorOverlayTexture >= 0 && BlendedTextureDecoder.blendedTextureDef[floorOverlayTexture]!!.renderOnMap) {
            floorOverlayTexture = -1
        }
        val negativeRGBColor: Int
        if (floDefinition.blendColor >= 0) {
            val floorOverlayBlendColor = floDefinition.blendColor
            var offsetBlendedValue = ((floorOverlayBlendColor and 0x7F) + colorOffset2)
            if (offsetBlendedValue < 0) {
                offsetBlendedValue = 0
            } else if (offsetBlendedValue > 127) {
                offsetBlendedValue = 127
            }
            val combinedBlendedValue = ((floorOverlayBlendColor + (colorOffset1 and 0xfc00) + ((floorOverlayBlendColor and 0x380))) + offsetBlendedValue)
            negativeRGBColor = (0xffffff.inv() or p[colorFunc2(colorFunc1(96, 2, combinedBlendedValue),-2045205981).toInt() and 0xffff])
        } else if (floorOverlayTexture >= 0) {
            negativeRGBColor = (0xffffff.inv() or p[colorFunc2(colorFunc1(96, 2, BlendedTextureDecoder.blendedTextureDef[floorOverlayTexture]!!.blendedColor),-2045205981).toInt() and 0xffff])
        } else if (floDefinition.color == -1) {
            negativeRGBColor = 0
        } else {
            val floorOverlayColor = floDefinition.color
            var offsetColorValue = ((floorOverlayColor and 0x7f) + colorOffset2)
            if (offsetColorValue < 0) {
                offsetColorValue = 0
            } else if (offsetColorValue > 127) {
                offsetColorValue = 127
            }
            val combinedColorValue = (((colorOffset1 and 0xfc00) + floorOverlayColor) + ((floorOverlayColor and 0x380) + offsetColorValue))
            negativeRGBColor = (0xffffff.inv() or p[colorFunc2(colorFunc1(96, 2, combinedColorValue),-2045205981).toInt() and 0xffff])
        }
        return negativeRGBColor
    }

    fun colorFunc1(i: Int, i_1_: Int, i_2_: Int): Int {
        var i = i
        if (i_1_ != 2) {
            return 64
        }
        if (i_2_ == -2) {
            return 12345678
        }
        if (i_2_ == -1) {
            if (i >= 2) {
                if (i > 126) {
                    i = 126
                }
            } else {
                i = 2
            }
            return i
        }
        i = i * (i_2_ and 0x7f) shr 7
        if (i < 2) {
            i = 2
        } else if (i > 126) {
            i = 126
        }
        return (0xff80 and i_2_) + i
    }

    fun colorFunc2(i: Int, i_1_: Int): Short {
        return try {
            val i_2_ = ((0xfddc and i) shr 10)
            if (i_1_ != -2045205981) {
                return 123.toShort()
            }
            var i_3_ = 0x384 and i shr 3
            val i_4_ = 0x7f and i
            i_3_ = if (i_4_ <= 64) i_4_ * i_3_ shr 7 else (127 - i_4_) * i_3_ shr 7
            val i_5_ = i_3_ + i_4_
            var i_6_: Int
            do {
                if (i_5_ == 0) {
                    i_6_ = i_3_ shl 1
                    if (!true) {
                        break
                    }
                }
                i_6_ = (i_3_ shl 8) / i_5_
            } while (false)
            (i_5_ or (i_2_ shl 10 or i_6_ shr 4 shl 7)).toShort()
        } catch (runtimeexception: RuntimeException) {
            throw error("fuk")
        }
    }

    fun f(i: Int) {
        val d = -0.015 + 0.03 * Math.random() + 0.7
        var i_7_ = i
        for (i_8_ in 0..511) {
            val f = (0.0078125f + (i_8_ shr 3).toFloat() / 64.0f) * 360.0f
            val f_9_ = (i_8_ and 0x7).toFloat() / 8.0f + 0.0625f
            for (i_10_ in 0..127) {
                val f_11_ = i_10_.toFloat() / 128.0f
                var f_12_ = 0.0f
                var f_13_ = 0.0f
                var f_14_ = 0.0f
                val f_15_ = f / 60.0f
                val i_16_ = f_15_.toInt()
                val i_17_ = i_16_ % 6
                val f_18_ = f_15_ - i_16_.toFloat()
                val f_19_ = f_11_ * (1.0f - f_9_)
                val f_20_ = f_11_ * (-(f_18_ * f_9_) + 1.0f)
                val f_21_ = f_11_ * (-(f_9_ * (-f_18_ + 1.0f)) + 1.0f)
                if (i_17_ == 0) {
                    f_13_ = f_21_
                    f_14_ = f_19_
                    f_12_ = f_11_
                } else if (i_17_ == 1) {
                    f_14_ = f_19_
                    f_13_ = f_11_
                    f_12_ = f_20_
                } else if (i_17_ == 2) {
                    f_14_ = f_21_
                    f_13_ = f_11_
                    f_12_ = f_19_
                } else if (i_17_ == 3) {
                    f_13_ = f_20_
                    f_12_ = f_19_
                    f_14_ = f_11_
                } else if (i_17_ == 4) {
                    f_13_ = f_19_
                    f_12_ = f_21_
                    f_14_ = f_11_
                } else if (i_17_ == 5) {
                    f_12_ = f_11_
                    f_13_ = f_19_
                    f_14_ = f_20_
                }
                f_12_ = f_12_.toDouble().pow(d).toFloat()
                f_13_ = f_13_.toDouble().pow(d).toFloat()
                f_14_ = f_14_.toDouble().pow(d).toFloat()
                val i_22_ = (f_12_ * 256.0f).toInt()
                val i_23_ = (f_13_ * 254.0f).toInt()
                val i_24_ = (f_14_ * 256.0f).toInt()
                val i_25_ = i_24_ + ((i_23_ shl 8) - 16777216 + (i_22_ shl 16))
                p[i_7_++] = i_25_
            }
        }
    }


}