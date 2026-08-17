package rt4;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;

/**
 * Presentation-only HUD for the MODERN first-person rig.
 *
	 * <p>The cache interface tree remains authoritative for the minimap, chat,
	 * tabs and input. This class positions those components and
 * draws a small generated-sprite skin plus assigned action/item slots. Slot
 * activation is delegated to {@link ModernQuickBars}, which reuses vanilla
 * menu actions instead of creating gameplay packets here.</p>
 */
public final class ModernHud {

	private static final int RESIZABLE_ROOT = 746;
	private static final int FIXED_ROOT = 548;
	private static final int CHAT_INTERFACE = 752;
	private static final int CHAT_CONTENT_INTERFACE = 137;
	private static final int WORLD_MAP_INTERFACE = 755;
	private static final int KEY_M = 70;
	private static final int KEY_ESCAPE = 13;
	private static final int DRAG_NONE = 0;
	private static final int DRAG_ACTION = 1;
	private static final int DRAG_ITEM = 2;
	private static final int STATUS_BAR_WIDTH = 232;
	private static final int STATUS_BAR_HEIGHT = 50;
	private static final int MINIMAP_FRAME_SIZE = 164;
	private static final int MODE_FRAME_WIDTH = 92;
	private static final int MODE_FRAME_HEIGHT = 24;
	private static final int[] STATUS_ORB_X = {20, 87, 153};
	private static final int[] STATUS_METER_X = {19, 86, 153};
	private static final int[] STATUS_METER_COLORS = {0xB72525, 0x3157B7, 0xD0A51D};
	private static final int[] STATUS_METER_HIGHLIGHTS = {0xF05252, 0x6387E8, 0xF4D45A};
	private static final int HITPOINTS_SKILL = 3;
	private static final int PRAYER_SKILL = 5;
	private static final int[] ITEM_SLOT_X = {14, 55, 96, 137, 179, 220};

	private static final String[] COMPASS_DIRECTIONS = {"N", "NE", "E", "SE", "S", "SW", "W", "NW"};
	private static final String ASSET_ROOT = "/modern_hud/";

	private static Sprite compassBackground;
	private static Sprite minimapFrame;
	private static Sprite modeFrame;
	private static Sprite chatBackground;
	private static Sprite actionbarBackground;
	private static Sprite itembarBackground;
	private static Sprite statusbarBackground;
	private static Sprite[] statusIcons;
	private static boolean assetsLoadedForGl;
	private static boolean assetsLoaded;
	private static boolean assetFailureReported;

	private static int lastDiagnosticLoop = -1000;
	private static boolean layoutApplied;
	private static int layoutRoot = -1;
	private static boolean worldMapWasPressed;
	private static boolean worldMapEscapeWasPressed;
	private static int dragType = DRAG_NONE;
	private static int dragSource = -1;
	private static int dragStartX;
	private static int dragStartY;
	private static boolean dragging;

	private ModernHud() {
	}

	/**
	 * Repositions the real cache widgets after normal responsive layout. Their
	 * existing component instances therefore remain the render and input
	 * hitboxes. Leaving the MODERN control profile runs the normal cache layout
	 * again.
	 */
	public static void layoutVanillaHud() {
		int root = InterfaceList.topLevelInterface;
		boolean active = isFullHudActive() && (root == RESIZABLE_ROOT || root == FIXED_ROOT);
		if (!active) {
			restoreVanillaLayout();
			return;
		}

		if (layoutApplied && layoutRoot != root) {
			restoreRoot(layoutRoot);
		}
		if (!InterfaceList.load(root) || InterfaceList.components[root] == null) {
			return;
		}

		if (root == RESIZABLE_ROOT) {
			layoutResizableRoot();
		} else {
			layoutFixedRoot();
		}
		layoutApplied = true;
		layoutRoot = root;
	}

	private static void layoutResizableRoot() {
		int canvasWidth = GameShell.canvasWidth;
		int canvasHeight = GameShell.canvasHeight;

		Component minimap = rootChild(RESIZABLE_ROOT, 7);
		if (minimap != null) {
			minimap.x = Math.max(0, canvasWidth - 258);
			minimap.y = 8;
			minimap.width = 260;
			minimap.height = 176;
		}
		Component map = rootChild(RESIZABLE_ROOT, 8);
		if (map != null) {
			map.x = 76;
			map.y = 9;
		}

		// Detach the real orb hit areas from the minimap container. Their cache
		// visuals are suppressed; the custom meters below keep the input routes.
		positionStatusOrbs(RESIZABLE_ROOT, 13);
		Component summoningOrb = rootChild(RESIZABLE_ROOT, 16);
		if (summoningOrb != null) {
			summoningOrb.x = -1000;
		}
		Component logout = rootChild(RESIZABLE_ROOT, 12);
		if (logout != null) {
			logout.x = 236;
			logout.y = 1;
		}

		Component chatFilters = rootChild(RESIZABLE_ROOT, 23);
		if (chatFilters != null) {
			chatFilters.x = 8;
			chatFilters.y = Math.max(0, canvasHeight - 173);
		}
		Component chat = rootChild(RESIZABLE_ROOT, 70);
		if (chat != null) {
			chat.x = 8;
			chat.y = Math.max(0, canvasHeight - 150);
		}

		// Three parallel vanilla layers make up each tab: hover, button and icon.
		// Move every layer to the same 7 x 2 grid so visuals and hitboxes agree.
		int tabsTop = Math.min(224, Math.max(8, canvasHeight - 80));
		Component tabStrip = rootChild(RESIZABLE_ROOT, 24);
		if (tabStrip != null) {
			tabStrip.x = Math.max(0, canvasWidth - 239);
			tabStrip.y = tabsTop;
			tabStrip.width = 231;
			tabStrip.height = 72;
		}
		resizeTabLayer(25);
		resizeTabLayer(40);
		resizeTabLayer(55);
		positionTabRange(26);
		positionTabRange(41);
		positionTabRange(56);

		// Child 73 owns the real single-tab and selected-tab attachment slots.
		// Move that parent so the rendered interface and all input descendants
		// remain synchronized with the two-row tab strip.
		Component sidePanel = rootChild(RESIZABLE_ROOT, 73);
		if (sidePanel != null) {
			int panelWidth = componentWidth(sidePanel, 190);
			int panelHeight = componentHeight(sidePanel, 261);
			sidePanel.x = Math.max(0, canvasWidth - panelWidth - 8);
			int panelY = tabsTop + 72;
			if (panelY + panelHeight + 8 > canvasHeight) {
				panelY = Math.max(8, tabsTop - panelHeight - 8);
			}
			sidePanel.y = panelY;
		}
	}

