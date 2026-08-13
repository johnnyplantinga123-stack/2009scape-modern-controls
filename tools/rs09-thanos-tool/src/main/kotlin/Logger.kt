import com.github.ajalt.mordant.rendering.TextColors
import com.github.ajalt.mordant.terminal.Terminal
import java.text.SimpleDateFormat
import java.util.*

object Logger {
    val t = Terminal()
    val formatter = SimpleDateFormat("HH:mm:ss")

    fun logInfo(message: String){
        t.println("${getTime()} ${TextColors.white("[INFO] $message")}")
    }

    fun logErr(message: String){
        t.println("${getTime()} ${TextColors.red("[ ERR] $message")}")
    }

    fun getTime(): String{
        return "[" + formatter.format(Date(System.currentTimeMillis())) +"]"
    }
}