package cacheops.cache.definition.data
import cacheops.cache.Definition

data class VarBitDefinition(
    override var id: Int = -1,
    var index: Int = 0,
    var leastSignificantBit: Int = 0,
    var mostSignificantBit: Int = 0
) : Definition