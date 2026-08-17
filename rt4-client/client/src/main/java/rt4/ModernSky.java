package rt4;

import com.jogamp.opengl.GL2;

import java.nio.ByteBuffer;

/**
 * Camera-oriented sky panorama for the MODERN control profile.
 *
 * <p>The sky is rendered before the scene and never writes depth. Existing
 * terrain, roofs, ceilings and scenery therefore remain the sole authority for
 * what can cover it. ORIGINAL keeps the vanilla clear/fog background.</p>
 */
public final class ModernSky {

	private static final int PANORAMA_WIDTH = 1024;
	private static final int PANORAMA_HEIGHT = 512;
	private static final int PANORAMA_X_MASK = PANORAMA_WIDTH - 1;

	private static final int ZENITH_COLOR = 0x28679E;
	private static final int UPPER_SKY_COLOR = 0x5595C5;
	private static final int HORIZON_COLOR = 0xB8D8EA;
	private static final int LOWER_HAZE_COLOR = 0x9CB9C8;
	private static final int SUN_COLOR = 0xFFF3CE;
	private static final int CLOUD_LIGHT_COLOR = 0xF5F7F8;
	private static final int CLOUD_MID_COLOR = 0xD5DEE4;
	private static final int CLOUD_SHADOW_COLOR = 0x899DAD;

	private static int[] panorama;
	private static int glTextureId = -1;
	private static int glTextureContext = -1;
	private static boolean loggedReady;

	private ModernSky() {
	}

	/** Draws the sky in the active gameplay viewport when MODERN owns the camera. */
	public static void render(int x, int y, int width, int height, int cameraYaw, int cameraPitch) {
		if (!CameraMode.isModern() || width <= 0 || height <= 0) {
			return;
		}
		ensurePanorama();

		float horizontalFov = currentHorizontalFov(width);
		float verticalFov = currentVerticalFov(height);
		double uSpan = horizontalFov / 360.0D;
		double vSpan = verticalFov / 180.0D;
		// RT4 yaw decreases when the player turns right. The panorama therefore
		// uses the inverse yaw so fixed clouds move across the screen exactly like
		// fixed world geometry instead of following the camera.
		double uCenter = -(double) (cameraYaw & 0x7FF) / 2048.0D;

		int signedPitch = cameraPitch & 0x7FF;
		if (signedPitch > 1024) {
			signedPitch -= 2048;
		}
		double pitchDegrees = signedPitch * (360.0D / 2048.0D);
		double vCenter = 0.5D + pitchDegrees / 180.0D;
		double uLeft = uCenter - uSpan * 0.5D;
		double vTop = vCenter - vSpan * 0.5D;

		if (GlRenderer.enabled) {
			renderGl(x, y, width, height, uLeft, vTop, uSpan, vSpan);
		} else {
			renderSoftware(x, y, width, height, uLeft, vTop, uSpan, vSpan);
		}

		if (!loggedReady) {
			loggedReady = true;
			System.out.println("[MODERN-SKY] panorama=" + PANORAMA_WIDTH + "x" + PANORAMA_HEIGHT
					+ " renderer=" + (GlRenderer.enabled ? "gl" : "software")
					+ " scope=modern_only");
		}
	}

	private static float currentHorizontalFov(int viewportWidth) {
		if (GlRenderer.enabled && GlRenderer.hFOV > 1.0F && GlRenderer.hFOV < 179.0F) {
			return GlRenderer.hFOV;
		}
		double projection = Math.max(1.0D, ScriptRunner.anInt5029);
		return (float) Math.toDegrees(2.0D * Math.atan(viewportWidth / (projection * 2.0D)));
	}

	private static float currentVerticalFov(int viewportHeight) {
		if (GlRenderer.enabled && GlRenderer.vFOV > 1.0F && GlRenderer.vFOV < 179.0F) {
			return GlRenderer.vFOV;
		}
		double projection = Math.max(1.0D, ScriptRunner.anInt5029);
		return (float) Math.toDegrees(2.0D * Math.atan(viewportHeight / (projection * 2.0D)));
	}

