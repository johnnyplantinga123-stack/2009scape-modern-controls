package rt4;

/**
 * First-person crosshair action overlay (Phase 3C round #5, P6).
 *
 * <p>When the player is in MODERN controls with the camera rig in
 * {@code FIRST_PERSON}, aiming the center crosshair at an existing
 * RuneScape interactable within {@link #INTERACT_RANGE_TILES} tiles shows a
 * compact action label next to the crosshair, e.g.:</p>
 *
 * <pre>
 *       +
 *   Chest
 *   1 Open
 *   2 Search
 * </pre>
 *
 * <h2>Design — reuse, do not invent</h2>
 * <ul>
 *   <li>Every frame, {@link LoginManager#method1841()} resets
 *       {@link MiniMenu#size} to 1 (Cancel) and the interface/scene scan
 *       ({@link ScriptRunner#method4326}, {@link MiniMenu#addEntries},
 *       {@link MiniMenu#addComponentEntries}) re-appends the entries for the
 *       current cursor pick. In FIRST_PERSON the cursor sits at the screen
 *       centre, so the menu arrays already contain exactly the existing
 *       crosshair-target actions. This overlay merely snapshots them.</li>
 *   <li>Number keys 1/2/3 execute the displayed actions by locating the
 *       matching live entry and calling {@link MiniMenu#doAction(int)} —
 *       the exact same execution route as a mouse click. No new packets.</li>
 *   <li>Selected item/spell targeting states ({@code MiniMenu.anInt5014},
 *       {@code MiniMenu.aBoolean302}) are respected because the snapshot
 *       simply reflects whichever entries the existing pipeline produced
 *       (Use X -&gt; target / Cast X -&gt; target variants included).</li>
 *   <li>Rendering uses the same in-pipeline dual-rasterizer + Fonts pattern
 *       as {@link DebugOverlay}/{@link ModernCrosshair} (no AWT Graphics2D).</li>
 * </ul>
 *
 * <p>STATUS: SOURCE VERIFIED (routes traced), COMPILE VERIFIED after build,
 * RUNTIME UNVERIFIED.</p>
 */
public final class ModernActionOverlay {

	/**
	 * World interaction range (in tiles) for the FP crosshair action UI only.
	 * This is NOT a combat range rule — combat continues to use existing
	 * RuneScape mechanics.
	 */
	private static final int INTERACT_RANGE_TILES = 3;

	/** Maximum number of actions displayed for the targeted entity. */
	private static final int MAX_DISPLAYED_ACTIONS = 3;

	// ---- Key codes (game keycode space, Keyboard.CODE_MAP) ----
	private static final int KEY_1 = 16; // VK_1
	private static final int KEY_E = 34; // VK_E (primary action, no existing conflict)

	/**
	 * Whitelist of world-interaction action codes (from MiniMenu's action
	 * constants). Component actions (button clicks, continue, close, etc.)
	 * are deliberately excluded — those belong to the dialogue/UI layer
	 * ({@link ModernDialogueKeyboard}).
	 */
	private static final int[] WORLD_ACTION_WHITELIST = {
			// Scenery / loc ops 1-5
			MiniMenu.LOC_ACTION_1, MiniMenu.LOC_ACTION_2, MiniMenu.LOC_ACTION_3,
			MiniMenu.LOC_ACTION_4, MiniMenu.LOC_ACTION_5,
			// NPC ops 1-5
			MiniMenu.NPC_ACTION_1, MiniMenu.NPC_ACTION_2, MiniMenu.NPC_ACTION_3,
			MiniMenu.NPC_ACTION_4, MiniMenu.NPC_ACTION_5,
			// Ground item stack ops 1-5 (21/34/18/20/24 from MiniMenu.addEntries)
			21, 34, MiniMenu.OBJSTACK_ACTION_1, MiniMenu.OBJSTACK_ACTION_2, 24,
			// Player options
			MiniMenu.PLAYER_ACTION_1, MiniMenu.PLAYER_ACTION_TRADE,
			MiniMenu.PLAYER_FOLLOW_ACTION, MiniMenu.PLAYER_REQ_ASSIST_ACTION,
			MiniMenu.UNKNOWN_44, MiniMenu.UNKNOWN_10, MiniMenu.PLAYER_ACTION_5,
			// "Use item ->" variants (existing selected-item state)
			MiniMenu.OBJ_LOC_ACTION, MiniMenu.OBJ_NPC_ACTION,
			MiniMenu.OBJ_PLAYER_ACTION, MiniMenu.OBJ_OBJSTACK_ACTION,
			// "Cast spell ->" variants (existing selected-spell state)
			MiniMenu.COMPONENT_LOC_ACTION, MiniMenu.COMPONENT_NPC_ACTION,
			MiniMenu.COMPONENT_PLAYER_ACTION, MiniMenu.COMPONENT_OBJSTACK_ACTION,
	};

