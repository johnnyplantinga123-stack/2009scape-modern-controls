package rt4;

import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;

/**
 * F12-toggled developer debug overlay (Phase 3C).
 *
 * <p>Displays compact on-screen diagnostics for camera, movement, input,
 * and scene state. Uses AWT Graphics2D for simplicity — does not interfere
 * with the game's rasteriser or font system.</p>
 *
 * <h2>Design Goals</h2>
 * <ul>
 *   <li>F12 is edge-triggered (no repeat while held).</li>
 *   <li>Overlay does NOT steal gameplay input.</li>
 *   <li>Compact enough to read while moving.</li>
 *   <li>Debug-only: zero overhead when hidden.</li>
 * </ul>
 */
public final class DebugOverlay {

	/** F12 in game keycode space (Keyboard.CODE_MAP[VK_F12] = 12). */
	private static final int KEY_F12 = 12;

	/** Whether the overlay is currently visible. */
	private static boolean visible = false;

	/** Previous-frame F12 state for edge detection. */
	private static boolean f12WasPressed = false;

	// ---- Diagnostic "last writer" trackers ----
	/** Name of the last system that wrote Camera fields. */
	public static String lastCameraWriter = "none";
	/** Name of the last system that wrote self.anInt3400. */
	public static String lastBodyYawWriter = "none";
	/** Name of the last movement rebase reason. */
	public static String lastMovementRebaseReason = "none";

	/** Movement update tick counter (incremented by ModernMovementController). */
	public static int movementUpdateTickCount;

	private DebugOverlay() {
	}

	/**
	 * Called from {@link CameraMode#onKeyPressed(int)} at the AWT boundary.
	 * F12 is edge-triggered: toggles once per physical press, no repeat.
	 */
	public static void onKeyPressed(int keyCode) {
		if (keyCode == KEY_F12) {
			visible = !visible;
		}
	}

	/**
	 * Returns whether the overlay is currently visible.
	 */
	public static boolean isVisible() {
		return visible;
	}

	/**
	 * Draws the debug overlay on the canvas. Called from the render pipeline
	 * after the game scene has been drawn.
	 *
	 * <p>Uses AWT Graphics2D with a monospaced font for readability.
	 * Draws a semi-transparent background behind the text.</p>
	 */
	public static void draw() {
		if (!visible) return;
		if (GameShell.canvas == null) return;

		Graphics2D g;
		try {
			g = (Graphics2D) GameShell.canvas.getGraphics();
			if (g == null) return;
		} catch (Exception e) {
			return;
		}

		try {
			g.setFont(new Font("Monospaced", Font.PLAIN, 11));
			g.setColor(new Color(0, 0, 0, 180));

			Player self = PlayerList.self;
			int px = (self != null) ? self.xFine : 0;
			int pz = (self != null) ? self.zFine : 0;
			int tileX = px >> 7;
			int tileZ = pz >> 7;
			int plane = Player.plane;

			String profile = CameraMode.isModern() ? "MODERN" : "ORIGINAL";
			String rig = "N/A";
			if (CameraMode.isModern() && ModernCameraRig.isActive()) {
				rig = ModernCameraRig.getRigState().name();
			}

			// Build overlay text
			String[] lines = new String[]{
					// CONTROL
					"=== CONTROL ===",
					"profile=" + profile,
					"rig=" + rig,
					"cameraType=" + Camera.cameraType,
					"",
					// PLAYER
					"=== PLAYER ===",
					"tile=(" + tileX + "," + tileZ + ",p" + plane + ")",
					"fine=(" + px + "," + pz + ")",
					"serverTile=(" + ModernMovementController.getLastServerTileX()
							+ "," + ModernMovementController.getLastServerTileZ() + ")",
					"pending=" + ModernMovementController.getPendingCount(),
					"moveUpdates=" + movementUpdateTickCount,
					"",
					// INPUT
					"=== INPUT ===",
					"W=" + bool(Keyboard.pressedKeys[33])
							+ " A=" + bool(Keyboard.pressedKeys[48])
							+ " S=" + bool(Keyboard.pressedKeys[49])
							+ " D=" + bool(Keyboard.pressedKeys[50]),
					"shift=" + bool(Keyboard.pressedKeys[81]),
					"chat=" + bool(ModernControlController.isChatInputActive()),
					"gameplay=" + bool(ModernControlController.isGameplayInputAllowed()),
					"",
					// CAMERA
					"=== CAMERA ===",
					"pos=(" + Camera.renderX + "," + Camera.anInt40 + "," + Camera.renderZ + ")",
					"yaw=" + Camera.cameraYaw + " pitch=" + Camera.cameraPitch,
					"yawTarget=" + (int) Camera.yawTarget + " pitchTarget=" + (int) Camera.pitchTarget,
					"desired=" + ModernCameraRig.getDesiredDistance()
							+ " safe=" + ModernCameraRig.getSafeDistance()
							+ " actual=" + ModernCameraRig.getActualDistance(),
					"wheelRot=" + MouseWheel.wheelRotation,
					"ZOOM=" + Camera.ZOOM,
					"",
					// BODY
					"=== BODY ===",
					"anInt3400(target)=" + (self != null ? self.anInt3400 : -1),
					"anInt3381(visual)=" + (self != null ? self.anInt3381 : -1),
					"anInt3385(counter)=" + (self != null ? self.anInt3385 : -1),
					"locomotionYaw=" + ModernMovementController.getTargetOrientationAngle(),
					"bodyYaw(rig)=" + ModernCameraRig.getBodyYaw(),
					"fpCamYaw=" + FirstPersonCamera.getYaw(),
					"",
					// SCENE
					"=== SCENE ===",
					"roofMode=" + ScriptRunner.method4047(),
					"allLevels=" + bool(SceneGraph.allLevelsAreVisible()),
					"fpStructOverride=" + bool(FirstPersonCamera.isActive()),
					"fpValidPos=" + bool(FirstPersonCamera.hasValidPosition()),
					"",
					// DIAGNOSTIC
					"=== DIAGNOSTIC ===",
					"lastCamWriter=" + lastCameraWriter,
					"lastBodyYawWriter=" + lastBodyYawWriter,
					"lastRebaseReason=" + lastMovementRebaseReason,
			};

			// Measure and draw
			int lineHeight = 13;
			int maxWidth = 0;
			for (String line : lines) {
				int w = g.getFontMetrics().stringWidth(line);
				if (w > maxWidth) maxWidth = w;
			}

			int overlayX = 4;
			int overlayY = 4;
			int overlayW = maxWidth + 12;
			int overlayH = lines.length * lineHeight + 8;

			// Semi-transparent background
			g.setColor(new Color(0, 0, 0, 180));
			g.fillRect(overlayX, overlayY, overlayW, overlayH);
			g.setColor(new Color(80, 80, 80));
			g.drawRect(overlayX, overlayY, overlayW, overlayH);

			// Text
			g.setColor(new Color(0, 255, 128));
			int textY = overlayY + 12;
			for (String line : lines) {
				if (line.startsWith("===")) {
					g.setColor(new Color(255, 255, 100));
				} else {
					g.setColor(new Color(0, 255, 128));
				}
				g.drawString(line, overlayX + 6, textY);
				textY += lineHeight;
			}
		} finally {
			g.dispose();
		}
	}

	private static String bool(boolean v) {
		return v ? "Y" : "N";
	}
}
