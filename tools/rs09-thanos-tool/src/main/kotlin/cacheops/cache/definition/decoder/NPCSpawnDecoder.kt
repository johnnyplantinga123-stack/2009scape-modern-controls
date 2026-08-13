package cacheops.cache.definition.decoder

import cacheops.cache.definition.data.NPCDefinition
import const.cache


object NPCParser {
    var decoder: NPCDecoder? = null

    fun getDef (id: Int) : NPCDefinition? {
	if (decoder == null && cache == null) return null
	if (decoder == null && cache != null) decoder = NPCDecoder(cache!!, true)
	return decoder?.forId(id)
    }
}
