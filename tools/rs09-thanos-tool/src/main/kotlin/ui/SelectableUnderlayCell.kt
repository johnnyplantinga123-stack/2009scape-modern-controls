package ui

import cacheops.cache.definition.decoder.MapTileParser
import java.awt.Color
import java.awt.Dimension
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import javax.swing.BorderFactory
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.SwingUtilities
import javax.swing.border.Border
import javax.swing.border.MatteBorder

class SelectableUnderlayCell(color: Color, size: Int, val id: Any) : JPanel() {
    init {
        when (id) {
            is Int -> {
                background = color
            }
            else -> {
                background = color
                border = BorderFactory.createDashedBorder(Color.GREEN)
            }
        }
        val text = JLabel("$id")
        text.foreground = Color((color.rgb).inv()).brighter()

        minimumSize = Dimension(size, size)
        maximumSize = Dimension(size, size)
        preferredSize = Dimension(size, size)
        add(text)

        addMouseListener(object : MouseAdapter() {
            val border: Border = MatteBorder(1, 1, 1, 1, Color.GRAY)
            val highlight: Border = MatteBorder(1, 1, 1, 1, Color.YELLOW)
            val selectionBorder = MatteBorder(1, 1, 1, 1, Color.WHITE)

            override fun mouseEntered(e: MouseEvent?) {
                this@SelectableUnderlayCell.border = selectionBorder
                Rs2MapEditor.colorPointMap.filter { it.value == id }.forEach{ Rs2MapEditor.componentPointMap[it.key]!!.border = highlight}
            }

            override fun mouseExited(e: MouseEvent?) {
                this@SelectableUnderlayCell.border = null
                Rs2MapEditor.colorPointMap.filter { it.value == id }.forEach{ Rs2MapEditor.componentPointMap[it.key]!!.border = border}
            }

            override fun mouseClicked(e: MouseEvent) {
                if (SwingUtilities.isLeftMouseButton(e)) {
                    when (id) {
                        is Int -> {
                            if(Rs2MapEditor.selectedUnderlayId == id){
                                Rs2MapEditor.state = EditorState.NONE
                            } else {
                                Rs2MapEditor.state = EditorState.SET_UNDERLAY
                                Rs2MapEditor.selectedUnderlayId = id
                            }
                            Rs2MapEditor.statusLabel.text = "<html>Region: ${Rs2MapEditor.region} | " +
                                    "Local Coordinates: [${Rs2MapEditor.selectedPointX}, ${Rs2MapEditor.selectedPointY}, ${Rs2MapEditor.plane}] | " +
                                    "Global Coordinates: [${MapTileParser.coordinateX(Rs2MapEditor.selectedPointX)}, ${
                                        MapTileParser.coordinateX(
                                            Rs2MapEditor.selectedPointY
                                        )
                                    }, ${Rs2MapEditor.plane}] | " +
                                    "Viewing Underlay: ${
                                        MapTileParser.definition.getTile(
                                            Rs2MapEditor.selectedPointX,
                                            Rs2MapEditor.selectedPointY, 0).underlayId - 1} | " +
                                    "Selected Underlay: ${if (Rs2MapEditor.selectedUnderlayId == null) "<font color='red'>No underlay selected!</font>" else "${Rs2MapEditor.selectedUnderlayId}"}</html>"
                        }
                        else -> {
                            println("User trying to add new underlay")
                        }
                    }
                }
            }
        })
    }
}