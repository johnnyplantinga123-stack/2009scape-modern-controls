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
        val underlayComponent: JComponent = UnderlaySelectionPanel()
        val panel3: JComponent = Rs2MapEditor.makeTextPanel("Howdy Ho! I'm an overlay panel, or soon will be that is!")

        preferredSize = Dimension(230, 4000)
        border = CompoundBorder(LineBorder(Color.DARK_GRAY), EmptyBorder(0, 0, 0, 0))

        addTab("Information", informationComponent)
        setMnemonicAt(0, KeyEvent.VK_1)
        add("Underlays", underlayComponent)
        setMnemonicAt(1, KeyEvent.VK_2)
        addTab("Overlays", panel3)
        setMnemonicAt(2, KeyEvent.VK_3)
        add("NPC Spawn", Rs2MapEditor.npcPanel)
        add("Item Spawn", Rs2MapEditor.itemPanel)
    }
}