import org.json.simple.JSONArray
import org.json.simple.JSONObject
import java.awt.BorderLayout
import java.awt.Dimension
import java.awt.FlowLayout
import java.awt.event.ActionEvent
import java.awt.event.KeyEvent
import java.awt.event.KeyListener
import java.util.*
import java.util.regex.Pattern
import java.util.regex.PatternSyntaxException
import javax.swing.*
import javax.swing.JComponent.WHEN_FOCUSED
import javax.swing.event.TableModelEvent
import javax.swing.event.TableModelListener
import javax.swing.table.DefaultTableModel
import javax.swing.table.TableRowSorter
import kotlin.collections.ArrayList

/**
 * Represents a generic JTable-based editor. Pretty much just a cleaner visualisation of JSON objects with editing functionality.
 */
class TableEditor(val editor: Editors) : JFrame("Editing ${editor.name.replace("_"," ").toLowerCase()}") {
    val searchField = JTextField()
    val model = DefaultTableModel()
    val rowHeaderModel = DefaultTableModel()
    var table = object : JTable(model){
        override fun editCellAt(p0: Int, p1: Int): Boolean {
            return super.editCellAt(p0, p1, null)
        }
        override fun editCellAt(p0: Int, p1: Int, p2: EventObject?): Boolean {
            val realRow = convertRowIndexToModel(p0)
            val realColumn = convertColumnIndexToModel(p1)
            val columnName = model.getColumnName(realColumn)
            if(columnName == "bonuses"){
                val existingBonuses = model.getValueAt(realRow,realColumn).toString()
                BonusEditor.setInitialValues(existingBonuses)
                BonusEditor.setCallback { result ->
                    model.setValueAt(result, realRow, realColumn)
                }
                BonusEditor.isVisible = true
                return false
            }
            return super.editCellAt(p0, p1, p2)
        }
    }
    var rowHeaderTable = JTable(rowHeaderModel)
    val sorter = TableRowSorter(model)

