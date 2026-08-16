package rt4;

/**
 * Control profile and camera mode framework (Phase 1 + 3C).
 *
 * <p>F11 toggles between two control profiles:
 * <ul>
 *   <li>{@link Mode#ORIGINAL} — pure vanilla RuneScape experience (click-to-move,
 *       legacy camera, scroll zoom, middle-mouse, minimap, animations).</li>
 *   <li>{@link Mode#THIRD_PERSON} — MODERN control profile (WASD movement,
 *       modern camera rig with FP/CHASE/FREE scroll continuum).</li>
 * </ul>
 *
 * <p>Inside MODERN, the {@link ModernCameraRig} manages the camera continuum:
 * <pre>
 *   FIRST_PERSON  ←scroll→  CHASE  ←scroll→  FREE
 * </pre>
 * Scrolling only changes the camera rig INSIDE MODERN. It never switches
 * the control profile. Only F11 switches between ORIGINAL and MODERN.
 *
 * <p>{@link Mode#FIRST_PERSON} is retained as a legacy enum value but is no
 * longer reached via F11. The rig manages FP camera state internally.
 *
 * <p>F11 is edge-triggered via {@link Keyboard#keyPressed} (the AWT boundary),
 * so it responds exactly once per physical key press.
 */
public final class CameraMode {

	/** F11 in the game keycode space ({@code Keyboard.CODE_MAP[VK_F11]}). */
	private static final int KEY_F11 = 11;

	/**
	 * Control profiles.
	 *
	 * <ul>
	 *     <li>{@link #ORIGINAL} — pure vanilla RuneScape. No modern controls.</li>
	 *     <li>{@link #FIRST_PERSON} — legacy value; no longer reached via F11.
	 *         The camera rig manages FP state internally within MODERN.</li>
	 *     <li>{@link #THIRD_PERSON} — MODERN control profile: WASD + modern camera rig.</li>
	 * </ul>
	 */
	public enum Mode {
		ORIGINAL,
		FIRST_PERSON,
		THIRD_PERSON
	}

	/** The currently active camera mode. Written only by the client game thread. */
	private static Mode current = Mode.ORIGINAL;

	/**
	 * F11 is received on the AWT event thread, but mode transitions touch live
	 * player, movement-queue and camera state owned by the client game thread.
	 * Keep the AWT boundary edge-trigger, and defer the actual transition until
	 * {@link #processPendingCycle()} runs from {@link client#mainLoop()}.
	 */
	private static volatile boolean cycleRequested;
	private static volatile String cycleRequestThread = "none";

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
	 * Returns the camera-relative yaw for locomotion movement basis.
	 *
	 * <ul>
	 *   <li>FIRST_PERSON (CameraMode): returns FirstPersonCamera's live yaw.</li>
	 *   <li>THIRD_PERSON + rig FP state (reached via scroll): returns FP camera yaw.</li>
	 *   <li>THIRD_PERSON + rig CHASE/FREE: returns -1 (movement uses body orientation,
	 *       NOT camera yaw — the camera is a follower, not the movement authority).</li>
	 *   <li>ORIGINAL: returns -1 (modern controller is inactive).</li>
	 * </ul>
	 */
	public static int getCameraRelativeYaw() {
		if (current == Mode.FIRST_PERSON) {
			return FirstPersonCamera.getYaw();
		}
		if (current == Mode.THIRD_PERSON && ModernCameraRig.isActive()) {
			// Only in rig FP state does the camera yaw drive locomotion.
			// In CHASE/FREE: stable movement heading (return -1; the movement
			// controller uses its own movementHeading, not camera yaw).
			// Phase 3C round #5 (P1): the FirstPersonCamera.isActive() check is
			// a safety net so FP WASD always tracks the FP look whenever the
			// FP camera is live, even if rigState disagrees for one tick.
			if (ModernCameraRig.getRigState() == ModernCameraRig.RigState.FIRST_PERSON
					|| FirstPersonCamera.isActive()) {
				return FirstPersonCamera.getYaw();
			}
		}
		return -1;
	}

