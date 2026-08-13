package cacheops.cache.definition.decoder

import cacheops.cache.definition.data.UnderlayDefinition
import ext.medium
import ext.unsignedByte
import ext.unsignedShort
import java.nio.ByteBuffer

class FloorUnderlayDecoder {

    private var color: Int = 0
    private var hsl16: Int = 0
    private var blendHueMultiplier: Int = 0
    private var hue: Int = 0
    private var saturation: Int = 0
    private var lumiance: Int = 0
    private var textureID = -1
    private var textureSize = 128
    private var blockShadow = true
    private var blendHue: Int = 0

    fun decode(buffer: ByteBuffer): UnderlayDefinition {
        while (true) {
            val opcode = buffer.unsignedByte()
            if (opcode == 0) {
                return UnderlayDefinition(color, hue, saturation, lumiance, textureID, textureSize, blockShadow, blendHueMultiplier, blendHue)
            }
            decodeUnderlayType(opcode, buffer)
        }
    }

    private fun decodeUnderlayType(opcode: Int, buffer: ByteBuffer) {
        when (opcode) {
            1 -> {
                color = buffer.medium()
                intToColor(color)
            }
            2 -> {
                textureID = buffer.unsignedShort()
                if (textureID == 65535) {
                    textureID = -1
                }
            }
            3 -> {
                textureSize = buffer.unsignedShort()
            }
            4 -> {
                blockShadow = false
            }
        }
    }

    private fun intToColor(rgb: Int) {
        val r: Double = (rgb shr 16 and 0xff) / 256.0
        val g: Double = (rgb shr 8 and 0xff) / 256.0
        val b: Double = (rgb and 0xff) / 256.0
        var min = r
        if (g < min) {
            min = g
        }
        if (b < min) {
            min = b
        }
        var max = r
        if (g > max) {
            max = g
        }
        if (b > max) {
            max = b
        }
        var h = 0.0
        var s = 0.0
        val l = (min + max) / 2.0
        if (min != max) {
            if (l < 0.5) {
                s = (max - min) / (max + min)
            }
            if (l >= 0.5) {
                s = (max - min) / (2.0 - max - min)
            }
            if (r == max) {
                h = (g - b) / (max - min)
            } else if (g == max) {
                h = 2.0 + (b - r) / (max - min)
            } else if (b == max) {
                h = 4.0 + (r - g) / (max - min)
            }
        }
        h /= 6.0
        hue = (h * 256.0).toInt()
        saturation = (s * 256.0).toInt()
        lumiance = (l * 256.0).toInt()
        if (saturation < 0) {
            saturation = 0
        } else if (saturation > 255) {
            saturation = 255
        }
        if (lumiance < 0) {
            lumiance = 0
        } else if (lumiance > 255) {
            lumiance = 255
        }
        blendHueMultiplier = if (l > 0.5) {
            ((1.0 - l) * s * 512.0).toInt()
        } else {
            (l * s * 512.0).toInt()
        }
        if (blendHueMultiplier < 1) {
            blendHueMultiplier = 1
        }
        blendHue = (h * blendHueMultiplier).toInt()
        hsl16 = hsl24to16(hue, saturation, lumiance)
    }


    private fun hsl24to16(h: Int, s: Int, l: Int): Int {
        var s = s
        if (l > 179) {
            s /= 2
        }
        if (l > 192) {
            s /= 2
        }
        if (l > 217) {
            s /= 2
        }
        if (l > 243) {
            s /= 2
        }
        return (h / (4 shl 10)) + (s / (32 shl 7)) + l / 2
    }
}
