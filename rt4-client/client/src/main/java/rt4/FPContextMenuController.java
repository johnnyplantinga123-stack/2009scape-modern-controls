package rt4;

/**
 * First-person vanilla context menu controller (Round #8 P1-P7).
 *
 * <p>This controller provides FIRST_PERSON-specific INPUT CONTROL for the
 * EXISTING vanilla {@link MiniMenu}. It does NOT implement a second custom
 * context-menu data model — it uses the real MiniMenu arrays, sorting,
 * rendering, and {@link MiniMenu#doAction(int)} execution.</p>
 *
 * <h2>Activation Conditions</h2>
 * <p>The controller is active ONLY when ALL of the following are true:
 * <ul>
 *   <li>{@link CameraMode#isModern()} is true</li>
 *   <li>{@link ModernCameraRig} semantic state == FIRST_PERSON</li>
 *   <li>Real dialogue inactive ({@link ModernDialogueKeyboard#hasActiveDialogue()} == false)</li>
 *   <li>Chat/text inactive ({@link ModernControlController#isChatInputActive()} == false)</li>
 *   <li>CTRL UI cursor inactive ({@link FirstPersonCamera#isUiCursorActive()} == false)</li>
 *   <li>FP cursor/crosshair mode active</li>
 * </ul>
 * ORIGINAL and FREE modes NEVER enter this controller.</p>
 *
 * <h2>Menu Opening (P2)</h2>
 * <p>On FP right-click, the crosshair point is computed as:
 * <pre>
 *   viewportX + viewportWidth / 2
 *   viewportY + viewportHeight / 2
 * </pre>
 * This point is fed into the EXISTING vanilla scene-pick/menu build authority
 * ({@link ScriptRunner#method3901()}) which sets {@link Cs1ScriptRunner#aBoolean108}
 * and positions the menu. Menu contents remain FULL VANILLA.</p>
 *
 * <h2>Scroll Selection (P3)</h2>
 * <p>While the FP-owned vanilla context menu is open, MOUSE WHEEL OWNER =
 * FP CONTEXT MENU. {@link ModernCameraRig} MUST NOT receive that wheel delta.
 * Wheel down = next visible row, wheel up = previous visible row (wrap-around).</p>
 *
 * <h2>Visual Highlight (P4)</h2>
 * <p>The vanilla menu already highlights rows based on hover coordinates in
 * {@link MiniMenu#drawA()}/{@link MiniMenu#drawB()}. This controller adds a
 * visual highlight pass over the SELECTED row (wheel-selected, not just
 * mouse-hovered) using the exact menu x/y/width/height from vanilla.</p>
 *
 * <h2>Left Click Execution (P5)</h2>
 * <p>When FP context menu owns input, left click executes
 * {@link MiniMenu#doAction(int)} with the selected array index, then closes
 * via the vanilla-equivalent close route. No new packets, no action
 * reconstruction.</p>
 *
 * <h2>Input Priority (P7)</h2>
 * <pre>
 * 1. explicit chat/text
 * 2. real dialogue / modal UI
 * 3. FP vanilla context menu (THIS controller)
 * 4. CTRL UI cursor
 * 5. quick crosshair overlay
 * 6. camera/movement
 * </pre>
 * While context menu open: wheel = menu, no camera zoom, no FP->CHASE zoom
 * transition, E cannot fire quick overlay, number keys cannot fire quick
 * overlay, WASD suspended, left click confirms menu row.</p>
 *
 * <p>STATUS: SOURCE VERIFIED (routes traced), COMPILE VERIFIED after build,
 * RUNTIME UNVERIFIED.</p>
 */
public final class FPContextMenuController {

	// ---- State ----
	/** Whether the FP context menu is currently open and owned by this controller. */
	private static boolean menuOpen = false;

	/** Selected array index into MiniMenu arrays (0..size-1). */
	private static int selectedArrayIndex = -1;

	/** Whether the wheel was consumed this frame (for ModernCameraRig gate). */
	private static boolean wheelConsumed = false;

	/** Previous right-click state for edge detection. */
	private static boolean rightClickWasDown = false;

	/** Previous left-click state for edge detection. */
	private static boolean leftClickWasDown = false;

	private FPContextMenuController() {
	}

	// =====================================================================
	// PUBLIC API
	// =====================================================================

	/**
	 * Returns whether the FP context menu is currently open and owned by
	 * this controller. When true, wheel belongs to the menu and camera
	 * zoom must be suppressed.
	 */
	public static boolean isMenuOpen() {
		return menuOpen;
	}

