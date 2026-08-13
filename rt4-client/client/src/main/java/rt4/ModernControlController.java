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

	private ModernControlController() {
	}

	/**
	 * Per-frame update. Called from {@link client#mainUpdate()} and/or the main
	 * loop. When in a modern mode, the corresponding controller duties are
	 * dispatched here (added in later phases); in {@code ORIGINAL} mode nothing
	 * is done so the original RuneScape code paths run untouched.
	 */
	public static void update() {
		// Always update chat input state (needed in any modern mode)
		updateChatInputState();

		switch (CameraMode.getCurrent()) {
			case ORIGINAL:
				// No modern override — original RuneScape controls run as-is.
				break;
		case FIRST_PERSON:
			ModernMovementController.update();
			FirstPersonCamera.update();
			break;
			case THIRD_PERSON:
				ModernMovementController.update();
				// Phase 14: ThirdPersonCamera.update();
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