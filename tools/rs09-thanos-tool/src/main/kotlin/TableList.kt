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

class TableList : JFrame("RS09 Drop Table Editor") {
    val searchField = JTextField()
    var model = DefaultTableModel()
    var sorter = TableRowSorter(model)
    var cellRenderer = DefaultTableCellRenderer()
    var populated = false

    init {
        isVisible = false
        layout = BorderLayout()

        val searchPanel = JPanel()
        val searchLabel = JLabel("Search for table: ")
        val addButton = JButton("Add Table")
        val saveButton = JButton("Save Tables")
        addButton.addActionListener {
            TableData.tables.add(NPCDropTable())
            DropTableEditor(TableData.tables.last()).open()
        }
        saveButton.addActionListener {
            Editors.DROP_TABLES.data.save()
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
                    TableData.tables.removeAt(table.convertRowIndexToModel(table.selectedRow))
                    model.removeRow(table.convertRowIndexToModel(table.selectedRow))
                } catch(e: Exception){
                    Logger.logErr("Tried to remove nonexistent row ${itemTable.selectedRow}")
                }
            }
        })

        model.addColumn("Name")

        itemTable.rowSorter = sorter
        itemTable.columnModel.getColumn(0).cellRenderer = cellRenderer

        itemTable.addMouseListener(object : MouseListener {
            override fun mouseClicked(mouseEvent: MouseEvent) {
                if (mouseEvent.clickCount == 2) {
                    val table = mouseEvent.source as JTable
                    val row = table.selectedRow
                    DropTableEditor(TableData.tables[table.convertRowIndexToModel(row)]).open()
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

	addWindowFocusListener(EditorFocusListener(EditorType.DROP_TABLES))
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
            Logger.logInfo("Loading table data...")
            for(i in 0 until model.rowCount){
                model.removeRow(0)
            }
            SwingUtilities.invokeLater {
                for (table in TableData.tables) {
                    model.addRow(arrayOf<Any>(if(table.ids.isNotBlank()) TableData.getNPCName(table.ids.split(",")[0].toInt()) else "None"))
                }
                isVisible = true
                populated = true
            }
        } else {
            isVisible = true
        }
    }
}