	// ---- Snapshot state (rebuilt every render frame) ----
	private static boolean snapshotValid;
	private static int snapshotCount;
	private static final long[] snapshotKeys = new long[MAX_DISPLAYED_ACTIONS];
	private static final int[] snapshotAction = new int[MAX_DISPLAYED_ACTIONS];
	private static final int[] snapshotIntArg1 = new int[MAX_DISPLAYED_ACTIONS];
	private static final int[] snapshotIntArg2 = new int[MAX_DISPLAYED_ACTIONS];
	private static final String[] snapshotOps = new String[MAX_DISPLAYED_ACTIONS];
	private static String snapshotTargetName = "";

	// ---- Target hysteresis (prevents reticle flicker between frames) ----
	private static long lastTargetKey = Long.MIN_VALUE;
	private static int lastTargetX = -1;
	private static int lastTargetZ = -1;

	// ---- Key edge detection ----
	private static boolean key1WasPressed;
	private static boolean key2WasPressed;
	private static boolean key3WasPressed;
	private static boolean keyEWasPressed;

	private ModernActionOverlay() {
	}

	/**
	 * Whether this overlay is active right now (all display/input gates).
	 */
	private static boolean isOverlayActive() {
		return CameraMode.isModern()
				&& ModernCameraRig.isFirstPersonRigState()
				&& !Cs1ScriptRunner.aBoolean108 // right-click menu owns input
				&& !ModernControlController.isChatInputActive();
	}

	/**
	 * Snapshots the current frame's MiniMenu world entries for the crosshair
	 * target. Called from the render pass (gameState 30) AFTER
	 * {@link LoginManager#method1841()} has rebuilt and sorted the menu.
	 */
	public static void snapshot() {
		snapshotValid = false;
		snapshotCount = 0;
		if (!isOverlayActive()) {
			return;
		}
		Player self = PlayerList.self;
		if (self == null) {
			return;
		}
		int selfTileX = self.xFine >> 7;
		int selfTileZ = self.zFine >> 7;

		// MiniMenu entries are appended in pick order and sort()ed so the
		// PRIMARY (top-of-menu) action sits at the END of the arrays. Walk
		// backwards so the first whitelisted entry we hit is the primary op.
		// All world entries carry the local tile coords in intArgs1/intArgs2.

		// Hysteresis: prefer last frame's target if it is still in the menu.
		long targetKey = Long.MIN_VALUE;
		int targetX = -1;
		int targetZ = -1;
		if (lastTargetKey != Long.MIN_VALUE && targetExists(lastTargetKey, lastTargetX, lastTargetZ, selfTileX, selfTileZ)) {
			targetKey = lastTargetKey;
			targetX = lastTargetX;
			targetZ = lastTargetZ;
		} else {
			for (int i = MiniMenu.size - 1; i >= 1; i--) {
				if (!isWorldAction(MiniMenu.actions[i])) {
					continue;
				}
				int tx = MiniMenu.intArgs1[i];
				int tz = MiniMenu.intArgs2[i];
				if (Math.max(Math.abs(tx - selfTileX), Math.abs(tz - selfTileZ)) > INTERACT_RANGE_TILES) {
					continue;
				}
				targetKey = MiniMenu.keys[i];
				targetX = tx;
				targetZ = tz;
				break;
			}
		}
		if (targetKey == Long.MIN_VALUE) {
			lastTargetKey = Long.MIN_VALUE;
			return;
		}

		// Collect up to MAX_DISPLAYED_ACTIONS ops for that target.
		JagString targetName = null;
		for (int i = MiniMenu.size - 1; i >= 1 && snapshotCount < MAX_DISPLAYED_ACTIONS; i--) {
			if (!isWorldAction(MiniMenu.actions[i]) || MiniMenu.keys[i] != targetKey
					|| MiniMenu.intArgs1[i] != targetX || MiniMenu.intArgs2[i] != targetZ) {
				continue;
			}
			snapshotKeys[snapshotCount] = MiniMenu.keys[i];
			snapshotAction[snapshotCount] = MiniMenu.actions[i];
			snapshotIntArg1[snapshotCount] = MiniMenu.intArgs1[i];
			snapshotIntArg2[snapshotCount] = MiniMenu.intArgs2[i];
			snapshotOps[snapshotCount] = toPlainString(MiniMenu.ops[i]);
			if (targetName == null) {
				targetName = MiniMenu.opBases[i];
			}
			snapshotCount++;
		}

		lastTargetKey = targetKey;
		lastTargetX = targetX;
		lastTargetZ = targetZ;
		snapshotTargetName = toPlainString(targetName);
		snapshotValid = snapshotCount > 0;
	}

