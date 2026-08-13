
import cacheops.cache.definition.data.OverlayDefinition
import cacheops.cache.definition.data.UnderlayDefinition
import cacheops.cache.definition.data.MapTile
import cacheops.cache.definition.decoder.*
import com.displee.cache.CacheLibrary
import const.Image.RED_DOT
import const.Image.YELLOW_DOT
import misc.CustomEventQueue
import tools.Util
import ui.*
import java.awt.*
import java.awt.event.*
import javax.swing.*
import javax.swing.border.*
import kotlin.system.exitProcess

object Rs2MapEditor {
    var region = 12850
    var npcs = TableData.npcSpawns[region]
    var items = TableData.itemSpawns[region]
    var sceneries = ArrayList<Scenery>()
    val npcRows = ArrayList<NPCSpawnPanel.NPCRow>()
    val itemRows = ArrayList<ItemSpawnPanel.ItemRow>()
    var npcsUpdated = false
    var itemsUpdated = false
    var tileDataUpdated = false
    var plane = 0
    var visualizeHeight = false
    var drawGrid = true
    var previousHeight = -1
    lateinit var library: CacheLibrary
    lateinit var mapPanel: MapPane
    lateinit var npcPanel: NPCSpawnPanel
    lateinit var itemPanel: ItemSpawnPanel
    lateinit var infoPane: InfoPane

    var cellX = 20
    var cellY = 20

    // Formatted points
    var selectedPointX = 0

    var selectedPointY = 0
        get() = (64 - field) - 1

    // Stores the underlay ID for each point
    var colorPointMap: HashMap<Point, Int> = hashMapOf()

    val componentPointMap = hashMapOf<Point, MapCell>()

    // Stores the underlay ID with the Underlay Definition
    lateinit var underlayMap: HashMap<Int, UnderlayDefinition>
    lateinit var overlayMap: HashMap<Int, OverlayDefinition>

    var selectedUnderlayId: Int? = null

    var statusLabel = JLabel()
    var infoLabel = JLabel()
    var underlayInfo = JLabel("Right click a tile to get more information")
    var underlayLabel = JLabel()
    lateinit var npcIdInput: JTextField
    lateinit var npcCanWalkCheckbox: JCheckBox
    lateinit var npcDirectionCheckboxes: Array<JCheckBox>
    lateinit var directionalCheckboxPanel: JPanel
    lateinit var itemIdInput: JTextField
    lateinit var itemRespawnInput: JTextField
    lateinit var itemAmountInput: JTextField
    lateinit var infoScrollPane: ScrollPane2
    lateinit var editorWin: EditorWindow
    var state = EditorState.NONE
    var editorInit = false

    fun open() {
        if (!editorInit) {
	    editorWin = EditorWindow().also { it.initGui() }
	    loadRegion(12850)
	}
	editorWin.isVisible = true
    }

    class EditorWindow : JFrame("Zaros Spawn Editor") {
        fun initGui() {
            val statusBar = JPanel(FlowLayout(FlowLayout.LEFT))
            statusBar.border = CompoundBorder(
                LineBorder(Color.DARK_GRAY),
                EmptyBorder(2, 3, 2, 3)
            )
            statusLabel.foreground = Color.YELLOW
            
	    directionalCheckboxPanel = JPanel(GridLayout(3,3))
	    npcDirectionCheckboxes = Array<JCheckBox>(8) { JCheckBox().also { it.addItemListener(DirectionCheckboxOnClick) } }
	    var cbCounter = 0
	    for (i in 0 until 9) {
	    	if (i == 0) npcDirectionCheckboxes[i].setSelected(true)
	    	if (i != 4) {
	    	    directionalCheckboxPanel.add(npcDirectionCheckboxes[cbCounter++])
		} else {
		    directionalCheckboxPanel.add(JLabel("↻", SwingConstants.CENTER))
		}    
	    }
	    statusBar.add(statusLabel)

            bounds = Rectangle(Dimension(1567, 850))
            size.setSize(1567, 850)
            maximumSize = Dimension(1567, 850)
            defaultCloseOperation = HIDE_ON_CLOSE
            jMenuBar = MenuBar()
            mapPanel = MapPane()
            npcIdInput = JTextField()
	    npcCanWalkCheckbox = JCheckBox()
            itemIdInput = JTextField()
            itemRespawnInput = JTextField()
            itemAmountInput = JTextField()
            npcPanel = NPCSpawnPanel()
            itemPanel = ItemSpawnPanel()
            infoPane = InfoPane()
	    infoScrollPane = ScrollPane2()
            add(ScrollPane(), BorderLayout.CENTER)
            add(infoScrollPane, BorderLayout.EAST)
            add(statusBar, BorderLayout.SOUTH)
            infoLabel.text = "Information"
            statusLabel.text = "-"
            setLocationRelativeTo(null)
            isResizable = false
            isVisible = false

            CustomEventQueue.install()
	    addWindowFocusListener(EditorFocusListener(EditorType.SPAWNS))

	    editorInit = true
        }
    }

