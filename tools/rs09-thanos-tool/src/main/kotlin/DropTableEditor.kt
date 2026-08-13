import java.awt.BorderLayout
import java.awt.Dimension
import java.awt.FlowLayout
import java.awt.event.*
import java.math.BigDecimal
import java.math.RoundingMode
import java.util.*
import javax.swing.*
import javax.swing.event.ChangeEvent
import javax.swing.table.DefaultTableModel

class DropTableEditor(val table: NPCDropTable) : JFrame("Editing ${if(table.ids.isNotBlank()) TableData.getNPCName(table.ids.split(",")[0].toInt()) ?: "Unknown Table" else "None"}") {
    val IDsField = JTextField()
    val descriptionField = JTextField()
    val mainModel = object : DefaultTableModel(){}
    val defaultModel = object : DefaultTableModel(){}
    val charmModel = object : DefaultTableModel(){}
    val tertiaryModel = object : DefaultTableModel(){}

    val mainTable = object : JTable(mainModel){
        override fun editCellAt(row: Int, column: Int): Boolean {
            if(column == 0 || column == 1){
                ItemMenu.caller = {id,name ->
                    model.setValueAt(id,row,0)
                    model.setValueAt(name,row,1)
                    table[row].id = id
                }
                ItemMenu.open()
                return false
            }
            return super.editCellAt(row, column)
        }

        override fun editCellAt(row: Int, column: Int, e: EventObject?): Boolean {
            if(column == 0 || column == 1){
                ItemMenu.caller = {id,name ->
                    model.setValueAt(id,row,0)
                    model.setValueAt(name,row,1)
                    table[row].id = id
                }
                ItemMenu.open()
                return false
            }
            return super.editCellAt(row, column, e)
        }
        override fun editingStopped(e: ChangeEvent?) {
            val editor = cellEditor
            val newValue = editor.cellEditorValue.toString()
            when(editingColumn){
                2 -> table[editingRow].minAmt = newValue
                3 -> table[editingRow].maxAmt = newValue
                4 -> {
                    table.totalWeight += newValue.toDouble() - table[editingRow].weight
                    table[editingRow].weight = newValue.toDouble()
                    updateMainPercentages()
                }
            }
            super.editingStopped(e)
        }
    }
    val mainScrollpane = JScrollPane(mainTable)
    val mainSubtable = Subtable("Main",mainScrollpane,table,mainModel)

    val defaultTable = object : JTable(defaultModel){
        override fun editCellAt(row: Int, column: Int): Boolean {
            if(column == 0 || column == 1){
                ItemMenu.caller = {id,name ->
                    model.setValueAt(id,row,0)
                    model.setValueAt(name,row,1)
                    table.alwaysTable[row].id = id
                }
                ItemMenu.open()
                return false
            }
            return super.editCellAt(row, column)
        }

        override fun editCellAt(row: Int, column: Int, e: EventObject?): Boolean {
            if(column == 0 || column == 1){
                ItemMenu.caller = {id,name ->
                    model.setValueAt(id,row,0)
                    model.setValueAt(name,row,1)
                    table.alwaysTable[row].id = id
                }
                ItemMenu.open()
                return false
            }
            return super.editCellAt(row, column, e)
        }
        override fun editingStopped(e: ChangeEvent?) {
            val editor = cellEditor
            val newValue = editor.cellEditorValue.toString()
            when(editingColumn){
                2 -> table.alwaysTable[editingRow].minAmt = newValue
                3 -> table.alwaysTable[editingRow].maxAmt = newValue
            }
            super.editingStopped(e)
        }
    }
    val defaultScrollPane = JScrollPane(defaultTable)
    val defaultSubtable = Subtable("Always",defaultScrollPane,table.alwaysTable,defaultModel)

