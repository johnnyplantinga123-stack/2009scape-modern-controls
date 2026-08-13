package misc

import java.awt.AWTEvent
import java.awt.Event
import java.awt.EventQueue
import java.awt.Toolkit
import java.awt.event.KeyEvent

class CustomEventQueue : EventQueue() {
    override fun dispatchEvent(event: AWTEvent?) {
        if(event is KeyEvent){
            GlobalKeybinds.handle(event)
        }
        super.dispatchEvent(event)
    }

    companion object {
        fun install() {
            Toolkit.getDefaultToolkit().systemEventQueue.push(CustomEventQueue())
        }
    }
}