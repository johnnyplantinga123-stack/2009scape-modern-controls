import com.github.weisj.darklaf.LafManager
import com.github.weisj.darklaf.theme.DarculaTheme
import com.displee.cache.CacheLibrary
import java.awt.BorderLayout
import java.awt.Dimension
import java.awt.FlowLayout
import java.io.File
import java.util.*
import javax.swing.*
import javax.swing.table.DefaultTableModel
import cacheops.cache.definition.decoder.FloorUnderlayConfiguration
import cacheops.cache.definition.decoder.FloorOverlayConfiguration
import cacheops.cache.definition.decoder.MapTileParser

object MainScreen : JFrame("Zaros ${EditorConstants.BUILD_NUMBER}") {
    val dirChooser = object : JFileChooser(DataStore.LastConfigPath){
        override fun updateUI() {
            EditorConstants.updateTheme()
            super.updateUI()
        }
    }
    val selectDir = JButton("Select Config Folder")
    val openDrops = JButton("Edit Drop Tables")
    val openNPC = JButton("Edit NPC Configs")
    val openItem = JButton("Edit Item Configs")
    val openShops = JButton("Edit Shops")
    val openObjects = JButton("Edit Object Configs")
    val openNpcItemSpawns = JButton("Edit NPC/Item Spawns")
    var loadedModel = object : DefaultTableModel(){}
    val loadedTable = object : JTable(loadedModel){
        override fun editCellAt(p0: Int, p1: Int): Boolean {
            return false
        }

        override fun editCellAt(p0: Int, p1: Int, p2: EventObject?): Boolean {
            return false
        }
    }
    val pane = JScrollPane(loadedTable)

    init {
        isResizable = false
        layout = BorderLayout()
        dirChooser.fileSelectionMode = JFileChooser.DIRECTORIES_ONLY
        defaultCloseOperation = EXIT_ON_CLOSE
        preferredSize = Dimension(550,300)
        loadedModel.addColumn("Filename")

        iconImage = ImageIcon(javaClass.getResource("/zaros.png")).image


        selectDir.addActionListener {
            val response = dirChooser.showOpenDialog(null)
            EditorConstants.updateTheme()
            if(response == JFileChooser.APPROVE_OPTION){
                Logger.logInfo("Selected directory: ${dirChooser.selectedFile.absolutePath}")
                EditorConstants.CONFIG_PATH = dirChooser.selectedFile.absolutePath
                EditorConstants.CACHE_PATH = dirChooser.selectedFile.parent + "/cache"
                showLoaded()
                if(loadedModel.rowCount > 0) {
                    selectDir.isEnabled = false
                    DataStore.LastConfigPath = EditorConstants.CONFIG_PATH
                }
            }
        }

        openDrops.addActionListener {
            FileLoader.dropTableList = TableList()
            FileLoader.dropTableList!!.open()
        }

        openItem.addActionListener {
            TableEditor(Editors.ITEM_CONFIGS).open()
        }

        openNPC.addActionListener {
            TableEditor(Editors.NPC_CONFIGS).open()
        }

        openObjects.addActionListener {
            TableEditor(Editors.OBJECT_CONFIGS).open()
        }

        openShops.addActionListener {
            ShopList().open()
        }

        openNpcItemSpawns.addActionListener {
            Rs2MapEditor.open()
        }

        val showCredits = JButton("Show Credits")
        showCredits.addActionListener {
            credits.isVisible = true
        }

        val editorPanel = JPanel()
        val selectionPanel = JPanel()

        editorPanel.layout = BoxLayout(editorPanel, BoxLayout.Y_AXIS)
        for(i in arrayOf(openDrops, openNPC, openItem, openObjects, openShops, openNpcItemSpawns, showCredits)){
            i.preferredSize = Dimension(175,40)
            i.maximumSize = Dimension(175,40)
            i.isEnabled = i == showCredits
            editorPanel.add(i)
        }

        add(editorPanel, BorderLayout.WEST)

        val buttonPane = JPanel()
        buttonPane.layout = BorderLayout()
        selectDir.preferredSize = Dimension(300,40)
        selectDir.maximumSize = Dimension(300,40)
        buttonPane.add(selectDir, BorderLayout.CENTER)

        selectionPanel.layout = BoxLayout(selectionPanel, BoxLayout.Y_AXIS)
        selectionPanel.add(buttonPane)

        pane.preferredSize = Dimension(225,200)
        selectionPanel.add(pane)

        add(JSeparator(SwingConstants.VERTICAL), BorderLayout.CENTER)

        setLocationRelativeTo(null)
        add(selectionPanel, BorderLayout.EAST)
        EditorConstants.updateTheme()
        pack()
    }
}