    val charmTable = object : JTable(charmModel){
        override fun editCellAt(row: Int, column: Int): Boolean {
            if(column == 0 || column == 1){
                ItemMenu.caller = {id,name ->
                    model.setValueAt(id,row,0)
                    model.setValueAt(name,row,1)
                    table.charmTable[row].id = id
                }
                ItemMenu.open()
                return false
            }
            return super.editCellAt(row, column)
        }

        override fun editCellAt(row: Int, column: Int, e: EventObject?): Boolean {
            if(column == 0 || column == 1){
                ItemMenu.caller = {id,name ->
                    model.setValueAt(id,row,0)
                    model.setValueAt(name,row,1)
                    table.charmTable[row].id = id
                }
                ItemMenu.open()
                return false
            }
            return super.editCellAt(row, column, e)
        }
        override fun editingStopped(e: ChangeEvent?) {
            val editor = cellEditor
            val newValue = editor.cellEditorValue.toString()
            when(editingColumn){
                2 -> table.charmTable[editingRow].minAmt = newValue
                3 -> table.charmTable[editingRow].maxAmt = newValue
                4 -> {
                    table.charmTable.totalWeight += newValue.toDouble() - table.charmTable[editingRow].weight
                    table.charmTable[editingRow].weight = newValue.toDouble()
                    updateCharmPercentages()
                }
            }
            super.editingStopped(e)
        }
    }
    val charmScrollPane = JScrollPane(charmTable)
    val charmSubtable = Subtable("Charm",charmScrollPane,table.charmTable,charmModel)

    val tertiaryTable = object : JTable(tertiaryModel){
        override fun editCellAt(row: Int, column: Int): Boolean {
            if(column == 0 || column == 1){
                ItemMenu.caller = {id,name ->
                    model.setValueAt(id,row,0)
                    model.setValueAt(name,row,1)
                    table.tertiaryTable[row].id = id
                }
                ItemMenu.open()
                return false
            }
            return super.editCellAt(row, column)
        }

        override fun editCellAt(row: Int, column: Int, e: EventObject?): Boolean {
            if(column == 0 || column == 1){
                ItemMenu.caller = {id,name ->
                    model.setValueAt(id,row,0)
                    model.setValueAt(name,row,1)
                    table.tertiaryTable[row].id = id
                }
                ItemMenu.open()
                return false
            }
            return super.editCellAt(row, column, e)
        }
        override fun editingStopped(e: ChangeEvent?) {
            val editor = cellEditor
            val newValue = editor.cellEditorValue.toString()
            when(editingColumn){
                2 -> table.tertiaryTable[editingRow].minAmt = newValue
                3 -> table.tertiaryTable[editingRow].maxAmt = newValue
                4 -> {
                    table.tertiaryTable.totalWeight += newValue.toDouble() - table.tertiaryTable[editingRow].weight
                    table.tertiaryTable[editingRow].weight = newValue.toDouble()
                    updateTertiaryPercentages()
                }
            }
            super.editingStopped(e)
        }
    }
    val tertiaryScrollPane = JScrollPane(tertiaryTable)
    val tertiarySubtable = Subtable("Tertiary",tertiaryScrollPane,table.tertiaryTable,tertiaryModel)

