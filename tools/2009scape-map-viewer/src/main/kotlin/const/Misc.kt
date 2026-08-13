package const

import cacheops.cache.Cache
import cacheops.cache.CacheDelegate

val cachePath = "/home/ceikry/IdeaProjects/rs09-remake/Server/data/cache"
val xteaJson = "/home/ceikry/IdeaProjects/rs09-remake/Server/data/configs/xteas.json"
val cache: Cache = CacheDelegate(cachePath)
