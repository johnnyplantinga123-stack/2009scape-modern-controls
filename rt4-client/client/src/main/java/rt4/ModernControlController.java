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
	 * Java boolean that tracks "chat typing mode". We therefore use Enter-key
	 * edge-detection in {@link #updateChatInputState()} to maintain this flag.
	 */
	private static boolean chatInputActive = false;

	/** Escape keycode in RS keycode space (CODE_MAP[VK_ESCAPE] = 13). */
	private static final int KEY_ESCAPE = 13;

	/** Previous frame's Enter-key state, used for edge-detection. */
	private static boolean enterWasPressed = false;

	// ---- WASD key codes (must match ModernMovementController) ----
	private static final int KEY_W = 33;
	private static final int KEY_A = 48;
	private static final int KEY_S = 49;
	private static final int KEY_D = 50;

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
				// No modern override — original RuneScape controls run as-is.
				break;
		case FIRST_PERSON:
			// Phase 3B fix #2: camera MUST update before movement so that
			// ModernMovementController reads the CURRENT frame's yaw
			// (fpCamYaw → Camera.cameraYaw), not the previous frame's.
			FirstPersonCamera.update();
			// Phase 3C: camera rig continuum (FP/CHASE/FREE)
			ModernCameraRig.update();
			ModernMovementController.update();
			break;
			case THIRD_PERSON:
				// Phase 3C: FirstPersonCamera provides mouse-look for FP rig state.
				// When rig is in CHASE/FREE, FP camera fields are not written to Camera.
				FirstPersonCamera.update();
				ModernCameraRig.update();
				ModernMovementController.update();
				break;
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
		enterWasPressed = Keyboard.pressedKeys[Keyboard.KEY_ENTER];
	}

	/**
	 * Returns whether the given typed key entry should be forwarded to the
	 * interface/chat system. When in a modern camera mode with gameplay input
	 * allowed (chat not active), WASD movement keys are filtered out so they
	 * do not reach the CS2 chatbox script as typed characters.
	 *
	 * <p>This is the correct fix for the WASD/chat conflict: instead of just
	 * blocking movement (which the previous approach did), we prevent the
	 * character codes from reaching the chat text insertion path entirely.
	 *
	 * @param keyCode the game keycode from {@link Keyboard#keyCode}, or -1 for char-only entries.
	 * @param keyChar the character from {@link Keyboard#keyChar}, or -1 for code-only entries.
	 * @return {@code true} if the entry should be delivered to interface onKey handlers.
	 */
	public static boolean shouldForwardKeyToChat(int keyCode, int keyChar) {
		if (CameraMode.getCurrent() == CameraMode.Mode.ORIGINAL) {
			return true; // Original mode: never filter
		}
		if (chatInputActive) {
			return true; // Chat typing active: allow all keys through
		}
		// In modern gameplay mode with chat closed, block movement keys
		// from reaching the chatbox. Both the keycode entry (from keyPressed)
		// and the character entry (from keyTyped) must be filtered.
		if (keyCode == KEY_W || keyCode == KEY_A || keyCode == KEY_S || keyCode == KEY_D) {
			return false;
		}
		// Also filter the character-only entries (keyCode == -1) for w/a/s/d
		if (keyCode < 0) {
			if (keyChar == 'w' || keyChar == 'W' || keyChar == 'a' || keyChar == 'A'
					|| keyChar == 's' || keyChar == 'S' || keyChar == 'd' || keyChar == 'D') {
				return false;
			}
		}
		return true;
	}

	// ---- Private helpers ----

	/**
	 * Tracks the chatbox text-input state using Enter-key edge-detection.
	 *
	 * <p>Pressing Enter toggles between "gameplay mode" and "chat typing mode".
	 * Escape also closes the chat (RS default behaviour handled by the CS2
	 * chatbox script); we mirror that here to keep the flag in sync.
	 *
	 * <p>This is called every frame from {@link #update()}, before the
	 * mode-specific dispatch, so the flag is current for the entire frame.
	 */
	private static void updateChatInputState() {
		boolean enterPressed = Keyboard.pressedKeys[Keyboard.KEY_ENTER];
		boolean escPressed = Keyboard.pressedKeys[KEY_ESCAPE];

		// Enter edge-detection: toggle on press (not hold)
		if (enterPressed && !enterWasPressed) {
			chatInputActive = !chatInputActive;
		}
		enterWasPressed = enterPressed;

		// Escape always closes chat input
		if (escPressed) {
			chatInputActive = false;
		}

		// When camera mode switches to ORIGINAL, reset chat state
		if (CameraMode.getCurrent() == CameraMode.Mode.ORIGINAL) {
			chatInputActive = false;
			enterWasPressed = enterPressed;
		}
	}
}