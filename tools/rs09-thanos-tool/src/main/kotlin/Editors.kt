import org.json.simple.JSONArray
import org.json.simple.JSONObject
import org.json.simple.parser.JSONParser
import tools.Util
import java.io.File
import java.io.FileReader
import java.io.FileWriter
import java.io.IOException
import java.lang.Exception
import java.lang.StringBuilder
import javax.script.ScriptEngineManager
import kotlin.io.println

enum class Editors(val data: EditorData) {
    DROP_TABLES(object : EditorData("drop_tables.json"){
        var counter = 0
        override fun parse() {
            Logger.logInfo("Parsing Drop Tables")
            configureParser()
            val usedIds = ArrayList<Int>()
            data.forEach dataLoop@ { dropTableRaw ->
                val dropTable = dropTableRaw as JSONObject
                val table = NPCDropTable()
                table.ids = dropTable["ids"].toString()
                table.ids.split(",").map { it.toInt() }.forEach {id ->
                    if(usedIds.contains(id)) {
                        println("Detected duplicate ID, ignoring table: ")
                        println(dropTableRaw.toJSONString())
                        return@dataLoop
                    }
                    usedIds.add(id)
                }
                parseTable(dropTable["main"] as JSONArray,table,false)
                parseTable(dropTable["default"] as JSONArray,table,true)
                parseTable(dropTable["charm"] as JSONArray,table.charmTable,false)
                (dropTable["tertiary"] as? JSONArray)?.let {
                    Logger.logInfo("Found tertiary table for " + table.ids)
                    parseTable(it, table.tertiaryTable, false)
                }
                table.description = (dropTable["description"] ?: "").toString()
                TableData.tables.add(table)
                counter++
            }
            Logger.logInfo("$counter Drop Tables Parsed.")
        }

        override fun save() {
            val CHARMS = intArrayOf(12160,12163,12159,12158)
            val dtables = JSONArray()
            for(table in TableData.tables){
                val t = JSONObject()
                t.put("ids",table.ids)
                val main = saveTable(table)
                val charms = saveTable(table.charmTable)
                val always = saveTable(table.alwaysTable)
                val tertiary = saveTable(table.tertiaryTable)
                if (tertiary.isNotEmpty())
                    t.put("tertiary", tertiary)
                t.put("main",main)
                t.put("charm",charms)
                t.put("default",always)
                t.put("description",table.description)
                dtables.add(t)
            }


            val manager = ScriptEngineManager()
            val scriptEngine = manager.getEngineByName("JavaScript")
            scriptEngine.put("jsonString", dtables.toJSONString())
            scriptEngine.eval("result = JSON.stringify(JSON.parse(jsonString), null, 2)")
            val prettyPrintedJson = scriptEngine["result"] as String

            try {
                FileWriter(EditorConstants.CONFIG_PATH + File.separator + fileName).use { file ->
                    file.write(prettyPrintedJson)
                    file.flush()
                }
            } catch (e: IOException) {
                e.printStackTrace()
            }
        }

        override fun show() {
            super.show()
        }
    }),
    NPC_CONFIGS(object : EditorData("npc_configs.json"){
        override fun parse() {
            Logger.logInfo("Parsing NPC configs")
            configureParser()
            val usedIds = ArrayList<Int>()
            data.forEach { npcDataRaw ->
                val npcData = npcDataRaw as JSONObject
                val id = npcData["id"].toString().toInt()
                if (usedIds.contains(id)) {
                    println("Duplicate NPC Config detected, ignoring config entry:")
                    println(npcDataRaw.toJSONString())
                    return@forEach
                }
                usedIds.add(id)
                val name = npcData["name"].toString()
                TableData.npcNames[id] = name
                TableData.npcConfigKeys.addAll(npcData.keys.toHashSet() as HashSet<String>)
                TableData.npcConfigs.add(npcData)
            }
        }

        override fun save() {
            val array = JSONArray()
            TableData.npcConfigs.forEach { array.add(it) }

            val prettyPrintedJson = generateJSON(array)

            try {
                FileWriter(EditorConstants.CONFIG_PATH + File.separator + fileName).use { file ->
                    file.write(prettyPrintedJson)
                    file.flush()
                }
            } catch (e: IOException) {
                e.printStackTrace()
            }
        }
    }),
    ITEM_CONFIGS(object : EditorData("item_configs.json"){
        override fun parse() {
            configureParser()
            val usedIds = ArrayList<Int>()
            data.forEach { itemDataRaw ->
                val itemData = itemDataRaw as JSONObject
                val id = itemData["id"].toString().toInt()
                if (usedIds.contains(id)) {
                    println("Duplicate Item Config detected, ignoring config entry:")
                    println(itemDataRaw.toJSONString())
                    return@forEach
                }
                val name = itemData["name"].toString()
                TableData.itemConfigKeys.addAll(itemData.keys.toHashSet() as HashSet<String>)
                TableData.itemNames[id] = name
                TableData.itemConfigs.add(itemData)
            }
        }

        override fun save() {
            val array = JSONArray()
            TableData.itemConfigs.forEach { array.add(it) }
            val prettyPrintedJson = generateJSON(array)


            try {
                FileWriter(EditorConstants.CONFIG_PATH + File.separator + fileName).use { file ->
                    file.write(prettyPrintedJson)
                    file.flush()
                }
            } catch (e: IOException) {
                e.printStackTrace()
            }
        }
    }),
    OBJECT_CONFIGS(object : EditorData("object_configs.json"){
        override fun parse() {
            Logger.logInfo("Parsing Object configs")
            configureParser()
            data.forEach { ObjDataRaw ->
                val objData = ObjDataRaw as JSONObject
                TableData.objConfigKeys.addAll(objData.keys.toHashSet() as HashSet<String>)
                TableData.objConfigs.add(objData)
            }
        }

        override fun save() {
            val array = JSONArray()
            TableData.objConfigs.forEach { array.add(it) }
            val sortedList = array
                .map { it as JSONObject }
                .sortedBy { (it["ids"] as String) }
            val sortedArray = JSONArray()
            sortedList.forEach { sortedArray.add(it) }
            val manager = ScriptEngineManager()
            val scriptEngine = manager.getEngineByName("JavaScript")
            scriptEngine.put("jsonString", sortedArray.toJSONString())
            scriptEngine.eval("result = JSON.stringify(JSON.parse(jsonString), null, 2)")
            val prettyPrintedJson = scriptEngine["result"] as String

            try {
                FileWriter(EditorConstants.CONFIG_PATH + File.separator + fileName).use { file ->
                    file.write(prettyPrintedJson)
                    file.flush()
                }
            } catch (e: IOException) {
                e.printStackTrace()
            }
        }
    }),
    SHOPS(object : EditorData("shops.json"){
        override fun parse() {
            Logger.logInfo("Parsing shop data...")
            configureParser()
            var counter = 0

            data.forEach { shopDataRaw ->
                val shopData = shopDataRaw as JSONObject
                val id = shopData["id"].toString().toInt()
                val title = shopData["title"].toString()
                val general = shopData["general_store"].toString().toBoolean()
                val stock = parseStock(shopData["stock"].toString())
                val npcs = if(shopData["npcs"].toString().isNotBlank()) shopData["npcs"].toString() else ""
                val currency = shopData["currency"].toString().toInt()
                val highAlch = shopData["high_alch"].toString() == "1"
                val forceShared = shopData.getOrDefault("force_shared", "false").toString().toBoolean()
                TableData.shops[id] = TableData.Shop(id,title,stock,npcs,currency,general,highAlch, forceShared)
                counter++
            }

            Logger.logInfo("Loaded $counter shops.")
        }

        override fun save() {
            val shs = JSONArray()

            fun <T> ArrayList<T>.isLast(thing: T): Boolean{
                return indexOf(thing) == size - 1
            }

            for((_,shop) in TableData.shops){
                val sh = JSONObject()
                sh.put("id",shop.id.toString())
                sh.put("title",shop.title)
                val stockBuilder = StringBuilder()
                for(item in shop.stock){
                    stockBuilder.append("{${item.id},${if(item.infinite) "inf" else item.amount},${item.restockTime}}${if(shop.stock.isLast(item)) "" else "-"}")
                }
                sh.put("stock",stockBuilder.toString())
                sh.put("npcs",shop.npcs)
                sh.put("currency",shop.currency.toString())
                sh.put("high_alch",if(shop.high_alch) "1" else "0")
                sh.put("general_store",shop.general_store.toString())
                if (shop.forceShared)
                    sh.put("force_shared", shop.forceShared.toString())
                shs.add(sh)
            }


            val manager = ScriptEngineManager()
            val scriptEngine = manager.getEngineByName("JavaScript")
            scriptEngine.put("jsonString", shs.toJSONString())
            scriptEngine.eval("result = JSON.stringify(JSON.parse(jsonString), null, 2)")
            val prettyPrintedJson = scriptEngine["result"] as String

            try {
                FileWriter(EditorConstants.CONFIG_PATH + File.separator + fileName).use { file ->
                    file.write(prettyPrintedJson)
                    file.flush()
                }
            } catch (e: IOException) {
                e.printStackTrace()
            }
        }
    }),
    NPC_SPAWNS(object : EditorData("npc_spawns.json") {
        override fun parse() {
            Logger.logInfo("Parsing npc spawn data...")
            val spawnMap = HashMap<Int, ArrayList<TableData.NPCSpawn>>()
            configureParser()
            data.forEach { spawnRaw ->
                val spawn = spawnRaw as JSONObject
                val id = spawn["npc_id"].toString().toInt()
                val datas: Array<String> = spawn["loc_data"].toString().split("-".toRegex()).toTypedArray()
                var tokens: Array<String>
                for (d in datas) {
                    if(d.isEmpty()){
                        continue
                    }
                    tokens = d.replace("{", "").replace("}", "").split(",".toRegex()).toTypedArray()
                    val isWalks = tokens[3].trim { it <= ' ' } == "1"
                    val spawnDirection = Integer.valueOf(tokens[4].trim { it <= ' ' })
                    val spawnCoords = intArrayOf(Integer.valueOf(tokens[0].trim { it <= ' ' }), Integer.valueOf(tokens[1].trim { it <= ' ' }), Integer.valueOf(tokens[2].trim { it <= ' ' }))
                    val npc = TableData.NPCSpawn(id, TableData.Location(spawnCoords[0], spawnCoords[1], spawnCoords[2]), isWalks, spawnDirection)
                    val regionId = Util.getRegionId(npc.location)
                    if (!spawnMap.containsKey(regionId))
                        spawnMap[regionId] = ArrayList()
                    spawnMap[regionId]!!.add(npc)
                }
            }
            TableData.npcSpawns = spawnMap
        }

	override fun save() {
	    Logger.logInfo("Saving NPC spawn info...")
	    val spawnMap = HashMap<Int, StringBuilder>()
	    for ((_, spawns) in TableData.npcSpawns) {
	    	for (spawn in spawns) {
		    if (!spawnMap.contains(spawn.id)) spawnMap[spawn.id] = StringBuilder()
		    spawnMap[spawn.id]!!.append("{${spawn.location.x},${spawn.location.y},${spawn.location.z},${if(spawn.canWalk) 1 else 0},${spawn.spawnDirection}}-")
		}
	    }
	    val spawnsJsonArray = JSONArray()
	    for ((id, spawnString) in spawnMap.entries.sortedBy { it.key.toInt() }) {
		val spawn = JSONObject()
		spawn["npc_id"] = id.toString()
		spawn["loc_data"] = spawnString.toString()
		spawnsJsonArray.add(spawn)
	    }
	    val manager = ScriptEngineManager()
            val scriptEngine = manager.getEngineByName("JavaScript")
            scriptEngine.put("jsonString", spawnsJsonArray.toJSONString())
            scriptEngine.eval("result = JSON.stringify(JSON.parse(jsonString), null, 2)")
            val prettyPrintedJson = scriptEngine["result"] as String

            try {
                FileWriter(EditorConstants.CONFIG_PATH + File.separator + fileName).use { file ->
                    file.write(prettyPrintedJson)
                    file.flush()
                }
            } catch (e: IOException) {
                e.printStackTrace()
            }
	}
    }),
    ITEM_SPAWNS(object : EditorData("ground_spawns.json") {
	override fun parse() {
	    Logger.logInfo("Parsing ground spawn data...")
	    val spawnMap = HashMap<Int, ArrayList<TableData.ItemSpawn>>()
	    configureParser()
            data.forEach { spawnRaw ->
                val spawn = spawnRaw as JSONObject
                val id = spawn["item_id"].toString().toInt()
                val datas: Array<String> = spawn["loc_data"].toString().split("-".toRegex()).toTypedArray()
                var tokens: Array<String>
                for (d in datas) {
                    if(d.isEmpty()){
                        continue
                    }
                    tokens = d.replace("{", "").replace("}", "").split(",".toRegex()).toTypedArray()
		    val amount = tokens[0].trim { it <= ' ' }.toInt()
                    val spawnCoords = intArrayOf(Integer.valueOf(tokens[1].trim { it <= ' ' }), Integer.valueOf(tokens[2].trim { it <= ' ' }), Integer.valueOf(tokens[3].trim { it <= ' ' }))
		    val respawnTime = tokens[4].trim { it <= ' ' }.toInt() and 0xFF
		    val item = TableData.ItemSpawn(id, TableData.Location(spawnCoords[0], spawnCoords[1], spawnCoords[2]), respawnTime, amount)
                    val regionId = Util.getRegionId(item.location)
                    if (!spawnMap.containsKey(regionId))
                        spawnMap[regionId] = ArrayList()
                    spawnMap[regionId]!!.add(item)
                }
            }
            TableData.itemSpawns = spawnMap
	}

	override fun save() {
	    Logger.logInfo("Saving Item spawn info...")
	    val spawnMap = HashMap<Int, StringBuilder>()
	    for ((_, spawns) in TableData.itemSpawns) {
	    	for (spawn in spawns) {
		    if (!spawnMap.contains(spawn.id)) spawnMap[spawn.id] = StringBuilder()
		    spawnMap[spawn.id]!!.append("{${spawn.amount},${spawn.location.x},${spawn.location.y},${spawn.location.z},${spawn.respawnTicks}}-")
		}
	    }
	    val spawnsJsonArray = JSONArray()
	    for ((id, spawnString) in spawnMap.entries.sortedBy { it.key.toInt() }) {
		val spawn = JSONObject()
		spawn["item_id"] = id.toString()
		spawn["loc_data"] = spawnString.toString()
		spawnsJsonArray.add(spawn)
	    }
	    val manager = ScriptEngineManager()
            val scriptEngine = manager.getEngineByName("JavaScript")
            scriptEngine.put("jsonString", spawnsJsonArray.toJSONString())
            scriptEngine.eval("result = JSON.stringify(JSON.parse(jsonString), null, 2)")
            val prettyPrintedJson = scriptEngine["result"] as String

            try {
                FileWriter(EditorConstants.CONFIG_PATH + File.separator + fileName).use { file ->
                    file.write(prettyPrintedJson)
                    file.flush()
                }
            } catch (e: IOException) {
                e.printStackTrace()
            }
	}
    })
}


