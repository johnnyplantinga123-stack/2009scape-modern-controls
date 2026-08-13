import java.awt.BorderLayout
import java.awt.Dimension
import java.awt.event.*
import java.util.*
import java.util.regex.Pattern
import java.util.regex.PatternSyntaxException
import javax.swing.*
import javax.swing.table.DefaultTableCellRenderer
import javax.swing.table.DefaultTableModel
import javax.swing.table.TableRowSorter
import kotlin.collections.ArrayList

class ShopList : JFrame("RS09 Shop Editor") {
    val searchField = JTextField()
    var model = DefaultTableModel()
    var sorter = TableRowSorter(model)
    var cellRenderer = DefaultTableCellRenderer()
    var populated = false

    init {
        isVisible = false
        layout = BorderLayout()

        val searchPanel = JPanel()
        val searchLabel = JLabel("Search for shop: ")
        val addButton = JButton("Add Shop")
        val saveButton = JButton("Save Shops")
        addButton.addActionListener {
            val highestID = TableData.shops.keys.toIntArray()[TableData.shops.size - 1]
            TableData.shops.put(highestID + 1, TableData.Shop(highestID + 1,"", ArrayList(),"",995,false,false,false))
            ShopEdit(highestID + 1).open()
        }
        saveButton.addActionListener {
            Editors.SHOPS.data.save()
            saveConfirmation.isVisible = true
        }
        searchField.preferredSize = Dimension(100, 20)
        searchPanel.add(searchLabel)
        searchPanel.add(searchField)
        searchPanel.add(addButton)
        searchPanel.add(saveButton)
        add(searchPanel, BorderLayout.NORTH)
        setLocationRelativeTo(null)

        val itemTable: JTable = object : JTable(model) {
            override fun editCellAt(i: Int, i1: Int, eventObject: EventObject): Boolean {
                return false
            }
        }

        itemTable.inputMap.put(KeyStroke.getKeyStroke(KeyEvent.VK_DELETE, 0), "delete")
        itemTable.actionMap.put("delete",object : AbstractAction() {
            override fun actionPerformed(e: ActionEvent?) {
                try {
                    val table = e?.source as JTable
                    TableData.shops.remove(table.getValueAt(table.selectedRow,0))
                    model.removeRow(table.convertRowIndexToModel(table.selectedRow))
                } catch(e: Exception){
                    Logger.logErr("Tried to remove nonexistent row ${itemTable.selectedRow}")
                }
            }
        })

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
                    ShopEdit(table.getValueAt(row,0) as Int).open()
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

	addWindowFocusListener(EditorFocusListener(EditorType.SHOPS))
        pack()
        isVisible = false
    }

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
            Logger.logInfo("Loading shops data...")
            for(i in 0 until model.rowCount){
                model.removeRow(0)
            }
            SwingUtilities.invokeLater {
                for ((id,shop) in TableData.shops) {
                    model.addRow(arrayOf<Any>(id, shop.title))
                }
                isVisible = true
                populated = true
            }
        } else {
            isVisible = true
        }
    }
}
