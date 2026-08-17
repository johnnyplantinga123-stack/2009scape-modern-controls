package rt4;

/**
 * Central modern-control dispatcher (Phase 1).
 *
 * <p>This is the single entry point called from the live game loop. It routes
 * per-frame updates based on the active {@link CameraMode.Mode}:
 *
 * <pre>
 * switch (cameraMode) {
 *     case ORIGINAL:
 *         runOriginalRuneScapeControls();
 *         break;
 *
 *     case FIRST_PERSON:
 *         ModernControlController.update();  // + FirstPersonCamera.update() (Phase 2)
 *         break;
 *
 *     case THIRD_PERSON:
 *         ModernControlController.update();  // + ThirdPersonCamera.update() (Phase 14)
 *         break;
 * }
 * </pre>
 *
 * <p>In Phase 1 only the {@code ORIGINAL} path runs real code; first/third
 * person cameras, WASD movement, targeting and interactions are added in later
 * phases. This keeps original RuneScape behaviour fully intact while the mode
 * framework and F11 cycling are in place.
 *
 * <h2>Round #6B/C P13 — INPUT OWNERSHIP MATRIX (single source of truth)</h2>
 * <pre>
 * ORIGINAL:
 *   vanilla everything — no modern filtering, cursor, keys or camera.
 *
 * MODERN FIRST_PERSON:
 *   CTRL held?           yes → FP_UI_CURSOR substate: normal cursor, mouse
 *                                belongs to interfaces, world 1-9/E disabled
 *                                ({@link FirstPersonCamera#isUiCursorActive()})
 *                        no  → FP mouse-look (cursor locked)
 *   explicit chat entry?  chat owns the keyboard (chatInputActive)
 *   dialogue active?      dialogue owns 1-9/SPACE
 *                         ({@link ModernDialogueKeyboard#hasActiveDialogue()});
 *                         world overlay has ZERO input authority
 *   otherwise:            WASD movement (ModernMovementController Q16),
 *                         E = primary world action, 1-3 = world actions
 *
 * MODERN CHASE:
 *   modern WASD movement (Q16 owner)
 *   dialogue/chat priorities still apply
 *   no FP crosshair world actions (overlay gated on FP rig state)
 *
 * MODERN FREE:
 *   VANILLA click-to-walk locomotion (movement queue + method2247)
 *   WASD DISABLED for movement (ModernMovementController.update() no-ops)
 *   vanilla interface/mouse behaviour
 *   MODERN FREE camera + expanded zoom (rig stays FREE, profile stays MODERN)
 *
 * Movement ownership is exclusive ({@link ModernMovementController#getMovementOwner()}):
 *   ORIGINAL → "ORIGINAL" (vanilla)
 *   MODERN FP/CHASE → "MODERN_Q16"
 *   MODERN FREE → "VANILLA_FREE"
 * F11 toggles ONLY ORIGINAL ↔ MODERN. CHASE ↔ FREE handoffs happen via the
 * scroll wheel inside the rig and never involve F11.
 *
 * <h2>Round #7 P1 — KEYBOARD OWNERSHIP MATRIX (single source of truth:
 * {@link #isModernGameplayKeyboardOwner()})</h2>
 * <pre>
 * FIRST_PERSON / CHASE:
 *   MODERN GAMEPLAY keyboard ownership — W/A/S/D/E/1-9/SPACE are gameplay
 *   bindings and are filtered out of the chatbox while chat is closed
 *   ({@link #shouldForwardKeyToChat}); dialogue keys belong to
 *   {@link ModernDialogueKeyboard}.
 *
 * FREE:
 *   VANILLA keyboard ownership — the MODERN gameplay-character suppression
 *   is bypassed entirely. W/A/S/D/E/1-9/SPACE are ordinary keyboard
 *   characters again, vanilla direct chat entry works (no explicit-ENTER
 *   ownership requirement), click-to-walk remains functional and the camera
 *   stays the MODERN expanded camera. {@link ModernDialogueKeyboard} and the
 *   FP world overlay are inert in FREE.
 *
 * ORIGINAL:
 *   untouched vanilla keyboard ownership (never filtered).
 * </pre>
 */
public final class ModernControlController {