    class MapPane : JPanel() {
        val gbc = GridBagConstraints()
        val mapHeight = 64
        val mapWidth = 64
        init {
            layout = GridBagLayout()
            gbc.fill = GridBagConstraints.NONE
            gbc.weightx = 1.0
            SwingUtilities.invokeLater {
                repaintMap()
            }
        }

        fun repaintMap() {
            colorPointMap.clear()
            componentPointMap.clear()
            this.removeAll()
            for (row in 0 until mapHeight) {
                for (column in 0 until mapWidth) {
                    gbc.gridx = column
                    gbc.gridy = row
                    var border: Border = MatteBorder(1, 1, if (row == mapHeight) 1 else 0, if (column == mapWidth) 1 else 0, Color(255,255,255,25))
                    val flippedY = (64 - row) - 1
                    val point = Point(column, flippedY)
                    colorPointMap[point] = MapTileParser.definition.getTile(column, flippedY, plane).underlayId - 1
                    val mapCell = MapCell()

                    val tileDef = MapTileParser.definition.getTile(column, flippedY, plane)
                    var overlayID = (MapTileParser.definition.getTile(column, flippedY, plane).overlayId - 1)
                    var underlayID = (MapTileParser.definition.getTile(column, flippedY, plane).underlayId - 1)


                    componentPointMap[point] = mapCell
                    add(mapCell, gbc)

                    if(overlayID < 0 && underlayID < 0){
                        mapCell.defaultBackground = Color(0,0,0,0)
                        mapCell.background = mapCell.defaultBackground
                        continue
                    }
                    if (underlayID < 0) {
                        underlayID = 0
                    }
                    if (overlayID < 0) {
                        overlayID = 0
                    }

                    val fluDef = underlayMap[underlayID]
                    val floDef = overlayMap[overlayID]

                    if (overlayID == 0) {
                        mapCell.background = fluDef!!.getRGB()
                    } else if (floDef!!.blendColor != -1) {
                        mapCell.background = Color(floDef.blendColor)
                    } else {
                        mapCell.background = floDef.getRGB()
                    }

                    if(visualizeHeight){
                        val height = tileDef.height
                        val otherTiles: List<MapTile> = Pair(column, flippedY).getSurroundingTiles()
                        
                        val borderSizes = intArrayOf(0,0,0,0)
                        var borderColor = Color(0,0,0,0)

                        for(tileIndex in otherTiles.indices){
                            val tile = otherTiles[tileIndex]
                            if(height > tile.height){
                                val tileDiff = height - tile.height
                                val alpha = Math.min(tileDiff * 8, 255)
                                val color: Color
                                try {
                                    color = Color(0,0,0, alpha)
                                } catch (e: Exception){
                                    println("Invalid alpha value: $alpha")
                                    return
                                }

                                val borderIndex = when(tileIndex){
                                    0 -> 0
                                    3 -> 1
                                    2 -> 2
                                    1 -> 3
                                    else -> -1
                                }

                                borderSizes[borderIndex] = 3
                                if(color.alpha > borderColor.alpha) borderColor = color
                            }
                        }

                        mapCell.border = MatteBorder(borderSizes[0], borderSizes[1], borderSizes[2], borderSizes[3], borderColor)
                    } else if(drawGrid) {
                        mapCell.border = border
                    }

                    val sceneryHere = sceneries.filter { it.x == column && it.y == flippedY && it.plane == plane }.toList()
                    if(sceneryHere.isNotEmpty()){
                        sceneryHere.forEach(mapCell::flagScenery)
                    }

                    val thisAbsolute = Util.getAbsoluteCoordinates(region, column, flippedY, plane)
                    val npcsHere = npcs!!.filter { it.location == thisAbsolute }.toList()
                    if (npcsHere.isNotEmpty()) {
                        mapCell.layout = BorderLayout()
                        mapCell.add(JLabel(YELLOW_DOT))
                    }
                    val itemSpawnsHere = items!!.filter { it.location == thisAbsolute }.toList()
                    if (itemSpawnsHere.isNotEmpty()) {
                        mapCell.layout = BorderLayout()
                        mapCell.add(JLabel(RED_DOT))
                    }
                }
            }
            SwingUtilities.invokeLater {
                this.repaint()
            }
        }
    }

    class ScrollPane : JPanel() {
        init {
            // Scroll bar
            val scrollFrame = JScrollPane(mapPanel)
            scrollFrame.maximumSize = Dimension(1300, 750)
            scrollFrame.preferredSize = Dimension(1300, 750)
            scrollFrame.verticalScrollBar.unitIncrement = 20
            add(scrollFrame, BorderLayout.CENTER)
        }
    }

    class ScrollPane2 : JPanel() {
    	lateinit var scrollFrame: JScrollPane
        init {
            // Scroll bar
            layout = BorderLayout()
            scrollFrame = JScrollPane(infoPane)
            scrollFrame.preferredSize = Dimension(255, 980)
            scrollFrame.verticalScrollBar.unitIncrement = 20
            add(scrollFrame, BorderLayout.CENTER)
        }
    }

