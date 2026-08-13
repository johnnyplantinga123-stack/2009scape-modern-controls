import org.json.simple.JSONObject
import org.json.simple.parser.JSONParser
import java.io.File
import java.io.FileReader
import java.io.FileWriter
import java.lang.Exception

object DataStore {
    val savePath = File(System.getProperty("user.home") + File.separator + ".thanos_tool")

    var LastConfigPath: String = System.getProperty("user.home")

    fun parse() {
        if(!savePath.exists()){
            savePath.mkdirs()
            return
        }
        try {
            val reader = FileReader(savePath.toString() + File.separator + "settings.json")
            val parser = JSONParser().parse(reader) as JSONObject

            if (parser.containsKey("lastConfigPath")) {
                LastConfigPath = parser["lastConfigPath"].toString()
            }
        } catch (ignored: Exception) {}
    }

    fun save() {
        val obj = JSONObject()
        obj["lastConfigPath"] = LastConfigPath
        Logger.logInfo("Saving user preferences...")
        try {
            FileWriter(savePath.toString() + File.separator + "settings.json").use {
                it.write(obj.toJSONString())
                it.flush()
                it.close()
                Logger.logInfo("User preferences saved!")
            }
        } catch (exception: Exception){
            exception.printStackTrace()
        }
    }
}