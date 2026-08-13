package misc

import cacheops.cache.definition.encoder.GroundItemEncoder
import cacheops.cache.definition.encoder.NPCSpawnEncoder
import java.awt.Event
import java.awt.event.KeyEvent

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
                    if(Rs2MapEditor.npcsUpdated){
                        NPCSpawnEncoder.write(Rs2MapEditor.npcs)
                    }
                    if(Rs2MapEditor.itemsUpdated){
                        GroundItemEncoder.write(Rs2MapEditor.items)
                    }
                }


            }
        }

        else when(event.keyCode){
            KeyEvent.VK_ESCAPE -> Rs2MapEditor.state = EditorState.NONE
        }
    }
}