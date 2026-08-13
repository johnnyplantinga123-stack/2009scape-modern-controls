package cacheops.cache.definition.data

import cacheops.cache.Definition
import cacheops.cache.definition.Extra

@Suppress("ArrayInDataClass")
data class InterfaceDefinition(
    override var id: Int = -1,
    var components: Map<Int, InterfaceComponentDefinition>? = null,
    override var extras: Map<String, Any> = emptyMap()
) : Definition, Extra