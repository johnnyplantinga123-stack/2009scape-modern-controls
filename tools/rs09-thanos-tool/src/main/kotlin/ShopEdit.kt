import java.awt.BorderLayout
import java.awt.Dimension
import java.awt.event.*
import java.util.*
import javax.swing.*
import javax.swing.event.ChangeEvent
import javax.swing.table.DefaultTableModel
import kotlin.collections.ArrayList

class ShopEdit(val shopID: Int) : JFrame("Edit Shop") {
    val shop = TableData.shops[shopID]
    val idField = JTextField()
    val titleField = JTextField()
    val currencyField = JTextField()
    val generalCheckbox = JCheckBox("General Store")
    val highAlchCheckbox = JCheckBox("High Alch Prices")
    val forceSharedCheckbox = JCheckBox("Force Shared")
    var model = DefaultTableModel()


    init {
        layout = BorderLayout()
        val topPanel = JPanel()
        val idLabel = JLabel("ID: ")
        idField.preferredSize = Dimension(40, 20)
        idField.text = shopID.toString()
        idField.addKeyListener(object : KeyListener{
            override fun keyTyped(e: KeyEvent?) {}
            override fun keyPressed(e: KeyEvent?) {}
            override fun keyReleased(e: KeyEvent?) {
                shop?.id = idField.text.toInt()
            }
        })
        topPanel.add(idLabel)
        topPanel.add(idField)

        val currencyLabel = JLabel("Currency: ")
        currencyField.preferredSize = Dimension(50,20)
        currencyField.text = shop?.currency.toString()
        currencyField.addKeyListener(object : KeyListener{
            override fun keyTyped(e: KeyEvent?) {}
            override fun keyPressed(e: KeyEvent?) {}
            override fun keyReleased(e: KeyEvent?) {
                shop?.currency = currencyField.text.toInt()
            }
        })
        topPanel.add(currencyLabel)
        topPanel.add(currencyField)

        val titleLabel = JLabel("Title: ")
        titleField.preferredSize = Dimension(300,20)
        titleField.text = shop?.title
        titleField.addKeyListener(object : KeyListener{
            override fun keyTyped(e: KeyEvent?) {}
            override fun keyPressed(e: KeyEvent?) {}
            override fun keyReleased(e: KeyEvent?) {
                shop?.title = titleField.text
            }
        })
        topPanel.add(titleLabel)
        topPanel.add(titleField)

        add(topPanel,BorderLayout.NORTH)

        val checkPanel = JPanel()
        val npcLabel = JLabel("NPCs: ")
        val npcField = JTextField()
        npcField.addKeyListener(object : KeyListener{
            override fun keyTyped(e: KeyEvent?) {
                shop?.npcs = npcField.text + e?.keyChar
            }
            override fun keyPressed(e: KeyEvent?) {}
            override fun keyReleased(e: KeyEvent?) {}
        })
        npcField.preferredSize = Dimension(50,20)
        npcField.text = shop?.npcs
        checkPanel.add(npcLabel)
        checkPanel.add(npcField)
        generalCheckbox.isSelected = shop?.general_store ?: false
        generalCheckbox.addChangeListener {
            shop!!.general_store = !shop.general_store
        }
        checkPanel.add(generalCheckbox)
        highAlchCheckbox.isBorderPaintedFlat = shop?.high_alch ?: false
        highAlchCheckbox.isSelected = shop?.high_alch ?: false
        highAlchCheckbox.addChangeListener {
            shop!!.high_alch = !shop.high_alch
        }
        checkPanel.add(highAlchCheckbox)
        forceSharedCheckbox.isBorderPaintedFlat = shop?.forceShared ?: false
        forceSharedCheckbox.isSelected = shop?.forceShared ?: false
        forceSharedCheckbox.addChangeListener {
            shop!!.forceShared = !shop.forceShared
        }
        checkPanel.add(forceSharedCheckbox)

        val addButton = JButton("Add Item")
        addButton.addActionListener {
            ItemMenu.caller = {id,name ->
                model.addRow(arrayOf(name,id,0,false,100))
                shop!!.stock.add(Item(id,"0",false, 100))
            }
            ItemMenu.open()
        }
        checkPanel.add(addButton)
        add(checkPanel,BorderLayout.CENTER)

        val itemTable: JTable = object : JTable(model) {
            override fun editCellAt(row: Int, column: Int): Boolean {
                if(column == 0 || column == 1){
                    ItemMenu.caller = {id,name ->
                        model.setValueAt(name,row,0)
                        model.setValueAt(id,row,1)
                        shop!!.stock[row].id = id
                    }
                    ItemMenu.open()
                    return false
                }
                return super.editCellAt(row, column)
            }

            override fun editCellAt(row: Int, column: Int, e: EventObject?): Boolean {
                if(column == 0 || column == 1){
                    ItemMenu.caller = {id,name ->
                        model.setValueAt(name,row,0)
                        model.setValueAt(id,row,1)
                        shop!!.stock[row].id = id
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
                    1 -> TableData.shops[shopID]!!.stock[editingRow].id = newValue.toInt()
                    2 -> TableData.shops[shopID]!!.stock[editingRow].amount = newValue
                    3 -> TableData.shops[shopID]!!.stock[editingRow].infinite = newValue.toBoolean()
                    4 -> TableData.shops[shopID]!!.stock[editingRow].restockTime = newValue.toInt()
                }
                super.editingStopped(e)
            }
        }
        itemTable.putClientProperty("terminateEditOnFocusLost", true);

        itemTable.inputMap.put(KeyStroke.getKeyStroke(KeyEvent.VK_DELETE, 0), "delete")
        itemTable.actionMap.put("delete",object : AbstractAction() {
            override fun actionPerformed(e: ActionEvent?) {
                try {
                    shop!!.stock.removeAt(itemTable.selectedRow)
                    model.removeRow(itemTable.selectedRow)
                } catch(e: Exception){
                    Logger.logErr("Tried to remove nonexistent row ${itemTable.selectedRow}")
                }
            }
        })

        model.addColumn("Name")
        model.addColumn("ID")
        model.addColumn("Amount")
        model.addColumn("Infinite?")
        model.addColumn("Restock Ticks")

        val scrollPane = JScrollPane(itemTable)
        add(scrollPane,BorderLayout.SOUTH)
	addWindowFocusListener(EditorFocusListener(EditorType.SHOPS))

        pack()
        isVisible = false
    }

    fun open(){
        for(item in shop?.stock ?: ArrayList()){
            model.addRow(arrayOf(TableData.getItemName(item.id, false),item.id,item.amount,item.infinite, item.restockTime))
        }
        isVisible = true
    }
}