    init {
        layout = BorderLayout()
        val topPanel = JPanel()
        val IDsLabel = JLabel("IDs: ")
        IDsField.preferredSize = Dimension(200,20)
        IDsField.addKeyListener(object : KeyListener {
            override fun keyTyped(e: KeyEvent?) {}
            override fun keyPressed(e: KeyEvent?) {}
            override fun keyReleased(e: KeyEvent?) {
                table.ids = IDsField.text
            }
        })
        IDsField.text = table.ids
        descriptionField.preferredSize = Dimension(400,20)
        descriptionField.text = table.description
        descriptionField.addKeyListener(object : KeyListener{
            override fun keyTyped(e: KeyEvent?) {}
            override fun keyPressed(e: KeyEvent?) {}
            override fun keyReleased(e: KeyEvent?) {
                table.description = descriptionField.text
            }
        })
        topPanel.add(IDsLabel)
        topPanel.add(IDsField)

        val mainButton = JButton("Edit Main")
        mainButton.addActionListener {
            mainSubtable.open()
        }
        topPanel.add(mainButton)

        val charmsButton = JButton("Edit Charms")
        charmsButton.addActionListener {
            charmSubtable.open()
        }
        topPanel.add(charmsButton)

        val tertiaryButton = JButton("Edit Tertiary")
        tertiaryButton.addActionListener {
            tertiarySubtable.open()
        }
        topPanel.add(tertiaryButton)

        val defaultButton = JButton("Edit Default")
        defaultButton.addActionListener {
            defaultSubtable.open()
        }
        topPanel.add(defaultButton)

        add(topPanel,BorderLayout.NORTH)

        val descriptionPanel = JPanel()
        val descriptionLabel = JLabel("Description: ")
        descriptionPanel.add(descriptionLabel)
        descriptionPanel.add(descriptionField)

        add(descriptionPanel,BorderLayout.SOUTH)

        mainModel.addColumn("ID")
        mainModel.addColumn("Name")
        mainModel.addColumn("Min Amt")
        mainModel.addColumn("Max Amt")
        mainModel.addColumn("Weight")
        mainModel.addColumn("(%)")
        mainModel.addColumn("(1/%)")

        defaultModel.addColumn("ID")
        defaultModel.addColumn("Name")
        defaultModel.addColumn("Min Amt")
        defaultModel.addColumn("Max Amt")

        charmModel.addColumn("ID")
        charmModel.addColumn("Name")
        charmModel.addColumn("Min Amt")
        charmModel.addColumn("Max Amt")
        charmModel.addColumn("Weight")
        charmModel.addColumn("(%)")

        tertiaryModel.addColumn("ID")
        tertiaryModel.addColumn("Name")
        tertiaryModel.addColumn("Min Amt")
        tertiaryModel.addColumn("Max Amt")
        tertiaryModel.addColumn("Weight")
        tertiaryModel.addColumn("(%)")
        tertiaryModel.addColumn("(1/%)")

        mainTable.inputMap.put(KeyStroke.getKeyStroke(KeyEvent.VK_DELETE, 0), "delete")
        mainTable.actionMap.put("delete",object : AbstractAction() {
            override fun actionPerformed(e: ActionEvent?) {
                try {
                    table.totalWeight -= table[mainTable.selectedRow].weight
                    table.removeAt(mainTable.selectedRow)
                    mainModel.removeRow(mainTable.selectedRow)
                    updateMainPercentages()
                } catch(e: Exception){
                    Logger.logErr("Tried to remove nonexistent row ${mainTable.selectedRow}")
                }
            }
        })

        defaultTable.inputMap.put(KeyStroke.getKeyStroke(KeyEvent.VK_DELETE, 0), "delete")
        defaultTable.actionMap.put("delete",object : AbstractAction() {
            override fun actionPerformed(e: ActionEvent?) {
                try {
                    table.alwaysTable.removeAt(defaultTable.selectedRow)
                    defaultModel.removeRow(defaultTable.selectedRow)
                } catch(e: Exception){
                    Logger.logErr("Tried to remove nonexistent row ${defaultTable.selectedRow}")
                }
            }
        })

        charmTable.inputMap.put(KeyStroke.getKeyStroke(KeyEvent.VK_DELETE, 0), "delete")
        charmTable.actionMap.put("delete",object : AbstractAction() {
            override fun actionPerformed(e: ActionEvent?) {
                try {
                    table.totalWeight -= table.charmTable[charmTable.selectedRow].weight
                    table.charmTable.removeAt(charmTable.selectedRow)
                    charmModel.removeRow(charmTable.selectedRow)
                    updateCharmPercentages()
                } catch(e: Exception){
                    Logger.logErr("Tried to remove nonexistent row ${charmTable.selectedRow}")
                }
            }
        })

        tertiaryTable.inputMap.put(KeyStroke.getKeyStroke(KeyEvent.VK_DELETE, 0), "delete")
        tertiaryTable.actionMap.put("delete",object : AbstractAction() {
            override fun actionPerformed(e: ActionEvent?) {
                try {
                    table.totalWeight -= table[tertiaryTable.selectedRow].weight
                    table.removeAt(tertiaryTable.selectedRow)
                    tertiaryModel.removeRow(tertiaryTable.selectedRow)
                    updateTertiaryPercentages()
                } catch(e: Exception){
                    Logger.logErr("Tried to remove nonexistent row ${tertiaryTable.selectedRow}")
                }
            }
        })

        addWindowListener(object : WindowAdapter(){
            override fun windowClosing(e: WindowEvent?) {
                FileLoader.dropTableList?.populated = false
                FileLoader.dropTableList?.dispatchEvent(WindowEvent(FileLoader.dropTableList!!, WindowEvent.WINDOW_CLOSING))
                FileLoader.dropTableList?.open()
                super.windowClosing(e)
            }
        })
	addWindowFocusListener(EditorFocusListener(EditorType.DROP_TABLES))

        pack()

        isVisible = false
    }

