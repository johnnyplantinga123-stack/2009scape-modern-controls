package ui

import EditorState
import Rs2MapEditor
import cacheops.cache.definition.decoder.NPC
import const.Image
import misc.ImgButton
import java.awt.Color
import java.awt.Dimension
import java.awt.FlowLayout
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import javax.swing.BoxLayout
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.border.MatteBorder

class NPCSpawnPanel : JPanel() {
    val rowBorder = MatteBorder(1, 1, 1, 1, Color.WHITE)
    init {
        layout = BoxLayout(this, BoxLayout.PAGE_AXIS)

        add(NPCSpawnerPanel())

        Rs2MapEditor.npcs.forEach {
            val row = NPCRow(it, this)
            row.border = rowBorder
            Rs2MapEditor.npcRows.add(row)
            add(row)
        }
    }

    fun redrawRows() {
        Rs2MapEditor.npcRows.forEach {
            remove(it)
        }
        Rs2MapEditor.npcRows.clear()
        Rs2MapEditor.npcs.filter { it.plane == Rs2MapEditor.plane }.forEach {
            val row = NPCRow(it, this)
            row.border = rowBorder
            Rs2MapEditor.npcRows.add(row)
            add(row)
        }
        repaint()
    }

    class NPCSpawnerPanel : JPanel() {
        init {
            val addButton = ImgButton(Image.ADD_HI, Image.ADD_LO)
            addButton.onClick {
                Rs2MapEditor.state = EditorState.ADD_NPC
            }
            Rs2MapEditor.npcIdInput.minimumSize = Dimension(200, 25)
            Rs2MapEditor.npcIdInput.maximumSize = Dimension(200, 25)
            Rs2MapEditor.npcIdInput.preferredSize = Dimension(200, 25)
            minimumSize = Dimension(300, 60)
            preferredSize = Dimension(300, 60)
            maximumSize = Dimension(300, 60)

            layout = FlowLayout()
            add(Rs2MapEditor.npcIdInput)
            add(addButton)
            border = MatteBorder(1, 1, 1, 1, Color.GREEN)
        }
    }

    class NPCRow(val npc: NPC, val parent: JPanel) : JPanel(){
        init {
            layout = FlowLayout()
            add(JLabel("${npc.definition.name} (LVL ${npc.definition.combat}) [${npc.x} | ${npc.y} | ${npc.plane}]"))
            val deleteButton = ImgButton(Image.DELETE_HI, Image.DELTE_LO)
            add(deleteButton)
            minimumSize = Dimension(300, 40)
            preferredSize = Dimension(300, 40)
            maximumSize = Dimension(300, 40)
            deleteButton.onClick {
                Rs2MapEditor.npcs.remove(npc)
                parent.remove(this)
                parent.repaint()
                Rs2MapEditor.npcRows.remove(this)
                if(Rs2MapEditor.npcs.filter { it.x == npc.x && it.y == npc.y }.isEmpty()){
                    Rs2MapEditor.componentPointMap.filter { it.key.x == npc.x && it.key.y == npc.y }.forEach { (_,cell) ->
                        cell.components.forEach { c ->
                            if(c is JLabel) cell.remove(c)
                        }
                        cell.repaint()
                        Rs2MapEditor.npcsUpdated = true
                    }
                }
            }

            addMouseListener(object : MouseAdapter(){
                val border = MatteBorder(1, 1, 1, 1, Color.YELLOW)
                val defaultBorder = MatteBorder(1, 1, 1, 1, Color.GRAY)
                override fun mouseEntered(e: MouseEvent?) {
                    super.mouseEntered(e)
                    Rs2MapEditor.componentPointMap.filter { it.key.x == npc.x && it.key.y == npc.y }.forEach { it.value.border = border }
                }

                override fun mouseExited(e: MouseEvent?) {
                    super.mouseExited(e)
                    Rs2MapEditor.componentPointMap.filter { it.key.x == npc.x && it.key.y == npc.y }.forEach{ it.value.border = defaultBorder }
                }
            })
        }
    }
}