	/**
	 * Distance (in tiles) used for nearby world interactions (doors, objects,
	 * ground items, nearby NPC talk/trade). Configured centrally here so the
	 * same value is never hardcoded in multiple places.
	 */
	public static final int MODERN_NEARBY_INTERACT_DISTANCE = 2;

	/**
	 * Wider distance (in tiles) used for combat-target <em>acquisition</em> via
	 * the crosshair (ranged/magic). This only controls which entity the player
	 * can <em>select</em>; actual attack/spell range and LOS stay with existing
	 * RuneScape code.
	 */
	public static final int MODERN_COMBAT_TARGET_DISTANCE = 10;

	// ---- Chat input state ----

	/**
	 * Whether the chatbox is currently in text-input mode. When {@code true},
	 * modern gameplay input (WASD movement, mouse-look, interaction) is
	 * suppressed so the player can type freely.
	 *
	 * <p>The RS chatbox is driven by CS2 scripts; there is no single existing
	 * Java boolean that tracks "chat typing mode". The keyboard event queue is
	 * therefore authoritative: {@link #shouldForwardKeyToChat(int, int)} sees
	 * every Enter event, including taps that begin and end between game ticks.
	 */
	private static boolean chatInputActive = false;

	/** Escape keycode in RS keycode space (CODE_MAP[VK_ESCAPE] = 13). */
	private static final int KEY_ESCAPE = 13;

	// ---- WASD key codes (must match ModernMovementController) ----
	private static final int KEY_W = 33;
	private static final int KEY_A = 48;
	private static final int KEY_S = 49;
	private static final int KEY_D = 50;

	// ---- Gameplay key codes (round #6A, P3: chat ownership) ----
	/** Interact key (FP action layer). */
	private static final int KEY_E = 34;
	/** World map hotkey in MODERN FIRST_PERSON. */
	private static final int KEY_M = 70;
	/** SPACE: dialogue continue / gameplay action. */
	private static final int KEY_SPACE = 83;
	/** First digit keycode ('1'); codes are contiguous 16..24 ('1'..'9'). */
	private static final int KEY_1 = 16;
	private static final int KEY_9 = 24;

	// ---- ORIGINAL wheel zoom (Phase 3C round #4, P4) ----
	// SOURCE PROOF: the classic camera transform (ScriptRunner.method4326,
	// cameraType==1) reads Camera.ZOOM + pitchTarget*3 as the boom distance.
	// Camera.ZOOM has no other Java writer in this client, and no functioning
	// wheel→camera path exists in the cache scripts, so this small ORIGINAL
	// wheel path writes Camera.ZOOM on the legacy zoom scale.
	// Requirements: smooth zoom, arrows/middle mouse unchanged, no modern rig
	// activation, no FOV change.
	/** ORIGINAL zoom target (-1 = idle/not engaged). */
	private static int originalZoomTarget = -1;
	/** Ticks remaining before legacyZoomInputSeen is cleared (for the overlay). */
	private static int legacyZoomSeenTimer;

	private ModernControlController() {
	}

	/**
	 * Per-frame update. Called from {@link client#mainUpdate()} and/or the main
	 * loop. When in a modern mode, the corresponding controller duties are
	 * dispatched here (added in later phases); in {@code ORIGINAL} mode nothing
	 * is done so the original RuneScape code paths run untouched.
	 *
	 * <p>Phase 3C: ModernCameraRig.update() runs AFTER FirstPersonCamera (for FP
	 * mouse-look) and BEFORE ModernMovementController (for camera yaw basis).
	 * The rig manages the FP↔CHASE↔FREE scroll-zoom continuum.
	 */
	public static void update() {
		// Always update chat input state (needed in any modern mode)
		updateChatInputState();

		switch (CameraMode.getCurrent()) {
			case ORIGINAL:
				// No modern control override — original RuneScape controls run as-is.
				// Phase 3C round #4 (P4): ORIGINAL wheel camera zoom on the legacy
				// Camera.ZOOM scale. No rig activation, no FOV change.
				updateOriginalWheelZoom();
				break;
		case FIRST_PERSON:
			// Phase 3B fix #2: camera MUST update before movement so that
			// ModernMovementController reads the CURRENT frame's yaw
			// (fpCamYaw → Camera.cameraYaw), not the previous frame's.
			FirstPersonCamera.update();
			// Phase 3C: camera rig continuum (FP/CHASE/FREE)
			ModernCameraRig.update();
			ModernMovementController.update();
			updateInteractionLayer();
			break;
			case THIRD_PERSON:
				// Phase 3C: FirstPersonCamera provides mouse-look for FP rig state.
				// When rig is in CHASE/FREE, FP camera fields are not written to Camera.
				FirstPersonCamera.update();
				ModernCameraRig.update();
				ModernMovementController.update();
				updateInteractionLayer();
				break;
		}
	}

