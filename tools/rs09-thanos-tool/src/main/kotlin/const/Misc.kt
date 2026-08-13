package const

import cacheops.cache.Cache
import cacheops.cache.CacheDelegate

var cache: Cache? = null
var xteaJson = ""

fun configureCacheDelegate (pathToCache: String, pathToXteas: String) {
    cache = CacheDelegate(pathToCache)
    xteaJson = pathToXteas
}
