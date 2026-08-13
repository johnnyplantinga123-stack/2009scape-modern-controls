package cacheops.cache.definition.data

import java.awt.Color

data class OverlayDefinition(
    var color: Int = 0,
    var texture: Int = -1,
    var hideUnderlay: Boolean = true,
    var blendColor: Int = -1,
    var scale: Int = 128,
    var blockShadow: Boolean = true,
    var aByte0: Int = 8,
    var blendTexture: Boolean = false,
    var waterColor: Int = 1190717,
    var waterScale: Int = 16,
    var anInt0: Int = -1,
    var waterIntensity: Int = 16
) {
    override fun toString(): String {
        return ""
    }

    fun getRGB(): Color {
        return Color(color)
    }
}
