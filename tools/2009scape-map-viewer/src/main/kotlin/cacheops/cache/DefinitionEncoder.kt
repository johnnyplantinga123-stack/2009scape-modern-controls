package cacheops.cache

import cacheops.cache.buffer.write.Writer

interface DefinitionEncoder<T : Definition> {
    fun Writer.encode(definition: T)
}