	/**
	 * Draws the compact action label next to the crosshair. Uses the proven
	 * in-pipeline raster + Fonts pattern (no AWT Graphics2D, full-canvas
	 * clip so no lingering interface clip region can hide it).
	 */
	public static void draw() {
		if (!snapshotValid) {
			return;
		}
		if (Fonts.p11Full == null) {
			return;
		}
		if (!isOverlayActive()) {
			return;
		}

		String[] lines = new String[snapshotCount + 1];
		lines[0] = snapshotTargetName;
		for (int i = 0; i < snapshotCount; i++) {
			lines[i + 1] = (i + 1) + " " + snapshotOps[i];
		}

		JagString[] jagLines = new JagString[lines.length];
		int maxWidth = 0;
		for (int i = 0; i < lines.length; i++) {
			jagLines[i] = JagString.parse(lines[i]);
			int w = Fonts.p11Full.getStringWidth(jagLines[i]);
			if (w > maxWidth) {
				maxWidth = w;
			}
		}

		int canvasW;
		int canvasH;
		if (GlRenderer.enabled) {
			canvasW = GlRenderer.canvasWidth;
			canvasH = GlRenderer.canvasHeight;
		} else {
			canvasW = SoftwareRaster.width;
			canvasH = SoftwareRaster.height;
		}

		int lineHeight = 12;
		int boxW = maxWidth + 12;
		int boxH = lines.length * lineHeight + 8;
		// Anchor the box just right/below of the center crosshair.
		int x = canvasW / 2 + 12;
		int y = canvasH / 2 - 10;
		if (x + boxW > canvasW) {
			x = canvasW / 2 - 12 - boxW;
		}
		if (y + boxH > canvasH) {
			y = canvasH - boxH - 2;
		}
		if (y < 0) {
			y = 0;
		}

		if (GlRenderer.enabled) {
			GlRaster.setClip(0, 0, canvasW, canvasH);
			GlRaster.fillRectAlpha(x, y, boxW, boxH, 0x000000, 170);
			GlRaster.drawRect(x, y, boxW, boxH, 0x555555);
		} else {
			SoftwareRaster.setClip(0, 0, canvasW, canvasH);
			SoftwareRaster.fillRectAlpha(x, y, boxW, boxH, 0x000000, 170);
			SoftwareRaster.drawRect(x, y, boxW, boxH, 0x555555);
		}

		int textY = y + 10;
		for (int i = 0; i < jagLines.length; i++) {
			// Target name cyan, action lines white — matches RS menu colors.
			int color = (i == 0) ? 0x00FFFF : 0xFFFFFF;
			Fonts.p11Full.renderLeft(jagLines[i], x + 6, textY, color, 0);
			textY += lineHeight;
		}
	}

