package rt4;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;

/**
 * Presentation-only HUD for the MODERN first-person rig.
 *
 * <p>The cache interface tree remains authoritative for the minimap, status
 * orbs, chat, tabs and their input. This class positions those components and
 * draws a small generated-sprite skin plus assigned action/item slots. Slot
 * activation is delegated to {@link ModernQuickBars}, which reuses vanilla
 * menu actions instead of creating gameplay packets here.</p>
 */
public final class ModernHud {

	private static final int RESIZABLE_ROOT = 746;
	private static final int FIXED_ROOT = 548;
	private static final int CHAT_INTERFACE = 752;

	private static final String[] COMPASS_DIRECTIONS = {"N", "NE", "E", "SE", "S", "SW", "W", "NW"};
	private static final String ASSET_ROOT = "/modern_hud/";

	private static Sprite compassBackground;
	private static Sprite minimapFrame;
	private static Sprite modeFrame;
	private static Sprite chatBackground;
	private static Sprite actionbarBackground;
	private static Sprite quickbarBackground;
	private static Sprite slotHover;
	private static Sprite slotSelected;
	private static boolean assetsLoadedForGl;
	private static boolean assetsLoaded;
	private static boolean assetFailureReported;

	private static int selectedActionSlot = -1;
	private static int selectedItemSlot = -1;
	private static int lastDiagnosticLoop = -1000;
	private static boolean layoutApplied;
	private static int layoutRoot = -1;

	private ModernHud() {
	}