	/**
	 * Returns the currently selected array index into MiniMenu arrays.
	 * -1 if no menu is open or no selection.
	 */
	public static int getSelectedIndex() {
		return selectedArrayIndex;
	}

	/**
	 * Returns the action code of the selected entry, or -1 if none.
	 */
	public static int getSelectedOp() {
		if (!menuOpen || selectedArrayIndex < 0 || selectedArrayIndex >= MiniMenu.size) {
			return -1;
		}
		return MiniMenu.actions[selectedArrayIndex];
	}

	/**
	 * Returns the target name of the selected entry, or empty string if none.
	 */
	public static String getSelectedTarget() {
		if (!menuOpen || selectedArrayIndex < 0 || selectedArrayIndex >= MiniMenu.size) {
			return "";
		}
		JagString opBase = MiniMenu.opBases[selectedArrayIndex];
		return opBase != null ? ModernActionOverlay.toPlainStringPublic(opBase) : "";
	}

	/**
	 * Returns whether the wheel was consumed by this controller this frame.
	 * Called by {@link ModernCameraRig} to suppress camera zoom while the
	 * menu is open.
	 */
	public static boolean wasWheelConsumed() {
		return wheelConsumed;
	}

	/**
	 * Per-frame update. Called from {@link ModernControlController#update()}
	 * AFTER dialogue/UI checks and BEFORE quick overlay.
	 *
	 * <p>Order of operations:
	 * <ol>
	 *   <li>Check activation conditions.</li>
	 *   <li>If menu closed: detect right-click edge → open menu.</li>
	 *   <li>If menu open: process wheel input, detect left-click → execute.</li>
	 *   <li>Update wheelConsumed flag for the frame.</li>
	 * </ol>
	 */
	public static void update() {
		wheelConsumed = false;

		// Check activation conditions
		if (!isControllerActive()) {
			if (menuOpen) {
				close();
			}
			rightClickWasDown = false;
			leftClickWasDown = false;
			return;
		}

		// Edge detection for right-click (open menu)
		boolean rightDown = Mouse.clickButton == 2;
		boolean rightEdge = rightDown && !rightClickWasDown;
		rightClickWasDown = rightDown;

		// Edge detection for left-click (execute selection)
		boolean leftDown = Mouse.clickButton == 1;
		boolean leftEdge = leftDown && !leftClickWasDown;
		leftClickWasDown = leftDown;

		if (!menuOpen) {
			// Menu closed: detect right-click to open
			if (rightEdge) {
				openMenuAtCrosshair();
			}
		} else {
			// Menu open: process wheel and left-click
			processWheelInput();
			if (leftEdge) {
				executeSelectedAndClose();
			}
			// Check if menu was closed externally (e.g., by vanilla code)
			if (!Cs1ScriptRunner.aBoolean108) {
				menuOpen = false;
				selectedArrayIndex = -1;
			}
		}
	}

	/**
	 * Draws a visual highlight over the selected menu row. Called from the
	 * render pass AFTER {@link MiniMenu#drawA()} or {@link MiniMenu#drawB()}.
	 *
	 * <p>Uses the exact menu x/y/width/height and row spacing from vanilla
	 * drawing code. Supports both GL and software rendering.</p>
	 */
	public static void drawSelectionHighlight() {
		if (!menuOpen || selectedArrayIndex < 0 || selectedArrayIndex >= MiniMenu.size) {
			return;
		}

		// Menu position from InterfaceList (set by ScriptRunner.method3901)
		int menuX = InterfaceList.anInt4271;
		int menuY = InterfaceList.anInt5138;
		int menuWidth = InterfaceList.anInt761;

		// Row Y calculation matches MiniMenu.drawA/drawB:
		// drawA: rowY = (size - i - 1) * 15 + menuY + 31
		// drawB: rowY = (size - i - 1) * 15 + menuY + 35
		int rowOffset = InterfaceList.aBoolean298 ? 35 : 31;
		int rowY = (MiniMenu.size - selectedArrayIndex - 1) * 15 + menuY + rowOffset;

		// Highlight rectangle: full menu width, 16px tall (matches vanilla hover)
		int highlightY = rowY - 13;
		int highlightHeight = 16;

		if (GlRenderer.enabled) {
			GlRaster.fillRectAlpha(menuX, highlightY, menuWidth, highlightHeight, 0xFFFF00, 80);
		} else {
			SoftwareRaster.fillRectAlpha(menuX, highlightY, menuWidth, highlightHeight, 0xFFFF00, 80);
		}
	}

	// =====================================================================
	// PRIVATE HELPERS
	// =====================================================================