	/**
	 * Per-tick keyboard handling: 1/2/3 execute displayed actions, E executes
	 * the primary action. Called from {@link ModernControlController#update()}
	 * ONLY when the dialogue/UI layer ({@link ModernDialogueKeyboard}) did not
	 * consume the key.
	 */
	public static void update() {
		boolean key1 = Keyboard.pressedKeys[KEY_1];
		boolean key2 = Keyboard.pressedKeys[KEY_1 + 1];
		boolean key3 = Keyboard.pressedKeys[KEY_1 + 2];
		boolean keyE = Keyboard.pressedKeys[KEY_E];

		boolean edge1 = key1 && !key1WasPressed;
		boolean edge2 = key2 && !key2WasPressed;
		boolean edge3 = key3 && !key3WasPressed;
		boolean edgeE = keyE && !keyEWasPressed;

		key1WasPressed = key1;
		key2WasPressed = key2;
		key3WasPressed = key3;
		keyEWasPressed = keyE;

		if (!isOverlayActive() || !ModernControlController.isGameplayInputAllowed()) {
			return;
		}
		if (!snapshotValid) {
			return;
		}
		if (edge1) {
			executeAction(0);
		} else if (edge2) {
			executeAction(1);
		} else if (edge3) {
			executeAction(2);
		} else if (edgeE) {
			executeAction(0); // E = primary/default action
		}
	}

	/**
	 * Executes a displayed action by finding the matching LIVE MiniMenu entry
	 * and invoking the exact existing {@link MiniMenu#doAction(int)} route.
	 * The menu arrays are rebuilt each frame, so we match by content
	 * (key + action + tile args) rather than by index.
	 */
	private static void executeAction(int slot) {
		if (slot < 0 || slot >= snapshotCount) {
			return;
		}
		long key = snapshotKeys[slot];
		int action = snapshotAction[slot];
		int arg1 = snapshotIntArg1[slot];
		int arg2 = snapshotIntArg2[slot];
		for (int i = 0; i < MiniMenu.size; i++) {
			if (MiniMenu.keys[i] == key && MiniMenu.actions[i] == action
					&& MiniMenu.intArgs1[i] == arg1 && MiniMenu.intArgs2[i] == arg2) {
				MiniMenu.doAction(i);
				return;
			}
		}
		// Target entry no longer present this frame — drop the stale target.
		snapshotValid = false;
		snapshotCount = 0;
		lastTargetKey = Long.MIN_VALUE;
	}

	/**
	 * Whether the given (entity key, tile) target still has at least one
	 * whitelisted, in-range entry in the current menu.
	 */
	private static boolean targetExists(long key, int tx, int tz, int selfTileX, int selfTileZ) {
		if (Math.max(Math.abs(tx - selfTileX), Math.abs(tz - selfTileZ)) > INTERACT_RANGE_TILES) {
			return false;
		}
		for (int i = 1; i < MiniMenu.size; i++) {
			if (MiniMenu.keys[i] == key && MiniMenu.intArgs1[i] == tx && MiniMenu.intArgs2[i] == tz
					&& isWorldAction(MiniMenu.actions[i])) {
				return true;
			}
		}
		return false;
	}

	private static boolean isWorldAction(short action) {
		int code = action;
		if (code >= 2000) {
			code -= 2000;
		}
		for (int whitelisted : WORLD_ACTION_WHITELIST) {
			if (whitelisted == code) {
				return true;
			}
		}
		return false;
	}

	/**
	 * Converts a JagString to a plain Java String, stripping any
	 * {@code <col=...>} crown/tag markup so the overlay stays compact.
	 */
	private static String toPlainString(JagString js) {
		if (js == null) {
			return "";
		}
		StringBuilder sb = new StringBuilder(js.length);
		boolean inTag = false;
		for (int i = 0; i < js.length; i++) {
			char c = (char) (js.chars[i] & 0xFF);
			if (c == '<') {
				inTag = true;
			} else if (c == '>') {
				inTag = false;
			} else if (!inTag) {
				sb.append(c);
			}
		}
		return sb.toString();
	}
}