	private static void layoutFixedRoot() {
		int canvasWidth = GameShell.canvasWidth;
		int canvasHeight = GameShell.canvasHeight;

		// The fixed root packs the map and four orbs into a 249px legacy frame.
		// Widen that real container and place the three requested live orbs in a
		// separate column so they no longer sit behind the circular map.
		Component minimap = rootChild(FIXED_ROOT, 12);
		if (minimap != null) {
			minimap.x = Math.max(0, canvasWidth - 258);
			minimap.y = 8;
			minimap.width = 260;
			minimap.height = 176;
		}
		Component map = rootChild(FIXED_ROOT, 64);
		if (map != null) {
			map.x = 76;
			map.y = 9;
		}
		positionStatusOrbs(FIXED_ROOT, 70);
		Component summoningOrb = rootChild(FIXED_ROOT, 73);
		if (summoningOrb != null) {
			summoningOrb.x = -1000;
		}
		Component globe = rootChild(FIXED_ROOT, 69);
		if (globe != null) {
			globe.x = 236;
			globe.y = 1;
		}

		Component chatFilters = rootChild(FIXED_ROOT, 0);
		if (chatFilters != null) {
			chatFilters.x = 8;
			chatFilters.y = Math.max(0, canvasHeight - 173);
		}
		Component chat = rootChild(FIXED_ROOT, 75);
		if (chat != null) {
			chat.x = 8;
			chat.y = Math.max(0, canvasHeight - 150);
		}

		// Keep the authoritative selected-tab contents usable as a compact
		// floating pane, while its large fixed-mode stone backdrop is suppressed.
		Component sidePanel = rootChild(FIXED_ROOT, 78);
		int tabsTop = Math.min(224, Math.max(8, canvasHeight - 80));
		int tabsLeft = Math.max(0, canvasWidth - 239);
		if (sidePanel != null) {
			int panelWidth = componentWidth(sidePanel, 190);
			int panelHeight = componentHeight(sidePanel, 261);
			sidePanel.x = Math.max(0, canvasWidth - panelWidth - 8);
			int panelY = tabsTop + 72;
			if (panelY + panelHeight + 8 > canvasHeight) {
				panelY = Math.max(8, tabsTop - panelHeight - 8);
			}
			sidePanel.y = panelY;
		}
		layoutFixedTabRow(34, 36, 38, 45, tabsLeft, tabsTop);
		layoutFixedTabRow(16, 18, 20, 27, tabsLeft, tabsTop + 36);
	}

	private static void layoutFixedTabRow(int rowChild, int layerChild,
			int firstBackgroundChild, int firstIconChild, int left, int top) {
		Component row = rootChild(FIXED_ROOT, rowChild);
		if (row != null) {
			row.x = left;
			row.y = top;
			row.width = 231;
			row.height = 36;
		}
		Component layer = rootChild(FIXED_ROOT, layerChild);
		if (layer != null) {
			layer.x = 0;
			layer.y = 0;
			layer.width = 231;
			layer.height = 36;
		}
		positionFixedTabRange(firstBackgroundChild);
		positionFixedTabRange(firstIconChild);
	}

	private static void positionFixedTabRange(int firstChild) {
		for (int i = 0; i < 7; i++) {
			Component tab = rootChild(FIXED_ROOT, firstChild + i);
			if (tab != null) {
				tab.x = i * 33 + (33 - tab.width) / 2;
				tab.y = (36 - tab.height) / 2;
			}
		}
	}

	private static void resizeTabLayer(int child) {
		Component layer = rootChild(RESIZABLE_ROOT, child);
		if (layer != null) {
			layer.x = 0;
			layer.y = 0;
			layer.width = 231;
			layer.height = 72;
		}
	}

	private static void positionTabRange(int firstChild) {
		for (int i = 0; i < 14; i++) {
			Component tab = rootChild(RESIZABLE_ROOT, firstChild + i);
			if (tab != null) {
				tab.x = i % 7 * 33 + (33 - tab.width) / 2;
				tab.y = i / 7 * 36 + (36 - tab.height) / 2;
			}
		}
	}

	private static int componentWidth(Component component, int fallback) {
		if (component.width > 0) {
			return component.width;
		}
		return component.baseWidth > 0 ? component.baseWidth : fallback;
	}

	private static int componentHeight(Component component, int fallback) {
		if (component.height > 0) {
			return component.height;
		}
		return component.baseHeight > 0 ? component.baseHeight : fallback;
	}

	private static void restoreVanillaLayout() {
		if (!layoutApplied) {
			return;
		}
		restoreRoot(layoutRoot);
		layoutApplied = false;
		layoutRoot = -1;
	}

	private static void restoreRoot(int root) {
		if (root >= 0 && InterfaceList.components != null && root < InterfaceList.components.length
				&& InterfaceList.components[root] != null) {
			restoreStatusOrbParents(root);
			InterfaceList.method4017(GameShell.canvasHeight, false, root, GameShell.canvasWidth);
		}
	}

	private static void positionStatusOrbs(int root, int firstChild) {
		BarLayout bars = barLayout(hudScale());
		int left = bars.actionLeft + (bars.actionWidth - scaled(STATUS_BAR_WIDTH, bars.scale)) / 2;
		int top = bars.actionTop - scaled(STATUS_BAR_HEIGHT, bars.scale) + scaled(1, bars.scale);
		for (int i = 0; i < 3; i++) {
			Component orb = rootChild(root, firstChild + i);
			if (orb != null) {
				orb.overlayer = -1;
				orb.x = left + scaled(STATUS_ORB_X[i], bars.scale);
				orb.y = top + scaled(8, bars.scale);
			}
		}
	}