fun showLoaded(){
    MainScreen.loadedModel = object : DefaultTableModel(){}
    MainScreen.loadedModel.addColumn("Filename")
    MainScreen.loadedTable.model = MainScreen.loadedModel

    val files = File(EditorConstants.CONFIG_PATH).listFiles() ?: return

    var cacheExists = File(EditorConstants.CACHE_PATH).listFiles().isNotEmpty()
    val spawnGating = arrayOf(false, false, false)

    for(file in files){
        if(file.name in EditorConstants.VALID_FILES){
            MainScreen.loadedModel.addRow(arrayOf(file.name))
            when(file.nameWithoutExtension){
                "drop_tables" -> MainScreen.openDrops.isEnabled = true.also { Editors.DROP_TABLES.data.parse() }
                "npc_configs" -> MainScreen.openNPC.isEnabled = true.also { Editors.NPC_CONFIGS.data.parse() }
                "item_configs" -> MainScreen.openItem.isEnabled = true.also { Editors.ITEM_CONFIGS.data.parse() }
                "shops" -> MainScreen.openShops.isEnabled = true.also { Editors.SHOPS.data.parse() }
                "object_configs" -> MainScreen.openObjects.isEnabled = true.also { Editors.OBJECT_CONFIGS.data.parse()}
                "npc_spawns" -> spawnGating[0] = true
		"ground_spawns" -> spawnGating[1] = true
		"xteas" -> spawnGating[2] = true
             }
        }
    }

    if (cacheExists && spawnGating[0] && spawnGating[1] && spawnGating[2]) {
    	try {
	    Editors.ITEM_SPAWNS.data.parse()
	    Editors.NPC_SPAWNS.data.parse()
	    const.configureCacheDelegate(EditorConstants.CACHE_PATH, EditorConstants.CONFIG_PATH + File.separator + "xteas.json")
	    Rs2MapEditor.library = CacheLibrary.create(EditorConstants.CACHE_PATH)
	    FloorOverlayConfiguration.init()
	    FloorUnderlayConfiguration.init()
	    MapTileParser.init()
            Rs2MapEditor.underlayMap = FloorUnderlayConfiguration.floorUnderlays
            Rs2MapEditor.overlayMap = FloorOverlayConfiguration.floorOverlays
    	    MainScreen.openNpcItemSpawns.isEnabled = true
	} catch (e: Exception) {
	    e.printStackTrace()
	}
    }

    if (!MainScreen.openDrops.isEnabled)
    	MainScreen.openDrops.toolTipText = "Needs drop_tables.json."
    if (!MainScreen.openNPC.isEnabled)
        MainScreen.openNPC.toolTipText = "Needs npc_configs.json"
    if (!MainScreen.openItem.isEnabled)
        MainScreen.openItem.toolTipText = "Needs item_configs.json"
    if (!MainScreen.openShops.isEnabled)
        MainScreen.openShops.toolTipText = "Needs shops.json"
    if (!MainScreen.openObjects.isEnabled)
        MainScreen.openObjects.toolTipText = "Needs object_configs.json"
    if (!MainScreen.openNpcItemSpawns.isEnabled)
        MainScreen.openNpcItemSpawns.toolTipText = "Needs cache next to configs folder\nNeeds xteas.json\nNeeds npc_spawns.json\nNeeds ground_spawns.json"

    MainScreen.pane.revalidate()
    MainScreen.pane.repaint()
}

fun main() {
    DataStore.parse()
    Runtime.getRuntime().addShutdownHook(Thread {
        DataStore.save()
    })
    MainScreen.isVisible = true
}
