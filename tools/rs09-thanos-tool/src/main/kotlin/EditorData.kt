import org.json.simple.JSONArray
import org.json.simple.parser.JSONParser
import java.io.File
import java.io.FileReader

open class EditorData(val fileName: String) {
    var reader: FileReader? = null// = FileReader(EditorConstants.CONFIG_PATH + File.separator + fileName)
    val parser = JSONParser()
    var data = JSONArray()

    fun configureParser(){
        reader = FileReader(EditorConstants.CONFIG_PATH + File.separator + fileName)
        data = parser.parse(reader) as JSONArray
    }

    open fun parse() {}
    open fun save() {}
    open fun show() {}
}