fun parseStock(stock: String): ArrayList<Item>{
    val items = ArrayList<Item>()
    if(stock.isEmpty()){
        return items
    }
    stock.split('-').map {
        val tokens = it.replace("{", "").replace("}", "").split(",".toRegex()).toTypedArray()
        var amount = tokens[1].trim()
        items.add(Item(tokens[0].trim().toInt(),amount,amount == "inf", tokens.getOrNull(2)?.toIntOrNull() ?: 100))
    }
    return items
}
private fun parseTable(data: JSONArray, destTable: WeightBasedTable, isAlways: Boolean) {
    for(it in data){
        val item = it as JSONObject
        val id = item["id"].toString().toInt()
        val minAmount = item["minAmount"].toString().toInt()
        val maxAmount = item["maxAmount"].toString().toInt()
        val weight = item["weight"].toString().toDouble()
        val newItem = WeightedItem(id,minAmount.toString(),maxAmount.toString(),weight.toDouble(),isAlways)
        destTable.add(newItem)
    }
}
private fun saveTable(table: WeightBasedTable): JSONArray{
    val arr = JSONArray()
    for(item in table){
        val it = JSONObject()
        it.put("id",item.id.toString())
        it.put("weight",item.weight.toString())
        it.put("minAmount",item.minAmt.toString())
        it.put("maxAmount",item.maxAmt.toString())
        arr.add(it)
    }
    return arr
}