    init {
        layout = BorderLayout()
        isVisible = false
        table.autoResizeMode = JTable.AUTO_RESIZE_OFF
        searchField.preferredSize = Dimension(200,20)
        searchField.maximumSize = Dimension(200,20)
        minimumSize = Dimension(1000, 650)

        searchField.addKeyListener(object : KeyListener{
            override fun keyTyped(p0: KeyEvent?) { filter() }
            override fun keyPressed(p0: KeyEvent?) {filter()}
            override fun keyReleased(p0: KeyEvent?) {filter()}
        })

        table.rowSorter = sorter
        rowHeaderTable.rowSorter = sorter


        val addButton = JButton("Add Row")
        val saveButton = JButton("Save Data")

        addButton.addActionListener {
            getData().add(JSONObject())
            model.addRow(arrayOf())
            rowHeaderModel.addRow(arrayOf())
            confirmation.isVisible = true
        }

        saveButton.addActionListener {
            editor.data.save()
            saveConfirmation.isVisible = true
        }

        val searchPanel = JPanel()
        searchPanel.layout = FlowLayout()
        searchPanel.add(JLabel("Search by Name "))
        searchPanel.add(searchField)
        searchPanel.add(addButton)
        searchPanel.add(saveButton)

        val pane = JScrollPane(table, JScrollPane.VERTICAL_SCROLLBAR_ALWAYS, JScrollPane.HORIZONTAL_SCROLLBAR_ALWAYS)

        if (editor != Editors.OBJECT_CONFIGS) {
            pane.setRowHeaderView(rowHeaderTable)
            val headerSize = pane.rowHeader.maximumSize
            headerSize.width = 200
            pane.rowHeader.maximumSize = headerSize
            pane.rowHeader.preferredSize = headerSize
        }

        add(pane, BorderLayout.CENTER)
        add(searchPanel, BorderLayout.NORTH)

        val keys = when (editor) {
            Editors.NPC_CONFIGS -> TableData.npcConfigKeys
            Editors.ITEM_CONFIGS -> TableData.itemConfigKeys
            Editors.OBJECT_CONFIGS -> TableData.objConfigKeys
            else -> ArrayList<JSONObject>()
        }

	val type = when (editor) {
	    Editors.NPC_CONFIGS -> EditorType.NPC_CONFIGS
	    Editors.ITEM_CONFIGS -> EditorType.ITEM_CONFIGS
	    Editors.OBJECT_CONFIGS -> EditorType.OBJECT_CONFIGS
	    else -> EditorType.NONE
	}

        if(editor == Editors.OBJECT_CONFIGS){
            model.addColumn("ids")
            model.addColumn("examine")
            keys.remove("ids")
            keys.remove("examine")
            table.columnModel.getColumn(0).preferredWidth = 400
            table.columnModel.getColumn(1).preferredWidth = 600
        } else {
            rowHeaderModel.addColumn("id")
            rowHeaderModel.addColumn("name")
            rowHeaderTable.columnModel.getColumn(0).preferredWidth = 85
            rowHeaderTable.columnModel.getColumn(0).maxWidth = 85
            rowHeaderTable.columnModel.getColumn(1).preferredWidth = 200
            rowHeaderTable.columnModel.getColumn(1).maxWidth = 200
            keys.remove("id")
            keys.remove("name")
            rowHeaderModel.addTableModelListener(object : TableModelListener{
                override fun tableChanged(event: TableModelEvent?) {
                    event ?: return
                    if(event.type != TableModelEvent.UPDATE) return
                    val label = rowHeaderModel.getColumnName(event.column)
                    getData()[event.firstRow][label] = rowHeaderModel.getValueAt(event.firstRow, event.column)
                    Logger.logInfo("Changed ${getData()[event.firstRow]["name"]}'s $label to ${rowHeaderModel.getValueAt(event.firstRow,event.column)}")
                }
            })
        }

        keys.sortedBy { it.toString() }.forEach { model.addColumn(it) }

        model.addTableModelListener(object : TableModelListener{
            override fun tableChanged(event: TableModelEvent?) {
                event ?: return
                if(event.type != TableModelEvent.UPDATE) return
                val label = model.getColumnName(event.column)
                getData()[event.firstRow][label] = model.getValueAt(event.firstRow, event.column)
                Logger.logInfo("Changed ${getData()[event.firstRow]["name"]}'s $label to ${model.getValueAt(event.firstRow,event.column)}")
            }
        })

        table.getInputMap(WHEN_FOCUSED).put(KeyStroke.getKeyStroke(KeyEvent.VK_DELETE, 0), "delete")
        table.actionMap.put("delete",object : AbstractAction() {
            override fun actionPerformed(e: ActionEvent?) {
                try {
                    for(i in 0 until table.selectedRowCount){
                        val realRow = table.convertRowIndexToModel(table.selectedRows[0])
                        getData().removeAt(realRow)
                        model.removeRow(realRow)
                        rowHeaderModel.removeRow(realRow)
                    }
                    repaint()
                } catch(e: Exception){
                    Logger.logErr("Tried to remove nonexistent row ${table.selectedRow}")
                }
            }
        })
        table.addKeyListener(object : KeyListener{
            override fun keyTyped(p0: KeyEvent?) {}

            override fun keyPressed(p0: KeyEvent?) {}

            override fun keyReleased(event: KeyEvent) {
                if(event.isControlDown){
                    if(event.keyCode == KeyEvent.VK_C){
                        //copy-then-paste
                        val data = model.dataVector
                        Logger.logInfo("Copy event received.")
                        for(i in 0 until table.selectedRowCount){
                            val realRow = table.convertRowIndexToModel(table.selectedRows[i])
                            val insertPos = table.convertRowIndexToModel(table.selectedRows[table.selectedRowCount - 1]) + 1
                            Logger.logInfo("Copying row $realRow")
                            val datum = data.elementAt(realRow) as Vector<*>
                            val copiedRow = datum.clone() as Vector<*>
                            val jsonData = getData()[realRow]
                            val copyData = jsonData.clone() as JSONObject
                            getData().add(insertPos, copyData)
                            model.insertRow(insertPos, copiedRow)
                            rowHeaderModel.insertRow(insertPos, arrayOf(copyData["id"], copyData["name"]))
                        }
                        table.revalidate()
                        table.repaint()
                    }
                }
            }
        })

	addWindowFocusListener(EditorFocusListener(type))
        EditorConstants.updateTheme()
        pack()
    }

    fun open(){
        val data = getData()

        for(datum in data){
            val vals = JSONArray()
            val idName = JSONArray()
            idName.add(datum["id"])
            idName.add(datum["name"])
            for(i in 0 until model.columnCount){
                val label = model.getColumnName(i)
                vals.add(datum[label] ?: "")
            }
            model.addRow(vals.toArray())
            rowHeaderModel.addRow(idName.toArray())
        }
        isVisible = true
    }

    private fun filter() {
        val searchPattern = Pattern.quote(searchField.text)
        val regex = Regex("(?i)${searchPattern}")
        if (editor == Editors.OBJECT_CONFIGS) {
            var rf: RowFilter<DefaultTableModel?, Any?>? = null
            //If current expression doesn't parse, don't update.
            rf = try {
                RowFilter.regexFilter(regex.pattern)
            } catch (e: PatternSyntaxException) {
                return
            }
            sorter.rowFilter = rf
        } else {
            val filter = object : RowFilter<DefaultTableModel, Any>() {
                override fun include(p0: Entry<out DefaultTableModel, out Any>?): Boolean {
                    val rowId: Int = p0?.identifier as? Int ?: return false
                    return regex.containsMatchIn(getData()[rowId]["name"].toString())
                }
            }
            sorter.rowFilter = filter
        }
    }

    fun getData(): ArrayList<JSONObject> {
        return when(editor){
            Editors.ITEM_CONFIGS -> TableData.itemConfigs
            Editors.NPC_CONFIGS -> TableData.npcConfigs
            Editors.OBJECT_CONFIGS -> TableData.objConfigs
            else -> ArrayList()
        }
    }
}
