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

	// ---- Round #6A (P2/P5): CHASE movement + rig diagnostics ----
	/** Last movement intent forward component ×100 (percent). */
	public static int intentForwardPct;
	/** Last movement intent right component ×100 (percent). */
	public static int intentRightPct;
	/** Last stable movement-space heading (camera convention). */
	public static int movementHeading;
	/** Number of rig state flips this session (flicker detector). */
	public static int rigTransitionCount;

	// ---- Round P4B: F11 EXIT (MODERN → ORIGINAL) resync diagnostics ----
	/** self.xFine/zFine captured before the exit resync. */
	public static int f11ExitBeforeFineX;
	public static int f11ExitBeforeFineZ;
	/** Authoritative tile resolved at exit (last server-confirmed). */
	public static int f11ExitAuthTileX = -1;
	public static int f11ExitAuthTileZ = -1;
	/** movementQueueX[0]/Z[0] before and after the resync. */
	public static int f11ExitQueue0BeforeX;
	public static int f11ExitQueue0BeforeZ;
	public static int f11ExitQueue0AfterX;
	public static int f11ExitQueue0AfterZ;
	/** self.xFine/zFine after the resync. */
	public static int f11ExitAfterFineX;
	public static int f11ExitAfterFineZ;
	/** Last server-confirmed tile at exit time. */
	public static int f11ExitServerTileX = -1;
	public static int f11ExitServerTileZ = -1;
	/** Last DDA walk target sent to the server at exit time. */
	public static int f11ExitLastSentTileX = -1;
	public static int f11ExitLastSentTileZ = -1;
	/** Pending modern walk requests at exit time. */
	public static int f11ExitPendingMoves;
	/** Whether a vanilla collision/pathfinding refresh was invoked. */
	public static boolean f11ExitCollisionRefresh;
	/** Which vanilla refresh route ran (VANILLA_TELEPORT_RESET / VANILLA_FREE_NOOP / none). */
	public static String f11ExitCollisionRefreshRoute = "none";

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

		// Round #7 P8: refresh player-tile ceiling diagnostics for the
		// CEILING block below (cheap, only runs while the overlay is visible).
		ModernCeiling.updateDiagnostics();

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
				// Round #6B/C P14: ownership + FP UI-cursor substate
				lbl("movementOwner ", ModernMovementController.getMovementOwner()),
				lbl("cameraOwner ", lastCameraWriter),
				JagString.concatenate(new JagString[]{JagString.parse("ctrlUICursor "), bool(FirstPersonCamera.isUiCursorActive()), JagString.parse(" cursorLocked "), bool(FirstPersonCamera.isCursorLocked())}),
				hdr("PLAYER"),
				JagString.concatenate(new JagString[]{JagString.parse("tile x:"), JagString.parseInt(tileX), JagString.parse(" z:"), JagString.parseInt(tileZ), JagString.parse(" p:"), JagString.parseInt(plane)}),
				JagString.concatenate(new JagString[]{JagString.parse("fine x:"), JagString.parseInt(px), JagString.parse(" z:"), JagString.parseInt(pz)}),
				JagString.concatenate(new JagString[]{JagString.parse("serverTile x:"), JagString.parseInt(ModernMovementController.getLastServerTileX()), JagString.parse(" z:"), JagString.parseInt(ModernMovementController.getLastServerTileZ())}),
				lbl("pending ", ModernMovementController.getPendingCount()),
				lbl("moveUpdates ", movementUpdateTickCount),
				hdr("INPUT"),
				JagString.concatenate(new JagString[]{JagString.parse("W"), bool(Keyboard.pressedKeys[33]), JagString.parse(" A"), bool(Keyboard.pressedKeys[48]), JagString.parse(" S"), bool(Keyboard.pressedKeys[49]), JagString.parse(" D"), bool(Keyboard.pressedKeys[50]), JagString.parse(" sh"), bool(Keyboard.pressedKeys[81])}),
				JagString.concatenate(new JagString[]{JagString.parse("intentF "), JagString.parseInt(intentForwardPct), JagString.parse(" intentR "), JagString.parseInt(intentRightPct), JagString.parse(" heading "), JagString.parseInt(movementHeading)}),
				JagString.concatenate(new JagString[]{JagString.parse("chaseYawT "), JagString.parseInt(ModernCameraRig.getChaseYawTarget()), JagString.parse(" rigFlips "), JagString.parseInt(rigTransitionCount)}),
				JagString.concatenate(new JagString[]{JagString.parse("chat "), bool(ModernControlController.isChatInputActive()), JagString.parse(" gameplay "), bool(ModernControlController.isGameplayInputAllowed())}),
				// Round #7 P1/P8: keyboard ownership (MODERN_GAMEPLAY / VANILLA_FREE / ORIGINAL)
				lbl("keyboardOwner ", ModernControlController.getKeyboardOwnerName()),
				lbl("chatForwardMode ", ModernControlController.isModernGameplayKeyboardOwner() ? "MODERN_FILTER" : "VANILLA"),
				hdr("DIALOGUE/TARGET"),
				// Round #6B/C P14: dialogue authority + FP crosshair target
				JagString.concatenate(new JagString[]{JagString.parse("dlgActive "), bool(ModernDialogueKeyboard.hasActiveDialogue()), JagString.parse(" choices "), JagString.parseInt(ModernDialogueKeyboard.getDialogueChoiceCount()), JagString.parse(" modal "), bool(Cs1ScriptRunner.aBoolean108)}),
				JagString.concatenate(new JagString[]{JagString.parse("worldBlockedByDlg "), bool(ModernDialogueKeyboard.hasActiveDialogue() && ModernCameraRig.isFirstPersonRigState())}),
				// Round #7 P3/P8: dialogue number-key route diagnostics
				JagString.concatenate(new JagString[]{JagString.parse("lastNumberKey "), JagString.parseInt(ModernDialogueKeyboard.getLastNumberKey()), JagString.parse(" lastChoiceComp "), JagString.parseInt(ModernDialogueKeyboard.getLastChoiceComponent())}),
				lbl("lastChoiceRoute ", ModernDialogueKeyboard.getLastChoiceRoute()),
				// Round #7C P5: numeric route stays AWAITING until a real
				// [DIALOGUE-CLICK-TRACE] manual mouse click is captured.
				lbl("numericRoute ", ModernDialogueKeyboard.getNumericRouteStatus()),
				// Round #7D P1: structural choice-family detection diagnostics
				JagString.concatenate(new JagString[]{JagString.parse("choiceIface "), JagString.parseInt(ModernDialogueKeyboard.getChoiceInterfaceId()), JagString.parse(" choiceCount "), JagString.parseInt(ModernDialogueKeyboard.getDialogueChoiceCount())}),
				JagString.concatenate(new JagString[]{JagString.parse("ch1 "), JagString.parseInt(ModernDialogueKeyboard.getChoiceChild(1)), JagString.parse(" ch2 "), JagString.parseInt(ModernDialogueKeyboard.getChoiceChild(2)), JagString.parse(" ch3 "), JagString.parseInt(ModernDialogueKeyboard.getChoiceChild(3)), JagString.parse(" ch4 "), JagString.parseInt(ModernDialogueKeyboard.getChoiceChild(4)), JagString.parse(" ch5 "), JagString.parseInt(ModernDialogueKeyboard.getChoiceChild(5))}),
				JagString.concatenate(new JagString[]{JagString.parse("lastChoiceKey "), JagString.parseInt(ModernDialogueKeyboard.getLastNumberKey()), JagString.parse(" lastActionCode "), JagString.parseInt(ModernDialogueKeyboard.getLastChoiceActionCode())}),
				lbl("targetType ", ModernActionOverlay.getTargetType()),
				lbl("targetName ", ModernActionOverlay.getTargetName()),
				lbl("targetDist ", ModernActionOverlay.getTargetDistance()),
				lbl("action1 ", ModernActionOverlay.getActionOp(0)),
				lbl("action2 ", ModernActionOverlay.getActionOp(1)),
				lbl("action3 ", ModernActionOverlay.getActionOp(2)),
				hdr("NPC TARGET"),
				// Round #7 P2/P8: NPC crosshair pick pipeline diagnostics
				JagString.concatenate(new JagString[]{JagString.parse("npcPickSeen "), bool(ModernActionOverlay.diagNpcPickTagSeen), JagString.parse(" npcMenuEntries "), JagString.parseInt(ModernActionOverlay.diagNpcMenuEntries), JagString.parse(" accepted "), bool(ModernActionOverlay.diagOverlayAccepted)}),
				lbl("npcUnderXhair ", ModernActionOverlay.diagNpcUnderCrosshair),
				lbl("firstNpcAction ", ModernActionOverlay.diagFirstNpcAction),
				lbl("rejectReason ", ModernActionOverlay.diagRejectReason.isEmpty() ? "-" : ModernActionOverlay.diagRejectReason),
				// Round #7C P4: independent NPC boundary proof
				JagString.concatenate(new JagString[]{JagString.parse("npcIndex "), JagString.parseInt(ModernActionOverlay.diagNpcIndex), JagString.parse(" npcExists "), bool(ModernActionOverlay.diagNpcExists), JagString.parse(" liveTile "), JagString.parseInt(ModernActionOverlay.diagNpcLiveX), JagString.parse(","), JagString.parseInt(ModernActionOverlay.diagNpcLiveZ)}),
				JagString.concatenate(new JagString[]{JagString.parse("playerTile "), JagString.parseInt(tileX), JagString.parse(","), JagString.parseInt(tileZ), JagString.parse(" distance "), JagString.parseInt(ModernActionOverlay.getTargetDistance())}),
				// Round #7D P2: NPC pick-chain boundary trace (LOC vs NPC)
				JagString.concatenate(new JagString[]{JagString.parse("npcRendered "), JagString.parseInt(ModernActionOverlay.diagNpcRenderedLast), JagString.parse(" attempts "), JagString.parseInt(ModernActionOverlay.diagNpcPickAttemptsLast), JagString.parse(" boundsHit "), JagString.parseInt(ModernActionOverlay.diagNpcBoundsHitsLast)}),
				JagString.concatenate(new JagString[]{JagString.parse("candNpc "), JagString.parseInt(ModernActionOverlay.diagNpcCandidateIndex), JagString.parse(" pickable "), bool(ModernActionOverlay.diagNpcLastPickable), JagString.parse(" mmPick "), bool(ModernActionOverlay.diagNpcLastMiniMenuPick)}),
				JagString.concatenate(new JagString[]{JagString.parse("allowInput "), bool(ModernActionOverlay.diagAllowInput), JagString.parse(" tagWritten "), JagString.parseInt(ModernActionOverlay.diagNpcTagsWrittenLast), JagString.parse(" boundary "), JagString.parse(ModernActionOverlay.diagNpcRejectBoundary.isEmpty() ? "-" : ModernActionOverlay.diagNpcRejectBoundary)}),
				hdr("WORLD OVERLAY"),
				// Round #7C P2: exact blocking boundary of the FP world overlay
				JagString.concatenate(new JagString[]{JagString.parse("overlayGate "), bool(ModernActionOverlay.isGateActive()), JagString.parse(" blockedReason "), JagString.parse(ModernActionOverlay.getBlockedReason().isEmpty() ? "-" : ModernActionOverlay.getBlockedReason())}),
				// Round #7D P3: visual camera mode vs semantic rig state vs FP gate
				JagString.concatenate(new JagString[]{JagString.parse("visualMode "), JagString.parse(ModernCameraRig.getVisualMode()), JagString.parse(" rigState "), JagString.parse(rig), JagString.parse(" overlayFpGate "), bool(ModernCameraRig.isFirstPersonRigState())}),
				JagString.concatenate(new JagString[]{JagString.parse("dialogueBlock "), bool(ModernDialogueKeyboard.hasActiveDialogue()), JagString.parse(" dialogueActive "), bool(ModernDialogueKeyboard.hasActiveDialogue()), JagString.parse(" choiceCount "), JagString.parseInt(ModernDialogueKeyboard.getDialogueChoiceCount())}),
				JagString.concatenate(new JagString[]{JagString.parse("menuSize "), JagString.parseInt(MiniMenu.size), JagString.parse(" scenePickTags "), JagString.parseInt(MiniMenu.anInt7), JagString.parse(" worldEntries "), JagString.parseInt(ModernActionOverlay.countWorldEntries())}),
				JagString.concatenate(new JagString[]{JagString.parse("locEntries "), JagString.parseInt(ModernActionOverlay.countLocEntries()), JagString.parse(" npcEntries "), JagString.parseInt(ModernActionOverlay.countNpcEntries()), JagString.parse(" accepted "), bool(ModernActionOverlay.isSnapshotValid())}),
				JagString.concatenate(new JagString[]{JagString.parse("targetType "), JagString.parse(ModernActionOverlay.getTargetType()), JagString.parse(" targetName "), JagString.parse(ModernActionOverlay.getTargetName().isEmpty() ? "-" : ModernActionOverlay.getTargetName())}),
				hdr("FP CONTEXT MENU"),
				// Round #8 P11: FP vanilla context menu diagnostics
				JagString.concatenate(new JagString[]{JagString.parse("menuOpen "), bool(FPContextMenuController.isMenuOpen()), JagString.parse(" selectedIdx "), JagString.parseInt(FPContextMenuController.getSelectedIndex()), JagString.parse(" wheelConsumed "), bool(FPContextMenuController.wasWheelConsumed())}),
				lbl("selectedOp ", FPContextMenuController.getSelectedOp()),
				lbl("selectedTarget ", FPContextMenuController.getSelectedTarget().isEmpty() ? "-" : FPContextMenuController.getSelectedTarget()),
				hdr("CEILING"),
				// Round #7C P1: quarantine visibility — rendererEnabled must be N
				// and trianglesSubmitted 0 while the pass is disabled.
				JagString.concatenate(new JagString[]{JagString.parse("rendererEnabled "), bool(ModernCeiling.RENDER_ENABLED), JagString.parse(" trianglesSubmitted "), JagString.parseInt(ModernCeiling.getQuadsDrawn())}),
				// Round #7 P6/P8: FP ceiling underside diagnostics
				JagString.concatenate(new JagString[]{JagString.parse("sourceMode "), JagString.parse(ModernCeiling.diagSourceMode), JagString.parse(" overheadPlane "), JagString.parseInt(ModernCeiling.diagOverheadPlane), JagString.parse(" quadsDrawn "), JagString.parseInt(ModernCeiling.getQuadsDrawn())}),
				JagString.concatenate(new JagString[]{JagString.parse("overheadTile "), bool(ModernCeiling.diagOverheadTilePresent), JagString.parse(" underRoofFlag "), bool(ModernCeiling.diagUnderRoofFlag), JagString.parse(" textureId "), JagString.parseInt(ModernCeiling.diagTextureId)}),
				// Round #7B P1: coverage pass counters
				JagString.concatenate(new JagString[]{JagString.parse("candidateTiles "), JagString.parseInt(ModernCeiling.diagCandidateTiles), JagString.parse(" drawnTiles "), JagString.parseInt(ModernCeiling.diagDrawnTiles), JagString.parse(" nearRejected "), JagString.parseInt(ModernCeiling.diagNearRejectedTiles)}),
				JagString.concatenate(new JagString[]{JagString.parse("behindVerts "), JagString.parseInt(ModernCeiling.diagBehindCameraVertices), JagString.parse(" plainTiles "), JagString.parseInt(ModernCeiling.diagPlainTiles), JagString.parse(" shapedTiles "), JagString.parseInt(ModernCeiling.diagShapedTiles)}),
				hdr("MOVEMENT"),
				JagString.concatenate(new JagString[]{JagString.parse("velX "), JagString.parseInt(ModernMovementController.getVelocityXQ16()), JagString.parse(" velZ "), JagString.parseInt(ModernMovementController.getVelocityZQ16()), JagString.parse(" run "), bool(Keyboard.pressedKeys[81])}),
				JagString.concatenate(new JagString[]{JagString.parse("queueSize "), JagString.parseInt(self != null ? self.movementQueueSize : 0), JagString.parse(" moveSeq "), JagString.parseInt(self != null ? self.movementSeqId : -1)}),
				JagString.concatenate(new JagString[]{JagString.parse("predictedTile x:"), JagString.parseInt((int) (ModernMovementController.getPredictedSubX() >> 16) >> 7), JagString.parse(" z:"), JagString.parseInt((int) (ModernMovementController.getPredictedSubZ() >> 16) >> 7)}),
				hdr("F11 EXIT"),
				// Round P4B: last MODERN → ORIGINAL resync snapshot
				JagString.concatenate(new JagString[]{JagString.parse("beforeFine x:"), JagString.parseInt(f11ExitBeforeFineX), JagString.parse(" z:"), JagString.parseInt(f11ExitBeforeFineZ), JagString.parse(" afterFine x:"), JagString.parseInt(f11ExitAfterFineX), JagString.parse(" z:"), JagString.parseInt(f11ExitAfterFineZ)}),
				JagString.concatenate(new JagString[]{JagString.parse("authTile x:"), JagString.parseInt(f11ExitAuthTileX), JagString.parse(" z:"), JagString.parseInt(f11ExitAuthTileZ), JagString.parse(" serverTile x:"), JagString.parseInt(f11ExitServerTileX), JagString.parse(" z:"), JagString.parseInt(f11ExitServerTileZ)}),
				JagString.concatenate(new JagString[]{JagString.parse("queue0Before x:"), JagString.parseInt(f11ExitQueue0BeforeX), JagString.parse(" z:"), JagString.parseInt(f11ExitQueue0BeforeZ), JagString.parse(" after x:"), JagString.parseInt(f11ExitQueue0AfterX), JagString.parse(" z:"), JagString.parseInt(f11ExitQueue0AfterZ)}),
				JagString.concatenate(new JagString[]{JagString.parse("lastSent x:"), JagString.parseInt(f11ExitLastSentTileX), JagString.parse(" z:"), JagString.parseInt(f11ExitLastSentTileZ), JagString.parse(" pendingMoves "), JagString.parseInt(f11ExitPendingMoves)}),
				JagString.concatenate(new JagString[]{JagString.parse("collisionRefresh "), bool(f11ExitCollisionRefresh), JagString.parse(" route "), JagString.parse(f11ExitCollisionRefreshRoute)}),
				hdr("CAMERA"),
				JagString.concatenate(new JagString[]{JagString.parse("pos x:"), JagString.parseInt(Camera.renderX), JagString.parse(" y:"), JagString.parseInt(Camera.anInt40), JagString.parse(" z:"), JagString.parseInt(Camera.renderZ)}),
				JagString.concatenate(new JagString[]{JagString.parse("yaw "), JagString.parseInt(Camera.cameraYaw), JagString.parse(" pitch "), JagString.parseInt(Camera.cameraPitch)}),
				JagString.concatenate(new JagString[]{JagString.parse("yawT "), JagString.parseInt((int) Camera.yawTarget), JagString.parse(" pitchT "), JagString.parseInt((int) Camera.pitchTarget)}),
				JagString.concatenate(new JagString[]{JagString.parse("des "), JagString.parseInt(ModernCameraRig.getDesiredDistance()), JagString.parse(" safe "), JagString.parseInt(ModernCameraRig.getSafeDistance()), JagString.parse(" act "), JagString.parseInt(ModernCameraRig.getActualDistance())}),
				JagString.concatenate(new JagString[]{JagString.parse("visDist "), JagString.parseInt(ModernCameraRig.getVisualDistance()), JagString.parse(" obstructed "), bool(ModernCameraRig.isObstructionLimited())}),
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
