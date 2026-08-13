package ui

import java.awt.Color
import java.awt.Dimension
import java.awt.event.KeyEvent
import javax.swing.JComponent
import javax.swing.JTabbedPane
import javax.swing.border.CompoundBorder
import javax.swing.border.EmptyBorder
import javax.swing.border.LineBorder

class InfoPane : JTabbedPane() {
    init {
        val informationComponent: JComponent = TileInformationPanel()

        preferredSize = Dimension(230, 4000)
        border = CompoundBorder(LineBorder(Color.DARK_GRAY), EmptyBorder(0, 0, 0, 0))

        addTab("Information", informationComponent)
        add("NPC Spawn", Rs2MapEditor.npcPanel)
        add("Item Spawn", Rs2MapEditor.itemPanel)
    }
}