	/**
	 * Phase 3C round #5 (P6/P7): keyboard interaction priority chain
	 * (brief §15). Chat text entry is handled by the isChatInputActive gate
	 * inside each controller; the dialogue/choice layer runs first and when
	 * it consumes a key the FP world-action layer is skipped for this tick.
	 * ORIGINAL mode never reaches this method.
	 *
	 * <p>Round #8 P7: FP context menu sits between dialogue and quick overlay
	 * in the priority chain. When the menu is open, it owns wheel/left-click
	 * and the quick overlay is suppressed.</p>
	 */
	private static void updateInteractionLayer() {
		ModernActionOverlay.beginInputFrame();
		boolean uiConsumed = ModernDialogueKeyboard.update();
		if (uiConsumed) {
			ModernHud.updateInput(true);
			ModernQuickBars.update(true);
		} else {
			// Round #8 P7: FP context menu update (right-click open, wheel select,
			// left-click execute). Runs before quick overlay so the menu can
			// suppress overlay input when open.
			FPContextMenuController.update();
			boolean hudActionConsumed = ModernHud.updateInput(FPContextMenuController.isMenuOpen());
			// World interaction retains priority for unmodified 1/2/3. Shifted
			// numbers always belong to the prayer/magic action bar.
			boolean worldActionConsumed = ModernActionOverlay.update();
			ModernQuickBars.update(worldActionConsumed || hudActionConsumed);
		}
	}

	/**
	 * Returns whether gameplay input (modern movement/interaction) is currently
	 * allowed. Returns {@code false} when the chatbox is in text-input mode so
	 * WASD keys type letters instead of generating movement.
	 *
	 * <p>ORIGINAL mode never consults this method — it is only called by
	 * {@link ModernMovementController} and (in the future) interaction/
	 * targeting controllers.
	 *
	 * @return {@code true} when modern WASD/E/click may act on the world.
	 */
	public static boolean isGameplayInputAllowed() {
		return !chatInputActive;
	}

	/**
	 * Returns whether the chatbox is currently in text-input mode.
	 */
	public static boolean isChatInputActive() {
		return chatInputActive;
	}

	/**
	 * Resets the chat input state to inactive. Called on camera mode
	 * transitions to prevent stale chat state from crossing mode boundaries.
	 */
	public static void resetChatState() {
		chatInputActive = false;
	}

	/**
	 * Round #7 P1 — ONE source of truth for keyboard ownership.
	 *
	 * <p>The MODERN gameplay keyboard (WASD movement, E interact, 1-9 world
	 * shortcuts, SPACE dialogue/gameplay — filtered from the chatbox and
	 * consumed by the dialogue/world layers) belongs to MODERN rigs whose
	 * state is FIRST_PERSON or CHASE. When the rig is FREE, vanilla keyboard
	 * ownership applies: every key is an ordinary keyboard character and the
	 * vanilla chat/interface routes receive them unfiltered.
	 *
	 * <p>Conceptually:
	 * <pre>modernGameplayKeyboardOwner = CameraMode.isModern()
	 *     && rig is FIRST_PERSON or CHASE</pre>
	 * (An inactive rig inside a modern mode conservatively keeps gameplay
	 * ownership — the rig activates on the first modern update tick.)
	 */
	public static boolean isModernGameplayKeyboardOwner() {
		return CameraMode.isModern()
				&& (!ModernCameraRig.isActive()
						|| ModernCameraRig.isFirstPersonRigState()
						|| ModernCameraRig.isChaseRigState());
	}

