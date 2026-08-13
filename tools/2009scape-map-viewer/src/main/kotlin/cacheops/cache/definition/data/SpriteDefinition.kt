package cacheops.cache.definition.data
import cacheops.cache.Definition

@Suppress("ArrayInDataClass")
data class SpriteDefinition(
    override var id: Int = -1,
    var sprites: Array<IndexedSprite>? = null
) : Definition