	private static void restoreStatusOrbParents(int root) {
		int firstChild;
		int parentChild;
		if (root == RESIZABLE_ROOT) {
			firstChild = 13;
			parentChild = 7;
		} else if (root == FIXED_ROOT) {
			firstChild = 70;
			parentChild = 12;
		} else {
			return;
		}
		for (int i = 0; i < 3; i++) {
			Component orb = rootChild(root, firstChild + i);
			if (orb != null) {
				orb.overlayer = root << 16 | parentChild;
			}
		}
	}

	/** Draws the generated skin and quick bars after the vanilla interfaces. */
	public static void draw() {
		if (!CameraMode.isModern() || Fonts.p11Full == null) {
			return;
		}
		if (isWorldMapOpen()) {
			return;
		}
		ensureAssets();
		setFullClip();

		if (!isFullHudActive()) {
			drawModeIndicator(hudScale());
			return;
		}

		float scale = hudScale();
		drawCompass(scale);
		drawMinimapFrame();
		drawModeIndicator(scale);
		drawTabs();
		drawActionBars(scale);
		drawStatusMeters(scale);
		diagnostic(scale);
	}

	/**
	 * Opens the FIRST_PERSON world map through the same component action as
	 * the hidden cache button. The key edge is updated while another UI owns
	 * input, preventing held-key repeats and delayed activations.
	 */
	public static boolean updateInput(boolean higherPriorityInputConsumed) {
		boolean escapeDown = Keyboard.pressedKeys[KEY_ESCAPE];
		boolean escapeEdge = escapeDown && !worldMapEscapeWasPressed;
		worldMapEscapeWasPressed = escapeDown;
		if (escapeEdge && isFullHudActive() && InterfaceList.topLevelInterface == WORLD_MAP_INTERFACE) {
			if (InterfaceList.load(WORLD_MAP_INTERFACE)) {
				Component closeButton = rootChild(WORLD_MAP_INTERFACE, 3);
				if (closeButton != null) {
					resetBarDrag();
					return invokeFirstComponentAction(closeButton);
				}
			}
		}

		boolean down = Keyboard.pressedKeys[KEY_M];
		boolean edge = down && !worldMapWasPressed;
		worldMapWasPressed = down;
		if (edge && !higherPriorityInputConsumed && isFullHudActive()
				&& ModernControlController.isGameplayInputAllowed()
				&& !Cs1ScriptRunner.aBoolean108 && !FPContextMenuController.isMenuOpen()
				&& !Keyboard.pressedKeys[Keyboard.KEY_CTRL]
				&& !Keyboard.pressedKeys[Keyboard.KEY_SHIFT]) {
			int root = InterfaceList.topLevelInterface;
			Component button = rootChild(root, root == RESIZABLE_ROOT ? 110 : 66);
			if (button != null) {
				return invokeFirstComponentAction(button);
			}
		}
		if (higherPriorityInputConsumed || !isFullHudActive()
				|| !ModernControlController.isGameplayInputAllowed()
				|| Cs1ScriptRunner.aBoolean108 || FPContextMenuController.isMenuOpen()
				|| InterfaceList.topLevelInterface != RESIZABLE_ROOT
				&& InterfaceList.topLevelInterface != FIXED_ROOT) {
			resetBarDrag();
			return false;
		}
		if (updateTabClick()) {
			return true;
		}
		return updateBarDrag();
	}

	public static boolean isWorldMapOpen() {
		return InterfaceList.topLevelInterface == WORLD_MAP_INTERFACE;
	}

	private static boolean invokeFirstComponentAction(Component button) {
		if (button.if3) {
			for (int op = 0; op < 10; op++) {
				if (InterfaceList.getOp(button, op) != null) {
					MiniMenu.invokeExistingAction(op < 5 ? MiniMenu.UNKNOWN_9 : MiniMenu.UNKNOWN_1003,
							button.createdComponentId, button.id, op + 1L, button.optionBase);
					return true;
				}
			}
			// Some cache close buttons have only server-side active properties and
			// no visible operation label. Operation one is still the real IF_BUTTON
			// route handled by the server listener.
			MiniMenu.invokeExistingAction(MiniMenu.UNKNOWN_9, button.createdComponentId,
					button.id, 1L, button.optionBase);
			return true;
		}
		MiniMenu.invokeExistingAction(MiniMenu.UNKNOWN_8, 0, button.id, 0L, button.option);
		return true;
	}

	private static boolean updateTabClick() {
		if (Mouse.clickButton != 1 || !FirstPersonCamera.isUiCursorActive()) {
			return false;
		}
		int root = InterfaceList.topLevelInterface;
		Component anchor = rootChild(root, root == RESIZABLE_ROOT ? 24 : 34);
		if (anchor == null) {
			return false;
		}
		int left = absoluteX(anchor);
		int top = absoluteY(anchor);
		int relativeX = Mouse.clickX - left;
		int relativeY = Mouse.clickY - top;
		if (relativeX < 0 || relativeX >= 231 || relativeY < 0 || relativeY >= 72) {
			return false;
		}
		int column = relativeX / 33;
		int row = relativeY / 36;
		int slot = row * 7 + column;
		Component button;
		if (root == RESIZABLE_ROOT) {
			button = rootChild(root, 41 + slot);
		} else {
			button = rootChild(root, (row == 0 ? 38 : 20) + column);
		}
		return button != null && invokeFirstComponentAction(button);
	}