	/**
	 * Round #7 P1 — whether FREE currently owns the keyboard with FULL
	 * VANILLA behaviour (modern mode, rig in FREE).
	 */
	public static boolean isVanillaFreeKeyboardOwner() {
		return CameraMode.isModern() && ModernCameraRig.isFreeRigState();
	}

	/** Debug overlay name for the current keyboard owner (Round #7 P8). */
	public static String getKeyboardOwnerName() {
		if (CameraMode.getCurrent() == CameraMode.Mode.ORIGINAL) {
			return "ORIGINAL";
		}
		return isVanillaFreeKeyboardOwner() ? "VANILLA_FREE" : "MODERN_GAMEPLAY";
	}

	/**
	 * Returns whether the given typed key entry should be forwarded to the
	 * interface/chat system. When in a modern camera mode with gameplay input
	 * allowed (chat not active), ALL gameplay keys are filtered out so they
	 * do not reach the CS2 chatbox script as typed characters.
	 *
	 * <p>Round #7 P1: FREE rig = FULL VANILLA keyboard/chat behaviour — the
	 * gameplay-character suppression is bypassed entirely (W/A/S/D/E/1-9/
	 * SPACE become ordinary keyboard characters; no explicit-ENTER ownership
	 * requirement). Both Keyboard.nextKey() drain sites (Protocol.java and
	 * client.java mainUpdate) route through this single method, so this one
	 * gate covers every keyboard entry.
	 *
	 * <p>Round #6A (P3, HARD FIX): the keyboard produces TWO queue entry types
	 * per key press — a keycode entry ({@code keyPressed}: keyCode ≥ 0,
	 * keyChar = -1) and a char entry ({@code keyTyped}: keyCode = -1,
	 * keyChar = the character). BOTH must be filtered, otherwise held keys
	 * leak into chat via the char-only repeat entries (the "eeeefddddss..."
	 * runtime failure). Filtered gameplay keys: W/A/S/D (movement), E
	 * (interact), 1-9 (FP action shortcuts), SPACE (dialogue/gameplay).
	 *
	 * <p>Priority chain: explicit chat entry (ENTER) > dialogue/choice UI >
	 * modal UI > FP action keys > movement. When {@code chatInputActive},
	 * everything is forwarded and gameplay yields to chat.
	 *
	 * @param keyCode the game keycode from {@link Keyboard#keyCode}, or -1 for char-only entries.
	 * @param keyChar the character from {@link Keyboard#keyChar}, or -1 for code-only entries.
	 * @return {@code true} if the entry should be delivered to interface onKey handlers.
	 */
	public static boolean shouldForwardKeyToChat(int keyCode, int keyChar) {
		if (CameraMode.getCurrent() == CameraMode.Mode.ORIGINAL) {
			return true; // Original mode: never filter
		}
		if (!isModernGameplayKeyboardOwner()) {
			// Round #7 P1: FREE rig — restore the exact vanilla keyboard/chat
			// behaviour. No gameplay-character suppression, no explicit-ENTER
			// ownership requirement.
			return true;
		}
		if (keyCode == Keyboard.KEY_ENTER) {
			if (!chatInputActive) {
				// The first Enter gives the modern chatbox keyboard ownership.
				// Do not pass it to script 73: an empty input would open Quick Chat.
				chatInputActive = true;
				return false;
			}
			// The second Enter is still delivered so script 73 can send the
			// current text, but gameplay ownership resumes immediately.
			chatInputActive = false;
			return true;
		}
		if (keyCode == KEY_ESCAPE && chatInputActive) {
			chatInputActive = false;
			return true;
		}
		if (chatInputActive) {
			return true; // Chat typing active: allow all keys through
		}
		// In modern gameplay mode with chat closed, block gameplay keys from
		// reaching the chatbox. Both the keycode entry (from keyPressed) and
		// the character entry (from keyTyped) must be filtered.
		if (isGameplayKeyCode(keyCode)) {
			return false;
		}
		if (keyCode < 0 && isGameplayChar(keyChar)) {
			return false;
		}
		return true;
	}

