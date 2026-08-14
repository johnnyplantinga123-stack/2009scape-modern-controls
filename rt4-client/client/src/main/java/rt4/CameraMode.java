package rt4;

/**
 * Camera mode framework (Phase 1).
 *
 * <p>Defines the three playable camera modes and the F11 cycling
 * {@code ORIGINAL -> FIRST_PERSON -> THIRD_PERSON -> ORIGINAL}.
 *
 * <p>Phase 1 only implements the mode <em>state machine</em>. The actual
 * first-person / third-person camera, WASD movement, targeting and networking
 * are added in later phases. While the mode is {@code ORIGINAL} the client runs
 * its original, untouched behaviour.
 *
 * <p>F11 is edge-triggered via {@link Keyboard#keyPressed} (the AWT boundary),
 * so it responds exactly once per physical key press.
 */
public final class CameraMode {

	/** F11 in the game keycode space ({@code Keyboard.CODE_MAP[VK_F11]}). */
	private static final int KEY_F11 = 11;

	/**
	 * The three camera modes.
	 *
	 * <ul>
	 *     <li>{@link #ORIGINAL} — classic RuneScape camera & controls, fully preserved.</li>
	 *     <li>{@link #FIRST_PERSON} — first-person camera (Phase 2+).</li>
	 *     <li>{@link #THIRD_PERSON} — third-person camera (Phase 14).</li>
	 * </ul>
	 */
	public enum Mode {
		ORIGINAL,
		FIRST_PERSON,
		THIRD_PERSON
	}

	/** The currently active camera mode. */
	private static Mode current = Mode.ORIGINAL;

	private CameraMode() {
	}

	/** Returns the currently active camera mode. */
	public static Mode getCurrent() {
		return current;
	}

	/** Returns whether a modern (non-original) camera mode is active. */
	public static boolean isModern() {
		return current != Mode.ORIGINAL;
	}

	/** Returns whether first-person mode is active. */
	public static boolean isFirstPerson() {
		return current == Mode.FIRST_PERSON;
	}

	/** Returns whether third-person mode is active. */
	public static boolean isThirdPerson() {
		return current == Mode.THIRD_PERSON;
	}

	/**
	 * Returns the camera-relative yaw for FIRST_PERSON locomotion,
	 * or {@code -1} if the current mode does not use camera-relative steering.
	 *
	 * <ul>
	 *   <li>FIRST_PERSON: returns FirstPersonCamera's live yaw (the mouse-look direction).</li>
	 *   <li>THIRD_PERSON: returns -1 (no camera-relative steering yet; Phase 14 will
	 *       supply a third-person camera). ModernMovementController falls back to
	 *       the player body heading when this returns -1.</li>
	 *   <li>ORIGINAL: returns -1 (modern controller is inactive).</li>
	 * </ul>
	 */
	public static int getCameraRelativeYaw() {
		if (current == Mode.FIRST_PERSON) {
			return FirstPersonCamera.getYaw();
		}
		return -1;
	}

	/**
	 * Cycles the camera mode:
	 * {@code ORIGINAL -> FIRST_PERSON -> THIRD_PERSON -> ORIGINAL}.
	 *
	 * <p>Mode switching is designed not to teleport or reset the player's
	 * position; only the camera-mode state changes here.
	 */
	public static void cycle() {
		Mode previous = current;
		switch (current) {
			case ORIGINAL:
				current = Mode.FIRST_PERSON;
				break;
			case FIRST_PERSON:
				current = Mode.THIRD_PERSON;
				break;
			case THIRD_PERSON:
			default:
				current = Mode.ORIGINAL;
				break;
		}
		onModeChanged(previous, current);
	}

	/**
	 * Handles activation/deactivation when mode changes.
	 *
	 * <p>Each camera mode gets its own clean state on entry:
	 * <ul>
	 *   <li>FIRST_PERSON: anchors at player position, safe pitch, cursor locked.</li>
	 *   <li>THIRD_PERSON: placeholder — resets camera to safe defaults, cursor unlocked.
	 *       No actual third-person camera is built yet (Phase 14).</li>
	 *   <li>ORIGINAL: restores legacy camera system, releases cursor lock,
	 *       resets pitch to safe value so FP state doesn't contaminate.</li>
	 * </ul>
	 */
	private static void onModeChanged(Mode previous, Mode next) {
		// Reset chat input state on any mode transition to prevent
		// inconsistent state across mode boundaries.
		ModernControlController.resetChatState();

		// Phase 3B: lifecycle hooks for modern movement controller
		if (previous == Mode.ORIGINAL && next != Mode.ORIGINAL) {
			ModernMovementController.enterModernMode();
		} else if (previous != Mode.ORIGINAL && next == Mode.ORIGINAL) {
			ModernMovementController.exitModernMode();
		} else if (previous != next) {
			// e.g. FIRST_PERSON ↔ THIRD_PERSON: locomotion unchanged, camera only
			ModernMovementController.onModernModeSwitch();
		}

		// Deactivate previous mode's camera
		if (previous == Mode.FIRST_PERSON) {
			FirstPersonCamera.deactivate();
			// Reset camera pitch/yaw to safe values so the next mode
			// doesn't inherit extreme FP pitch (e.g., looking straight up
			// could put the camera underground in ORIGINAL/THIRD_PERSON).
			FirstPersonCamera.resetToSafeDefaults();
		}
		// THIRD_PERSON deactivation: nothing to do (placeholder, no camera state)

		// Activate new mode's camera
		if (next == Mode.FIRST_PERSON) {
			FirstPersonCamera.activate();
		}
		// THIRD_PERSON activation: placeholder only.
		// The camera state was already reset to safe defaults above when
		// leaving the previous mode. No mouse-lock, no camera override.
		// The original camera system runs in its default state.
		// Phase 14 will add actual third-person camera here.

		if (next == Mode.ORIGINAL) {
			// Ensure cursor is fully unlocked for original RS controls.
			// FirstPersonCamera.deactivate() already calls unlockCursor(),
			// but this is a safety net for edge cases (e.g., rapid F11 spam).
			FirstPersonCamera.resetToSafeDefaults();
		}
	}

	/**
	 * Called from {@link Keyboard#keyPressed} at the AWT boundary. A key-pressed
	 * event fires once per physical press, providing natural edge-triggering so
	 * F11 cycles the mode exactly once per keypress.
	 *
	 * @param keyCode the mapped game keycode, or {@code -1} for unmapped keys.
	 */
	public static void onKeyPressed(int keyCode) {
		if (keyCode == KEY_F11) {
			cycle();
		}
	}
}