	/**
	 * Called at the ordinary component render point. It replaces only known
	 * decorative cache sprites; all text, scroll/input components and scripts
	 * continue down the original renderer and input paths.
	 */
	public static boolean replaceOrSuppressVanillaComponent(Component component, int x, int y) {
		if (!isFullHudActive() || component == null) {
			return false;
		}
		int group = component.id >>> 16;
		int child = component.id & 0xFFFF;
		if (group == CHAT_INTERFACE && child == 1) {
			ensureAssets();
			if (chatBackground != null) {
				drawAssetAlpha(chatBackground, x, y, component.width, component.height, 145);
			}
			return true;
		}
		if (group == CHAT_CONTENT_INTERFACE) {
			// Preserve message/input text and CS2 hooks; remove only the old
			// name/input ornaments and separator lines.
			return child == 4 || child == 5 || child == 6
					|| child == 53 || child == 54 || child == 55;
		}
		// The modern status bar draws live HP/Prayer/Run percentages itself.
		// Suppress only the three cache orb visuals; their positioned component
		// trees remain present so the established input routes stay available.
		if (group == 748 || group == 749 || group == 750) {
			return true;
		}
		if (group == RESIZABLE_ROOT) {
			if (child >= 78 && child <= 92) {
				if (!component.hidden) {
					drawComponentSpriteAlpha(component, x, y, component.width, component.height, 190);
				}
				return true;
			}
			return child == 9 || child == 10 || child == 11
					|| child == 75 || child == 109 || child == 110
					|| child >= 26 && child <= 69;
		}
		if (group == FIXED_ROOT) {
			if (child == 79) {
				if (!component.hidden) {
					drawComponentSpriteAlpha(component, x, y, component.width, component.height, 190);
				}
				return true;
			}
			return child == 17 || child == 35
					|| child >= 20 && child <= 33 || child >= 38 && child <= 51
					|| child == 53 || child == 55 || child == 58 || child == 59
					|| child == 61 || child == 62 || child == 63 || child == 65
					|| child == 66 || child == 67 || child == 68;
		}
		return false;
	}

	/** Keeps the original chat contents readable on the translucent FP skin. */
	public static int overrideTextColor(Component component, int color) {
		if (isFullHudActive() && component != null
				&& component.id >>> 16 == CHAT_CONTENT_INTERFACE && component.type == 4
				&& color == 0) {
			return 0xE8E0D0;
		}
		return color;
	}

	/** Subtle marker on inventory items that are already assigned to the quickbar. */
	public static void drawInventoryQuickbarHighlight(Component component, int itemId, int x, int y) {
		if (!isFullHudActive() || component == null || component.id >>> 16 != 149
				|| !ModernQuickBars.isItemAssigned(itemId)) {
			return;
		}
		fillRectAlpha(x - 1, y - 1, 34, 34, 0xC89532, 42);
		drawRect(x - 1, y - 1, 34, 34, 0xE4BE62);
	}

	private static void drawCompass(float scale) {
		int width = scaled(380, scale);
		int height = scaled(44, scale);
		int left = (GameShell.canvasWidth - width) / 2;
		int top = scaled(9, scale);
		drawAssetAlpha(compassBackground, left, top, width, height, 190);

		int yaw = FirstPersonCamera.getYaw() & 0x7FF;
		int spacing = scaled(43, scale);
		int remainder = yaw & 0xFF;
		int first = yaw >> 8;
		int offsetPixels = remainder * spacing / 256;
		int centerX = left + width / 2;
		for (int i = -4; i <= 4; i++) {
			int direction = first + i & 7;
			int labelX = centerX + i * spacing - offsetPixels;
			if (labelX > left + scaled(18, scale) && labelX < left + width - scaled(18, scale)) {
				int color = Math.abs(labelX - centerX) < spacing / 2 ? 0xFFD36A : 0xE8E0D0;
				drawCenteredText(COMPASS_DIRECTIONS[direction], labelX, top + scaled(29, scale), color);
			}
		}
	}

	private static void drawMinimapFrame() {
		int root = InterfaceList.topLevelInterface;
		if (root != RESIZABLE_ROOT && root != FIXED_ROOT) {
			return;
		}
		Component map = rootChild(root, root == RESIZABLE_ROOT ? 8 : 64);
		if (map == null) {
			return;
		}
		int mapX = absoluteX(map);
		int mapY = absoluteY(map);
		int inset = (componentWidth(map, 152) - MINIMAP_FRAME_SIZE) / 2;
		drawAsset(minimapFrame, mapX + inset, mapY + inset,
				MINIMAP_FRAME_SIZE, MINIMAP_FRAME_SIZE);
	}

	private static void drawModeIndicator(float scale) {
		int width = scaled(MODE_FRAME_WIDTH, scale);
		int height = scaled(MODE_FRAME_HEIGHT, scale);
		int left = GameShell.canvasWidth - width - scaled(14, scale);
		int top = scaled(12, scale);
		int root = InterfaceList.topLevelInterface;
		if (root == RESIZABLE_ROOT || root == FIXED_ROOT) {
			Component map = rootChild(root, root == RESIZABLE_ROOT ? 8 : 64);
			if (map != null) {
				left = absoluteX(map) + componentWidth(map, 152) / 2 - width / 2;
				top = absoluteY(map) + componentHeight(map, 152) + scaled(15, scale);
			}
		}
		drawAsset(modeFrame, left, top, width, height);
		drawCenteredText(currentModeLabel(), left + width / 2, top + height / 2 + 4, 0xE7D4A3);
	}

	/** Draws the real cache tab icons once, in an explicit 7-by-2 grid. */
	private static void drawTabs() {
		int root = InterfaceList.topLevelInterface;
		if (root == RESIZABLE_ROOT) {
			Component strip = rootChild(root, 24);
			if (strip != null) {
				drawTabGrid(absoluteX(strip), absoluteY(strip), root, 56, 41, 26, 93);
			}
		} else if (root == FIXED_ROOT) {
			Component top = rootChild(root, 34);
			if (top != null) {
				drawFixedTabGrid(absoluteX(top), absoluteY(top));
			}
		}
	}

	private static void drawTabGrid(int left, int top, int root,
			int firstIcon, int firstNormal, int firstHighlight, int firstContent) {
		int selected = selectedTabIndex(root, firstContent);
		for (int slot = 0; slot < 14; slot++) {
			int slotX = left + slot % 7 * 33;
			int slotY = top + slot / 7 * 36;
			drawTabSlot(slotX, slotY, rootChild(root, firstIcon + slot),
					rootChild(root, firstNormal + slot), rootChild(root, firstHighlight + slot),
					slot == selected);
		}
	}

	private static void drawFixedTabGrid(int left, int top) {
		int selected = selectedTabIndex(FIXED_ROOT, 83);
		for (int slot = 0; slot < 14; slot++) {
			boolean secondRow = slot >= 7;
			int index = secondRow ? slot - 7 : slot;
			int slotX = left + index * 33;
			int slotY = top + (secondRow ? 36 : 0);
			drawTabSlot(slotX, slotY,
					rootChild(FIXED_ROOT, (secondRow ? 27 : 45) + index),
					rootChild(FIXED_ROOT, (secondRow ? 20 : 38) + index),
					null, slot == selected);
		}
	}

