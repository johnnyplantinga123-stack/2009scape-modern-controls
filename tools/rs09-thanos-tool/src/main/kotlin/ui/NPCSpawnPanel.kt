package ui

import EditorState
import Rs2MapEditor
import TableData
import const.Image
import misc.ImgButton
import tools.Util
import java.awt.Color
import java.awt.Dimension
import java.awt.FlowLayout
import java.awt.BorderLayout
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import javax.swing.BoxLayout
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.JTextField
import javax.swing.SwingUtilities
import javax.swing.Box
import javax.swing.SwingConstants
import javax.swing.border.MatteBorder
import javax.swing.event.DocumentListener
import javax.swing.event.DocumentEvent

class NPCSpawnPanel : JPanel() {
    val rowBorder = MatteBorder(1, 1, 1, 1, Color.WHITE)
    val removedRows = ArrayList<NPCRow>()

    init {
        layout = BoxLayout(this, BoxLayout.Y_AXIS)

        add(NPCSpawnerPanel())
	
	val filterPanel = JPanel(FlowLayout())
	val filterLabel = JLabel("Filter ")
	val filterBox = JTextField("Enter search term...")
	filterBox.document.addDocumentListener(FilterBoxListener(filterBox, ::filterRows))

	filterPanel.add(filterLabel)
	filterPanel.add(filterBox)
	filterBox.minimumSize = Dimension(150, 30)
	filterBox.preferredSize = Dimension(150, 30)
	filterBox.maximumSize = Dimension(150, 60)
	filterPanel.minimumSize = Dimension(300, 60)
	filterPanel.maximumSize = Dimension(300, 60)

	add(filterPanel)


        Rs2MapEditor.npcs!!.forEach {
            val row = NPCRow(it, this)
            row.border = rowBorder
            Rs2MapEditor.npcRows.add(row)
            add(row)
        }
    }

    fun filterRows (term: String) {
    	SwingUtilities.invokeLater {
	    var termAsInt = term.toIntOrNull()
	    Rs2MapEditor.npcRows.addAll(removedRows)
 	    var rows = Rs2MapEditor.npcRows.toTypedArray()
	    removedRows.clear()

	    if (term.isNotEmpty() && termAsInt != null) {
	        for (row in rows) {
		    if (row.npc.id != termAsInt) {
		        removedRows.add(row)
		        Rs2MapEditor.npcRows.remove(row)
		    }
	        }
	    }
	    else if (term.isNotEmpty()) {
	        for (row in rows) {
		    var name = TableData.getNPCName(row.npc.id)?.toLowerCase() ?: "null"
		    if (!name.contains(term, ignoreCase = true) && !term.contains(name, ignoreCase = true) && term.toLowerCase() != name) {
		        removedRows.add(row)
		        Rs2MapEditor.npcRows.remove(row)
		    }
	        }
	    }

	    for (row in rows) {
	    	remove(row)
		revalidate()
	    }
	    for (row in Rs2MapEditor.npcRows) {
	    	add(row)
		revalidate()
	    }
	    Rs2MapEditor.infoPane.revalidate()

	    repaint(5L)
	    Rs2MapEditor.infoScrollPane.scrollFrame.viewport.repaint(5L)
	}
    }

    fun redrawRows() {
        Rs2MapEditor.npcRows.forEach {
            remove(it)
        }
        Rs2MapEditor.npcRows.clear()
        Rs2MapEditor.npcs!!.filter { it.location.z == Rs2MapEditor.plane }.forEach {
            val row = NPCRow(it, this)
            row.border = rowBorder
            Rs2MapEditor.npcRows.add(row)
            add(row)
        }
	removedRows.clear()
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
	    
	    Rs2MapEditor.npcIdInput.addMouseListener (object : MouseAdapter() {
		override fun mouseClicked (e: MouseEvent) {
		    NPCMenu.caller = {id, name ->
			Rs2MapEditor.npcIdInput.text = id.toString()
		    }
		    NPCMenu.open()
		}
	    })

            minimumSize = Dimension(300, 120)
            preferredSize = Dimension(300, 120)
            maximumSize = Dimension(300, 120)

            layout = FlowLayout()
            add(Rs2MapEditor.npcIdInput)
            add(addButton)
	    add(Rs2MapEditor.directionalCheckboxPanel)
	    add(JLabel("Can Walk?"))
	    add(Rs2MapEditor.npcCanWalkCheckbox)
            border = MatteBorder(1, 1, 1, 1, Color.GREEN)
        }
    }

    class FilterBoxListener (val textField: JTextField, val filterRows: (String) -> Unit) : DocumentListener {
	override fun changedUpdate (e: DocumentEvent) {
	    filterRows(textField.text)
	}

	override fun insertUpdate (e: DocumentEvent) {
	    filterRows(textField.text)
	}

	override fun removeUpdate (e: DocumentEvent) {
	    filterRows(textField.text)
	}
    }

    class NPCRow(val npc: TableData.NPCSpawn, val parent: JPanel) : JPanel(){
        init {
            layout = BorderLayout()
	    val topPanel = JPanel(FlowLayout())
	    topPanel.add(JLabel("${TableData.getNPCName(npc.id)}[${npc.id}]"))

            val deleteButton = ImgButton(Image.DELETE_HI, Image.DELTE_LO)
            topPanel.add(deleteButton)

	    val bottomPanel = JPanel(FlowLayout())
	    bottomPanel.add(JLabel("Direction [${spawnArrows[npc.spawnDirection]}] Walks? ${npc.canWalk}"))
	    add(topPanel, BorderLayout.NORTH)
	    add(JLabel("{${npc.location.x}, ${npc.location.y}, ${npc.location.z}}", SwingConstants.CENTER), BorderLayout.CENTER)
	    add(bottomPanel, BorderLayout.SOUTH)

            minimumSize = Dimension(300, 65)
            preferredSize = Dimension(300, 65)
            maximumSize = Dimension(300, 65)
            deleteButton.onClick {
                Rs2MapEditor.npcs!!.remove(npc)
                parent.remove(this)
                parent.repaint()
                Rs2MapEditor.npcRows.remove(this)
                if(Rs2MapEditor.npcs!!.filter { it.location.x == npc.location.x && it.location.y == npc.location.y }.isEmpty()){
                    Rs2MapEditor.componentPointMap.filter { it.key.x == npc.location.localCoords[0] && it.key.y == npc.location.localCoords[1] }.forEach { (_,cell) ->
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
                    Rs2MapEditor.componentPointMap.filter { it.key.x == npc.location.localCoords[0] && it.key.y == npc.location.localCoords[1] }.forEach { it.value.border = border }
                }

                override fun mouseExited(e: MouseEvent?) {
                    super.mouseExited(e)
                    Rs2MapEditor.componentPointMap.filter { it.key.x == npc.location.localCoords[0] && it.key.y == npc.location.localCoords[1] }.forEach{ it.value.border = defaultBorder }
                }
            })
        }

	companion object {
	    val spawnArrows = arrayOf("⇖","⇑","⇗","⇐","⇒","⇙","⇓","⇘") 
	}
    }
}
