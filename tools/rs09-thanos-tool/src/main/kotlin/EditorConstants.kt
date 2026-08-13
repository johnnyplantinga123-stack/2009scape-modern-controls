import com.github.weisj.darklaf.LafManager
import com.github.weisj.darklaf.theme.OneDarkTheme
import javax.swing.*
import java.awt.event.*

object EditorConstants {
    var BUILD_NUMBER = "2.0.1"

    var DARK_MODE = true

    var CONFIG_PATH = ""
    
    var CACHE_PATH = ""

    var VALID_FILES = arrayOf("drop_tables.json","npc_configs.json","item_configs.json", "shops.json", "object_configs.json", "npc_spawns.json", "ground_spawns.json", "xteas.json")

    var CREDITS = arrayOf(
        "weisJ - darklaf look and feel themes http://github.com/weisj",
        "ceikry - design and programming http://gitlab.com/ceikry",
	"woahscam - design and programming http://gitlab.com/woahscam"
    )

    fun updateTheme(){
        if(EditorConstants.DARK_MODE) LafManager.install(OneDarkTheme()) else LafManager.install()
    }

    var FOCUSED_EDITOR: EditorType = EditorType.NONE

    fun setFocusedEditor (type: EditorType) {
    	Logger.logInfo("Set focused editor to ${type.name}")
	FOCUSED_EDITOR = type
    }

    fun saveFocusedEditor () {
	when (FOCUSED_EDITOR) {
	    EditorType.NONE -> Logger.logInfo("NO EDITOR FOCUSED - NOT SAVING")
	    EditorType.SPAWNS -> {
	        if (Rs2MapEditor.npcsUpdated)
		    Editors.NPC_SPAWNS.data.save()
		if (Rs2MapEditor.itemsUpdated)
		    Editors.ITEM_SPAWNS.data.save()
	    }
	    EditorType.ITEM_CONFIGS -> Editors.ITEM_CONFIGS.data.save()
	    EditorType.NPC_CONFIGS -> Editors.NPC_CONFIGS.data.save()
	    EditorType.OBJECT_CONFIGS -> Editors.OBJECT_CONFIGS.data.save()
	    EditorType.SHOPS -> Editors.SHOPS.data.save()
	    EditorType.DROP_TABLES -> Editors.DROP_TABLES.data.save()
    	}
	Logger.logInfo("Saving ${FOCUSED_EDITOR.name}...")
	JOptionPane.showMessageDialog(null, "Saved ${FOCUSED_EDITOR.name.toLowerCase().replace('_', ' ')}!")
    }
}

class EditorFocusListener(val type: EditorType) : WindowFocusListener {
    override fun windowLostFocus(e: WindowEvent) {}
    override fun windowGainedFocus(e: WindowEvent) { EditorConstants.setFocusedEditor(type) }
}

enum class EditorType {
    SPAWNS,
    ITEM_CONFIGS,
    NPC_CONFIGS,
    OBJECT_CONFIGS,
    SHOPS,
    DROP_TABLES,
    NONE
}
