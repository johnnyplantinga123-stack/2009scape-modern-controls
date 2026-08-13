import org.json.simple.JSONArray
import org.json.simple.JSONObject
import tools.Util
import java.io.File
import java.io.FileWriter
import java.io.IOException
import java.lang.StringBuilder
import javax.script.ScriptEngineManager
import cacheops.cache.definition.decoder.ItemParser
import cacheops.cache.definition.decoder.NPCParser

object TableData {
    class Shop(var id: Int,var title: String,var stock: ArrayList<Item>,var npcs: String,var currency: Int,var general_store: Boolean,var high_alch: Boolean,var forceShared: Boolean)
    data class NPCSpawn(var id: Int, var location: Location, var canWalk: Boolean, var spawnDirection: Int)
    data class ItemSpawn(var id: Int, var location: Location, var respawnTicks: Int, var amount: Int)
    data class Location(val x: Int, val y: Int, val z: Int) {
        val localCoords = Util.getRegionCoordinates(this)
    }
    val tables = ArrayList<NPCDropTable>()
    val itemNames = hashMapOf<Int,String>()
    val npcNames = hashMapOf<Int,String>()
    val npcConfigKeys = HashSet<String>()
    val npcConfigs = ArrayList<JSONObject>()
    var npcSpawns = HashMap<Int, ArrayList<NPCSpawn>>()
    var itemSpawns = HashMap<Int, ArrayList<ItemSpawn>>()
    val objConfigKeys = HashSet<String>()
    val objConfigs = ArrayList<JSONObject>()
    var itemConfigKeys = HashSet<String>()
    var itemConfigs = ArrayList<JSONObject>()
    val shops = hashMapOf<Int,Shop>()

    fun getItemName(id: Int, dropTable: Boolean = true): String{
        if (dropTable) {
		return when(id){
		    31 -> "RDT Slot"
		    1 -> "Clue Scroll (easy)"
		    5733 -> "Clue Scroll (med)"
		    12070 -> "Clue Scroll (hard)"
		    0 -> "Nothing"
		    else -> itemNames[id] ?: ItemParser.forId(id)?.name ?: "Undefined"
		}
	}
	else return itemNames[id] ?: ItemParser.forId(id)?.name ?: "Undefined"
    }

    fun getNPCName(id: Int) : String {
	return npcNames[id] ?: NPCParser.getDef(id)?.name ?: "Undefined"
    }
}

