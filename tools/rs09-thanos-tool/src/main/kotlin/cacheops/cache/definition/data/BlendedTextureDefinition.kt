package cacheops.cache.definition.data

import cacheops.cache.Definition

data class BlendedTextureDefinition(
    override var id: Int = -1,
    var renderOnMap: Boolean = false,
    var blendedColor: Int = -1
) : Definition