/**
 * Sorts and generates a string from an unsorted JSONArray.
 * Currently this is only used by NPC_Config or item_config as it assumes each entry has 1 ID.
 * Most other tables have some collection of IDs
 * ID name and examine will be first if present then everything else will be alphabetical
 * If other things wish to be sorted add them to the when statement
 * @param array the JSONArray from npc_config or item_config
 * @return The string ready to write out
 */
private fun generateJSON(array: JSONArray): String{
    val sortedArray = array
        .map{ it as JSONObject }
        // Sort IDs
        .sortedBy { (it["id"] as String).toIntOrNull() ?: Int.MAX_VALUE }
        .map { itemJSONObj ->
            val sortedKeys = itemJSONObj.keys
                .asSequence()
                .map { it as String}
                .sortedWith(compareBy<String> { key ->
                    when (key) {
                        "id" -> 0
                        "name" -> 1
                        "examine" -> 2
                        else -> 3
                    }
                }.thenBy { it })
                .toList()

            val orderedItem = LinkedHashMap<String, Any?>()
            for (key in sortedKeys){
                orderedItem[key] = itemJSONObj[key]
            }
            orderedItem
        }
    val sortedJSONArray = JSONArray()
    sortedArray.forEach { sortedJSONArray.add(it) }

    val manager = ScriptEngineManager()
    val scriptEngine = manager.getEngineByName("JavaScript")
    scriptEngine.put("jsonString", sortedJSONArray.toJSONString())
    scriptEngine.eval("result = JSON.stringify(JSON.parse(jsonString), null, 2)")
    val prettyPrintedJson = scriptEngine["result"] as String
    return prettyPrintedJson
}