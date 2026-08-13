package misc

import java.awt.Event
import java.awt.event.KeyEvent
import tools.*

var ctrlPressed = false

object GlobalKeybinds{
    fun handle(event: KeyEvent){
        if(event.id == Event.KEY_PRESS){
            if(event.keyCode == KeyEvent.VK_CONTROL) {
                ctrlPressed = true
            }
            return
        }
        if(event.id == Event.KEY_RELEASE){
            if(event.keyCode == KeyEvent.VK_CONTROL) {
                ctrlPressed = false
                return
            }
        }

        if(ctrlPressed){ //Handle keybinds that rely on control being pressed
            when(event.keyCode){
                KeyEvent.VK_S -> { //CTRL+S
		    EditorConstants.saveFocusedEditor()
                }

		KeyEvent.VK_UP -> {
		    if (EditorConstants.FOCUSED_EDITOR != EditorType.SPAWNS) return
		    var newRegion = Util.getRegion (Rs2MapEditor.region, 0, 1)
		    Rs2MapEditor.loadRegion(newRegion)
		}

		KeyEvent.VK_DOWN -> { 
		var newRegion = Util.getRegion (Rs2MapEditor.region, 0, -1)
		    if (EditorConstants.FOCUSED_EDITOR != EditorType.SPAWNS) return
		    Rs2MapEditor.loadRegion(newRegion)
		}

		KeyEvent.VK_LEFT -> {
		    if (EditorConstants.FOCUSED_EDITOR != EditorType.SPAWNS) return
		    var newRegion = Util.getRegion (Rs2MapEditor.region, -1, 0)
		    Rs2MapEditor.loadRegion(newRegion)
		}

		KeyEvent.VK_RIGHT -> {
		    if (EditorConstants.FOCUSED_EDITOR != EditorType.SPAWNS) return
		    var newRegion = Util.getRegion (Rs2MapEditor.region, 1, 0)
		    Rs2MapEditor.loadRegion(newRegion)
		}

            }
        }

        else when(event.keyCode){
            KeyEvent.VK_ESCAPE -> Rs2MapEditor.state = EditorState.NONE
        }
    }
}
