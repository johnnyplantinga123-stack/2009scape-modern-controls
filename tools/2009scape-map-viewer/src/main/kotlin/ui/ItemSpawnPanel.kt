package ui

import cacheops.cache.definition.decoder.Item
import const.Image
import misc.ImgButton
import java.awt.BorderLayout
import java.awt.Color
import java.awt.Dimension
import java.awt.FlowLayout
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import javax.swing.BoxLayout
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.border.MatteBorder

class ItemSpawnPanel : JPanel() {
    val rowBorder = MatteBorder(1, 1, 1, 1, Color.WHITE)
    init {
        layout = BoxLayout(this, BoxLayout.PAGE_AXIS)

        add(ItemSpawnerPanel())

        Rs2MapEditor.items.forEach {
            val row = ItemRow(it, this)
            row.border = rowBorder
            Rs2MapEditor.itemRows.add(row)
            add(row)
        }
    }

    fun redrawRows() {
        Rs2MapEditor.itemRows.forEach {
            remove(it)
        }
        Rs2MapEditor.itemRows.clear()
        Rs2MapEditor.items.filter { it.plane == Rs2MapEditor.plane }.forEach {
            val row = ItemRow(it, this)
            row.border = rowBorder
            Rs2MapEditor.itemRows.add(row)
            add(row)
        }
        repaint()
    }

    class ItemSpawnerPanel : JPanel() {
        init {
            val topPanel = JPanel(FlowLayout())
            val bottomPanel = JPanel(FlowLayout())
            val addButton = ImgButton(Image.ADD_HI, Image.ADD_LO)
            addButton.onClick {
                Rs2MapEditor.state = EditorState.ADD_GROUNDITEM
            }
            Rs2MapEditor.itemIdInput.minimumSize = Dimension(200, 25)
            Rs2MapEditor.itemIdInput.maximumSize = Dimension(200, 25)
            Rs2MapEditor.itemIdInput.preferredSize = Dimension(200, 25)

            Rs2MapEditor.itemRespawnInput.minimumSize = Dimension(50, 25)
            Rs2MapEditor.itemRespawnInput.maximumSize = Dimension(50, 25)
            Rs2MapEditor.itemRespawnInput.preferredSize = Dimension(50, 25)
            Rs2MapEditor.itemRespawnInput.text = "1"

            Rs2MapEditor.itemAmountInput.minimumSize = Dimension(75, 25)
            Rs2MapEditor.itemAmountInput.maximumSize = Dimension(75, 25)
            Rs2MapEditor.itemAmountInput.preferredSize = Dimension(75, 25)
            Rs2MapEditor.itemAmountInput.text = "1"

            val timeLabel = JLabel("\uD83D\uDD64")
            timeLabel.toolTipText = "Respawn Time (ticks)"

            minimumSize = Dimension(300, 68)
            preferredSize = Dimension(300, 68)
            maximumSize = Dimension(300, 68)

            layout = BorderLayout()
            topPanel.add(Rs2MapEditor.itemIdInput)
            topPanel.add(addButton)
            bottomPanel.add(timeLabel)
            bottomPanel.add(Rs2MapEditor.itemRespawnInput)
            bottomPanel.add(JLabel("AMT:"))
            bottomPanel.add(Rs2MapEditor.itemAmountInput)
            add(topPanel,BorderLayout.NORTH)
            add(bottomPanel,BorderLayout.SOUTH)
            border = MatteBorder(1, 1, 1, 1, Color.GREEN)
        }
    }

    class ItemRow(val item: Item, val parent: JPanel) : JPanel(){
        init {
            layout = FlowLayout()
            add(JLabel("${item.definition.name} [${item.amount}] -- \uD83D\uDD64 ${item.respawnTime}"))
            val deleteButton = ImgButton(Image.DELETE_HI, Image.DELTE_LO)
            add(deleteButton)
            minimumSize = Dimension(300, 40)
            preferredSize = Dimension(300, 40)
            maximumSize = Dimension(300, 40)
            deleteButton.onClick {
                Rs2MapEditor.items.remove(item)
                parent.remove(this)
                parent.repaint()
                Rs2MapEditor.itemRows.remove(this)
                if(Rs2MapEditor.items.filter { it.x == item.x && it.y == item.y }.isEmpty()){
                    Rs2MapEditor.componentPointMap.filter { it.key.x == item.x && it.key.y == item.y }.forEach { (_,cell) ->
                        cell.components.forEach { c ->
                            if(c is JLabel && c.icon == Image.RED_DOT) cell.remove(c)
                        }
                        cell.repaint()
                        Rs2MapEditor.itemsUpdated = true
                    }
                }
            }

            addMouseListener(object : MouseAdapter(){
                val border = MatteBorder(1, 1, 1, 1, Color.YELLOW)
                val defaultBorder = MatteBorder(1, 1, 1, 1, Color.GRAY)
                override fun mouseEntered(e: MouseEvent?) {
                    super.mouseEntered(e)
                    Rs2MapEditor.componentPointMap.filter { it.key.x == item.x && it.key.y == item.y }.forEach { it.value.border = border }
                }

                override fun mouseExited(e: MouseEvent?) {
                    super.mouseExited(e)
                    Rs2MapEditor.componentPointMap.filter { it.key.x == item.x && it.key.y == item.y }.forEach{ it.value.border = defaultBorder }
                }
            })
        }
    }
}