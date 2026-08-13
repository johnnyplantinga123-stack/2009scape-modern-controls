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

	/**
	 * Set when a scene/region rebuild occurs. The next {@link #update()} call
	 * will perform a full reinitialisation from the player's new position
	 * once valid terrain data is available.
	 */
	private static boolean sceneRebuildPending = false;

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
	 * Resets camera state to safe defaults when transitioning away from
	 * FIRST_PERSON mode. This prevents extreme pitch/yaw values from being
	 * inherited by the next camera mode (e.g., camera ending up under terrain
	 * after looking straight up in FPS and pressing F11).
	 *
	 * <p>Called from {@link CameraMode#onModeChanged} when leaving FIRST_PERSON.
	 */
	public static void resetToSafeDefaults() {
		// Reset pitch to a neutral forward-looking value.
		// The original camera system uses pitch 128..383 (down from horizon).
		// 256 is a moderate downward angle, safe for the original camera.
		Camera.cameraPitch = 256;
		Camera.pitchTarget = 256;

		// Keep the current yaw (player's facing direction) — this is natural.
		// The original camera system will take over and adjust from here.

		// Reset height offset to zero (no FP eye offset).
		Camera.anInt40 = 0;

		// Reset FP-specific state so re-entering FPS starts clean.
		fpCamPitch = 0;
		fpCamYOffset = 0;
		bobPhase = 0;
		lastMouseLookX = -1;
		lastMouseLookY = -1;
	}

	/**
	 * Called every frame when FIRST_PERSON is active.
	 * Updates camera state and writes to Camera fields.
	 *
	 * <p>Handles the scene-rebuild lifecycle:
	 * <ol>
	 *   <li>If a rebuild is pending and player/terrain data is valid,
	 *       reinitialise camera position, pitch/yaw, and cursor lock.</li>
	 *   <li>Self-heal {@link Camera#cameraType} every frame — region rebuilds
	 *       set it to 1, which must be overridden back to 0 for FP mode.</li>
	 *   <li>Terrain safety: never let camera height go below terrain.</li>
	 * </ol>
	 */
	public static void update() {
		if (!active || PlayerList.self == null) {
			return;
		}

		// --- Scene rebuild lifecycle ---
		// After a region/chunk/scene rebuild, Camera.cameraType is set to 1
		// by the deob code (LoginManager.method2463 line 816). We must
		// re-assert cameraType=0 every frame to prevent the original camera
		// system from interfering.
		Camera.cameraType = 0;

		if (sceneRebuildPending) {
			// Reinitialise camera position from the player's current position.
			// This is deferred until update() so that player position and
			// terrain data are guaranteed to be valid (the rebuild is complete).
			fpCamX = PlayerList.self.xFine;
			fpCamZ = PlayerList.self.zFine;
			// Keep current yaw (player's facing direction) — natural feel.
			// Reset pitch to horizon — safe default after a region change.
			fpCamPitch = 0;
			fpCamYOffset = 0;
			bobPhase = 0;
			// Reset mouse tracking to prevent a large delta spike
			lastMouseLookX = -1;
			lastMouseLookY = -1;
			// Re-lock cursor if it was lost during rebuild
			if (!cursorLocked) {
				lockCursor();
			}
			sceneRebuildPending = false;
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
		// Terrain safety: SceneGraph.getTileHeight returns 0 when tileHeights
		// is null (during scene transition) or when coordinates are out of
		// bounds. A height of 0 would place the camera at -EYE_HEIGHT
		// (underground). We detect this and use a safe minimum height.
		int groundHeight = SceneGraph.getTileHeight(Player.plane, fpCamX, fpCamZ);
		if (groundHeight <= 0) {
			// Terrain data not yet available or invalid — use a safe default
			// height that keeps the camera above ground level.
			groundHeight = EYE_HEIGHT;
		}
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
	 * Called on scene/region rebuild to notify the first-person camera that
	 * the scene is being rebuilt. Sets a pending flag so the next
	 * {@link #update()} call performs a full reinitialisation once valid
	 * player/terrain data is available.
	 *
	 * <p>Called from:
	 * <ul>
	 *   <li>{@link LoginManager#setupLoadingScreenRegion()} — loading screen region setup</li>
	 *   <li>{@link LoginManager#method2463} — all gameplay region rebuilds
	 *       (REBUILD_REGION packet, plane changes, reconnects)</li>
	 * </ul>
	 */
	public static void onSceneRebuild() {
		if (!active) {
			return;
		}
		// Mark that a rebuild happened. The actual reinitialisation is
		// deferred to the next update() call, when player position and
		// terrain data are guaranteed to be valid.
		sceneRebuildPending = true;
		// Immediately re-assert cameraType so the original camera system
		// doesn't run even for one frame during the rebuild.
		Camera.cameraType = 0;
	}

	/**
	 * Returns whether a scene rebuild reinitialisation is pending.
	 * Visible for debugging / lifecycle diagnostics.
	 */
	public static boolean isRebuildPending() {
		return sceneRebuildPending;
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