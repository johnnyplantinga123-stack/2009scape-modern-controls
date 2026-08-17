package rt4;

/**
 * First-person damage-direction ring.
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
	private static final int RING_RADIUS = 52;
	private static final int RING_POINTS = 32;
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

	/** Draws a subtle RS-styled ring and the fading directional hit wedges. */
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
		// Quiet bronze ring: it establishes the circular language without
		// obscuring the world. The active hit segment is always much brighter.
		for (int point = 0; point < RING_POINTS; point++) {
			int angle = point * 2048 / RING_POINTS;
			int x = centerX - (MathUtils.sin[angle] * RING_RADIUS >> 16);
			int y = centerY - (MathUtils.cos[angle] * RING_RADIUS >> 16);
			fillAlpha(x - 1, y - 1, 3, 3, 0x3A2814, 130);
		}

		int cameraYaw = FirstPersonCamera.getYaw();
		for (int octant = 0; octant < OCTANTS; octant++) {
			int remaining = visibleUntil[octant] - client.loop;
			if (remaining <= 0) {
				continue;
			}
			int alpha = 70 + remaining * 180 / DISPLAY_TICKS;
			int relative = ((octant << 8) - cameraYaw) & 2047;
			drawHitWedge(centerX, centerY, relative, alpha);
		}
	}

	private static void drawHitWedge(int centerX, int centerY, int angle, int alpha) {
		int unitX = -MathUtils.sin[angle];
		int unitY = -MathUtils.cos[angle];
		int sideX = -unitY;
		int sideY = unitX;
		// Three short rows form a clean arrowhead/arc segment, readable at
		// small resolutions and intentionally independent of scaled sprites.
		for (int depth = 0; depth < 3; depth++) {
			int radius = RING_RADIUS - 4 + depth * 5;
			int width = 7 - depth * 2;
			for (int lateral = -width; lateral <= width; lateral += 3) {
				int x = centerX + (unitX * radius >> 16) + (sideX * lateral >> 16);
				int y = centerY + (unitY * radius >> 16) + (sideY * lateral >> 16);
				fillAlpha(x - 2, y - 2, 5, 5, 0xA81E1E, alpha);
				fillAlpha(x - 1, y - 1, 3, 3, 0xF05A45, alpha);
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