	private static void renderGl(int x, int y, int width, int height,
			double uLeft, double vTop, double uSpan, double vSpan) {
		GL2 gl = GlRenderer.gl;
		gl.glPushAttrib(GL2.GL_ALL_ATTRIB_BITS);
		gl.glMatrixMode(GL2.GL_PROJECTION);
		gl.glPushMatrix();
		gl.glLoadIdentity();
		gl.glOrtho(0.0D, GlRenderer.canvasWidth, 0.0D, GlRenderer.canvasHeight, -1.0D, 1.0D);
		gl.glMatrixMode(GL2.GL_MODELVIEW);
		gl.glPushMatrix();
		gl.glLoadIdentity();

		gl.glViewport(0, 0, GlRenderer.canvasWidth, GlRenderer.canvasHeight);
		gl.glDisable(GL2.GL_LIGHTING);
		gl.glDisable(GL2.GL_FOG);
		gl.glDisable(GL2.GL_DEPTH_TEST);
		gl.glDisable(GL2.GL_CULL_FACE);
		gl.glDisable(GL2.GL_ALPHA_TEST);
		gl.glDisable(GL2.GL_BLEND);
		gl.glDepthMask(false);
		gl.glActiveTexture(GL2.GL_TEXTURE0);
		gl.glEnable(GL2.GL_TEXTURE_2D);
		ensureGlTexture(gl);
		gl.glBindTexture(GL2.GL_TEXTURE_2D, glTextureId);
		gl.glTexEnvi(GL2.GL_TEXTURE_ENV, GL2.GL_TEXTURE_ENV_MODE, GL2.GL_REPLACE);

		float left = x;
		float right = x + width;
		float top = GlRenderer.canvasHeight - y;
		float bottom = top - height;
		float texLeft = (float) uLeft;
		float texRight = (float) (uLeft + uSpan);
		float texTop = (float) vTop;
		float texBottom = (float) (vTop + vSpan);

		gl.glBegin(GL2.GL_TRIANGLE_FAN);
		gl.glTexCoord2f(texRight, texTop);
		gl.glVertex2f(right, top);
		gl.glTexCoord2f(texLeft, texTop);
		gl.glVertex2f(left, top);
		gl.glTexCoord2f(texLeft, texBottom);
		gl.glVertex2f(left, bottom);
		gl.glTexCoord2f(texRight, texBottom);
		gl.glVertex2f(right, bottom);
		gl.glEnd();

		gl.glMatrixMode(GL2.GL_MODELVIEW);
		gl.glPopMatrix();
		gl.glMatrixMode(GL2.GL_PROJECTION);
		gl.glPopMatrix();
		gl.glMatrixMode(GL2.GL_MODELVIEW);
		gl.glPopAttrib();
	}

	private static void ensureGlTexture(GL2 gl) {
		if (glTextureId != -1 && glTextureContext == GlCleaner.contextId) {
			return;
		}

		ByteBuffer pixels = ByteBuffer.allocateDirect(PANORAMA_WIDTH * PANORAMA_HEIGHT * 4);
		for (int color : panorama) {
			pixels.put((byte) (color >> 16));
			pixels.put((byte) (color >> 8));
			pixels.put((byte) color);
			pixels.put((byte) 0xFF);
		}
		pixels.flip();

		int[] texture = new int[1];
		gl.glGenTextures(1, texture, 0);
		glTextureId = texture[0];
		glTextureContext = GlCleaner.contextId;
		gl.glBindTexture(GL2.GL_TEXTURE_2D, glTextureId);
		gl.glTexParameteri(GL2.GL_TEXTURE_2D, GL2.GL_TEXTURE_MIN_FILTER, GL2.GL_LINEAR);
		gl.glTexParameteri(GL2.GL_TEXTURE_2D, GL2.GL_TEXTURE_MAG_FILTER, GL2.GL_LINEAR);
		gl.glTexParameteri(GL2.GL_TEXTURE_2D, GL2.GL_TEXTURE_WRAP_S, GL2.GL_REPEAT);
		gl.glTexParameteri(GL2.GL_TEXTURE_2D, GL2.GL_TEXTURE_WRAP_T, GL2.GL_CLAMP_TO_EDGE);
		gl.glTexImage2D(GL2.GL_TEXTURE_2D, 0, GL2.GL_RGBA, PANORAMA_WIDTH, PANORAMA_HEIGHT,
				0, GL2.GL_RGBA, GL2.GL_UNSIGNED_BYTE, pixels);
		GlCleaner.onCard2d += pixels.limit();
	}