	/**
	 * Repositions the real cache widgets after normal responsive layout. Their
	 * existing component instances therefore remain the render and input
	 * hitboxes. Leaving first person runs the normal cache layout again.
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
			minimap.x = Math.max(0, canvasWidth - 238);
			minimap.y = 8;
			minimap.width = 234;
		}

		// These are sub-interface parents for the live HP, prayer and run orbs.
		for (int i = 0; i < 3; i++) {
			Component orb = rootChild(RESIZABLE_ROOT, 13 + i);
			if (orb != null) {
				if ((orb.overlayer & 0xFFFF) == 7) {
					orb.x = 176;
					orb.y = 45 + i * 39;
				} else {
					orb.x = Math.max(0, canvasWidth - 64);
					orb.y = 53 + i * 39;
				}
			}
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
		int tabsTop = Math.max(0, Math.min(228, canvasHeight - 76));
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
			int stackedY = tabsTop + 78;
			if (stackedY + panelHeight + 8 <= canvasHeight) {
				sidePanel.y = stackedY;
			} else {
				sidePanel.y = Math.max(8, canvasHeight - panelHeight - 8);
				if (tabStrip != null) {
					tabStrip.x = Math.max(8, sidePanel.x - tabStrip.width - 8);
					tabStrip.y = Math.max(8, Math.min(sidePanel.y, 200));
				}
			}
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
			minimap.x = Math.max(0, canvasWidth - 264);
			minimap.y = 8;
			minimap.width = 260;
			minimap.height = 176;
		}
		Component map = rootChild(FIXED_ROOT, 64);
		if (map != null) {
			map.x = 76;
			map.y = 9;
		}
		for (int i = 0; i < 3; i++) {
			Component orb = rootChild(FIXED_ROOT, 70 + i);
			if (orb != null) {
				orb.x = 12;
				orb.y = 20 + i * 39;
			}
		}
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
		int tabsTop = Math.max(0, Math.min(228, canvasHeight - 76));
		int tabsLeft = Math.max(0, canvasWidth - 239);
		if (sidePanel != null) {
			int panelWidth = componentWidth(sidePanel, 190);
			int panelHeight = componentHeight(sidePanel, 261);
			sidePanel.x = Math.max(0, canvasWidth - panelWidth - 8);
			int stackedY = tabsTop + 78;
			if (stackedY + panelHeight + 8 <= canvasHeight) {
				sidePanel.y = stackedY;
			} else {
				sidePanel.y = Math.max(8, canvasHeight - panelHeight - 8);
				tabsLeft = Math.max(8, sidePanel.x - 239 - 8);
				tabsTop = Math.max(8, Math.min(sidePanel.y, 200));
			}
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
			InterfaceList.method4017(GameShell.canvasHeight, false, root, GameShell.canvasWidth);
		}
	}

	/** Draws the generated skin and read-only bars after the vanilla interfaces. */
	public static void draw() {
		if (!CameraMode.isModern() || Fonts.p11Full == null) {
			return;
		}
		ensureAssets();
		setFullClip();

		if (!isFullHudActive()) {
			drawModeIndicator(hudScale(), false);
			return;
		}

		float scale = hudScale();
		drawCompass(scale);
		drawMinimapFrame();
		drawModeIndicator(scale, true);
		drawTabs();
		drawActionBars(scale);
		diagnostic(scale);
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
				chatBackground.renderAlpha(x, y, component.width, component.height, 150);
			}
			return true;
		}
		if (group == RESIZABLE_ROOT) {
			// Keep child 10: it is the functional minimap compass. Only the old
			// surrounds and tab artwork are replaced. The components themselves
			// remain live as the authoritative click targets.
			return child == 9 || child == 11 || child == 75
					|| child >= 26 && child <= 69 || child >= 78 && child <= 92;
		}
		if (group == FIXED_ROOT) {
			return child == 17 || child == 35
					|| child >= 20 && child <= 33 || child >= 38 && child <= 51
					|| child == 53 || child == 55 || child == 58 || child == 59
					|| child == 61 || child == 62 || child == 63
					|| child == 66 || child == 67 || child == 68 || child == 79;
		}
		return false;
	}

	private static void drawCompass(float scale) {
		int width = scaled(380, scale);
		int height = scaled(44, scale);
		int left = (GameShell.canvasWidth - width) / 2;
		int top = scaled(9, scale);
		drawAsset(compassBackground, left, top, width, height);

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
		drawAsset(minimapFrame, mapX - 12, mapY - 12, 176, 176);
	}

	private static void drawModeIndicator(float scale, boolean besideMinimap) {
		int width = scaled(174, scale);
		int height = scaled(28, scale);
		int left = GameShell.canvasWidth - width - scaled(14, scale);
		int top = besideMinimap ? 184 : scaled(12, scale);
		drawAsset(modeFrame, left, top, width, height);
		drawCenteredText(currentModeLabel(), left + width / 2, top + height / 2 + 4, 0xE7D4A3);
	}

	/** Draws the real cache tab icons once, in an explicit 7-by-2 grid. */
	private static void drawTabs() {
		int root = InterfaceList.topLevelInterface;
		if (root == RESIZABLE_ROOT) {
			Component strip = rootChild(root, 24);
			if (strip != null) {
				drawTabGrid(absoluteX(strip), absoluteY(strip), root, 56, 26, 41);
			}
		} else if (root == FIXED_ROOT) {
			Component top = rootChild(root, 34);
			if (top != null) {
				drawTabRow(absoluteX(top), absoluteY(top), root, 45, 38);
			}
			Component bottom = rootChild(root, 16);
			if (bottom != null) {
				drawTabRow(absoluteX(bottom), absoluteY(bottom), root, 27, 20);
			}
		}
	}

	private static void drawTabGrid(int left, int top, int root,
			int firstIcon, int firstState, int secondState) {
		drawAsset(quickbarBackground, left, top, 231, 72);
		for (int slot = 0; slot < 14; slot++) {
			int slotX = left + slot % 7 * 33;
			int slotY = top + slot / 7 * 36;
			drawTabSlot(slotX, slotY, rootChild(root, firstIcon + slot),
					rootChild(root, firstState + slot), rootChild(root, secondState + slot));
		}
	}

	private static void drawTabRow(int left, int top, int root,
			int firstIcon, int firstState) {
		drawAsset(quickbarBackground, left, top, 231, 36);
		for (int slot = 0; slot < 7; slot++) {
			int slotX = left + slot * 33;
			drawTabSlot(slotX, top, rootChild(root, firstIcon + slot),
					rootChild(root, firstState + slot), null);
		}
	}

	private static void drawTabSlot(int x, int y, Component icon,
			Component firstState, Component secondState) {
		boolean selected = firstState != null && Cs1ScriptRunner.isTrue(firstState)
				|| secondState != null && Cs1ScriptRunner.isTrue(secondState)
				|| icon != null && Cs1ScriptRunner.isTrue(icon);
		if (selected) {
			drawAsset(slotSelected, x + 1, y + 2, 31, 32);
		}
		if (isUiHover(x, y, 33, 36)) {
			drawAsset(slotHover, x + 1, y + 2, 31, 32);
		}
		if (icon != null) {
			Sprite sprite = icon.method489(Cs1ScriptRunner.isTrue(icon));
			if (sprite == null) {
				sprite = icon.method489(false);
			}
			if (sprite != null) {
				int iconWidth = Math.min(28, icon.width > 0 ? icon.width : 28);
				int iconHeight = Math.min(28, icon.height > 0 ? icon.height : 28);
				sprite.renderResized(x + (33 - iconWidth) / 2, y + (36 - iconHeight) / 2,
						iconWidth, iconHeight);
			}
		}
	}

	private static void drawActionBars(float scale) {
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
		int actionTop = quickTop - actionHeight - scaled(7, scale);

		drawAsset(actionbarBackground, actionLeft, actionTop, actionWidth, actionHeight);
		drawAsset(quickbarBackground, quickLeft, quickTop, quickWidth, quickHeight);
		drawActionSlots(actionLeft, actionTop, scale);
		drawItemSlots(quickLeft, quickTop, scale);
	}

	private static void drawActionSlots(int left, int top, float scale) {
		int slot = scaled(32, scale);
		int firstX = left + scaled(6, scale);
		int slotY = top + scaled(8, scale);
		int stride = scaled(35, scale);
		for (int i = 0; i < ModernQuickBars.ACTION_SLOT_COUNT; i++) {
			int slotX = firstX + i * stride;
			drawSlotState(i == selectedActionSlot || ModernQuickBars.isActionActive(i), slotX, slotY, slot);
			Sprite icon = ModernQuickBars.getActionSprite(i);
			if (icon != null) {
				int iconSize = Math.min(slot - 4, scaled(28, scale));
				icon.renderResized(slotX + (slot - iconSize) / 2, slotY + (slot - iconSize) / 2, iconSize, iconSize);
			}
			if (isUiHover(slotX, slotY, slot, slot)) {
				drawAsset(slotHover, slotX, slotY, slot, slot);
				if (Mouse.clickButton == 1) {
					selectedActionSlot = i;
					ModernQuickBars.activateAction(i);
				}
			}
			String key = i == 9 ? "S0" : "S" + (i + 1);
			Fonts.p11Full.renderLeft(JagString.parse(key), slotX + 2, slotY + 10, 0xD8C19A, 0);
		}
	}

	private static void drawItemSlots(int left, int top, float scale) {
		int slot = scaled(38, scale);
		int firstX = left + scaled(7, scale);
		int slotY = top + scaled(8, scale);
		int stride = scaled(42, scale);
		for (int i = 0; i < ModernQuickBars.ITEM_SLOT_COUNT; i++) {
			int slotX = firstX + i * stride;
			drawSlotState(i == selectedItemSlot, slotX, slotY, slot);
			int assignedId = ModernQuickBars.getAssignedItemId(i);
			int availableId = ModernQuickBars.getAvailableItemId(i);
			int displayId = availableId >= 0 ? availableId : assignedId;
			if (displayId >= 0) {
				Sprite item = Inv.getObjectSprite(1, displayId, false, 1, 3153952);
				if (item != null) {
					int iconSize = Math.min(slot - 4, scaled(32, scale));
					int iconX = slotX + (slot - iconSize) / 2;
					int iconY = slotY + (slot - iconSize) / 2;
					if (availableId >= 0) {
						item.renderResized(iconX, iconY, iconSize, iconSize);
					} else {
						item.renderAlpha(iconX, iconY, iconSize, iconSize, 96);
					}
				}
			}
			if (isUiHover(slotX, slotY, slot, slot)) {
				drawAsset(slotHover, slotX, slotY, slot, slot);
				if (Mouse.clickButton == 1) {
					selectedItemSlot = i;
					ModernQuickBars.activateItem(i);
				}
			}
			Fonts.p11Full.renderLeft(JagString.parse(Integer.toString(i + 1)), slotX + 3, slotY + 11, 0xD8C19A, 0);
		}
	}

	private static void drawSlotState(boolean selected, int x, int y, int size) {
		if (selected) {
			drawAsset(slotSelected, x, y, size, size);
		}
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
			quickbarBackground = loadAsset("modern_hud_quickbar_bg.png");
			slotHover = loadAsset("modern_hud_slot_hover.png");
			slotSelected = loadAsset("modern_hud_slot_selected.png");
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

	private static boolean isFullHudActive() {
		return CameraMode.isModern() && ModernCameraRig.isFirstPersonRigState();
	}

	private static String currentModeLabel() {
		switch (ModernCameraRig.getRigState()) {
			case FIRST_PERSON:
				return "FIRST-PERSON  [F11]";
			case CHASE:
				return "CHASE  [F11]";
			case FREE:
				return "FREE CAMERA  [F11]";
			default:
				return "MODERN  [F11]";
		}
	}

	private static float hudScale() {
		float scale = Math.min(GameShell.canvasWidth / 1280.0F, GameShell.canvasHeight / 720.0F);
		if (scale < 0.75F) {
			return 0.75F;
		}
		return Math.min(scale, 1.15F);
	}

	private static int scaled(int value, float scale) {
		return Math.max(1, Math.round(value * scale));
	}

	private static void drawAsset(Sprite sprite, int x, int y, int width, int height) {
		if (sprite != null && width > 0 && height > 0) {
			sprite.renderResized(x, y, width, height);
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