	/**
	 * Toggles between ORIGINAL and MODERN control profiles.
	 *
	 * <p>F11 from ORIGINAL → enters MODERN (THIRD_PERSON).
	 * F11 from MODERN → returns to ORIGINAL.
	 *
	 * <p>Inside MODERN, scroll wheel controls the camera rig
	 * (FP ↔ CHASE ↔ FREE). Scrolling never switches control profile.
	 *
	 * <p>Mode switching is designed not to teleport or reset the player's
	 * position; only the control profile changes here.
	 */
	private static void cycle() {
		Mode previous = current;
		if (previous == Mode.ORIGINAL) {
			DebugOverlay.captureMovementBoundary("HEALTHY_ORIGINAL");
		} else {
			DebugOverlay.captureMovementBoundary("BEFORE_F11_EXIT");
		}
		if (current == Mode.ORIGINAL) {
			current = Mode.THIRD_PERSON; // Enter MODERN
		} else {
			current = Mode.ORIGINAL; // Return to vanilla
		}
		onModeChanged(previous, current);
		if (current == Mode.ORIGINAL) {
			DebugOverlay.captureMovementBoundary("AFTER_F11_EXIT");
		}
	}

	/**
	 * Consumes an F11 request on the client game thread. Called immediately
	 * after {@link Keyboard#loop()}, before modern movement and packet decoding,
	 * so the ownership handoff cannot interleave with either subsystem.
	 */
	public static void processPendingCycle() {
		if (!cycleRequested) {
			return;
		}
		cycleRequested = false;
		System.out.println("[F11-TRANSITION] requestedThread=" + cycleRequestThread
				+ " processedThread=" + Thread.currentThread().getName()
				+ " tick=" + client.loop);
		cycle();
	}

	/**
	 * Handles activation/deactivation when control profile changes.
	 *
	 * <p>ORIGINAL → MODERN: saves legacy camera state, activates modern
	 * movement and camera rig. Camera rig starts in CHASE state.
	 *
	 * <p>MODERN → ORIGINAL: deactivates FP camera if active, deactivates
	 * rig and movement, restores saved legacy camera state so the vanilla
	 * camera returns exactly where it was before MODERN was entered.
	 */
	private static void onModeChanged(Mode previous, Mode next) {
		// Reset chat input state on any mode transition to prevent
		// inconsistent state across mode boundaries.
		ModernControlController.resetChatState();

		if (previous == Mode.ORIGINAL && next != Mode.ORIGINAL) {
			// ORIGINAL → MODERN
			ModernMovementController.enterModernMode();
			ModernCameraRig.onEnterModernMode(); // saves legacy camera state
		} else if (previous != Mode.ORIGINAL && next == Mode.ORIGINAL) {
			// MODERN → ORIGINAL
			// Deactivate FP camera first if the rig was in FP state
			if (FirstPersonCamera.isActive()) {
				FirstPersonCamera.deactivate();
			}
			ModernMovementController.exitModernMode();
			ModernCameraRig.onExitModernMode(); // restores legacy camera state
		} else if (previous != next) {
			// Modern mode switch (e.g. FP ↔ TP): locomotion unchanged
			ModernMovementController.onModernModeSwitch();
		}

		// Safety net for ORIGINAL: ensure clean camera state
		if (next == Mode.ORIGINAL) {
			if (FirstPersonCamera.isActive()) {
				FirstPersonCamera.deactivate();
			}
			FirstPersonCamera.resetToSafeDefaults();
		}
	}

	/**
	 * Called from {@link Keyboard#keyPressed} at the AWT boundary. A key-pressed
	 * event fires once per physical press, providing natural edge-triggering so
	 * F11 toggles ORIGINAL ↔ MODERN exactly once per keypress.
	 * F12 toggles the debug overlay (Phase 3C).
	 *
	 * @param keyCode the mapped game keycode, or {@code -1} for unmapped keys.
	 */
	public static void onKeyPressed(int keyCode) {
		if (keyCode == KEY_F11) {
			cycleRequestThread = Thread.currentThread().getName();
			cycleRequested = true;
		}
		ModernCeiling.onKeyPressed(keyCode);
		// F12 debug overlay toggle — works in any mode, edge-triggered
		DebugOverlay.onKeyPressed(keyCode);
	}
}