	private static void drawTabSlot(int x, int y, Component icon,
			Component normalState, Component highlightState, boolean selected) {
		boolean hovered = isUiHover(x, y, 33, 36);
		if (!drawComponentSprite(normalState, x, y, 33, 36)) {
			fillRectAlpha(x + 1, y + 1, 31, 34, 0x17120D, 190);
			drawRect(x, y, 33, 36, 0x73542D);
		}
		if ((selected || hovered) && highlightState != null) {
			drawComponentSprite(highlightState, x, y, 33, 36);
		}
		if (selected) {
			fillRectAlpha(x + 2, y + 2, 29, 32, 0xD09B37, 55);
			drawRect(x + 1, y + 1, 31, 34, 0xE5C16B);
		}
		if (hovered) {
			fillRectAlpha(x + 2, y + 2, 29, 32, 0xE8E0D0, 35);
		}
		if (icon != null) {
			Sprite sprite = icon.method489(false);
			if (sprite == null) {
				sprite = icon.method489(true);
			}
			if (sprite != null) {
				drawCenteredSprite(sprite, x, y, 33, 36, 28, 256);
			}
		}
	}

	private static int selectedTabIndex(int root, int firstContent) {
		for (int slot = 0; slot < 14; slot++) {
			Component content = rootChild(root, firstContent + slot);
			if (content != null && !content.hidden) {
				return slot;
			}
		}
		return -1;
	}

	private static boolean drawComponentSprite(Component component, int x, int y, int width, int height) {
		if (component == null) {
			return false;
		}
		Sprite sprite = component.method489(false);
		if (sprite == null) {
			sprite = component.method489(true);
		}
		if (sprite != null) {
			drawAsset(sprite, x, y, width, height);
			return true;
		}
		return false;
	}

	private static boolean drawComponentSpriteAlpha(Component component, int x, int y,
			int width, int height, int alpha) {
		if (component == null) {
			return false;
		}
		Sprite sprite = component.method489(false);
		if (sprite == null) {
			sprite = component.method489(true);
		}
		if (sprite != null) {
			drawAssetAlpha(sprite, x, y, width, height, alpha);
			return true;
		}
		return false;
	}

	private static void drawActionBars(float scale) {
		BarLayout layout = barLayout(scale);
		drawAsset(actionbarBackground, layout.actionLeft, layout.actionTop,
				layout.actionWidth, layout.actionHeight);
		drawAsset(itembarBackground, layout.quickLeft, layout.quickTop,
				layout.quickWidth, layout.quickHeight);
		drawActionSlots(layout.actionLeft, layout.actionTop, scale);
		drawItemSlots(layout.quickLeft, layout.quickTop, scale);
		drawDraggedSlot(scale);
	}

	/** Draws three live custom meters while retaining the cache orb hit areas. */
	private static void drawStatusMeters(float scale) {
		BarLayout bars = barLayout(scale);
		int width = scaled(STATUS_BAR_WIDTH, scale);
		int height = scaled(STATUS_BAR_HEIGHT, scale);
		int left = bars.actionLeft + (bars.actionWidth - width) / 2;
		int top = bars.actionTop - height + scaled(1, scale);

		int[] percentages = {
				statusPercent(PlayerSkillXpTable.boostedLevels[HITPOINTS_SKILL],
						PlayerSkillXpTable.baseLevels[HITPOINTS_SKILL]),
				statusPercent(PlayerSkillXpTable.boostedLevels[PRAYER_SKILL],
						PlayerSkillXpTable.baseLevels[PRAYER_SKILL]),
				clampPercent(Player.runEnergy)
		};
		int[] values = {
				Math.max(0, PlayerSkillXpTable.boostedLevels[HITPOINTS_SKILL]),
				Math.max(0, PlayerSkillXpTable.boostedLevels[PRAYER_SKILL]),
				Math.max(0, Player.runEnergy)
		};
		for (int meter = 0; meter < percentages.length; meter++) {
			drawStatusMeterFill(left, top, scale, meter, percentages[meter]);
		}
		drawAsset(statusbarBackground, left, top, width, height);
		for (int meter = 0; meter < percentages.length; meter++) {
			int meterLeft = left + scaled(STATUS_METER_X[meter], scale);
			int meterWidth = scaled(60, scale);
			int iconSize = scaled(18, scale);
			drawAsset(statusIcons == null ? null : statusIcons[meter],
					meterLeft + scaled(5, scale), top + scaled(16, scale) - iconSize / 2,
					iconSize, iconSize);
			drawCenteredText(Integer.toString(values[meter]),
					meterLeft + scaled(41, scale), top + scaled(30, scale), 0xFFFFFF);
		}
	}

	private static void drawStatusMeterFill(int left, int top, float scale,
			int meter, int percentage) {
		int meterLeft = left + scaled(STATUS_METER_X[meter], scale);
		int meterTop = top + scaled(8, scale);
		int meterWidth = scaled(60, scale);
		int meterHeight = scaled(34, scale);
		int padding = Math.max(1, scaled(2, scale));
		int innerLeft = meterLeft + padding;
		int innerTop = meterTop + padding;
		int innerWidth = meterWidth - padding * 2;
		int innerHeight = meterHeight - padding * 2;
		fillRectAlpha(innerLeft, innerTop, innerWidth, innerHeight, 0x110E0B, 235);
		int fillWidth = innerWidth * percentage / 100;
		if (fillWidth > 0) {
			fillRectAlpha(innerLeft, innerTop, fillWidth, innerHeight,
					STATUS_METER_COLORS[meter], 238);
			fillRectAlpha(innerLeft, innerTop, fillWidth,
					Math.max(1, innerHeight / 3), STATUS_METER_HIGHLIGHTS[meter], 110);
			fillRectAlpha(innerLeft, innerTop + innerHeight * 2 / 3, fillWidth,
					Math.max(1, innerHeight / 3), 0x000000, 55);
		}
		drawRect(meterLeft, meterTop, meterWidth, meterHeight, 0x120F0C);
	}

	private static int statusPercent(int current, int maximum) {
		if (maximum <= 0) {
			return 0;
		}
		return clampPercent((current * 100 + maximum / 2) / maximum);
	}

