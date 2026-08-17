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
 * <h2>Round #7 P2 — NPC pick finding + fix (source-proven)</h2>
 * <p>Scene pick tags for NPCs (and players) carry the entity index in the
 * high 32 bits and NO tile bits in the low 29 bits
 * ({@code key = npcIndex << 32 | 0x20000000L}). {@link MiniMenu#addEntries}
 * decodes x/z from the key's low bits, so every NPC/player menu entry has
 * {@code intArgs1 = intArgs2 = 0}. The old range check compared those zeros
 * against the player's tile and rejected EVERY NPC regardless of range
 * (Round #6B/C runtime: {@code [FP-TARGET] NPC menu entries=1 outOfRange=1}).
 * This is NOT a range problem — the pick/menu pipeline delivers NPC entries
 * correctly. The fix resolves NPC/player tiles from the LIVE entity lists
 * ({@link NpcList#npcs} / {@link PlayerList#players}) before the range
 * check; loc/objstack entries keep their intArgs tiles. Execution still
 * goes through the exact existing {@link MiniMenu#doAction(int)} route
 * (its NPC branches use the live NPC from the key, so tile args are
 * irrelevant there). {@code NPC_EXAMINE} (1007) added to the whitelist.
 * A throttled {@code NPC_PICK:} diagnostic (F12 on) replaces [FP-TARGET].
 *
 * <p>STATUS: SOURCE VERIFIED (routes traced), COMPILE VERIFIED after build,
 * RUNTIME UNVERIFIED.</p>
 */
public final class ModernActionOverlay {

	/**
	 * World interaction DISPLAY/ACQUISITION range (in tiles) for the FP
	 * crosshair action UI only. Round #8 P8: restored to 2 tiles (was 8).
	 * DISPLAY RANGE != GAME ACTION RANGE — executing an action still goes
	 * through existing RuneScape action logic; existing pathfinding/server
	 * decides where the player must stand and whether the action is in range.
	 */

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
			// Scenery / loc ops 1-5 (+ Examine, existing menu entry 1004)
			MiniMenu.LOC_ACTION_1, MiniMenu.LOC_ACTION_2, MiniMenu.LOC_ACTION_3,
			MiniMenu.LOC_ACTION_4, MiniMenu.LOC_ACTION_5, MiniMenu.LOC_ACTION_EXAMINE,
			// NPC ops 1-5 (+ Examine — NPC_EXAMINE 1007, Round #7 P2)
			MiniMenu.NPC_ACTION_1, MiniMenu.NPC_ACTION_2, MiniMenu.NPC_ACTION_3,
			MiniMenu.NPC_ACTION_4, MiniMenu.NPC_ACTION_5, MiniMenu.NPC_EXAMINE,
			// Ground item stack ops 1-5 (21/34/18/20/24 from MiniMenu.addEntries)
			// + Examine (existing menu entry 1002)
			21, 34, MiniMenu.OBJSTACK_ACTION_1, MiniMenu.OBJSTACK_ACTION_2, 24,
			MiniMenu.OBJ_EXAMINE,
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
	/** True only for the frame in which FP direct-LMB dispatched Attack. */
	private static boolean directCombatClickConsumed;

	// ---- FIRST_PERSON combat target lock (presentation only) ----
	private static final int COMBAT_LOCK_NONE = 0;
	private static final int COMBAT_LOCK_NPC = 1;
	private static final int COMBAT_LOCK_PLAYER = 2;
	private static int combatLockType = COMBAT_LOCK_NONE;
	private static int combatLockIndex = -1;
	private static String combatLockName = "";

	/** Scratch buffer for live entity tile resolution (no per-frame alloc). */
	private static final int[] scratchTile = new int[2];

	// ---- Round #7 P2/P8: NPC_PICK diagnostics (read by DebugOverlay) ----
	/** Whether the scene pick pass produced at least one NPC pick tag. */
	public static boolean diagNpcPickTagSeen;
	/** Number of NPC action entries currently in the MiniMenu arrays. */
	public static int diagNpcMenuEntries;
	/** First NPC entry's action code/op ("" if none). */
	public static String diagFirstNpcAction = "";
	/** NPC under the crosshair this frame ("none" if no target). */
	public static String diagNpcUnderCrosshair = "none";
	/** Whether the overlay accepted (acquired) a target this frame. */
	public static boolean diagOverlayAccepted;
	/** Why the overlay rejected ("" when accepted or overlay inactive). */
	public static String diagRejectReason = "";

	// ---- Round #7C P4: extended NPC pick diagnostics (DebugOverlay) ----
	/** Scene-pick NPC list index (-1 if no NPC pick tag this frame). */
	public static int diagNpcIndex = -1;
	/** Whether NpcList.npcs[diagNpcIndex] holds a live Npc. */
	public static boolean diagNpcExists;
	/** Live tile of the picked NPC (-1/-1 if none). */
	public static int diagNpcLiveX = -1;
	public static int diagNpcLiveZ = -1;

	// ---- Round #7D P2: NPC pick-chain boundary counters (DebugOverlay) ----
	// Incremented by the vanilla render/pick chain itself (Npc.render and
	// GlModel.render) so the FIRST boundary where the NPC path diverges from
	// the working LOC path is visible on F12 without any behavioural change.
	/** NPCs that reached Npc.render this frame (render chain entry). */
	public static int diagNpcRendered;
	/** NPC-keyed models that entered the GlModel.render pick gate. */
	public static int diagNpcPickAttempts;
	/** NPC-keyed models that passed the mouse-bounds box check. */
	public static int diagNpcBoundsHits;
	/** NPC pick tags actually written to Model.aLongArray11 this frame. */
	public static int diagNpcTagsWritten;
	/** Index of the last NPC-keyed model that passed the bounds check. */
	public static int diagNpcCandidateIndex = -1;
	/** this.pickable of the last NPC-keyed bounds-hit model. */
	public static boolean diagNpcLastPickable;
	/** miniMenuPick (key > 0) of the last NPC-keyed bounds-hit model. */
	public static boolean diagNpcLastMiniMenuPick;
	/** RawModel.allowInput at the last snapshot refresh (shared gate). */
	public static boolean diagAllowInput;
	/** First pick-chain stage where NPC diverges from working LOC ("" = none). */
	public static String diagNpcRejectBoundary = "";
	// Folded per-frame copies for F12 (the live accumulators above are reset
	// by refreshNpcPickChain before the overlay draw reads them).
	/** diagNpcRendered folded at the last snapshot. */
	public static int diagNpcRenderedLast;
	/** diagNpcPickAttempts folded at the last snapshot. */
	public static int diagNpcPickAttemptsLast;
	/** diagNpcBoundsHits folded at the last snapshot. */
	public static int diagNpcBoundsHitsLast;
	/** diagNpcTagsWritten folded at the last snapshot. */
	public static int diagNpcTagsWrittenLast;

	private ModernActionOverlay() {
	}

	/**
	 * Whether this overlay is active right now (all display/input gates).
	 *
	 * <p>Round #6B/C input authority (brief P2/P6): a dialogue owns 1-9/E
	 * completely ({@link ModernDialogueKeyboard#hasActiveDialogue()}), and
	 * the P1 CTRL-held UI-cursor substate gives the mouse/keys to normal
	 * interfaces — in both cases the world overlay has ZERO authority.
	 */
	private static boolean isOverlayActive() {
		return CameraMode.isModern()
				&& ModernCameraRig.isFirstPersonRigState()
				&& !Cs1ScriptRunner.aBoolean108 // right-click menu owns input
				&& !ModernControlController.isChatInputActive()
				// P2: dialogue owns 1-9/E — world target has zero authority.
				&& !ModernDialogueKeyboard.hasActiveDialogue()
				// P1: CTRL-held UI cursor — no world shortcuts.
				&& !FirstPersonCamera.isUiCursorActive()
				// Round #8 P7: FP context menu owns input — no quick overlay.
				&& !FPContextMenuController.isMenuOpen();
	}

	// ---- Round #7C P2: WORLD OVERLAY gate diagnostics (DebugOverlay) ----

	/** Whether all overlay gates pass this frame (same as the private gate). */
	public static boolean isGateActive() {
		return isOverlayActive();
	}

	/**
	 * Whether this frame's left click already dispatched an existing vanilla
	 * combat action from the FP crosshair. Protocol uses this to avoid running
	 * the same click through its normal menu-default path a second time.
	 */
	public static boolean wasDirectCombatClickConsumed() {
		return directCombatClickConsumed;
	}

	/** True while a live NPC/player selected by a vanilla Attack action is locked. */
	public static boolean hasCombatTargetLock() {
		return combatLockType != COMBAT_LOCK_NONE;
	}

	/** Current lock label for HUD/debug presentation, empty when unlocked. */
	public static String getCombatTargetLockName() {
		return combatLockName;
	}

	/** Clears one-frame input consumption before modern input priority runs. */
	public static void beginInputFrame() {
		directCombatClickConsumed = false;
	}

	/**
	 * Which gate currently blocks the overlay ("" when active). Evaluated in
	 * the exact {@link #isOverlayActive()} order so the FIRST failing gate is
	 * reported — proves the blocking boundary at runtime.
	 */
	public static String getBlockedReason() {
		if (!CameraMode.isModern()) {
			return "NOT_MODERN";
		}
		if (!ModernCameraRig.isFirstPersonRigState()) {
			return "NOT_FP";
		}
		if (Cs1ScriptRunner.aBoolean108) {
			return "RCLICK_MENU";
		}
		if (ModernControlController.isChatInputActive()) {
			return "CHAT_INPUT";
		}
		if (ModernDialogueKeyboard.hasActiveDialogue()) {
			return "DIALOGUE_BLOCK";
		}
		if (FirstPersonCamera.isUiCursorActive()) {
			return "CTRL_UI_CURSOR";
		}
		return "";
	}

	/** Whitelisted world-action entries currently in the MiniMenu arrays. */
	public static int countWorldEntries() {
		int n = 0;
		for (int i = 1; i < MiniMenu.size; i++) {
			if (isWorldAction(MiniMenu.actions[i])) {
				n++;
			}
		}
		return n;
	}

	/** Whitelisted LOC (scenery/object) entries: key type bits == 2. */
	public static int countLocEntries() {
		return countEntriesByType(2);
	}

	/** Whitelisted NPC entries currently exposed by the vanilla menu. */
	public static int countNpcEntries() {
		int n = 0;
		for (int i = 1; i < MiniMenu.size; i++) {
			if (isNpcActionCode(MiniMenu.actions[i])) {
				n++;
			}
		}
		return n;
	}

	/**
	 * Round #7D P2: fold the per-frame NPC pick-chain counters into the F12
	 * boundary string, then reset them for the next frame. Called FIRST in
	 * {@link #snapshot()} so the diagnostics refresh even while the overlay
	 * gate blocks (the counters come from the vanilla render path and must
	 * never depend on the overlay being active).
	 */
	private static void refreshNpcPickChain() {
		diagAllowInput = RawModel.allowInput;
		diagNpcMenuEntries = countNpcEntries();
		int rendered = diagNpcRendered;
		int attempts = diagNpcPickAttempts;
		int boundsHits = diagNpcBoundsHits;
		int tagsWritten = diagNpcTagsWritten;
		if (tagsWritten > 0) {
			// Tags reached Model.aLongArray11 — divergence (if any) is in the
			// menu build after SceneGraph pickup.
			diagNpcRejectBoundary = diagNpcMenuEntries > 0 ? "" : "MENU_BUILD";
		} else if (boundsHits > 0) {
			// Model passed the mouse-bounds box but wrote no tag.
			diagNpcRejectBoundary = diagNpcLastPickable
					? "WRITE_MISS" : "NOT_PICKABLE";
		} else if (attempts > 0) {
			// Model entered the pick gate but missed the bounds box.
			diagNpcRejectBoundary = "BOUNDS_MISS";
		} else if (rendered > 0) {
			// NPC rendered but its model never reached the pick gate.
			diagNpcRejectBoundary = diagAllowInput ? "PICK_GATE" : "ALLOW_INPUT";
		} else {
			diagNpcRejectBoundary = "NPC_NOT_RENDERED";
		}
		// Publish the folded frame counts for F12, then reset the accumulators.
		diagNpcRenderedLast = rendered;
		diagNpcPickAttemptsLast = attempts;
		diagNpcBoundsHitsLast = boundsHits;
		diagNpcTagsWrittenLast = tagsWritten;
		diagNpcRendered = 0;
		diagNpcPickAttempts = 0;
		diagNpcBoundsHits = 0;
		diagNpcTagsWritten = 0;
		diagNpcCandidateIndex = -1;
	}

	private static int countEntriesByType(int type) {
		int n = 0;
		for (int i = 1; i < MiniMenu.size; i++) {
			if (isWorldAction(MiniMenu.actions[i])
					&& ((int) MiniMenu.keys[i] >> 29 & 0x3) == type) {
				n++;
			}
		}
		return n;
	}

	// ---- Round #6B/C P14: debug overlay accessors ----

	/** Whether a valid world target snapshot exists this frame. */
	public static boolean isSnapshotValid() {
		return snapshotValid;
	}

	/** Display name of the current crosshair target (empty if none). */
	public static String getTargetName() {
		return snapshotValid ? snapshotTargetName : "";
	}

	/** Number of displayed actions for the current target. */
	public static int getActionCount() {
		return snapshotValid ? snapshotCount : 0;
	}

	/** Displayed op text for slot i (empty if absent). */
	public static String getActionOp(int slot) {
		if (!snapshotValid || slot < 0 || slot >= snapshotCount) {
			return "";
		}
		return snapshotOps[slot];
	}

	/** Chebyshev tile distance from self to the current target (-1 if none). */
	public static int getTargetDistance() {
		if (!snapshotValid || PlayerList.self == null) {
			return -1;
		}
		int selfTileX = PlayerList.self.xFine >> 7;
		int selfTileZ = PlayerList.self.zFine >> 7;
		return Math.max(Math.abs(lastTargetX - selfTileX), Math.abs(lastTargetZ - selfTileZ));
	}

	/**
	 * Coarse entity type of the current target, derived from the primary
	 * snapshot's MiniMenu action code (existing action constants only).
	 * For the debug overlay (P14).
	 */
	public static String getTargetType() {
		if (!snapshotValid || snapshotCount == 0) {
			return "NONE";
		}
		int code = snapshotAction[0];
		if (code >= 2000) {
			code -= 2000;
		}
		if (code == MiniMenu.LOC_ACTION_1 || code == MiniMenu.LOC_ACTION_2
				|| code == MiniMenu.LOC_ACTION_3 || code == MiniMenu.LOC_ACTION_4
				|| code == MiniMenu.LOC_ACTION_5
				|| code == MiniMenu.OBJ_LOC_ACTION || code == MiniMenu.COMPONENT_LOC_ACTION) {
			return "OBJECT";
		}
		if (code == MiniMenu.NPC_ACTION_1 || code == MiniMenu.NPC_ACTION_2
				|| code == MiniMenu.NPC_ACTION_3 || code == MiniMenu.NPC_ACTION_4
				|| code == MiniMenu.NPC_ACTION_5 || code == MiniMenu.NPC_EXAMINE
				|| code == MiniMenu.OBJ_NPC_ACTION || code == MiniMenu.COMPONENT_NPC_ACTION) {
			return "NPC";
		}
		if (code == 21 || code == 34 || code == 18 || code == 20 || code == 24
				|| code == MiniMenu.OBJSTACK_ACTION_1 || code == MiniMenu.OBJSTACK_ACTION_2
				|| code == MiniMenu.OBJ_OBJSTACK_ACTION || code == MiniMenu.COMPONENT_OBJSTACK_ACTION) {
			return "ITEM";
		}
		return "PLAYER";
	}

	/**
	 * Snapshots the current frame's MiniMenu world entries for the crosshair
	 * target. Called from the render pass (gameState 30) AFTER
	 * {@link LoginManager#method1841()} has rebuilt and sorted the menu.
	 */
	public static void snapshot() {
		validateCombatTargetLock();
		// Round #7D P2: refresh the NPC pick-chain boundary diagnostics from
		// the vanilla render-path counters BEFORE any gate return.
		refreshNpcPickChain();
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
		// Round #7 P2: NPC/player keys carry NO tile bits (intArgs are 0),
		// so tiles are resolved from the LIVE entity lists via
		// resolveEntryTile before any range check. The collect loop below
		// therefore matches on the pick KEY only.

		// Hysteresis: prefer last frame's target if it is still in the menu.
		long targetKey = Long.MIN_VALUE;
		int targetX = -1;
		int targetZ = -1;
		String reject = "";
		if (lastTargetKey != Long.MIN_VALUE) {
			for (int i = 1; i < MiniMenu.size; i++) {
				if (MiniMenu.keys[i] != lastTargetKey || !isWorldAction(MiniMenu.actions[i])) {
					continue;
				}
				if (!resolveEntryTile(lastTargetKey, MiniMenu.actions[i], MiniMenu.intArgs1[i], MiniMenu.intArgs2[i], scratchTile)) {
					continue;
				}
				targetKey = lastTargetKey;
				targetX = scratchTile[0];
				targetZ = scratchTile[1];
				break;
			}
		}
		if (targetKey == Long.MIN_VALUE) {
			for (int i = MiniMenu.size - 1; i >= 1; i--) {
				if (!isWorldAction(MiniMenu.actions[i])) {
					continue;
				}
				if (!resolveEntryTile(MiniMenu.keys[i], MiniMenu.actions[i], MiniMenu.intArgs1[i], MiniMenu.intArgs2[i], scratchTile)) {
					reject = "NO_LIVE_TILE";
					continue;
				}
				targetKey = MiniMenu.keys[i];
				targetX = scratchTile[0];
				targetZ = scratchTile[1];
				break;
			}
		}
		if (targetKey == Long.MIN_VALUE) {
			lastTargetKey = Long.MIN_VALUE;
			// Round #7 P2/P8: NPC_PICK diagnostics (fields refreshed every
			// frame for DebugOverlay; console print throttled to ~1 Hz and
			// only while F12 is visible).
			refreshNpcPickDiagnostics(selfTileX, selfTileZ, reject);
			if (DebugOverlay.isVisible() && client.loop % 50 == 0) {
				System.out.println("NPC_PICK: npcUnderCrosshair=" + diagNpcUnderCrosshair
						+ " scenePickTagSeen=" + (diagNpcPickTagSeen ? "Y" : "N")
						+ " npcMiniMenuEntries=" + diagNpcMenuEntries
						+ " firstNpcAction=" + diagFirstNpcAction
						+ " overlayAccepted=N"
						+ " rejectReason=" + (diagRejectReason.isEmpty() ? "NO_WORLD_ENTRY" : diagRejectReason));
			}
			return;
		}

		// Collect up to MAX_DISPLAYED_ACTIONS ops for that target. KEY match
		// only — NPC/player intArgs are 0 while targetX/Z hold the resolved
		// live tiles.
		JagString targetName = null;
		for (int i = MiniMenu.size - 1; i >= 1 && snapshotCount < MAX_DISPLAYED_ACTIONS; i--) {
			if (!isWorldAction(MiniMenu.actions[i]) || MiniMenu.keys[i] != targetKey) {
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
		refreshNpcPickDiagnostics(selfTileX, selfTileZ, reject);
	}

	/**
	 * Round #7 P2/P8: refreshes the NPC_PICK diagnostic fields read by
	 * {@link DebugOverlay}. Scans the scene pick tags ({@link Model#aLongArray11},
	 * count {@link MiniMenu#anInt7}) for NPC picks and the live menu arrays
	 * for NPC action entries. Called every frame while the overlay is active.
	 */
	private static void refreshNpcPickDiagnostics(int selfTileX, int selfTileZ, String reject) {
		diagNpcPickTagSeen = false;
		diagNpcMenuEntries = 0;
		diagFirstNpcAction = "";
		diagNpcUnderCrosshair = "none";
		diagOverlayAccepted = false;
		diagRejectReason = reject;
		diagNpcIndex = -1;
		diagNpcExists = false;
		diagNpcLiveX = -1;
		diagNpcLiveZ = -1;

		// Scene pick pass: NPC tags have type bits == 1 (key >> 29 & 0x3).
		int pickCount = Math.min(MiniMenu.anInt7, Model.aLongArray11.length);
		for (int i = 0; i < pickCount; i++) {
			long tag = Model.aLongArray11[i];
			if (((int) tag >> 29 & 0x3) != 1) {
				continue;
			}
			diagNpcPickTagSeen = true;
			int index = (int) (tag >>> 32);
			diagNpcIndex = index;
			if (index >= 0 && index < NpcList.npcs.length) {
				Npc npc = NpcList.npcs[index];
				if (npc != null) {
					diagNpcExists = true;
					diagNpcLiveX = npc.xFine >> 7;
					diagNpcLiveZ = npc.zFine >> 7;
					if (npc.type != null && npc.type.name != null) {
						diagNpcUnderCrosshair = toPlainString(npc.type.name);
					}
				}
			}
			break;
		}

		// Menu pass: count NPC action entries + first op text.
		for (int i = MiniMenu.size - 1; i >= 1; i--) {
			if (!isNpcActionCode(MiniMenu.actions[i])) {
				continue;
			}
			diagNpcMenuEntries++;
			if (diagFirstNpcAction.isEmpty()) {
				diagFirstNpcAction = toPlainString(MiniMenu.ops[i]);
			}
		}
		diagOverlayAccepted = snapshotValid;
	}

	/** Whether the action code (−2000 sort flag stripped) is an NPC op. */
	private static boolean isNpcActionCode(short action) {
		int code = action;
		if (code >= 2000) {
			code -= 2000;
		}
		return code == MiniMenu.NPC_ACTION_1 || code == MiniMenu.NPC_ACTION_2
				|| code == MiniMenu.NPC_ACTION_3 || code == MiniMenu.NPC_ACTION_4
				|| code == MiniMenu.NPC_ACTION_5 || code == MiniMenu.NPC_EXAMINE
				|| code == MiniMenu.OBJ_NPC_ACTION || code == MiniMenu.COMPONENT_NPC_ACTION;
	}

	/** True for existing NPC/player menu action families that can carry Attack. */
	private static boolean isCombatEntityAction(int action) {
		int code = action;
		if (code >= 2000) {
			code -= 2000;
		}
		return isNpcActionCode((short) code)
				|| code == MiniMenu.PLAYER_ACTION_1 || code == MiniMenu.PLAYER_ACTION_BLOCK
				|| code == MiniMenu.PLAYER_ACTION_TRADE || code == MiniMenu.PLAYER_REQ_ASSIST_ACTION
				|| code == MiniMenu.PLAYER_FOLLOW_ACTION || code == MiniMenu.PLAYER_ACTION_5;
	}

	/**
	 * Draws the compact action label next to the crosshair. Uses the proven
	 * in-pipeline raster + Fonts pattern (no AWT Graphics2D, full-canvas
	 * clip so no lingering interface clip region can hide it).
	 */
	public static void draw() {
		if (Fonts.p11Full == null) {
			return;
		}
		if (!isOverlayActive()) {
			return;
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

		if (snapshotValid) {

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
		drawCombatTargetLock(canvasW, canvasH);
	}

	/** Draws the active lock independently of what is currently under the crosshair. */
	private static void drawCombatTargetLock(int canvasW, int canvasH) {
		if (!hasCombatTargetLock()) {
			return;
		}
		JagString text = JagString.parse("LOCK  " + combatLockName);
		int width = Fonts.p11Full.getStringWidth(text) + 12;
		int x = canvasW / 2 - width / 2;
		int y = canvasH / 2 + 34;
		if (GlRenderer.enabled) {
			GlRaster.fillRectAlpha(x, y, width, 16, 0x000000, 170);
			GlRaster.drawRect(x, y, width, 16, 0xB98A24);
		} else {
			SoftwareRaster.fillRectAlpha(x, y, width, 16, 0x000000, 170);
			SoftwareRaster.drawRect(x, y, width, 16, 0xB98A24);
		}
		Fonts.p11Full.renderCenter(text, canvasW / 2, y + 12, 0xFFE07A, 0);
	}

	/** Clears a presentation lock when its live entity or the FP context is gone. */
	private static void validateCombatTargetLock() {
		if (!CameraMode.isModern() || !ModernCameraRig.isFirstPersonRigState()) {
			clearCombatTargetLock();
			return;
		}
		if (combatLockType == COMBAT_LOCK_NPC) {
			if (combatLockIndex < 0 || combatLockIndex >= NpcList.npcs.length
					|| NpcList.npcs[combatLockIndex] == null
					|| !NpcList.npcs[combatLockIndex].isVisible()) {
				clearCombatTargetLock();
			}
		} else if (combatLockType == COMBAT_LOCK_PLAYER) {
			if (combatLockIndex < 0 || combatLockIndex >= PlayerList.players.length
					|| PlayerList.players[combatLockIndex] == null
					|| !PlayerList.players[combatLockIndex].isVisible()) {
				clearCombatTargetLock();
			}
		}
	}

	private static void lockCombatTarget(short action, long key, JagString targetName) {
		combatLockType = isNpcActionCode(action) ? COMBAT_LOCK_NPC : COMBAT_LOCK_PLAYER;
		combatLockIndex = (int) key;
		combatLockName = toPlainString(targetName);
	}

	private static void clearCombatTargetLock() {
		combatLockType = COMBAT_LOCK_NONE;
		combatLockIndex = -1;
		combatLockName = "";
	}

	/**
	 * Per-tick keyboard handling: 1/2/3 execute displayed actions, E executes
	 * the primary action. Called from {@link ModernControlController#update()}
	 * ONLY when the dialogue/UI layer ({@link ModernDialogueKeyboard}) did not
	 * consume the key.
	 */
	public static boolean update() {
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

		if (!isOverlayActive() || !ModernControlController.isGameplayInputAllowed()
				|| Keyboard.pressedKeys[Keyboard.KEY_SHIFT]) {
			return false;
		}
		if (!snapshotValid) {
			return false;
		}
		// First-person combat is intentionally direct: a left click only maps
		// to a real visible Attack entry on the current crosshair target. Normal
		// object clicks, UI clicks, movement and non-combat target actions retain
		// their existing vanilla behaviour.
		if (Mouse.clickButton == 1 && executeDirectCombatClick()) {
			directCombatClickConsumed = true;
			return true;
		}
		if (edge1) {
			executeAction(0);
			return true;
		} else if (edge2) {
			executeAction(1);
			return true;
		} else if (edge3) {
			executeAction(2);
			return true;
		} else if (edgeE) {
			executeAction(0); // E = primary/default action
			return true;
		}
		return false;
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
				if (isCombatEntityAction(action) && MiniMenu.ops[i] != null
						&& MiniMenu.ops[i].equalsIgnoreCase(LocalizedText.ATTACK)) {
					lockCombatTarget(MiniMenu.actions[i], MiniMenu.keys[i], MiniMenu.opBases[i]);
				}
				MiniMenu.doAction(i);
				return;
			}
		}
		// Target entry no longer present this frame — drop the stale target.
		snapshotValid = false;
		snapshotCount = 0;
		lastTargetKey = Long.MIN_VALUE;
	}

	/** Executes the current target's existing NPC/player Attack menu entry. */
	private static boolean executeDirectCombatClick() {
		for (int slot = 0; slot < snapshotCount; slot++) {
			if (!isCombatEntityAction(snapshotAction[slot])) {
				continue;
			}
			long key = snapshotKeys[slot];
			int action = snapshotAction[slot];
			int arg1 = snapshotIntArg1[slot];
			int arg2 = snapshotIntArg2[slot];
			for (int i = 0; i < MiniMenu.size; i++) {
				if (MiniMenu.keys[i] == key && MiniMenu.actions[i] == action
						&& MiniMenu.intArgs1[i] == arg1 && MiniMenu.intArgs2[i] == arg2
						&& MiniMenu.ops[i] != null
						&& MiniMenu.ops[i].equalsIgnoreCase(LocalizedText.ATTACK)) {
					lockCombatTarget(MiniMenu.actions[i], MiniMenu.keys[i], MiniMenu.opBases[i]);
					MiniMenu.doAction(i);
					return true;
				}
			}
		}
		return false;
	}

	/**
	 * Resolves the world tile for a MiniMenu entry. The scene-pick tag and its
	 * derived menu entry do not use the same key layout: a scene tag encodes
	 * an entity index in its high bits, but the vanilla NPC menu entry uses
	 * the NPC index itself as its key. Entity kind is therefore determined
	 * from the existing action code, not arbitrary menu-key bits. Loc/objstack
	 * entries keep their menu tile arguments.
	 */
	private static boolean resolveEntryTile(long key, short action, int fallbackX, int fallbackZ, int[] out) {
		int index = (int) key;
		if (isNpcActionCode(action)) {
			if (index < 0 || index >= NpcList.npcs.length) {
				return false;
			}
			Npc npc = NpcList.npcs[index];
			if (npc == null) {
				return false;
			}
			out[0] = npc.xFine >> 7;
			out[1] = npc.zFine >> 7;
			return true;
		}
		out[0] = fallbackX;
		out[1] = fallbackZ;
		return true;
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
	 * Public accessor for {@link #toPlainString(JagString)} so that
	 * {@link FPContextMenuController} can reuse the same markup-stripping
	 * logic without duplicating it.
	 */
	public static String toPlainStringPublic(JagString js) {
		return toPlainString(js);
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
