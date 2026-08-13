package ui

import java.awt.Color
import javax.swing.JPanel

class UnderlaySelectionPanel : JPanel() {
    init {
        Rs2MapEditor.underlayMap.forEach {
            val underlay = SelectableUnderlayCell(it.value.getRGB(), 30, it.key)
            add(underlay)
        }
        val addAnUnderlay = SelectableUnderlayCell(Color.darkGray, 30, "+")
        add(addAnUnderlay)
    }
}