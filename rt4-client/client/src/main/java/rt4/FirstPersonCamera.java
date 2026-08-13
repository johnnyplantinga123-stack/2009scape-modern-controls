package rt4;

import java.awt.Point;

/**
 * First-person camera controller (Phase 2).
 *
 * <p>This is a camera-only implementation that follows the player's position.
 * The camera writes directly to {@link Camera#renderX}, {@link Camera#renderZ},
 * {@link Camera#anInt40}, {@link Camera#cameraYaw}, and {@link Camera#cameraPitch}
 * each frame, bypassing the normal camera update methods.
 *
 * <p>Toggle via {@link CameraMode} cycling (F11). Mouse-look is cursor-locked.
 *
 * <p>This class does NOT handle movement, networking, or combat - those are
 * separate concerns for later phases.
 */
public final class FirstPersonCamera {

	// ---- Configuration ----
	private static final int DEFAULT_MOUSE_SENSITIVITY = 3;
	private static final int MIN_MOUSE_SENSITIVITY = 1;
	private static final int MAX_MOUSE_SENSITIVITY = 12;
	private static final int DEFAULT_FOV_DEGREES = 75;
	private static final int MIN_FOV_DEGREES = 60;
	private static final int MAX_FOV_DEGREES = 110;
	private static final int EYE_HEIGHT = 200;
	// Pitch limits: -384 (looking up) to 512 (looking down)
	// The legacy camera uses 128..383 (down from horizon).
	// First-person stores pitch as signed, then wraps to 0..2047 for renderer.
	private static final int PITCH_MIN = -384;
	private static final int PITCH_MAX = 512;

	// ---- State ----
	private static boolean active = false;
	private static int mouseSensitivity = DEFAULT_MOUSE_SENSITIVITY;
	private static int fovDegrees = DEFAULT_FOV_DEGREES;

	// Camera position (fine coordinates, same space as Camera.renderX/renderZ)
	private static int fpCamX;
	private static int fpCamZ;
	private static int fpCamYaw;
	private static int fpCamPitch;
	private static int fpCamYOffset;
	private static int bobPhase;

	// Mouse look tracking
	private static int lastMouseLookX = -1;
	private static int lastMouseLookY = -1;
	private static boolean cursorLocked;
	private static boolean discardLockedMouseSample;

	// Saved state for restoring normal camera
	private static int savedCameraType;

	private FirstPersonCamera() {
	}

	/**
	 * Returns whether first-person camera is currently active.
	 */
	public static boolean isActive() {
		return active;
	}

	/**
	 * Called when entering FIRST_PERSON mode. Initializes camera state from player.
	 */
	public static void activate() {
		if (PlayerList.self == null) {
			return;
		}

		active = true;
		savedCameraType = Camera.cameraType;
		// Set cameraType to 0 so both camera update sites skip their normal updates
		Camera.cameraType = 0;

		// Initialize camera position at player's current position
		fpCamX = PlayerList.self.xFine;
		fpCamZ = PlayerList.self.zFine;
		fpCamYaw = Camera.cameraYaw;
		fpCamPitch = 0; // Horizon; negative values look up
		fpCamYOffset = 0;
		bobPhase = 0;

		// Reset mouse look tracking
		lastMouseLookX = -1;
		lastMouseLookY = -1;
		lockCursor();
	}

	/**
	 * Called when leaving FIRST_PERSON mode. Restores normal camera.
	 */
	public static void deactivate() {
		if (!active) {
			return;
		}
		active = false;
		unlockCursor();
		Camera.cameraType = savedCameraType;
	}

	/**
	 * Called every frame when FIRST_PERSON is active.
	 * Updates camera state and writes to Camera fields.
	 */
	public static void update() {
		if (!active || PlayerList.self == null) {
			return;
		}

		// Follow player position (camera follows, doesn't lead)
		// Use xFine/zFine for smooth interpolation following player movement
		fpCamX = PlayerList.self.xFine;
		fpCamZ = PlayerList.self.zFine;

		// Head bob is disabled for stability (Phase 3 stabilization).
		// The code is preserved so it can be re-enabled later as polish.
		// updateHeadBob();
		fpCamYOffset = 0;

		// --- Mouse Look ---
		// Skip mouse-look when chat input is active to prevent camera
		// disturbance while typing.
		if (ModernControlController.isChatInputActive()) {
			// Reset tracking so no delta accumulates while typing
			lastMouseLookX = -1;
			lastMouseLookY = -1;
		} else {
			int curX = Mouse.currentMouseX;
			int curY = Mouse.currentMouseY;

			if (cursorLocked) {
				updateLockedMouseLook(curX, curY);
			} else if (curX >= 0 && curY >= 0) {
				if (lastMouseLookX >= 0 && lastMouseLookY >= 0) {
					int deltaX = curX - lastMouseLookX;
					int deltaY = curY - lastMouseLookY;

					// Yaw: mouse right -> yaw decreases (turn right)
					fpCamYaw -= deltaX * mouseSensitivity;
					fpCamYaw &= 0x7FF; // Wrap at 2048

					// Pitch: mouse down -> pitch increases (look down)
					fpCamPitch += deltaY * mouseSensitivity;
					if (fpCamPitch < PITCH_MIN) fpCamPitch = PITCH_MIN;
					if (fpCamPitch > PITCH_MAX) fpCamPitch = PITCH_MAX;
				}
				lastMouseLookX = curX;
				lastMouseLookY = curY;
			} else {
				lastMouseLookX = -1;
				lastMouseLookY = -1;
			}
		}

		// --- Write to Camera fields ---
		int groundHeight = SceneGraph.getTileHeight(Player.plane, fpCamX, fpCamZ);
		Camera.renderX = fpCamX;
		Camera.renderZ = fpCamZ;
		// anInt40 is the height component (terrain height - eye offset)
		Camera.anInt40 = groundHeight - EYE_HEIGHT - fpCamYOffset;
		Camera.cameraYaw = fpCamYaw;
		Camera.cameraPitch = fpCamPitch & 0x7FF;
		Camera.yawTarget = fpCamYaw;
		Camera.pitchTarget = Camera.cameraPitch;
		// Also set cameraX/cameraZ so normal camera system doesn't snap
		Camera.cameraX = fpCamX;
		Camera.cameraZ = fpCamZ;
	}

