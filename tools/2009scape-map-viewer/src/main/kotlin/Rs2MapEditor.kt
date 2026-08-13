
import cacheops.cache.definition.data.OverlayDefinition
import cacheops.cache.definition.data.UnderlayDefinition
import cacheops.cache.definition.data.MapTile
import cacheops.cache.definition.decoder.*
import com.displee.cache.CacheLibrary
import com.formdev.flatlaf.FlatDarculaLaf
import const.Image.RED_DOT
import const.Image.YELLOW_DOT
import const.cachePath
import misc.CustomEventQueue
import tools.ItemSearchTool
import tools.NPCSearchTool
import ui.*
import java.awt.*
import javax.swing.*
import javax.swing.border.*
import kotlin.system.exitProcess

object Rs2MapEditor {

    val library = CacheLibrary.create(cachePath)
    var region = 12850
    var npcs = NPCSpawnParser.parseNPCSpawns(region)
    var items = GroundItemSpawnParser.parseItemSpawns(region)
    var sceneries = TileSceneryParser.parseRegion(region)
    val npcRows = ArrayList<NPCSpawnPanel.NPCRow>()
    val itemRows = ArrayList<ItemSpawnPanel.ItemRow>()
    var npcsUpdated = false
    var itemsUpdated = false
    var tileDataUpdated = false
    var plane = 0
    var visualizeHeight = false
    var drawGrid = true
    var previousHeight = -1
    lateinit var mapPanel: MapPane
    lateinit var npcPanel: NPCSpawnPanel
    lateinit var itemPanel: ItemSpawnPanel
    lateinit var infoPane: InfoPane

    @JvmStatic
    fun main(args: Array<String>) {
        FlatDarculaLaf.setup()
        FloorUnderlayConfiguration.init()
        FloorOverlayConfiguration.init()
        MapTileParser.init()
        underlayMap = FloorUnderlayConfiguration.floorUnderlays
        overlayMap = FloorOverlayConfiguration.floorOverlays
        MainFrame()
    }

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
    lateinit var itemIdInput: JTextField
    lateinit var itemRespawnInput: JTextField
    lateinit var itemAmountInput: JTextField
    var state = EditorState.NONE

    class MainFrame : JFrame("2009scape Map Viewer v1.0") {
        init {
            val statusBar = JPanel(FlowLayout(FlowLayout.LEFT))
            statusBar.border = CompoundBorder(
                LineBorder(Color.DARK_GRAY),
                EmptyBorder(2, 3, 2, 3)
            )
            statusLabel.foreground = Color.YELLOW
            statusBar.add(statusLabel)

            bounds = Rectangle(Dimension(1567, 850))
            size.setSize(1567, 850)
            maximumSize = Dimension(1567, 850)
            defaultCloseOperation = EXIT_ON_CLOSE
            jMenuBar = MenuBar()
            mapPanel = MapPane()
            npcIdInput = JTextField()
            itemIdInput = JTextField()
            itemRespawnInput = JTextField()
            itemAmountInput = JTextField()
            npcPanel = NPCSpawnPanel()
            itemPanel = ItemSpawnPanel()
            infoPane = InfoPane()
            add(ScrollPane(), BorderLayout.CENTER)
            add(ScrollPane2(), BorderLayout.EAST)
            add(statusBar, BorderLayout.SOUTH)
            infoLabel.text = "Information"
            statusLabel.text = "-"
            setLocationRelativeTo(null)
            isResizable = false
            isVisible = true
            CustomEventQueue.install()
            JOptionPane.showConfirmDialog(this, "Please make sure it is okay to make cache changes right now.","ALERT",JOptionPane.OK_OPTION)
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

                    val npcsHere = npcs.filter { it.x == column && it.y == flippedY && it.plane == plane }.toList()
                    if (npcsHere.isNotEmpty()) {
                        mapCell.layout = BorderLayout()
                        mapCell.add(JLabel(YELLOW_DOT))
                    }
                    val itemSpawnsHere = items.filter { it.x == column && it.y == flippedY && it.plane == plane }.toList()
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
        init {
            // Scroll bar
            layout = BorderLayout()
            val scrollFrame = JScrollPane(infoPane)
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
            add(FileMenuOption())
            add(RegionMenuOption())
            add(PrefMenuOption())
            add(ToolsMenuOption())
            add(JSeparator(JSeparator.VERTICAL))
            add(JLabel("Plane: "))
            add(PlaneButton(0))
            add(PlaneButton(1))
            add(PlaneButton(2))
            add(PlaneButton(3))
        }
    }