	private static int clampPercent(int value) {
		return Math.max(0, Math.min(100, value));
	}

	private static void drawActionSlots(int left, int top, float scale) {
		int slot = scaled(32, scale);
		int firstX = left + scaled(6, scale);
		int slotY = top + scaled(8, scale);
		int stride = scaled(35, scale);
		for (int i = 0; i < ModernQuickBars.ACTION_SLOT_COUNT; i++) {
			int slotX = firstX + i * stride;
			boolean hovered = isUiHover(slotX, slotY, slot, slot);
			drawSlotState(ModernQuickBars.isActionActive(i), hovered, slotX, slotY, slot);
			Sprite icon = ModernQuickBars.getActionSprite(i);
			if (icon != null) {
				int iconSize = Math.min(slot - 4, scaled(28, scale));
				drawCenteredSprite(icon, slotX, slotY, slot, slot, iconSize,
						dragging && dragType == DRAG_ACTION && dragSource == i ? 75 : 256);
			}
			String key = i == 9 ? "S0" : "S" + (i + 1);
			Fonts.p11Full.renderLeft(JagString.parse(key), slotX + 2, slotY + 10, 0xD8C19A, 0);
		}
	}

	private static void drawItemSlots(int left, int top, float scale) {
		int slot = scaled(36, scale);
		int slotY = top + scaled(9, scale);
		for (int i = 0; i < ModernQuickBars.ITEM_SLOT_COUNT; i++) {
			int slotX = left + scaled(ITEM_SLOT_X[i], scale);
			boolean hovered = isUiHover(slotX, slotY, slot, slot);
			drawSlotState(ModernQuickBars.isItemEquipped(i), hovered, slotX, slotY, slot);
			int assignedId = ModernQuickBars.getAssignedItemId(i);
			int availableId = ModernQuickBars.getAvailableItemId(i);
			int displayId = availableId >= 0 ? availableId : assignedId;
			if (displayId >= 0) {
				Sprite item = Inv.getObjectSprite(1, displayId, false, 1, 3153952);
				if (item != null) {
					int iconSize = Math.min(slot - 4, scaled(32, scale));
					drawCenteredSprite(item, slotX, slotY, slot, slot, iconSize,
							dragging && dragType == DRAG_ITEM && dragSource == i ? 75
									: availableId >= 0 ? 256 : 96);
				}
			}
			Fonts.p11Full.renderLeft(JagString.parse(Integer.toString(i + 1)), slotX + 3, slotY + 11, 0xD8C19A, 0);
		}
	}

	private static void drawSlotState(boolean selected, boolean hovered, int x, int y, int size) {
		if (selected) {
			fillRectAlpha(x + 1, y + 1, size - 2, size - 2, 0xD39B32, 58);
			drawRect(x, y, size, size, 0xF1CD73);
		}
		if (hovered) {
			fillRectAlpha(x + 1, y + 1, size - 2, size - 2, 0xFFFFFF, 35);
			drawRect(x, y, size, size, 0xD9D1C0);
		}
	}

	private static boolean updateBarDrag() {
		BarLayout layout = barLayout(hudScale());
		if (dragType == DRAG_NONE) {
			if (Mouse.clickButton != 1 || !FirstPersonCamera.isUiCursorActive()) {
				return false;
			}
			int actionSlot = layout.actionSlotAt(Mouse.clickX, Mouse.clickY);
			if (actionSlot >= 0 && ModernQuickBars.getActionComponent(actionSlot) != null) {
				beginBarDrag(DRAG_ACTION, actionSlot);
				return true;
			}
			int itemSlot = layout.itemSlotAt(Mouse.clickX, Mouse.clickY);
			if (itemSlot >= 0 && ModernQuickBars.getAssignedItemId(itemSlot) >= 0) {
				beginBarDrag(DRAG_ITEM, itemSlot);
				return true;
			}
			return false;
		}

		if (Mouse.pressedButton == 1) {
			int dx = Mouse.lastMouseX - dragStartX;
			int dy = Mouse.lastMouseY - dragStartY;
			if (dx * dx + dy * dy > 25) {
				dragging = true;
			}
			return true;
		}

		if (dragging) {
			if (dragType == DRAG_ACTION) {
				int target = layout.actionSlotAt(Mouse.lastMouseX, Mouse.lastMouseY);
				if (target >= 0) {
					ModernQuickBars.moveActionSlot(dragSource, target);
				} else if (!layout.insideActionBar(Mouse.lastMouseX, Mouse.lastMouseY)) {
					ModernQuickBars.clearActionSlot(dragSource);
				}
			} else {
				int target = layout.itemSlotAt(Mouse.lastMouseX, Mouse.lastMouseY);
				if (target >= 0) {
					ModernQuickBars.moveItemSlot(dragSource, target);
				} else if (!layout.insideItemBar(Mouse.lastMouseX, Mouse.lastMouseY)) {
					ModernQuickBars.clearItemSlot(dragSource);
				}
			}
		} else if (dragType == DRAG_ACTION) {
			ModernQuickBars.activateAction(dragSource);
		} else {
			ModernQuickBars.activateItem(dragSource);
		}
		resetBarDrag();
		return true;
	}

	private static void beginBarDrag(int type, int slot) {
		dragType = type;
		dragSource = slot;
		dragStartX = Mouse.clickX;
		dragStartY = Mouse.clickY;
		dragging = false;
	}

	private static void resetBarDrag() {
		dragType = DRAG_NONE;
		dragSource = -1;
		dragging = false;
	}

	private static void drawDraggedSlot(float scale) {
		if (!dragging || dragSource < 0) {
			return;
		}
		if (dragType == DRAG_ACTION) {
			Sprite icon = ModernQuickBars.getActionSprite(dragSource);
			int size = scaled(28, scale);
			drawCenteredSprite(icon, Mouse.lastMouseX - size / 2, Mouse.lastMouseY - size / 2,
					size, size, size, 190);
		} else {
			int availableId = ModernQuickBars.getAvailableItemId(dragSource);
			int displayId = availableId >= 0 ? availableId : ModernQuickBars.getAssignedItemId(dragSource);
			if (displayId >= 0) {
				Sprite item = Inv.getObjectSprite(1, displayId, false, 1, 3153952);
				int size = scaled(32, scale);
				drawCenteredSprite(item, Mouse.lastMouseX - size / 2, Mouse.lastMouseY - size / 2,
						size, size, size, 190);
			}
		}
	}