	/**
	 * Called on scene/region rebuild to restore first-person state.
	 */
	public static void onSceneRebuild() {
		if (!active) {
			return;
		}
		Camera.cameraType = 0;
		if (!cursorLocked) {
			lockCursor();
		}
	}

	/**
	 * Returns the FOV projection scale for the GL renderer.
	 * 75 degrees is the legacy-neutral baseline.
	 */
	public static float getProjectionScale() {
		double defaultHalfAngle = Math.toRadians(DEFAULT_FOV_DEGREES / 2.0);
		double configuredHalfAngle = Math.toRadians(fovDegrees / 2.0);
		return (float) (Math.tan(configuredHalfAngle) / Math.tan(defaultHalfAngle));
	}

	/**
	 * Returns the configured FOV in degrees.
	 */
	public static int getFovDegrees() {
		return fovDegrees;
	}

	/**
	 * Returns the configured mouse sensitivity.
	 */
	public static int getMouseSensitivity() {
		return mouseSensitivity;
	}

	/**
	 * Sets the mouse sensitivity (clamped to valid range).
	 */
	public static void setMouseSensitivity(int sensitivity) {
		mouseSensitivity = Math.max(MIN_MOUSE_SENSITIVITY, Math.min(MAX_MOUSE_SENSITIVITY, sensitivity));
	}

	/**
	 * Sets the FOV in degrees (clamped to valid range).
	 */
	public static void setFovDegrees(int fov) {
		fovDegrees = Math.max(MIN_FOV_DEGREES, Math.min(MAX_FOV_DEGREES, fov));
	}

	/**
	 * Cycles mouse sensitivity through its range.
	 */
	public static void cycleMouseSensitivity() {
		mouseSensitivity = mouseSensitivity >= MAX_MOUSE_SENSITIVITY
				? MIN_MOUSE_SENSITIVITY : mouseSensitivity + 1;
	}

	/**
	 * Cycles FOV through its range in 5-degree steps.
	 */
	public static void cycleFov() {
		fovDegrees = fovDegrees >= MAX_FOV_DEGREES ? MIN_FOV_DEGREES : fovDegrees + 5;
	}

	// ---- Private helpers ----

	private static void updateHeadBob() {
		// Head bob disabled (Phase 3 stabilization).
		// Preserved for future re-enable as optional polish.
		// When re-enabling, also uncomment the call in update() and
		// remove the fpCamYOffset = 0 override.
		/*
		if (PlayerList.self.movementQueueSize > 0) {
			bobPhase = (bobPhase + 96) & 0x7FF;
			fpCamYOffset = MathUtils.sin[bobPhase] >> 10;
		} else {
			fpCamYOffset = fpCamYOffset * 3 / 4;
		}
		*/
	}

	private static void updateLockedMouseLook(int mouseX, int mouseY) {
		if (mouseX < 0 || mouseY < 0) {
			return;
		}
		if (discardLockedMouseSample) {
			discardLockedMouseSample = false;
			centreCursor();
			return;
		}
		int centreX = GameShell.canvasWidth / 2;
		int centreY = GameShell.canvasHeight / 2;
		int deltaX = mouseX - centreX;
		int deltaY = mouseY - centreY;
		if (deltaX == 0 && deltaY == 0) {
			return;
		}
		fpCamYaw = (fpCamYaw - deltaX * mouseSensitivity) & 0x7FF;
		fpCamPitch += deltaY * mouseSensitivity;
		if (fpCamPitch < PITCH_MIN) fpCamPitch = PITCH_MIN;
		if (fpCamPitch > PITCH_MAX) fpCamPitch = PITCH_MAX;
		centreCursor();
	}

	private static void lockCursor() {
		if (GameShell.signLink == null || GameShell.canvas == null) {
			return;
		}
		try {
			// Use 1x1 transparent pixel as cursor to effectively hide it
			GameShell.signLink.setCursor(new int[]{0}, 1, GameShell.canvas, new Point(0, 0), 1);
			cursorLocked = true;
			discardLockedMouseSample = true;
			centreCursor();
		} catch (Throwable ignored) {
			cursorLocked = false;
		}
	}

	private static void unlockCursor() {
		if (!cursorLocked) {
			return;
		}
		cursorLocked = false;
		discardLockedMouseSample = false;
		if (GameShell.signLink == null || GameShell.canvas == null) {
			return;
		}
		try {
			// Restore default cursor
			GameShell.signLink.setCursor(null, -1, GameShell.canvas, new Point(), -1);
		} catch (Throwable ignored) {
		}
	}

	private static void centreCursor() {
		try {
			Point canvasOnScreen = GameShell.canvas.getLocationOnScreen();
			// Use Robot directly to recenter cursor (CursorManager.setPosition is private)
			java.awt.Robot robot = new java.awt.Robot();
			robot.mouseMove(
					canvasOnScreen.x + GameShell.canvasWidth / 2,
					canvasOnScreen.y + GameShell.canvasHeight / 2);
		} catch (Throwable ignored) {
			// Canvas transition can temporarily make screen coordinates unavailable
		}
	}
}