	private static void renderSoftware(int x, int y, int width, int height,
			double uLeft, double vTop, double uSpan, double vSpan) {
		int left = Math.max(x, SoftwareRaster.clipLeft);
		int right = Math.min(x + width, SoftwareRaster.clipRight);
		int top = Math.max(y, SoftwareRaster.clipTop);
		int bottom = Math.min(y + height, SoftwareRaster.clipBottom);
		if (left >= right || top >= bottom) {
			return;
		}

		double uStep = uSpan / width;
		double vStep = vSpan / height;
		for (int screenY = top; screenY < bottom; screenY++) {
			double v = vTop + (screenY - y + 0.5D) * vStep;
			int sourceY = clamp((int) (v * PANORAMA_HEIGHT), 0, PANORAMA_HEIGHT - 1);
			double u = uLeft + (left - x + 0.5D) * uStep;
			int destination = screenY * SoftwareRaster.width + left;
			for (int screenX = left; screenX < right; screenX++) {
				int sourceX = floorToInt(u * PANORAMA_WIDTH) & PANORAMA_X_MASK;
				SoftwareRaster.pixels[destination++] = panorama[sourceY * PANORAMA_WIDTH + sourceX];
				u += uStep;
			}
		}
	}

	private static void ensurePanorama() {
		if (panorama != null) {
			return;
		}

		int size = PANORAMA_WIDTH * PANORAMA_HEIGHT;
		float[] cloudDensity = new float[size];
		for (int y = 0; y < PANORAMA_HEIGHT; y++) {
			double v = (double) y / (PANORAMA_HEIGHT - 1);
			double skyAmount = 1.0D - smoothstep(0.50D, 0.57D, v);
			for (int x = 0; x < PANORAMA_WIDTH; x++) {
				double warpX = (valueNoisePeriodic(x * 3.0D / PANORAMA_WIDTH,
						y * 2.0D / PANORAMA_HEIGHT, 3, 9137) - 0.5D) * 1.8D;
				double warpY = (valueNoisePeriodic(x * 4.0D / PANORAMA_WIDTH,
						y * 2.0D / PANORAMA_HEIGHT, 4, 2719) - 0.5D) * 1.2D;
				double nx = x * 7.0D / PANORAMA_WIDTH + warpX;
				double ny = y * 3.5D / PANORAMA_HEIGHT + warpY;
				double broad = fbm(nx, ny, 7, 104729, 5);
				double billow = fbm(x * 13.0D / PANORAMA_WIDTH + 2.4D,
						y * 7.0D / PANORAMA_HEIGHT - 1.7D, 13, 49999, 4);
				double shape = broad * 0.78D + billow * 0.22D;
				double body = smoothstep(0.50D, 0.68D, shape);
				double wisps = smoothstep(0.60D, 0.75D, billow) * 0.20D;
				double horizonFade = smoothstep(0.0D, 0.08D, 0.55D - v);
				cloudDensity[y * PANORAMA_WIDTH + x] = (float) (clamp01(body + wisps)
						* skyAmount * horizonFade);
			}
		}

		panorama = new int[size];
		for (int y = 0; y < PANORAMA_HEIGHT; y++) {
			double v = (double) y / (PANORAMA_HEIGHT - 1);
			for (int x = 0; x < PANORAMA_WIDTH; x++) {
				int index = y * PANORAMA_WIDTH + x;
				int sky = skyColor(v);
				double sunDistanceX = circularDistance((double) x / PANORAMA_WIDTH, 0.72D) * 1.7D;
				double sunDistanceY = (v - 0.20D) * 1.25D;
				double sunDistance = Math.sqrt(sunDistanceX * sunDistanceX + sunDistanceY * sunDistanceY);
				double sunGlow = Math.pow(clamp01(1.0D - sunDistance / 0.18D), 2.4D);
				double sunDisk = smoothstep(0.018D, 0.006D, sunDistance);
				sky = mixColor(sky, SUN_COLOR, sunGlow * 0.33D + sunDisk * 0.92D);

				double density = cloudDensity[index];
				if (density > 0.001D) {
					int lightX = x + 8 & PANORAMA_X_MASK;
					int lightY = clamp(y + 5, 0, PANORAMA_HEIGHT - 1);
					double towardSunDensity = cloudDensity[lightY * PANORAMA_WIDTH + lightX];
					double edgeLight = clamp01((density - towardSunDensity) * 3.8D);
					double shadow = clamp01((towardSunDensity - density) * 2.4D + (1.0D - density) * 0.22D);
					int cloud = mixColor(CLOUD_SHADOW_COLOR, CLOUD_MID_COLOR, 1.0D - shadow);
					cloud = mixColor(cloud, CLOUD_LIGHT_COLOR,
							clamp01(density * 0.72D + edgeLight * 0.55D + sunGlow * 0.28D));
					double opacity = clamp01(density * 0.88D + density * density * 0.12D);
					sky = mixColor(sky, cloud, opacity);
				}
				panorama[index] = sky;
			}
		}
	}

