package rt4;

/**
 * Center-screen reticle for MODERN FP/CHASE gameplay.
 *
 * <p>Presentation only — no hitscan, no gameplay effect.
 * The reticle marks the camera forward direction so the player
 * can identify which entity they are targeting.</p>
 *
 * <p>Visibility rules:
 * <ul>
 *   <li>Only in MODERN control profile (ORIGINAL untouched).</li>
 *   <li>Only in FP or CHASE rig state (FREE = overview, no reticle).</li>
 *   <li>Hidden when a modal interface/dialog is open.</li>
 * </ul>
 *
 * <p>Uses the same dual-rasterizer pattern (GlRaster / SoftwareRaster)
 * as the rest of the RT4 render pipeline.</p>
 */
public final class ModernCrosshair {

	/** Crosshair arm length in pixels. */
	private static final int ARM_LENGTH = 4;
	/** Crosshair arm thickness in pixels. */
	private static final int ARM_THICKNESS = 2;
	/** Center dot size in pixels. */
	private static final int DOT_SIZE = 2;
	/** Crosshair color (white). */
	private static final int COLOR = 0xFFFFFF;
	/** Crosshair alpha (0 = invisible, 255 = fully opaque). Semi-transparent. */
	private static final int ALPHA = 180;

	private ModernCrosshair() {
	}

	/**
	 * Draws the center-screen reticle if conditions are met.
	 *
	 * <p>Safe to call every render frame. No-op when:
	 * <ul>
	 *   <li>Not in MODERN control profile</li>
	 *   <li>Rig is in FREE state</li>
	 *   <li>Modal interface/dialog is open</li>
	 *   <li>Viewport component is unavailable</li>
	 * </ul>
	 */
	public static void draw() {
		// Only in MODERN control profile
		if (!CameraMode.isModern()) return;

		// Only in FP or CHASE rig state (not FREE)
		ModernCameraRig.RigState state = ModernCameraRig.getRigState();
		if (state != ModernCameraRig.RigState.FIRST_PERSON
				&& state != ModernCameraRig.RigState.CHASE) {
			return;
		}

		// Hide when a modal interface is open (dialog, bank, etc.)
		if (Cs1ScriptRunner.aBoolean108) return;

		// Determine viewport center from the viewport component
		Component viewport = InterfaceList.aClass13_26;
		int cx;
		int cy;
		if (viewport != null && viewport.width > 0 && viewport.height > 0) {
			cx = viewport.x + viewport.width / 2;
			cy = viewport.y + viewport.height / 2;
		} else {
			// Fallback: canvas center
			cx = GameShell.canvasWidth / 2;
			cy = GameShell.canvasHeight / 2;
		}

		drawReticle(cx, cy);
	}

	/**
	 * Renders a small cross reticle centered at (cx, cy).
	 *
	 * <p>Shape:
	 * <pre>
	 *       ##
	 *   ##  ##  ##
	 *       ##
	 * </pre>
	 * Center dot + 4 short arms extending up/down/left/right.
	 */
	private static void drawReticle(int cx, int cy) {
		int halfDot = DOT_SIZE / 2;

		if (GlRenderer.enabled) {
			// Center dot
			GlRaster.fillRectAlpha(cx - halfDot, cy - halfDot, DOT_SIZE, DOT_SIZE, COLOR, ALPHA);
			// Up arm
			GlRaster.fillRectAlpha(cx - ARM_THICKNESS / 2, cy - halfDot - ARM_LENGTH,
					ARM_THICKNESS, ARM_LENGTH, COLOR, ALPHA);
			// Down arm
			GlRaster.fillRectAlpha(cx - ARM_THICKNESS / 2, cy + halfDot,
					ARM_THICKNESS, ARM_LENGTH, COLOR, ALPHA);
			// Left arm
			GlRaster.fillRectAlpha(cx - halfDot - ARM_LENGTH, cy - ARM_THICKNESS / 2,
					ARM_LENGTH, ARM_THICKNESS, COLOR, ALPHA);
			// Right arm
			GlRaster.fillRectAlpha(cx + halfDot, cy - ARM_THICKNESS / 2,
					ARM_LENGTH, ARM_THICKNESS, COLOR, ALPHA);
		} else {
			// Center dot
			SoftwareRaster.fillRectAlpha(cx - halfDot, cy - halfDot, DOT_SIZE, DOT_SIZE, COLOR, ALPHA);
			// Up arm
			SoftwareRaster.fillRectAlpha(cx - ARM_THICKNESS / 2, cy - halfDot - ARM_LENGTH,
					ARM_THICKNESS, ARM_LENGTH, COLOR, ALPHA);
			// Down arm
			SoftwareRaster.fillRectAlpha(cx - ARM_THICKNESS / 2, cy + halfDot,
					ARM_THICKNESS, ARM_LENGTH, COLOR, ALPHA);
			// Left arm
			SoftwareRaster.fillRectAlpha(cx - halfDot - ARM_LENGTH, cy - ARM_THICKNESS / 2,
					ARM_LENGTH, ARM_THICKNESS, COLOR, ALPHA);
			// Right arm
			SoftwareRaster.fillRectAlpha(cx + halfDot, cy - ARM_THICKNESS / 2,
					ARM_LENGTH, ARM_THICKNESS, COLOR, ALPHA);
		}
	}
}