    class Subtable(val label: String, val pane: JScrollPane, val table: WeightBasedTable, val model: DefaultTableModel) : JFrame("Editing $label Drops"){
        init {
            layout = BorderLayout()
            val topPanel = JPanel()

            val mainLabel = JLabel("$label Drops")
            topPanel.add(mainLabel)
            val addButton = JButton("Add Drop")
            addButton.addActionListener {
                ItemMenu.caller = {id,name ->
                    when(label){
                        "Always" -> {
                            model.addRow(arrayOf(id,name,1,1))
                            table.add(WeightedItem(id,"1","1",1.0,true))
                        }
                        else -> {
                            model.addRow(arrayOf(id,name,1,1,1.0,""))
                            table.add(WeightedItem(id,"1","1",1.0,false))
                            updatePercentages(model)
                        }
                    }
                }
                ItemMenu.open()
            }
            topPanel.add(addButton)
            add(topPanel, BorderLayout.NORTH)
            add(pane, BorderLayout.CENTER)
            pack()
            minimumSize = Dimension(width + 40,height + 50)
            isVisible = false
        }

        fun open(){
            isVisible = true
        }
        fun updatePercentages(model: DefaultTableModel){
            for(i in 0 until model.rowCount){
                model.setValueAt(getPercentage(table[i].weight,table),i,5)
            }
        }

        fun getPercentage(weight: Double, table: WeightBasedTable): String{
            return "${round((weight / table.totalWeight) * 100.0,4)}%"
        }

        fun round(value: Double, places: Int): Double {
            require(places >= 0)
            var bd: BigDecimal = BigDecimal.valueOf(value)
            bd = bd.setScale(places, RoundingMode.HALF_UP)
            return bd.toDouble()
        }
    }

    fun open(){
        for(item in table.alwaysTable){
            defaultModel.addRow(arrayOf(item.id,TableData.getItemName(item.id),item.minAmt,item.maxAmt))
        }
        for(item in table.charmTable){
            charmModel.addRow(arrayOf(item.id,TableData.getItemName(item.id),item.minAmt,item.maxAmt,item.weight,getPercentage(item.weight,table.charmTable)))
        }
        for(item in table){
            mainModel.addRow(arrayOf(item.id,TableData.getItemName(item.id),item.minAmt,item.maxAmt,item.weight,getPercentage(item.weight,table),getInversePercentage(item.weight, table)))
        }
        for(item in table.tertiaryTable){
            tertiaryModel.addRow(arrayOf(item.id,TableData.getItemName(item.id),item.minAmt,item.maxAmt,item.weight,getPercentage(item.weight,table.tertiaryTable),getInversePercentage(item.weight, table.tertiaryTable)))
        }
        isVisible = true
    }

    fun getPercentage(weight: Double, table: WeightBasedTable): String{
        return "${round((weight / table.totalWeight) * 100.0,2)}%"
    }

    fun getInversePercentage(weight: Double, table: WeightBasedTable): String {
        return "${1.0/(weight / table.totalWeight)}"
    }

    fun updateCharmPercentages(){
        for(i in 0 until charmModel.rowCount){
            charmModel.setValueAt(getPercentage(table.charmTable[i].weight,table.charmTable),i,5)
        }
    }

    fun updateTertiaryPercentages(){
        for(i in 0 until tertiaryModel.rowCount){
            tertiaryModel.setValueAt(getPercentage(table.tertiaryTable[i].weight,table.tertiaryTable),i,5)
            tertiaryModel.setValueAt(getInversePercentage(table.tertiaryTable[i].weight,table.tertiaryTable),i,6)
        }
    }

    fun updateMainPercentages(){
        for(i in 0 until mainModel.rowCount){
            mainModel.setValueAt(getPercentage(table[i].weight,table),i,5)
            mainModel.setValueAt(getInversePercentage(table[i].weight,table),i,6)
        }
    }

    fun round(value: Double, places: Int): Double {
        require(places >= 0)
        var bd: BigDecimal = BigDecimal.valueOf(value)
        bd = bd.setScale(places, RoundingMode.HALF_UP)
        return bd.toDouble()
    }
}