	private static int skyColor(double v) {
		if (v <= 0.5D) {
			double elevation = (0.5D - v) * 2.0D;
			int upper = mixColor(HORIZON_COLOR, UPPER_SKY_COLOR, Math.pow(elevation, 0.58D));
			return mixColor(upper, ZENITH_COLOR, Math.pow(elevation, 2.1D) * 0.72D);
		}
		return mixColor(HORIZON_COLOR, LOWER_HAZE_COLOR,
				clamp01((v - 0.5D) / 0.34D));
	}

	private static double fbm(double x, double y, int basePeriodX, int seed, int octaves) {
		double sum = 0.0D;
		double amplitude = 0.5D;
		double amplitudeSum = 0.0D;
		int periodX = basePeriodX;
		for (int octave = 0; octave < octaves; octave++) {
			sum += valueNoisePeriodic(x, y, periodX, seed + octave * 1013) * amplitude;
			amplitudeSum += amplitude;
			x *= 2.0D;
			y *= 2.0D;
			periodX <<= 1;
			amplitude *= 0.5D;
		}
		return sum / amplitudeSum;
	}

	private static double valueNoisePeriodic(double x, double y, int periodX, int seed) {
		int x0 = floorToInt(x);
		int y0 = floorToInt(y);
		int x1 = x0 + 1;
		int y1 = y0 + 1;
		double tx = fade(x - x0);
		double ty = fade(y - y0);
		double a = hash01(floorMod(x0, periodX), y0, seed);
		double b = hash01(floorMod(x1, periodX), y0, seed);
		double c = hash01(floorMod(x0, periodX), y1, seed);
		double d = hash01(floorMod(x1, periodX), y1, seed);
		return lerp(lerp(a, b, tx), lerp(c, d, tx), ty);
	}

	private static double hash01(int x, int y, int seed) {
		int value = x * 374761393 + y * 668265263 + seed * 1442695041;
		value = (value ^ value >>> 13) * 1274126177;
		value ^= value >>> 16;
		return (value & 0x7FFFFFFF) / 2147483647.0D;
	}

	private static int mixColor(int from, int to, double amount) {
		double t = clamp01(amount);
		int red = (int) ((from >> 16 & 0xFF) + ((to >> 16 & 0xFF) - (from >> 16 & 0xFF)) * t + 0.5D);
		int green = (int) ((from >> 8 & 0xFF) + ((to >> 8 & 0xFF) - (from >> 8 & 0xFF)) * t + 0.5D);
		int blue = (int) ((from & 0xFF) + ((to & 0xFF) - (from & 0xFF)) * t + 0.5D);
		return red << 16 | green << 8 | blue;
	}

	private static double circularDistance(double a, double b) {
		double distance = Math.abs(a - b);
		return Math.min(distance, 1.0D - distance);
	}

	private static double fade(double value) {
		return value * value * (3.0D - value * 2.0D);
	}

	private static double smoothstep(double edge0, double edge1, double value) {
		if (edge0 == edge1) {
			return value < edge0 ? 0.0D : 1.0D;
		}
		double t = clamp01((value - edge0) / (edge1 - edge0));
		return t * t * (3.0D - t * 2.0D);
	}

	private static double lerp(double from, double to, double amount) {
		return from + (to - from) * amount;
	}

	private static int floorToInt(double value) {
		int integer = (int) value;
		return value < integer ? integer - 1 : integer;
	}

	private static int floorMod(int value, int modulus) {
		int result = value % modulus;
		return result < 0 ? result + modulus : result;
	}

	private static int clamp(int value, int minimum, int maximum) {
		return value < minimum ? minimum : Math.min(value, maximum);
	}

	private static double clamp01(double value) {
		return value < 0.0D ? 0.0D : Math.min(value, 1.0D);
	}
}
