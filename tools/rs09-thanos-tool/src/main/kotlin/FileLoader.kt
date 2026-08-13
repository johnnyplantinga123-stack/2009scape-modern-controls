import org.json.simple.JSONArray
import org.json.simple.JSONObject
import org.json.simple.parser.JSONParser
import java.io.FileReader
import java.lang.Exception
import javax.swing.*

object FileLoader{
    val loadStatus = JLabel("File not loaded.")
    val loadButton = JButton("Select drop_tables.json")
    val chooser = JFileChooser()
    var dropTableList: TableList? = null

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

    fun load(){
        Logger.logInfo("Parsing Drop Tables...")
        var parser = JSONParser()
        var reader = FileReader(EditorConstants.CONFIG_PATH + "/drop_tables.json")
        var data = parser.parse(reader) as JSONArray
        var counter = 0

        data.forEach { dropTableRaw ->
            val dropTable = dropTableRaw as JSONObject
            val table = NPCDropTable()
            table.ids = dropTable["ids"].toString()
            parseTable(dropTable["main"] as JSONArray, table, false)
            parseTable(dropTable["default"] as JSONArray, table, true)
            parseTable(dropTable["charm"] as JSONArray, table.charmTable, false)
            (dropTable["tertiary"] as? JSONArray)?.let { parseTable(it, table.tertiaryTable, false) }
            table.description = (dropTable["description"] ?: "").toString()
            TableData.tables.add(table)
            counter++
        }
        Logger.logInfo("Parsed Drop Tables.")
        Logger.logInfo("Loaded $counter drop tables.")

        Logger.logInfo("Parsing Item Configs...")
        try {
            data = parser.parse(FileReader(EditorConstants.CONFIG_PATH + "/item_configs.json")) as JSONArray
            data.forEach { itemDataRaw ->
                val itemData = itemDataRaw as JSONObject
                val id = itemData["id"].toString().toInt()
                val name = itemData["name"].toString()
                TableData.itemNames[id] = name
            }
            Logger.logInfo("Parsed item configs.")
        } catch (e: Exception){
            Logger.logErr("Failed to load item_configs.json. Item names will be unavailable when viewing and editing shops.")
        }
        Logger.logInfo("Parsing NPC configs...")
        try {
            data = parser.parse(FileReader(EditorConstants.CONFIG_PATH + "/npc_configs.json")) as JSONArray
            data.forEach { npcDataRaw ->
                val npcData = npcDataRaw as JSONObject
                val id = npcData["id"].toString().toInt()
                val name = npcData["name"].toString()
                TableData.npcNames[id] = name
            }
            Logger.logInfo("Parsed NPC configs")
        } catch (e: Exception){
            Logger.logErr("Failed to load npc_configs.json. Drop tables will lack proper naming. Good luck figuring out what you want to edit.")
        }

    }
}

class Item(var id: Int, var amount: String, var infinite: Boolean, var restockTime: Int = 100)
class WeightedItem(var id: Int, var minAmt: String, var maxAmt: String, var weight: Double, var isAlways: Boolean)

private val CHARMS = intArrayOf(12160,12163,12159,12158)

open class WeightBasedTable() : ArrayList<WeightedItem>(){
    var totalWeight: Double = 0.0
    override fun add(element: WeightedItem): Boolean {
        totalWeight += element.weight
        return super.add(element)
    }
}

class NPCDropTable() : WeightBasedTable(){
    val charmTable = WeightBasedTable()
    val alwaysTable = WeightBasedTable()
    val tertiaryTable = WeightBasedTable()

    var ids = ""
    var description = ""
    override fun add(element: WeightedItem): Boolean {
        if(element.isAlways){
            alwaysTable.add(element)
            return true
        }
        if(CHARMS.contains(element.id)){
            charmTable.add(element)
            return true
        }
        return super.add(element)
    }
}