	private static BarLayout barLayout(float scale) {
		int quickWidth = scaled(270, scale);
		int quickHeight = scaled(54, scale);
		int actionWidth = scaled(360, scale);
		int actionHeight = scaled(48, scale);
		int chatRight = InterfaceList.topLevelInterface == RESIZABLE_ROOT ? 535 : 520;
		int quickLeft = (GameShell.canvasWidth - quickWidth) / 2;
		int quickTop = GameShell.canvasHeight - quickHeight - scaled(10, scale);
		if (chatRight + quickWidth + 18 <= GameShell.canvasWidth) {
			quickLeft = Math.max(quickLeft, chatRight + 8);
		} else {
			quickTop = Math.max(60, GameShell.canvasHeight - quickHeight - 184);
		}
		int actionLeft = quickLeft + (quickWidth - actionWidth) / 2;
		// One-pixel border overlap removes the transparent padding seam between
		// the authored action and item bar sprites.
		int actionTop = quickTop - actionHeight + scaled(1, scale);
		return new BarLayout(quickLeft, quickTop, quickWidth, quickHeight,
				actionLeft, actionTop, actionWidth, actionHeight, scale);
	}

	private static final class BarLayout {
		private final int quickLeft;
		private final int quickTop;
		private final int quickWidth;
		private final int quickHeight;
		private final int actionLeft;
		private final int actionTop;
		private final int actionWidth;
		private final int actionHeight;
		private final float scale;

		private BarLayout(int quickLeft, int quickTop, int quickWidth, int quickHeight,
				int actionLeft, int actionTop, int actionWidth, int actionHeight, float scale) {
			this.quickLeft = quickLeft;
			this.quickTop = quickTop;
			this.quickWidth = quickWidth;
			this.quickHeight = quickHeight;
			this.actionLeft = actionLeft;
			this.actionTop = actionTop;
			this.actionWidth = actionWidth;
			this.actionHeight = actionHeight;
			this.scale = scale;
		}

		private int actionSlotAt(int x, int y) {
			return slotAt(x, y, actionLeft + scaled(6, scale), actionTop + scaled(8, scale),
					scaled(32, scale), scaled(35, scale), ModernQuickBars.ACTION_SLOT_COUNT);
		}

		private int itemSlotAt(int x, int y) {
			int size = scaled(36, scale);
			int top = quickTop + scaled(9, scale);
			if (y < top || y >= top + size) {
				return -1;
			}
			for (int slot = 0; slot < ModernQuickBars.ITEM_SLOT_COUNT; slot++) {
				int left = quickLeft + scaled(ITEM_SLOT_X[slot], scale);
				if (x >= left && x < left + size) {
					return slot;
				}
			}
			return -1;
		}

		private boolean insideActionBar(int x, int y) {
			return inside(x, y, actionLeft, actionTop, actionWidth, actionHeight);
		}

		private boolean insideItemBar(int x, int y) {
			return inside(x, y, quickLeft, quickTop, quickWidth, quickHeight);
		}
	}

	private static int slotAt(int x, int y, int firstX, int top, int size, int stride, int count) {
		if (y < top || y >= top + size) {
			return -1;
		}
		for (int slot = 0; slot < count; slot++) {
			int slotX = firstX + slot * stride;
			if (x >= slotX && x < slotX + size) {
				return slot;
			}
		}
		return -1;
	}

	private static boolean inside(int x, int y, int left, int top, int width, int height) {
		return x >= left && y >= top && x < left + width && y < top + height;
	}

	private static boolean isUiHover(int x, int y, int width, int height) {
		return FirstPersonCamera.isUiCursorActive()
				&& Mouse.lastMouseX >= x && Mouse.lastMouseY >= y
				&& Mouse.lastMouseX < x + width && Mouse.lastMouseY < y + height;
	}

	private static void ensureAssets() {
		if (assetsLoaded && assetsLoadedForGl == GlRenderer.enabled) {
			return;
		}
		try {
			compassBackground = loadAsset("modern_hud_compass_bg.png");
			minimapFrame = loadAsset("modern_hud_minimap_frame.png");
			modeFrame = loadAsset("modern_hud_mode_frame.png");
			chatBackground = loadAsset("modern_hud_chat_bg.png");
			actionbarBackground = loadAsset("modern_hud_actionbar_bg.png");
			itembarBackground = loadAsset("modern_hud_itembar_bg.png");
			statusbarBackground = loadAsset("modern_hud_statusbar_bg.png");
			statusIcons = new Sprite[] {
					loadAsset("modern_hud_status_hp.png"),
					loadAsset("modern_hud_status_prayer.png"),
					loadAsset("modern_hud_status_run.png")
			};
			assetsLoadedForGl = GlRenderer.enabled;
			assetsLoaded = true;
		} catch (IOException ex) {
			assetsLoaded = false;
			if (!assetFailureReported) {
				assetFailureReported = true;
				System.err.println("[MODERN-HUD] Failed to load generated sprites: " + ex.getMessage());
			}
		}
	}

	private static Sprite loadAsset(String name) throws IOException {
		InputStream input = ModernHud.class.getResourceAsStream(ASSET_ROOT + name);
		if (input == null) {
			throw new IOException("missing resource " + ASSET_ROOT + name);
		}
		try {
			BufferedImage image = ImageIO.read(input);
			if (image == null) {
				throw new IOException("unreadable resource " + ASSET_ROOT + name);
			}
			int width = image.getWidth();
			int height = image.getHeight();
			int[] pixels = image.getRGB(0, 0, width, height, null, 0, width);
			if (GlRenderer.enabled) {
				return new GlAlphaSprite(width, height, 0, 0, width, height, pixels);
			}
			return new SoftwareAlphaSprite(width, height, 0, 0, width, height, pixels);
		} finally {
			input.close();
		}
	}