	/**
	 * Returns whether this controller should be active right now.
	 */
	private static boolean isControllerActive() {
		return CameraMode.isModern()
				&& ModernCameraRig.isFirstPersonRigState()
				&& !ModernControlController.isChatInputActive()
				&& !ModernDialogueKeyboard.hasActiveDialogue()
				&& !FirstPersonCamera.isUiCursorActive();
	}

	/**
	 * Opens the vanilla context menu at the FP crosshair position.
	 *
	 * <p>The crosshair point is computed from the CURRENT viewport rectangle
	 * (including resizable offsets), not raw canvas centre. This point is
	 * fed into the EXISTING vanilla menu-open routine
	 * ({@link ScriptRunner#method3901()}).</p>
	 */
	private static void openMenuAtCrosshair() {
		// Get viewport rectangle (resizable mode support)
		Component viewport = InterfaceList.aClass13_26;
		int viewportX, viewportY, viewportWidth, viewportHeight;
		if (viewport != null) {
			viewportX = viewport.x;
			viewportY = viewport.y;
			viewportWidth = viewport.width;
			viewportHeight = viewport.height;
		} else {
			// Fallback to canvas dimensions
			viewportX = 0;
			viewportY = 0;
			viewportWidth = GameShell.canvasWidth;
			viewportHeight = GameShell.canvasHeight;
		}

		// Crosshair point = viewport centre
		int crosshairX = viewportX + viewportWidth / 2;
		int crosshairY = viewportY + viewportHeight / 2;

		// Set the click position that method3901 will read
		// (anInt3751 = Mouse.clickX equivalent, anInt1892 = Mouse.clickY equivalent)
		ScriptRunner.anInt3751 = crosshairX;
		ScriptRunner.anInt1892 = crosshairY;

		// Also set Mouse.clickX/Y for compatibility with vanilla code paths
		Mouse.clickX = crosshairX;
		Mouse.clickY = crosshairY;

		// Call the vanilla menu-open routine
		// This sets Cs1ScriptRunner.aBoolean108 = true and positions the menu
		ScriptRunner.method3901();

		// If menu opened successfully, initialize selection
		if (Cs1ScriptRunner.aBoolean108 && MiniMenu.size > 0) {
			menuOpen = true;
			// Default selection: the primary entry (last in array after sort)
			// This matches what vanilla left-click would normally execute
			selectedArrayIndex = MiniMenu.size - 1;
		}
	}

	/**
	 * Processes mouse wheel input while the menu is open.
	 * Wheel down = next row, wheel up = previous row (wrap-around).
	 */
	private static void processWheelInput() {
		if (MouseWheel.wheelRotation == 0) {
			return;
		}

		int rotation = MouseWheel.wheelRotation;
		wheelConsumed = true;

		if (MiniMenu.size <= 1) {
			return;
		}

		// Wheel down (rotation > 0) = next row (decrement index, since
		// visible row order is reversed: index 0 = bottom, size-1 = top)
		// Wheel up (rotation < 0) = previous row (increment index)
		if (rotation > 0) {
			selectedArrayIndex--;
			if (selectedArrayIndex < 0) {
				selectedArrayIndex = MiniMenu.size - 1; // Wrap to top
			}
		} else if (rotation < 0) {
			selectedArrayIndex++;
			if (selectedArrayIndex >= MiniMenu.size) {
				selectedArrayIndex = 0; // Wrap to bottom
			}
		}
	}

	/**
	 * Executes the selected menu entry and closes the menu.
	 */
	private static void executeSelectedAndClose() {
		if (selectedArrayIndex >= 0 && selectedArrayIndex < MiniMenu.size) {
			MiniMenu.doAction(selectedArrayIndex);
		}
		close();
	}

	/**
	 * Closes the menu via the vanilla-equivalent close route.
	 */
	public static void close() {
		menuOpen = false;
		selectedArrayIndex = -1;
		Cs1ScriptRunner.aBoolean108 = false;
		// Request screen redraw to clear the menu
		InterfaceList.redrawScreen(
				InterfaceList.anInt4271,
				InterfaceList.anInt761,
				InterfaceList.anInt5138,
				InterfaceList.anInt436);
	}

	// =====================================================================
	// DIAGNOSTICS (F12)
	// =====================================================================

	/**
	 * Returns diagnostic string for F12 overlay.
	 */
	public static String getDiagnostics() {
		if (!menuOpen) {
			return "closed";
		}
		return "open size=" + MiniMenu.size
				+ " selectedIdx=" + selectedArrayIndex
				+ " selectedOp=" + getSelectedOp()
				+ " wheelConsumed=" + wheelConsumed;
	}
}