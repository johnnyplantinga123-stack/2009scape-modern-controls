package rt4;

/**
 * First-person damage-direction arcs.
 *
 * <p>The server sends only the finalized hit's eight-way world direction via
 * a project-reserved varp. This class rotates that direction into the live
 * camera space, so no server-side camera data or attacker identity is needed.
 * Multiple incoming directions coexist briefly and naturally fade out.</p>
 */
public final class ModernDamageIndicator {

	/** Reserved local-only varp; no cache varp definition is required. */
	public static final int HIT_DIRECTION_VARP = 3499;
	private static final int OCTANTS = 8;
	private static final int DISPLAY_TICKS = 50;
	/** A compact, peripheral arc is readable without hiding the crosshair. */
	private static final int ARC_HALF_ANGLE = 150;
	private static final int ARC_STEP = 14;
	private static final int ARC_THICKNESS = 7;
	private static final int[] visibleUntil = new int[OCTANTS];

	private ModernDamageIndicator() {
	}

	/** Receives a server-authoritative, quantized attacker direction. */
	public static void onVarpUpdate(int id, int value) {
		if (id != HIT_DIRECTION_VARP) {
			return;
		}
		visibleUntil[value & (OCTANTS - 1)] = client.loop + DISPLAY_TICKS;
	}

	/** Draws prominent, fading peripheral hit-direction arcs. */
	public static void draw() {
		if (!CameraMode.isModern() || !ModernCameraRig.isFirstPersonRigState()) {
			return;
		}
		int canvasW = GlRenderer.enabled ? GlRenderer.canvasWidth : SoftwareRaster.width;
		int canvasH = GlRenderer.enabled ? GlRenderer.canvasHeight : SoftwareRaster.height;
		if (canvasW <= 0 || canvasH <= 0) {
			return;
		}

		boolean active = false;
		for (int octant = 0; octant < OCTANTS; octant++) {
			if (visibleUntil[octant] > client.loop) {
				active = true;
				break;
			}
		}
		if (!active) {
			return;
		}

		int centerX = canvasW / 2;
		int centerY = canvasH / 2;
		setFullClip(canvasW, canvasH);
		int cameraYaw = FirstPersonCamera.getYaw();
		for (int octant = 0; octant < OCTANTS; octant++) {
			int remaining = visibleUntil[octant] - client.loop;
			if (remaining <= 0) {
				continue;
			}
			int alpha = 105 + remaining * 150 / DISPLAY_TICKS;
			int relative = ((octant << 8) - cameraYaw) & 2047;
			drawHitArc(centerX, centerY, relative, alpha, canvasW, canvasH);
		}
	}

	/**
	 * Builds the curved, edge-of-view cue used by modern action HUDs. The curve
	 * follows a circle around the reticle and its inward arrowhead says exactly
	 * which side the impact came from. It is raster-drawn at native resolution,
	 * avoiding the softened/pixelated look of a scaled sprite.
	 */
	private static void drawHitArc(int centerX, int centerY, int angle, int alpha,
			int canvasW, int canvasH) {
		// Keep the cue inside the clear world-view band: it must not intrude
		// into the compass above or the quick/action bars below.
		int radius = Math.max(112, Math.min(canvasW * 28 / 100, canvasH * 30 / 100));
		for (int offset = -ARC_HALF_ANGLE; offset <= ARC_HALF_ANGLE; offset += ARC_STEP) {
			int arcAngle = (angle + offset) & 0x7FF;
			int x = centerX - (MathUtils.sin[arcAngle] * radius >> 16);
			int y = centerY - (MathUtils.cos[arcAngle] * radius >> 16);
			// Dark red outer body plus a hot-red centre make this stand out on
			// bright sand, snow and dark interiors alike.
			fillAlpha(x - ARC_THICKNESS / 2, y - ARC_THICKNESS / 2,
					ARC_THICKNESS, ARC_THICKNESS, 0x65100D, alpha);
			fillAlpha(x - 2, y - 2, 5, 5, 0xE53A2E, alpha);
		}

		int unitX = -MathUtils.sin[angle];
		int unitY = -MathUtils.cos[angle];
		int sideX = -unitY;
		int sideY = unitX;
		// The tip points inward toward the reticle; its base blends into the arc.
		for (int depth = 0; depth <= 16; depth += 2) {
			int width = 16 - depth;
			for (int lateral = -width; lateral <= width; lateral += 3) {
				int x = centerX + (unitX * radius >> 16) - (unitX * depth >> 16)
						+ (sideX * lateral >> 16);
				int y = centerY + (unitY * radius >> 16) - (unitY * depth >> 16)
						+ (sideY * lateral >> 16);
				fillAlpha(x - 3, y - 3, 7, 7, 0x75130F, alpha);
				fillAlpha(x - 1, y - 1, 3, 3, 0xFF5C4B, alpha);
			}
		}
	}

	private static void setFullClip(int width, int height) {
		if (GlRenderer.enabled) {
			GlRaster.setClip(0, 0, width, height);
		} else {
			SoftwareRaster.setClip(0, 0, width, height);
		}
	}

	private static void fillAlpha(int x, int y, int width, int height, int color, int alpha) {
		if (GlRenderer.enabled) {
			GlRaster.fillRectAlpha(x, y, width, height, color, alpha);
		} else {
			SoftwareRaster.fillRectAlpha(x, y, width, height, color, alpha);
		}
	}
}
