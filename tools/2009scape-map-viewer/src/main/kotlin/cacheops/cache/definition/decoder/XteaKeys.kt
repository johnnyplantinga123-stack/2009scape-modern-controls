package cacheops.cache.definition.decoder

import const.xteaJson
import org.json.simple.JSONArray
import org.json.simple.JSONObject
import org.json.simple.parser.JSONParser
import java.io.FileReader

object XteaKeys {
    val XTEAS = HashMap<Int,IntArray>()
    val DEFAULT_REGION_KEYS = intArrayOf(0,0,0,0)

    fun get(regionId: Int): IntArray{
        return XTEAS[regionId] ?: DEFAULT_REGION_KEYS
    }

    val parser = JSONParser()
    var reader: FileReader? = null

    fun load() {
        var count = 0
        reader = FileReader(xteaJson)
        val obj = parser.parse(reader) as JSONObject
        val configlist = obj["xteas"] as JSONArray
        for(config in configlist){
            val e = config as JSONObject
            val id = e["regionId"].toString().toInt()
            val keys = e["keys"].toString().split(",").map(String::toInt)
            XTEAS[id] = intArrayOf(keys[0],keys[1],keys[2],keys[3])
            count++
        }
    }
}