	/** Whether a game keycode belongs to a gameplay binding (chat closed). */
	private static boolean isGameplayKeyCode(int keyCode) {
		return keyCode == KEY_W || keyCode == KEY_A || keyCode == KEY_S || keyCode == KEY_D
				|| keyCode == KEY_E || keyCode == KEY_M || keyCode == KEY_SPACE
				|| (keyCode >= KEY_1 && keyCode <= KEY_9) || keyCode == 25;
	}

	/** Whether a typed character belongs to a gameplay binding (chat closed). */
	private static boolean isGameplayChar(int keyChar) {
		return keyChar == 'w' || keyChar == 'W' || keyChar == 'a' || keyChar == 'A'
				|| keyChar == 's' || keyChar == 'S' || keyChar == 'd' || keyChar == 'D'
				|| keyChar == 'e' || keyChar == 'E' || keyChar == 'm' || keyChar == 'M'
				|| (keyChar >= '0' && keyChar <= '9')
				|| keyChar == ' ';
	}

	// ---- Private helpers ----

	/**
	 * ORIGINAL-mode wheel camera zoom (Phase 3C round #4, P4).
	 *
	 * <p>Wheel input moves a zoom TARGET on the legacy zoom scale (vanilla
	 * range 100..1200, step 50/notch); the actual {@link Camera#ZOOM} then
	 * approaches the target smoothly per tick. {@code Camera.ZOOM} is the
	 * field the classic camera transform really reads (proven from
	 * {@code ScriptRunner.method4326} cameraType==1). Arrows and middle mouse
	 * are untouched; the modern rig is never activated; FOV is unchanged.</p>
	 */
	private static void updateOriginalWheelZoom() {
		if (CameraMode.isModern()) {
			// Not ORIGINAL — release any legacy zoom engagement.
			originalZoomTarget = -1;
			return;
		}

		// Overlay diagnostics: keep "seen" visible for ~1s after last event.
		if (legacyZoomSeenTimer > 0) {
			legacyZoomSeenTimer--;
			if (legacyZoomSeenTimer == 0) {
				DebugOverlay.legacyZoomInputSeen = false;
			}
		}

		// Wheel input → adjust target (skip when UI owns the wheel).
		if (MouseWheel.wheelRotation != 0 && !ModernCameraRig.isMouseOverScrollableUI()) {
			int rotation = MouseWheel.wheelRotation;
			if (originalZoomTarget < 0) {
				originalZoomTarget = Camera.ZOOM;
			}
			DebugOverlay.legacyZoomInputSeen = true;
			DebugOverlay.legacyZoomBefore = Camera.ZOOM;
			legacyZoomSeenTimer = 50;
			// Scroll IN (rotation < 0) → zoom in (smaller ZOOM).
			originalZoomTarget += rotation * 50;
			if (originalZoomTarget < 100) originalZoomTarget = 100;
			if (originalZoomTarget > 1200) originalZoomTarget = 1200;
		}

		// Smooth per-tick approach toward the target.
		if (originalZoomTarget >= 0) {
			int delta = originalZoomTarget - Camera.ZOOM;
			if (delta != 0) {
				int step = delta / 3;
				if (step == 0) step = (delta > 0) ? 1 : -1;
				Camera.ZOOM += step;
				DebugOverlay.legacyZoomAfter = Camera.ZOOM;
			} else {
				originalZoomTarget = -1; // Converged — disengage.
			}
		}
	}

	/**
	 * Applies lifecycle fallbacks for the event-driven chat input state.
	 *
	 * <p>Enter and Escape are handled from the lossless keyboard event queue in
	 * {@link #shouldForwardKeyToChat(int, int)}. Polling Enter here used to miss
	 * fast taps and could leave mouse-look disabled after sending a message.
	 *
	 * <p>This is called every frame from {@link #update()}, before the
	 * mode-specific dispatch, so the flag is current for the entire frame.
	 */
	private static void updateChatInputState() {
		// Escape polling is retained as a harmless fallback if an interface
		// drains the event before the normal keyboard ownership gate sees it.
		if (Keyboard.pressedKeys[KEY_ESCAPE]) {
			chatInputActive = false;
		}

		// When camera mode switches to ORIGINAL, reset chat state
		if (CameraMode.getCurrent() == CameraMode.Mode.ORIGINAL) {
			chatInputActive = false;
		}
	}
}