    class ToolsMenuOption : JMenu("Tools") {
        init {
            add(NPCSearchItem())
            add(ItemSearchItem())
        }
    }

    class NPCSearchItem : JMenuItem("NPC Search") {
        init {
            addActionListener {
                NPCSearchTool.open()
                NPCSearchTool.caller = { id, _ ->
                    npcIdInput.text = id.toString()
                    infoPane.selectedIndex = 3
                    state = EditorState.ADD_NPC
                }
            }
        }
    }

    class ItemSearchItem : JMenuItem("Item Search"){
        init {
            addActionListener {
                ItemSearchTool.open()
                ItemSearchTool.caller = {id, _ ->
                    itemIdInput.text = id.toString()
                    infoPane.selectedIndex = 4
                    state = EditorState.ADD_GROUNDITEM
                }
            }
        }
    }

    class FileMenuOption : JMenu("File") {
        init {
            add(LoadItem())
            add(QuitItem())
        }
    }

    class LoadItem : JMenuItem("Load Cache") {
        init {
            addActionListener {
                println("This feature has not been enabled yet")
            }
        }
    }

    class QuitItem : JMenuItem("Quit") {
        init {
            addActionListener {
                exitProcess(0)
            }
        }
    }

    class RegionMenuOption : JMenu("Region") {
        init {
            add(LoadRegionItem())
        }
    }

    class LoadRegionItem : JMenuItem("Load") {
        init {
            addActionListener {
                val region = if(npcsUpdated || tileDataUpdated || itemsUpdated){
                    val response = JOptionPane.showConfirmDialog(this,"You have unsaved changes, continue?")
                    if(response == 0){
                        JOptionPane.showInputDialog("Enter Desired Region ID")
                    }
                    else return@addActionListener
                }
                else JOptionPane.showInputDialog("Enter Desired Region ID")

                val regionId = region.toIntOrNull() ?: JOptionPane.showInputDialog("Invalid Integer Entered").toIntOrNull() ?: return@addActionListener

                Rs2MapEditor.region = regionId
                Rs2MapEditor.plane = 0
                npcs = NPCSpawnParser.parseNPCSpawns(regionId)
                items = GroundItemSpawnParser.parseItemSpawns(regionId)
                sceneries = TileSceneryParser.parseRegion(regionId)
                npcPanel.redrawRows()
                itemPanel.redrawRows()
                try {
                    MapTileParser.init()
                    mapPanel.repaintMap()
                } catch (e: Exception){
                    mapPanel.removeAll()
                    mapPanel.revalidate()
                    JOptionPane.showMessageDialog(this, "No Map Data for Region ID $regionId.")
                }
            }
        }
    }

    class PrefMenuOption : JMenu("Preferences") {
        init {
            add(DisplayUnderlayItem())
            add(DisplayOverlayItem())
            add(DisplaySceneryItem())
            addSeparator()
            add(drawGridItem())
            add(DisplayHeightItem())
            add(visualizeHeightItem())
        }
    }

    class drawGridItem : JCheckBoxMenuItem("Draw Grid", true) {
        init {
            addActionListener {
                Rs2MapEditor.drawGrid = !Rs2MapEditor.drawGrid
                SwingUtilities.invokeLater {
                    mapPanel.repaintMap()
                }
            }
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

    class DisplayUnderlayItem : JCheckBoxMenuItem("Display Underlays", true) {
        init {
            addActionListener {
                println("This feature has not been enabled yet")
            }
        }
    }

    class DisplayOverlayItem : JCheckBoxMenuItem("Display Overlays", false) {
        init {
            addActionListener {
                println("This feature has not been enabled yet")
            }
        }
    }

    class DisplaySceneryItem : JCheckBoxMenuItem("Display Scenery", false) {
        init {
            addActionListener {
                println("This feature has not been enabled yet")
            }
        }
    }

    class DisplayHeightItem : JCheckBoxMenuItem("Display Underlay IDs", false) {
        init {
            addActionListener {
                underlayLabel.isVisible = !underlayLabel.isVisible
            }
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