    fun makeTextPanel(text: String?): JComponent {
        val panel = JPanel(false)
        val filler = JLabel(text)
        filler.horizontalAlignment = JLabel.CENTER
        panel.layout = GridLayout(1, 1)
        panel.add(filler)
        return panel
    }

    class MenuBar : JMenuBar() {
        init {
            add(GoMenuOption())
	    add(MoveRegionButton("NORTH", 0, 1))
	    add(MoveRegionButton("SOUTH", 0, -1))
	    add(MoveRegionButton("EAST", 1, 0))
	    add(MoveRegionButton("WEST", -1, 0))
            add(JSeparator(JSeparator.VERTICAL))
	    var saveButton = JButton("Save")
	    saveButton.addMouseListener(object : MouseAdapter() {
		override fun mouseClicked(e: MouseEvent) { EditorConstants.saveFocusedEditor() }
	    })
	    add(saveButton)
	    add(JSeparator(JSeparator.VERTICAL))
            add(JLabel("Plane: "))
            add(PlaneButton(0))
            add(PlaneButton(1))
            add(PlaneButton(2))
            add(PlaneButton(3))

        }
    }

    class GoMenuOption : JMenu("Go..") {
        init {
            add(LoadRegionItem())
	    add(LoadByCoordinatesItem())
        }
    }

    class LoadRegionItem : JMenuItem("To Region") {
        init {
            addActionListener {
                val region = JOptionPane.showInputDialog("Enter Desired Region ID")
                val regionId = region.toIntOrNull() ?: JOptionPane.showInputDialog("Invalid Integer Entered").toIntOrNull() ?: return@addActionListener

                Rs2MapEditor.loadRegion(regionId)
            }
        }
    }

    class LoadByCoordinatesItem : JMenuItem("To Coords") {
	init {
	    addActionListener {
	        val coords = JOptionPane.showInputDialog("Enter Coords")
		val components = coords.split(",").map { it.trim { it <= ' ' }.toInt() }.toIntArray()
		val regionId = Util.getRegionId (TableData.Location(components[0], components[1], 0))

		Rs2MapEditor.loadRegion(regionId)
	    }
	}
    }

    class PrefMenuOption : JMenu("Preferences") {
        init {
            add(visualizeHeightItem())
        }
    }


    class visualizeHeightItem : JCheckBoxMenuItem("Visualize Heights", false) {
        init {
            addActionListener {
                Rs2MapEditor.visualizeHeight = !Rs2MapEditor.visualizeHeight
                SwingUtilities.invokeLater {
                    mapPanel.repaintMap()
                }
            }
        }
    }

    class MoveRegionButton(symbol: String, val diffX: Int, val diffY: Int) : JButton(symbol) {
	init {
	    this.addMouseListener (object : MouseAdapter() {
	    	override fun mouseClicked (e: MouseEvent) { 
			var currentRegion = Rs2MapEditor.region
			var newRegion = Util.getRegion(currentRegion, diffX, diffY)
			Rs2MapEditor.loadRegion(newRegion)
		}
	    })
	}
    }

    var DirectionCheckboxOnClick = object : ItemListener {
	override fun itemStateChanged (e: ItemEvent) {
	    var source = e.getItemSelectable()
	    if (source !is JCheckBox) return
	    if (e.getStateChange() == ItemEvent.SELECTED) {
		for (checkbox in npcDirectionCheckboxes) { if (checkbox != source) checkbox.setSelected(false) }
	    } else {
		var anySelected = false
		for (checkbox in npcDirectionCheckboxes) { if (checkbox.isSelected) anySelected = true }
		if (!anySelected) source.setSelected(true)
	    }
	}
    }

    fun loadRegion (regionId: Int) {
	Rs2MapEditor.region = regionId
	Rs2MapEditor.plane = 0
	npcs = TableData.npcSpawns[regionId] ?: ArrayList<TableData.NPCSpawn>().also { TableData.npcSpawns[regionId] = it } 
	items = TableData.itemSpawns[regionId] ?: ArrayList<TableData.ItemSpawn>().also { TableData.itemSpawns[regionId] = it }
	sceneries = TileSceneryParser.parseRegion(regionId)
	npcPanel.redrawRows()
	itemPanel.redrawRows()
	try {
	    MapTileParser.init()
	    mapPanel.repaintMap()
	} catch (e: Exception){
	    mapPanel.removeAll()
	    mapPanel.revalidate()
	    JOptionPane.showMessageDialog(null, "No Map Data for Region ID $regionId.")
	}
    }
}

enum class EditorState{
    NONE,
    SET_UNDERLAY,
    SET_OVERLAY,
    ADD_NPC,
    ADD_GROUNDITEM,
    DEL_NPC,
    DEL_GROUNDITEM
}
