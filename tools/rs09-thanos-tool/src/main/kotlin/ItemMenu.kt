import java.awt.BorderLayout
import java.awt.Dimension
import java.awt.event.KeyEvent
import java.awt.event.KeyListener
import java.awt.event.MouseEvent
import java.awt.event.MouseListener
import java.util.*
import java.util.regex.Pattern
import java.util.regex.PatternSyntaxException
import javax.swing.*
import javax.swing.table.DefaultTableCellRenderer
import javax.swing.table.DefaultTableModel
import javax.swing.table.TableRowSorter

object ItemMenu : JFrame("Item Selection Menu") {
    var model = DefaultTableModel()
    var searchField = JTextField()
    var amountField = JTextField()
    var sorter = TableRowSorter(model)
    var cellRenderer = DefaultTableCellRenderer()
    var populated = false
    var caller: ((Int,String) -> Unit)? = null
    private fun filter() {
        var rf: RowFilter<DefaultTableModel?, Any?>? = null
        //If current expression doesn't parse, don't update.
        rf = try {
            val searchPattern = Pattern.quote(searchField.text)
            RowFilter.regexFilter("(?i)${searchPattern}")
        } catch (e: PatternSyntaxException) {
            return
        }
        sorter.rowFilter = rf
    }

    fun open() {
        if (!populated) {
            SwingUtilities.invokeLater {
                for ((id,_) in TableData.itemNames) {
                    model.addRow(arrayOf(id,TableData.getItemName(id)))
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
        val searchLabel = JLabel("Search for Item:")
        val amountLabel = JLabel("Amount: ")
        amountField.preferredSize = Dimension(45, 20)
        searchField.preferredSize = Dimension(100, 20)
        searchPanel.add(searchLabel)
        searchPanel.add(searchField)
        add(searchPanel, BorderLayout.NORTH)
        setLocationRelativeTo(null)
        val itemTable: JTable = object : JTable(model) {
            override fun editCellAt(i: Int, i1: Int, eventObject: EventObject): Boolean {
                return false
            }
        }
        model.addColumn("ID")
        model.addColumn("Name")
        itemTable.rowSorter = sorter
        itemTable.columnModel.getColumn(0).maxWidth = 55
        itemTable.columnModel.getColumn(0).cellRenderer = cellRenderer
        itemTable.columnModel.getColumn(1).cellRenderer = cellRenderer
        itemTable.addMouseListener(object : MouseListener {
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
        searchField.addKeyListener(object : KeyListener {
            override fun keyTyped(keyEvent: KeyEvent) {
                filter()
            }

            override fun keyPressed(keyEvent: KeyEvent) {}
            override fun keyReleased(keyEvent: KeyEvent) {}
        })
        val scrollPane = JScrollPane(itemTable)
        add(scrollPane, BorderLayout.SOUTH)
        pack()
        isVisible = false
    }
}