	private static Component rootChild(int group, int child) {
		if (InterfaceList.components == null || group < 0 || group >= InterfaceList.components.length
				|| InterfaceList.components[group] == null || child < 0
				|| child >= InterfaceList.components[group].length) {
			return null;
		}
		return InterfaceList.components[group][child];
	}

	private static int absoluteX(Component component) {
		int value = component.x;
		for (int depth = 0; depth < 16 && component.overlayer != -1; depth++) {
			component = InterfaceList.getComponent(component.overlayer);
			if (component == null) {
				break;
			}
			value += component.x - component.scrollX;
		}
		return value;
	}

	private static int absoluteY(Component component) {
		int value = component.y;
		for (int depth = 0; depth < 16 && component.overlayer != -1; depth++) {
			component = InterfaceList.getComponent(component.overlayer);
			if (component == null) {
				break;
			}
			value += component.y - component.scrollY;
		}
		return value;
	}

	/**
	 * FIRST_PERSON and CHASE share the modern action HUD. FREE deliberately
	 * restores the normal vanilla interface: it is the classic free-camera
	 * experience, even though the MODERN movement profile remains active.
	 */
	private static boolean isFullHudActive() {
		return CameraMode.isModern()
				&& ModernCameraRig.getRigState() != ModernCameraRig.RigState.FREE;
	}

	private static String currentModeLabel() {
		switch (ModernCameraRig.getRigState()) {
			case FIRST_PERSON:
				return "FP";
			case CHASE:
				return "CHASE";
			case FREE:
				return "FREE";
			default:
				return "MODERN";
		}
	}

	private static float hudScale() {
		float scale = Math.min(GameShell.canvasWidth / 1280.0F, GameShell.canvasHeight / 720.0F);
		if (scale < 0.75F) {
			return 0.75F;
		}
		// Custom assets are authored at their exact HUD sizes. Upscaling them on
		// large windows made the border work and cache icons visibly pixelated.
		return Math.min(scale, 1.0F);
	}

	private static int scaled(int value, float scale) {
		return Math.max(1, Math.round(value * scale));
	}

	private static void drawAsset(Sprite sprite, int x, int y, int width, int height) {
		if (sprite != null && width > 0 && height > 0) {
			int sourceWidth = sprite.anInt1860 > 0 ? sprite.anInt1860 : sprite.width;
			int sourceHeight = sprite.anInt1866 > 0 ? sprite.anInt1866 : sprite.height;
			if (sourceWidth == width && sourceHeight == height) {
				sprite.render(x, y);
			} else {
				sprite.renderResized(x, y, width, height);
			}
		}
	}

	private static void drawAssetAlpha(Sprite sprite, int x, int y, int width, int height, int alpha) {
		if (sprite != null && width > 0 && height > 0) {
			int sourceWidth = sprite.anInt1860 > 0 ? sprite.anInt1860 : sprite.width;
			int sourceHeight = sprite.anInt1866 > 0 ? sprite.anInt1866 : sprite.height;
			if (sourceWidth == width && sourceHeight == height) {
				sprite.renderAlpha(x, y, alpha);
			} else {
				sprite.renderAlpha(x, y, width, height, alpha);
			}
		}
	}

	private static void drawCenteredSprite(Sprite sprite, int x, int y, int width, int height,
			int maxSize, int alpha) {
		if (sprite == null || maxSize <= 0) {
			return;
		}
		int sourceWidth = sprite.anInt1860 > 0 ? sprite.anInt1860 : sprite.width;
		int sourceHeight = sprite.anInt1866 > 0 ? sprite.anInt1866 : sprite.height;
		if (sourceWidth <= 0 || sourceHeight <= 0) {
			return;
		}
		float spriteScale = Math.min(1.0F,
				Math.min(maxSize / (float) sourceWidth, maxSize / (float) sourceHeight));
		int drawWidth = Math.max(1, Math.round(sourceWidth * spriteScale));
		int drawHeight = Math.max(1, Math.round(sourceHeight * spriteScale));
		int drawX = x + (width - drawWidth) / 2;
		int drawY = y + (height - drawHeight) / 2;
		if (alpha >= 256 && drawWidth == sourceWidth && drawHeight == sourceHeight) {
			sprite.render(drawX, drawY);
		} else if (alpha >= 256) {
			sprite.renderResized(drawX, drawY, drawWidth, drawHeight);
		} else if (drawWidth == sourceWidth && drawHeight == sourceHeight) {
			sprite.renderAlpha(drawX, drawY, alpha);
		} else {
			sprite.renderAlpha(drawX, drawY, drawWidth, drawHeight, alpha);
		}
	}

	private static void fillRectAlpha(int x, int y, int width, int height, int color, int alpha) {
		if (GlRenderer.enabled) {
			GlRaster.fillRectAlpha(x, y, width, height, color, alpha);
		} else {
			SoftwareRaster.fillRectAlpha(x, y, width, height, color, alpha);
		}
	}

	private static void drawRect(int x, int y, int width, int height, int color) {
		if (GlRenderer.enabled) {
			GlRaster.drawRect(x, y, width, height, color);
		} else {
			SoftwareRaster.drawRect(x, y, width, height, color);
		}
	}

	private static void drawCenteredText(String text, int centerX, int baselineY, int color) {
		JagString value = JagString.parse(text);
		Fonts.p11Full.renderLeft(value, centerX - Fonts.p11Full.getStringWidth(value) / 2, baselineY, color, 0);
	}

	private static void setFullClip() {
		if (GlRenderer.enabled) {
			GlRaster.setClip(0, 0, GameShell.canvasWidth, GameShell.canvasHeight);
		} else {
			SoftwareRaster.setClip(0, 0, GameShell.canvasWidth, GameShell.canvasHeight);
		}
	}

	private static void diagnostic(float scale) {
		if (!DebugOverlay.isVisible() || client.loop - lastDiagnosticLoop < 50) {
			return;
		}
		lastDiagnosticLoop = client.loop;
		System.out.println("[MODERN-HUD] canvas=" + GameShell.canvasWidth + "x" + GameShell.canvasHeight
				+ " scale=" + scale + " root=" + InterfaceList.topLevelInterface
				+ " minimap=clientCode1338 tabs=cache-root chat=752/137"
				+ " status=748/749/750 action=271+192 items=149 mode=" + currentModeLabel());
	}
}
