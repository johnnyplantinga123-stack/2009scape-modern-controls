package ui

import java.awt.BorderLayout
import java.awt.Color
import javax.swing.JPanel
import javax.swing.SwingConstants

class TileInformationPanel : JPanel() {
    init {
        Rs2MapEditor.underlayInfo.foreground = Color.white
        Rs2MapEditor.underlayInfo.verticalAlignment = SwingConstants.TOP
        Rs2MapEditor.underlayInfo.horizontalAlignment = SwingConstants.CENTER
        layout = BorderLayout()
        add(Rs2MapEditor.underlayInfo)
    }
}