package cacheops.cache.definition.data

import java.awt.Color

data class UnderlayDefinition(
    var color: Int = 0,
    var hue: Int = 0,
    var saturation: Int = 0,
    var lumiance: Int = 0,
    var texture: Int = -1,
    var scale: Int = 128,
    var blockShadow: Boolean = true,
    var blendHueMultiplier: Int = 0,
    var blendHue: Int = 0
) {
    override fun toString(): String {
        return "[RAW(HSL): $color, BlendH: $blendHue, H: $hue, S: $saturation, L: $lumiance, Texture: $texture, Scale: $scale, Shadow: $blockShadow]"
    }

    fun getRGB(): Color {
        return Color(color)
    }
}
