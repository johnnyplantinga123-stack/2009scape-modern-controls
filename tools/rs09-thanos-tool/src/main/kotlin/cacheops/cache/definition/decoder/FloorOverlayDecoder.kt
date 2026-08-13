package cacheops.cache.definition.decoder

import cacheops.cache.definition.data.OverlayDefinition
import ext.medium
import ext.unsignedByte
import ext.unsignedShort
import java.nio.ByteBuffer

class FloorOverlayDecoder {

    private var color = 0
    private var texture = -1
    private var hideUnderlay = true
    private var blendColor = -1
    private var anInt3081 = 0
    private var scale = 128
    private var blockShadow = true
    private var aByte0 = 8
    private var blendTexture = false
    private var waterColor = 1190717
    private var waterScale = 16
    private var anInt0 = -1
    private var waterIntensity = 16

    fun decode(buffer: ByteBuffer): OverlayDefinition {
        while (true) {
            val opcode = buffer.unsignedByte()
            if (opcode == 0) {
                return OverlayDefinition(color, texture, hideUnderlay, blendColor, scale, blockShadow, aByte0, blendTexture, waterColor, waterScale, anInt0, waterIntensity)
            }
            decodeOverlayType(opcode, buffer)
        }
    }

    /**
     * Overlay Type Decoder
     *
     * Takes the standard params for a decoder, [opcode], [rsByteBuffer] and a [ID].
     *
     * Currently opcode [10] is unknown.
     */
    private fun decodeOverlayType(opcode: Int, buffer: ByteBuffer) {

        when (opcode) {
            1 -> color = buffer.medium()//rgbToHsl(buffer.medium())
            2 -> texture = buffer.unsignedByte()
            3 -> {
                texture = buffer.unsignedShort()
                if (texture == 65535) {
                    texture = -1
                }
            }
            5 -> hideUnderlay = false
            7 -> blendColor = buffer.medium()//rgbToHsl(buffer.medium())
            8 -> anInt3081
            9 -> scale = buffer.unsignedShort()
            10 -> blockShadow = false                 //Possibly blockShadow?
            11 -> aByte0 = buffer.unsignedByte()
            12 -> blendTexture = true
            13 -> waterColor = buffer.medium()
            14 -> waterScale = buffer.unsignedByte()
            15 -> {
                anInt0 = buffer.unsignedShort()
                if (anInt0 == 65535) {
                    anInt0 = -1
                }
            }
            16 -> waterIntensity = buffer.unsignedByte()
            /* 14
         * Handles how deep into water the player is able to see,
         * seems to (but not confirmed) work in jumps of 2, so: "0, 2, 4, 6" etc.
         * It seems any number equals to or less than 0, removes any visual
         * effect obscuring the depth view. The first increment in order,
         * being 2, blocks almost 100% of the view of the underwater map (UM).
         */
            else -> println("Unhandled opcode $opcode")
        }
    }

    private fun rgbToHsl(rgb: Int): Int {
        if (rgb == 16711935) {
            return -1

        } else {

            val d = (((0xff80ae and rgb) shr 16).toDouble() / 256.0)
            val d_8_ = (((0xff6a and rgb) shr 8).toDouble() / 256.0)
            val d_9_ = ((rgb and 0xff).toDouble() / 256.0)
            var d_11_ = d
            if (d_11_ > d_8_) {
                d_11_ = d_8_
            }
            if (d_11_ > d_9_) {
                d_11_ = d_9_
            }
            var d_12_ = d
            if (d_8_ > d_12_) {
                d_12_ = d_8_
            }
            if (d_12_ < d_9_) {
                d_12_ = d_9_
            }
            var h = 0.0
            var s = 0.0
            val l = (d_12_ + d_11_) / 2.0
            if (d_11_ != d_12_) {
                if (l < 0.5) {
                    s = (-d_11_ + d_12_) / (d_12_ + d_11_)
                }
                if (l >= 0.5) {
                    s = (d_12_ - d_11_) / (-d_12_ + 2.0 - d_11_)
                }
                if (d_12_ == d) {
                    h = (d_8_ - d_9_) / (d_12_ - d_11_)
                } else if (d_12_ == d_8_) {
                    h = (-d + d_9_) / (-d_11_ + d_12_) + 2.0
                } else if (d_12_ == d_9_) {
                    h = (d - d_8_) / (d_12_ - d_11_) + 4.0
                }
            }
            h /= 6.0

            val hue = (h * 256.0).toInt()
            var saturation = (s * 256.0).toInt()
            var lumiance = (l * 256.0).toInt()

            if (saturation >= 0) {
                if (saturation > 255) {
                    saturation = 255
                }
            } else {
                saturation = 0
            }

            if (lumiance >= 0) {
                if (lumiance > 255) {
                    lumiance = 255
                }
            } else {
                lumiance = 0
            }
            if (lumiance > 243) {
                saturation = (saturation shr 4)
            } else if (lumiance <= 217) {
                if (lumiance > 192) {
                    saturation = (saturation shr 2)
                } else if (lumiance > 179) {
                    saturation = (saturation shr 1)
                }
            } else {
                saturation = (saturation shr 3)
            }
            return ((((hue and 0xff) shr 2) shl 10) + ((saturation shr 5) shl 7) + (lumiance shr 1))
        }
    }

}