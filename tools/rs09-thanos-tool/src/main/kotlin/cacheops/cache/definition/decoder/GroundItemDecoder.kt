package cacheops.cache.definition.decoder

import cacheops.cache.definition.data.ItemDefinition
import const.cache

object ItemParser {
    var decoder: ItemDecoder? = null
    fun forId (id: Int): ItemDefinition? {
	if (decoder == null && cache == null) return null
	if (decoder == null && cache != null) decoder = ItemDecoder(cache!!)
	return decoder?.forId(id)
    }
}
