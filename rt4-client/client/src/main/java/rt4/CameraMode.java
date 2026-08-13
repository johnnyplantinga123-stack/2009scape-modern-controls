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
	 */
	private static void onModeChanged(Mode previous, Mode next) {
		// Deactivate previous mode's camera
		if (previous == Mode.FIRST_PERSON) {
			FirstPersonCamera.deactivate();
		}
		// Third-person deactivation would go here in Phase 14

		// Activate new mode's camera
		if (next == Mode.FIRST_PERSON) {
			FirstPersonCamera.activate();
		}
		// Third-person activation would go here in Phase 14
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