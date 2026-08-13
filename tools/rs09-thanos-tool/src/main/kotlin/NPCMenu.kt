import java.awt.BorderLayout
import java.awt.Dimension
import java.awt.event.KeyEvent
import java.awt.event.KeyListener
import java.awt.event.MouseEvent
import java.awt.event.MouseListener
import java.util.*
import java.util.regex.PatternSyntaxException
import javax.swing.*
import javax.swing.table.DefaultTableCellRenderer
import javax.swing.table.DefaultTableModel
import javax.swing.table.TableRowSorter

object NPCMenu : JFrame("NPC Selection Menu") {
    var model = DefaultTableModel()
    var searchNameField = JTextField()
    var searchIdField = JTextField()
    var sorter = TableRowSorter(model)
    var cellRenderer = DefaultTableCellRenderer()
    var populated = false
    var caller: ((Int,String) -> Unit)? = null
    private fun filter() {
        val rfs: List<RowFilter<DefaultTableModel?, Any?>?,> = listOf(
            try {
                RowFilter.regexFilter("(?i)${searchNameField.text}", 1)
            } catch (e: PatternSyntaxException) {
                null
            },
            try {
                RowFilter.regexFilter(searchIdField.text, 0)
            } catch (e: PatternSyntaxException) {
                return
            }

        )
        sorter.rowFilter = RowFilter.andFilter(rfs)
    }

    fun open() {
        if (!populated) {
            SwingUtilities.invokeLater {
                for ((id,name) in TableData.npcNames) {
                    model.addRow(arrayOf(id,name))
                }
                isVisible = true
                populated = true
            }
        } else {
            isVisible = true
        }
    }

    init {
        layout = BorderLayout()
        cellRenderer.toolTipText = "Double-Click to select."
        val searchPanel = JPanel()
        val searchIdLabel = JLabel("Search for ID:")
        val searchNameLabel = JLabel("Search for NPC:")
        searchIdField.preferredSize = Dimension(100, 20)
        searchNameField.preferredSize = Dimension(100, 20)
        searchPanel.add(searchIdLabel)
        searchPanel.add(searchIdField)
        searchPanel.add(searchNameLabel)
        searchPanel.add(searchNameField)
        add(searchPanel, BorderLayout.NORTH)
        setLocationRelativeTo(null)
        val npcTable: JTable = object : JTable(model) {
            override fun editCellAt(i: Int, i1: Int, eventObject: EventObject): Boolean {
                return false
            }
        }
        model.addColumn("ID")
        model.addColumn("Name")
        npcTable.rowSorter = sorter
        npcTable.columnModel.getColumn(0).maxWidth = 55
        npcTable.columnModel.getColumn(0).cellRenderer = cellRenderer
        npcTable.columnModel.getColumn(1).cellRenderer = cellRenderer
        npcTable.addMouseListener(object : MouseListener {
            override fun mouseClicked(mouseEvent: MouseEvent) {
                if (mouseEvent.clickCount == 2) {
                    val table = mouseEvent.source as JTable
                    val row = table.selectedRow
                    val selectedID = table.getValueAt(row,0).toString().toInt()
                    val selectedName = table.getValueAt(row,1).toString()
                    caller?.invoke(selectedID,selectedName)
                    isVisible = false
                }
            }

            override fun mousePressed(mouseEvent: MouseEvent) {}
            override fun mouseReleased(mouseEvent: MouseEvent) {}
            override fun mouseEntered(mouseEvent: MouseEvent) {}
            override fun mouseExited(mouseEvent: MouseEvent) {}
        })
        searchNameField.addKeyListener(object : KeyListener {
            override fun keyTyped(keyEvent: KeyEvent) {}
            override fun keyPressed(keyEvent: KeyEvent) {}

            override fun keyReleased(keyEvent: KeyEvent) {
                filter()
            }
        })
        searchIdField.addKeyListener(object : KeyListener {
            override fun keyTyped(keyEvent: KeyEvent) {}
            override fun keyPressed(keyEvent: KeyEvent) {}

            override fun keyReleased(keyEvent: KeyEvent) {
                filter()
            }
        })
        val scrollPane = JScrollPane(npcTable)
        add(scrollPane, BorderLayout.SOUTH)
        pack()
        isVisible = false
    }
}
