package rt4;

/**
 * F12-toggled developer debug overlay (Phase 3C, round 4).
 *
 * <p>Displays compact on-screen diagnostics for camera, movement, input,
 * and scene state. Rendered INSIDE the RT4 render pipeline using the
 * existing dual-rasterizer (GlRaster / SoftwareRaster) + Fonts text system.
 * This avoids the previous crash caused by opening a separate AWT
 * Graphics2D context on the canvas after the GL buffer swap.</p>
 *
 * <h2>Design Goals</h2>
 * <ul>
 *   <li>F12 is edge-triggered (no repeat while held).</li>
 *   <li>Overlay does NOT steal gameplay input.</li>
 *   <li>Compact enough to read while moving.</li>
 *   <li>Debug-only: zero overhead when hidden.</li>
 *   <li>Works in both HD/OpenGL and SD/software renderers.</li>
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

	// ---- Temporary ORIGINAL-mode zoom diagnostics (P4) ----
	/** Whether a wheel event reached the legacy zoom path this tick. */
	public static boolean legacyZoomInputSeen;
	/** pitchTarget value before the legacy wheel step. */
	public static int legacyZoomBefore;
	/** pitchTarget value after the legacy wheel step. */
	public static int legacyZoomAfter;

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
	 * Draws the debug overlay INSIDE the RT4 render pipeline using the
	 * dual-rasterizer + Fonts text system. Must be called from the in-game
	 * render pass (gameState 30) BEFORE the framebuffer is presented, so the
	 * rasterizer surface is the active draw target.
	 *
	 * <p>Uses the same pattern as {@link ModernCrosshair} and the FPS text in
	 * {@link Cs1ScriptRunner}: GlRaster / SoftwareRaster for background and
	 * {@code Fonts.p11Full.renderLeft} for text.</p>
	 */
	public static void draw() {
		if (!visible) return;
		if (Fonts.p11Full == null) return;

		Player self = PlayerList.self;
		int px = (self != null) ? self.xFine : 0;
		int pz = (self != null) ? self.zFine : 0;
		int tileX = px >> 7;
		int tileZ = pz >> 7;
		int plane = Player.plane;

		String profile = CameraMode.isModern() ? "MODERN" : "ORIGINAL";
		String rig = "NA";
		if (CameraMode.isModern() && ModernCameraRig.isActive()) {
			rig = ModernCameraRig.getRigState().name();
		}

		JagString[] lines = new JagString[]{
				hdr("CONTROL"),
				lbl("profile ", profile),
				lbl("rig ", rig),
				lbl("cameraType ", Camera.cameraType),
				hdr("PLAYER"),
				JagString.concatenate(new JagString[]{JagString.parse("tile x:"), JagString.parseInt(tileX), JagString.parse(" z:"), JagString.parseInt(tileZ), JagString.parse(" p:"), JagString.parseInt(plane)}),
				JagString.concatenate(new JagString[]{JagString.parse("fine x:"), JagString.parseInt(px), JagString.parse(" z:"), JagString.parseInt(pz)}),
				JagString.concatenate(new JagString[]{JagString.parse("serverTile x:"), JagString.parseInt(ModernMovementController.getLastServerTileX()), JagString.parse(" z:"), JagString.parseInt(ModernMovementController.getLastServerTileZ())}),
				lbl("pending ", ModernMovementController.getPendingCount()),
				lbl("moveUpdates ", movementUpdateTickCount),
				hdr("INPUT"),
				JagString.concatenate(new JagString[]{JagString.parse("W"), bool(Keyboard.pressedKeys[33]), JagString.parse(" A"), bool(Keyboard.pressedKeys[48]), JagString.parse(" S"), bool(Keyboard.pressedKeys[49]), JagString.parse(" D"), bool(Keyboard.pressedKeys[50]), JagString.parse(" sh"), bool(Keyboard.pressedKeys[81])}),
				JagString.concatenate(new JagString[]{JagString.parse("chat "), bool(ModernControlController.isChatInputActive()), JagString.parse(" gameplay "), bool(ModernControlController.isGameplayInputAllowed())}),
				hdr("CAMERA"),
				JagString.concatenate(new JagString[]{JagString.parse("pos x:"), JagString.parseInt(Camera.renderX), JagString.parse(" y:"), JagString.parseInt(Camera.anInt40), JagString.parse(" z:"), JagString.parseInt(Camera.renderZ)}),
				JagString.concatenate(new JagString[]{JagString.parse("yaw "), JagString.parseInt(Camera.cameraYaw), JagString.parse(" pitch "), JagString.parseInt(Camera.cameraPitch)}),
				JagString.concatenate(new JagString[]{JagString.parse("yawT "), JagString.parseInt((int) Camera.yawTarget), JagString.parse(" pitchT "), JagString.parseInt((int) Camera.pitchTarget)}),
				JagString.concatenate(new JagString[]{JagString.parse("des "), JagString.parseInt(ModernCameraRig.getDesiredDistance()), JagString.parse(" safe "), JagString.parseInt(ModernCameraRig.getSafeDistance()), JagString.parse(" act "), JagString.parseInt(ModernCameraRig.getActualDistance())}),
				JagString.concatenate(new JagString[]{JagString.parse("visYaw "), JagString.parseInt(ModernCameraRig.getVisualYaw()), JagString.parse(" fp "), bool(ModernCameraRig.isFirstPersonRigState())}),
				JagString.concatenate(new JagString[]{JagString.parse("wheel "), JagString.parseInt(MouseWheel.wheelRotation), JagString.parse(" ZOOM "), JagString.parseInt(Camera.ZOOM)}),
				hdr("ZOOM DIAG"),
				JagString.concatenate(new JagString[]{JagString.parse("seen "), bool(legacyZoomInputSeen), JagString.parse(" before "), JagString.parseInt(legacyZoomBefore), JagString.parse(" after "), JagString.parseInt(legacyZoomAfter)}),
				hdr("BODY"),
				JagString.concatenate(new JagString[]{JagString.parse("target "), JagString.parseInt(self != null ? self.anInt3400 : 0), JagString.parse(" visual "), JagString.parseInt(self != null ? self.anInt3381 : 0)}),
				JagString.concatenate(new JagString[]{JagString.parse("locoYaw "), JagString.parseInt(ModernMovementController.getTargetOrientationAngle()), JagString.parse(" rigYaw "), JagString.parseInt(ModernCameraRig.getBodyYaw())}),
				lbl("fpCamYaw ", FirstPersonCamera.getYaw()),
				hdr("SCENE"),
				JagString.concatenate(new JagString[]{JagString.parse("roofMode "), JagString.parseInt(ScriptRunner.method4047()), JagString.parse(" allLvl "), bool(SceneGraph.allLevelsAreVisible())}),
				JagString.concatenate(new JagString[]{JagString.parse("fpStruct "), bool(FirstPersonCamera.isActive()), JagString.parse(" fpValid "), bool(FirstPersonCamera.hasValidPosition())}),
				hdr("DIAGNOSTIC"),
				lbl("lastCamWriter ", lastCameraWriter),
				lbl("lastBodyYawWriter ", lastBodyYawWriter),
				lbl("lastRebase ", lastMovementRebaseReason),
		};

		// Measure widest line
		int maxWidth = 0;
		for (JagString line : lines) {
			int w = Fonts.p11Full.getStringWidth(line);
			if (w > maxWidth) maxWidth = w;
		}

		int lineHeight = 12;
		int x = 4;
		int y = 4;
		int boxW = maxWidth + 12;
		int boxH = lines.length * lineHeight + 8;

		// Set clip to the full canvas so the overlay is never clipped by a
		// lingering interface clip region, then draw background + border.
		if (GlRenderer.enabled) {
			GlRaster.setClip(0, 0, GlRenderer.canvasWidth, GlRenderer.canvasHeight);
			GlRaster.fillRectAlpha(x, y, boxW, boxH, 0x000000, 170);
			GlRaster.drawRect(x, y, boxW, boxH, 0x555555);
		} else {
			SoftwareRaster.setClip(0, 0, SoftwareRaster.width, SoftwareRaster.height);
			SoftwareRaster.fillRectAlpha(x, y, boxW, boxH, 0x000000, 170);
			SoftwareRaster.drawRect(x, y, boxW, boxH, 0x555555);
		}

		// Draw text lines
		int textY = y + 10;
		for (JagString line : lines) {
			if (isHeader(line)) {
				Fonts.p11Full.renderLeft(line, x + 6, textY, 0xFFFF00, 0);
			} else {
				Fonts.p11Full.renderLeft(line, x + 6, textY, 0x00FF80, 0);
			}
			textY += lineHeight;
		}
	}

	/** Builds a section header line. */
	private static JagString hdr(String name) {
		return JagString.parse("== " + name + " ==");
	}

	/** Returns whether a line is a section header (starts with "=="). */
	private static boolean isHeader(JagString line) {
		return line.length >= 2 && line.chars[0] == '=' && line.chars[1] == '=';
	}

	/** Builds a label + integer value line. */
	private static JagString lbl(String label, int value) {
		return JagString.concatenate(new JagString[]{JagString.parse(label), JagString.parseInt(value)});
	}

	/** Builds a label + string value line. */
	private static JagString lbl(String label, String value) {
		return JagString.concatenate(new JagString[]{JagString.parse(label), JagString.parse(value)});
	}

	/** Returns "Y" or "N" as a JagString. */
	private static JagString bool(boolean v) {
		return JagString.parse(v ? "Y" : "N");
	}
}
