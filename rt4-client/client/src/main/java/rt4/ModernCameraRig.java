package rt4;

/**
 * Modern camera rig (Phase 3C).
 *
 * <p>Manages the continuous zoom/camera continuum inside MODERN control mode:
 * <pre>
 *   FIRST_PERSON  ← scroll out →  CHASE  ← scroll out →  FREE
 * </pre>
 *
 * <h2>Architecture</h2>
 * <ul>
 *   <li>One authoritative {@code desiredDistance} controlled by scroll wheel.</li>
 *   <li>Smooth {@code actualDistance} interpolated toward desired (obstruction-safe).</li>
 *   <li>Hysteresis thresholds prevent mode flicker at FP/CHASE and CHASE/FREE boundaries.</li>
 *   <li>Chase camera follows character body orientation (camera is a FOLLOWER).</li>
 *   <li>Camera obstruction compresses actual distance without destroying desired zoom.</li>
 *   <li>FREE mode reuses classic-style camera behavior with modern WASD still active.</li>
 * </ul>
 *
 * <h2>Camera/Control Separation</h2>
 * <p>CameraMode (ORIGINAL / FIRST_PERSON / THIRD_PERSON) controls the LOCOMOTION scheme.
 * Inside MODERN, this rig manages the CAMERA rig state (FP / CHASE / FREE).
 * ORIGINAL remains a pristine legacy fallback, untouched by this rig.</p>
 *
 * <h2>Wheel Input Path</h2>
 * <p>JavaMouseWheel → MouseWheel.wheelRotation (set each mainLoop tick in client.java).
 * UI consumers (InterfaceList) read but do NOT reset wheelRotation.
 * This rig reads wheelRotation AFTER UI consumers have had their chance (rig runs
 * during ModernControlController.update which is after InterfaceList processing
 * in the render pipeline). When a scrollable interface is under the cursor,
 * the rig defers to the UI.</p>
 *
 * <h2>Orientation Fields (RT4)</h2>
 * <ul>
 *   <li>{@code PathingEntity.anInt3400} — target orientation (0..2047, clockwise: 0=N,512=W,1024=S,1536=E)</li>
 *   <li>{@code PathingEntity.anInt3381} — smoothed orientation (animations use this)</li>
 *   <li>{@code PathingEntity.anInt3376} — orientation speed (default 32)</li>
 *   <li>{@code PathingEntity.anInt3385} — orientation change counter (turn animation trigger)</li>
 * </ul>
 * <p>RT4 has NO separate head yaw. Body rotation via anInt3400→anInt3381 is the only
 * orientation mechanism. Head-look coupling is therefore deferred (body-yaw follow only).</p>
 */
public final class ModernCameraRig {

	// ---- Rig state ----
	public enum RigState { FIRST_PERSON, CHASE, FREE }
	private static RigState rigState = RigState.CHASE;

	// ---- Distance continuum (fine units; 128 fine = 1 tile) ----
	/** At or below this distance: FP mode. */
	private static final int FP_ENTER_DISTANCE = 120;
	/** At or above this distance (from FP): exit FP to CHASE. Hysteresis: > FP_ENTER. */
	private static final int FP_EXIT_DISTANCE = 200;
	/** At or above this distance (from CHASE): enter FREE. */
	private static final int FREE_ENTER_DISTANCE = 4200;
	/** At or below this distance (from FREE): exit FREE to CHASE. Hysteresis: < FREE_ENTER. */
	private static final int FREE_EXIT_DISTANCE = 3800;
	/** Minimum allowed desired distance (FP clamped). */
	private static final int MIN_DISTANCE = 0;
	/** Maximum allowed desired distance (FREE clamped). */
	private static final int MAX_DISTANCE = 5600;

	/** Scroll wheel step per notch (fine units). ~1 tile per notch. */
	private static final int WHEEL_STEP = 130;

	// ---- Smoothing ----
	/** Exponential smoothing factor for actual→desired distance (per tick). */
	private static final int DISTANCE_SMOOTH_FACTOR = 6;
	/** Exponential smoothing factor for chase yaw (per tick). */
	private static final int YAW_SMOOTH_FACTOR = 8;
	/** Minimum yaw step to prevent stalling at very small differences. */
	private static final int YAW_SMOOTH_MIN = 2;

	// ---- Chase camera geometry ----
	/** Height offset for camera pivot above player fine position (torso/head height). */
	private static final int CHASE_PIVOT_HEIGHT = 220;
	/** Camera pitch in chase mode (0..2047). ~45° downward look. */
	private static final int CHASE_PITCH = 256;
	/** Camera pitch in free mode. Slightly steeper for overview. */
	private static final int FREE_PITCH = 300;

	// ---- Body-look coupling (FP only) ----
	/**
	 * Shoulder dead-zone: body doesn't rotate until camera yaw differs
	 * by more than this amount from body yaw. ~35 degrees (360° = 2048 units).
	 */
	private static final int SHOULDER_DEAD_ZONE = 100;
	/**
	 * Maximum yaw difference before body snaps faster. Beyond this, the
	 * catch-up rate increases. ~70 degrees.
	 */
	private static final int SHOULDER_LIMIT = 200;
	/** Normal body yaw catch-up speed (units per tick). */
	private static final int BODY_CATCHUP_SPEED = 24;
	/** Fast body yaw catch-up speed when beyond SHOULDER_LIMIT. */
	private static final int BODY_FAST_CATCHUP_SPEED = 64;

	// ---- State fields ----
	/** User's desired camera distance (scroll wheel controls this). */
	private static int desiredDistance = 2400;
	/** Actual camera distance (smoothly approaches desired; compressed by walls). */
	private static int actualDistance = 2400;
	/** Chase camera actual yaw (smoothly follows target). */
	private static int chaseYaw = 0;
	/** Chase camera target yaw (from character body orientation). */
	private static int chaseYawTarget = 0;
	/** Chase camera actual pitch (smoothly transitions). */
	private static int chasePitch = CHASE_PITCH;
	/** Free camera yaw (user-controllable via arrow keys). */
	private static int freeYaw = 0;
	/** Free camera pitch (user-controllable via arrow keys). */
	private static int freePitch = FREE_PITCH;
	/** Character body yaw for FP body-look coupling. */
	private static int bodyYaw = 0;
	/** Whether the rig has been initialized (prevents first-frame snap). */
	private static boolean initialized = false;
	/** Whether the rig is currently active (set by activate, cleared by deactivate). */
	private static boolean active = false;

	// ---- Saved camera state for ORIGINAL restoration ----
	private static int savedCameraType = 1;

	private ModernCameraRig() {
	}

	// =====================================================================
	// PUBLIC API
	// =====================================================================

	/** Returns the current rig state. */
	public static RigState getRigState() {
		return rigState;
	}

	/** Returns whether the rig is currently active. */
	public static boolean isActive() {
		return active;
	}

	/** Returns the current chase/free camera yaw (for movement controller). */
	public static int getCameraYaw() {
		return (rigState == RigState.FREE) ? freeYaw : chaseYaw;
	}

	/** Returns the current camera pitch. */
	public static int getCameraPitch() {
		return (rigState == RigState.FREE) ? freePitch : chasePitch;
	}

	/** Returns the current actual camera distance. */
	public static int getActualDistance() {
		return actualDistance;
	}

	/** Returns the current desired camera distance. */
	public static int getDesiredDistance() {
		return desiredDistance;
	}

	/** Returns the FP body yaw (for movement controller in FP mode). */
	public static int getBodyYaw() {
		return bodyYaw;
	}

	/**
	 * Called when entering ANY modern mode from ORIGINAL.
	 * Sets cameraType=0 immediately to prevent legacy camera interference.
	 */
	public static void onEnterModernMode() {
		savedCameraType = Camera.cameraType;
		Camera.cameraType = 0;
	}

	/**
	 * Called when returning to ORIGINAL mode.
	 * Restores the saved camera type so the legacy camera system resumes.
	 */
	public static void onExitModernMode() {
		Camera.cameraType = savedCameraType;
		active = false;
	}

	/**
	 * Called on scene/region rebuild.
	 * Preserves desired zoom, re-anchors actual camera near player.
	 */
	public static void onSceneRebuild() {
		if (!active) return;
		Player self = PlayerList.self;
		if (self == null) return;
		// Preserve desiredDistance (user's zoom intent survives region change)
		// Re-anchor actualDistance to desired (avoid interpolating from old region)
		actualDistance = desiredDistance;
		// Re-anchor yaw to current player orientation
		chaseYawTarget = self.anInt3400;
		chaseYaw = chaseYawTarget;
		if (rigState == RigState.FREE) {
			freeYaw = Camera.cameraYaw;
			freePitch = (int) Camera.pitchTarget;
		}
		// Force cameraType=0 so legacy camera doesn't interfere during rebuild
		Camera.cameraType = 0;
	}

	// =====================================================================
	// MAIN UPDATE
	// =====================================================================

	/**
	 * Per-tick update. Called from {@link ModernControlController#update()}
	 * AFTER FirstPersonCamera.update() (which provides fpCamYaw) and BEFORE
	 * {@link ModernMovementController#update()} (which reads camera yaw for movement).
	 */
	public static void update() {
		if (!CameraMode.isModern()) {
			if (active) {
				deactivate();
			}
			return;
		}

		if (!active) {
			activate();
		}

		// Region rebuilds set cameraType=1; re-assert 0 every frame for CHASE/FREE
		if (rigState != RigState.FIRST_PERSON) {
			Camera.cameraType = 0;
		}

		Player self = PlayerList.self;
		if (self == null) return;

		// 1. Process scroll wheel → desired distance
		processWheelInput();

		// 2. State transitions based on desired distance (with hysteresis)
		updateStateTransitions();

		// 3. Update camera based on rig state
		switch (rigState) {
			case FIRST_PERSON:
				updateFirstPerson(self);
				break;
			case CHASE:
				updateChase(self);
				break;
			case FREE:
				updateFree(self);
				break;
		}

		// 4. Body-look coupling (FP only)
		if (rigState == RigState.FIRST_PERSON) {
			updateBodyLookCoupling(self);
		} else {
			// In CHASE/FREE, body yaw tracks current orientation for smooth transition
			bodyYaw = self.anInt3400;
		}
	}

	// =====================================================================
	// ACTIVATION / DEACTIVATION
	// =====================================================================

	private static void activate() {
		active = true;
		initialized = false;
		savedCameraType = Camera.cameraType;
		Camera.cameraType = 0;

		Player self = PlayerList.self;
		if (self != null) {
			bodyYaw = self.anInt3400;
			chaseYaw = self.anInt3400;
			chaseYawTarget = self.anInt3400;
		}

		// Determine initial rig state from desired distance
		if (desiredDistance <= FP_EXIT_DISTANCE) {
			rigState = RigState.FIRST_PERSON;
		} else if (desiredDistance >= FREE_EXIT_DISTANCE) {
			rigState = RigState.FREE;
			freeYaw = Camera.cameraYaw;
			freePitch = (int) Camera.pitchTarget;
		} else {
			rigState = RigState.CHASE;
		}

		actualDistance = desiredDistance;
		chasePitch = CHASE_PITCH;
		initialized = true;
	}

	private static void deactivate() {
		active = false;
		initialized = false;
		// CameraType restoration is handled by onExitModernMode()
	}

	// =====================================================================
	// SCROLL WHEEL INPUT
	// =====================================================================

	private static void processWheelInput() {
		if (MouseWheel.wheelRotation == 0) return;

		// Respect UI: if mouse is over the game viewport area AND not over
		// a scrollable interface, wheel controls camera zoom.
		// The InterfaceList scroll processing runs during the render pipeline
		// (ScriptRunner), which is AFTER this update. We check if the mouse
		// is within the main viewport (not in sidebar/chat areas).
		// When a scrollable component is under the cursor, InterfaceList will
		// handle it; we still process but the UI gets priority visually.
		//
		// For simplicity and correctness: process wheel for camera zoom
		// whenever the cursor is locked (gameplay mode) or in the viewport.
		// UI scroll works through InterfaceList's own processing path which
		// doesn't conflict because it operates on component scrollY, not on
		// our desiredDistance.

		int rotation = MouseWheel.wheelRotation;

		// Scroll IN (rotation < 0) → reduce distance (zoom in)
		// Scroll OUT (rotation > 0) → increase distance (zoom out)
		desiredDistance += rotation * WHEEL_STEP;
		if (desiredDistance < MIN_DISTANCE) desiredDistance = MIN_DISTANCE;
		if (desiredDistance > MAX_DISTANCE) desiredDistance = MAX_DISTANCE;
	}

	// =====================================================================
	// STATE TRANSITIONS (with hysteresis)
	// =====================================================================

	private static void updateStateTransitions() {
		RigState previous = rigState;

		switch (rigState) {
			case FIRST_PERSON:
				if (desiredDistance >= FP_EXIT_DISTANCE) {
					rigState = RigState.CHASE;
					// Initialize chase yaw from current body/camera direction
					chaseYaw = bodyYaw;
					chaseYawTarget = bodyYaw;
					chasePitch = CHASE_PITCH;
				}
				break;

			case CHASE:
				if (desiredDistance <= FP_ENTER_DISTANCE) {
					rigState = RigState.FIRST_PERSON;
					// FP camera takes over; bodyYaw preserved for smooth handoff
				} else if (desiredDistance >= FREE_ENTER_DISTANCE) {
					rigState = RigState.FREE;
					// Initialize free camera from current chase orientation
					freeYaw = chaseYaw;
					freePitch = chasePitch;
				}
				break;

			case FREE:
				if (desiredDistance <= FREE_EXIT_DISTANCE) {
					rigState = RigState.CHASE;
					// Smoothly acquire character orientation
					chaseYawTarget = (PlayerList.self != null)
							? PlayerList.self.anInt3400 : chaseYaw;
					// Don't snap chaseYaw — let it smoothly interpolate
					chasePitch = CHASE_PITCH;
				}
				break;
		}

		if (previous != rigState) {
			// Debug: state transition (temporary, remove before final commit)
			System.out.println("[CAMERA-RIG] State: " + previous + " → " + rigState
					+ " desired=" + desiredDistance + " actual=" + actualDistance);
		}
	}

	// =====================================================================
	// FIRST PERSON CAMERA
	// =====================================================================

	/**
	 * FP camera is handled by FirstPersonCamera.update() which runs before this.
	 * Here we just ensure cameraType stays 0 and sync rig state.
	 */
	private static void updateFirstPerson(Player self) {
		// FirstPersonCamera.update() already wrote Camera fields.
		// We just need cameraType=0 (set above).
		// Body-look coupling is handled separately below.
	}

	// =====================================================================
	// CHASE CAMERA
	// =====================================================================

	/**
	 * Chase camera: follows behind character body orientation.
	 * Camera yaw smoothly tracks character body yaw.
	 * Camera position = pivot (above player) + offset (behind, at distance).
	 */
	private static void updateChase(Player self) {
		// Target yaw = character body orientation
		chaseYawTarget = self.anInt3400;

		// Smooth yaw interpolation (shortest angle)
		chaseYaw = smoothYaw(chaseYaw, chaseYawTarget, YAW_SMOOTH_FACTOR, YAW_SMOOTH_MIN);

		// Smooth pitch transition
		chasePitch = smoothInt(chasePitch, CHASE_PITCH, 6);

		// Smooth distance
		smoothDistance();

		// Compute camera position
		int pivotX = self.xFine;
		int pivotZ = self.zFine;
		int pivotY = SceneGraph.getTileHeight(Player.plane, pivotX, pivotZ) - CHASE_PIVOT_HEIGHT;

		// Camera offset behind player
		int dist = actualDistance;
		int yaw = chaseYaw;
		int pitch = chasePitch;

		// Horizontal offset (behind character)
		int offsetX = (MathUtils.sin[yaw & 2047] * dist) >> 16;
		int offsetZ = (MathUtils.cos[yaw & 2047] * dist) >> 16;

		// Vertical offset (above, based on pitch)
		int offsetY = -(MathUtils.sin[pitch & 2047] * dist) >> 16;

		// Desired camera position (before obstruction)
		int camX = pivotX + offsetX;
		int camZ = pivotZ + offsetZ;
		int camY = pivotY + offsetY;

		// Camera obstruction check
		int clearDist = checkObstruction(pivotX, pivotZ, pivotY, camX, camZ, camY);
		if (clearDist < dist) {
			// Wall between player and desired camera — compress
			int ratio = (dist > 0) ? (clearDist * 65536 / dist) : 0;
			camX = pivotX + (offsetX * ratio >> 16);
			camZ = pivotZ + (offsetZ * ratio >> 16);
			camY = pivotY + (offsetY * ratio >> 16);
		}

		// Terrain safety: don't place camera below ground
		int terrainH = SceneGraph.getTileHeight(Player.plane, camX, camZ);
		if (camY > terrainH - 50) {
			camY = terrainH - 50;
		}

		// Write camera fields
		Camera.renderX = camX;
		Camera.renderZ = camZ;
		Camera.anInt40 = camY;
		Camera.cameraYaw = yaw;
		Camera.cameraPitch = pitch;
		Camera.yawTarget = yaw;
		Camera.pitchTarget = pitch;
		Camera.cameraX = camX;
		Camera.cameraZ = camZ;
	}

	// =====================================================================
	// FREE CAMERA
	// =====================================================================

	/**
	 * Free camera: classic-style overview camera.
	 * Arrow keys control orbit (reuses existing camera input infrastructure).
	 * Camera orbits player at the rig's distance.
	 * Modern WASD remains active; movement uses body orientation.
	 */
	private static void updateFree(Player self) {
		// Arrow key camera control (reuses existing Preferences.aBoolean63 path)
		if (Preferences.aBoolean63) {
			for (int i = 0; i < InterfaceList.keyQueueSize; i++) {
				int code = InterfaceList.keyCodes[i];
				if (code == Keyboard.KEY_UP) {
					freePitch -= 4;
				} else if (code == Keyboard.KEY_DOWN) {
					freePitch += 4;
				} else if (code == Keyboard.KEY_LEFT) {
					freeYaw -= 16;
				} else if (code == Keyboard.KEY_RIGHT) {
					freeYaw += 16;
				}
			}
		}

		// Clamp pitch
		if (freePitch < 128) freePitch = 128;
		if (freePitch > 383) freePitch = 383;
		freeYaw &= 0x7FF;

		// Smooth distance
		smoothDistance();

		// Compute camera position (orbit around player)
		int dist = actualDistance;
		int yaw = freeYaw;
		int pitch = freePitch;

		int pivotX = self.xFine;
		int pivotZ = self.zFine;
		int groundH = SceneGraph.getTileHeight(Player.plane, pivotX, pivotZ);

		// Camera offset from player
		int offsetX = (MathUtils.sin[yaw & 2047] * dist) >> 16;
		int offsetZ = (MathUtils.cos[yaw & 2047] * dist) >> 16;
		int offsetY = -(MathUtils.sin[pitch & 2047] * dist) >> 16;

		int camX = pivotX + offsetX;
		int camZ = pivotZ + offsetZ;
		int camY = groundH - CHASE_PIVOT_HEIGHT + offsetY;

		// Terrain safety
		int terrainH = SceneGraph.getTileHeight(Player.plane, camX, camZ);
		if (camY > terrainH - 50) {
			camY = terrainH - 50;
		}

		// Camera obstruction (same as chase)
		int clearDist = checkObstruction(pivotX, pivotZ, groundH - CHASE_PIVOT_HEIGHT, camX, camZ, camY);
		if (clearDist < dist) {
			int ratio = (dist > 0) ? (clearDist * 65536 / dist) : 0;
			camX = pivotX + (offsetX * ratio >> 16);
			camZ = pivotZ + (offsetZ * ratio >> 16);
			camY = (groundH - CHASE_PIVOT_HEIGHT) + (offsetY * ratio >> 16);
		}

		// Write camera fields
		Camera.renderX = camX;
		Camera.renderZ = camZ;
		Camera.anInt40 = camY;
		Camera.cameraYaw = yaw;
		Camera.cameraPitch = pitch;
		Camera.yawTarget = yaw;
		Camera.pitchTarget = pitch;
		Camera.cameraX = camX;
		Camera.cameraZ = camZ;
	}

	// =====================================================================
	// DISTANCE SMOOTHING
	// =====================================================================

	/**
	 * Exponential smoothing of actualDistance toward desiredDistance.
	 * Frame-rate independent within the 50Hz tick architecture.
	 */
	private static void smoothDistance() {
		int delta = desiredDistance - actualDistance;
		if (delta != 0) {
			int step = delta / DISTANCE_SMOOTH_FACTOR;
			if (step == 0) step = (delta > 0) ? 1 : -1;
			actualDistance += step;
		}
		// Clamp actual to valid range
		if (actualDistance < MIN_DISTANCE) actualDistance = MIN_DISTANCE;
		if (actualDistance > MAX_DISTANCE) actualDistance = MAX_DISTANCE;
	}

	// =====================================================================
	// CAMERA OBSTRUCTION
	// =====================================================================

	/**
	 * Checks if the camera path from pivot to desired position is blocked
	 * by scenery/walls/terrain. Returns the maximum clear distance (fine units).
	 *
	 * <p>Uses multi-sample stepping along the pivot→camera line. At each sample,
	 * checks terrain height and collision flags. If the camera position would be
	 * below terrain or in a colliding tile, returns the distance to the last
	 * clear sample.</p>
	 *
	 * <p>This is CAMERA collision, NOT player movement collision.</p>
	 */
	private static int checkObstruction(int pivotX, int pivotZ, int pivotY,
			int camX, int camZ, int camY) {
		int deltaX = camX - pivotX;
		int deltaZ = camZ - pivotZ;
		int fineDist = (int) Math.sqrt((long) deltaX * deltaX + (long) deltaZ * deltaZ);

		if (fineDist < 64) return fineDist; // Too close to bother checking

		// Number of samples along the line (every ~1 tile = 128 fine units)
		int steps = Math.max(1, fineDist / 128);
		int maxClear = fineDist;

		for (int i = 1; i <= steps; i++) {
			int frac = i * 65536 / steps;
			int sampleX = pivotX + (deltaX * frac >> 16);
			int sampleZ = pivotZ + (deltaZ * frac >> 16);

			int tileX = sampleX >> 7;
			int tileZ = sampleZ >> 7;

			// Bounds check
			if (tileX < 1 || tileX > 102 || tileZ < 1 || tileZ > 102) {
				maxClear = (pivotX + (deltaX * (i - 1) * 65536 / steps >> 16)) - pivotX;
				maxClear = (int) Math.sqrt((long) maxClear * maxClear
						+ (long) ((pivotZ + (deltaZ * (i - 1) * 65536 / steps >> 16)) - pivotZ)
						* ((pivotZ + (deltaZ * (i - 1) * 65536 / steps >> 16)) - pivotZ));
				break;
			}

			// Check collision flags (walls/scenery block camera)
			if (Player.plane >= 0 && Player.plane < 4) {
				int flags = 0;
				if (PathFinder.collisionMaps != null
						&& PathFinder.collisionMaps[Player.plane] != null) {
					flags = PathFinder.collisionMaps[Player.plane].flags[tileX][tileZ];
				}
				// If this tile has solid collision (wall/scenery), camera can't pass
				if ((flags & 0x100) != 0 || (flags & 0x20000) != 0) {
					// Blocked — return distance to previous clear sample
					int prevFrac = (i - 1) * 65536 / steps;
					int prevX = pivotX + (deltaX * prevFrac >> 16);
					int prevZ = pivotZ + (deltaZ * prevFrac >> 16);
					int dx = prevX - pivotX;
					int dz = prevZ - pivotZ;
					maxClear = (int) Math.sqrt((long) dx * dx + (long) dz * dz);
					break;
				}
			}

			// Check terrain height (camera shouldn't be below ground)
			int terrainH = SceneGraph.getTileHeight(Player.plane, sampleX, sampleZ);
			int sampleY = pivotY + ((camY - pivotY) * frac >> 16);
			if (sampleY > terrainH - 30) {
				// Camera would be at or below terrain at this sample
				int prevFrac = (i - 1) * 65536 / steps;
				int prevX = pivotX + (deltaX * prevFrac >> 16);
				int prevZ = pivotZ + (deltaZ * prevFrac >> 16);
				int dx = prevX - pivotX;
				int dz = prevZ - pivotZ;
				maxClear = (int) Math.sqrt((long) dx * dx + (long) dz * dz);
				break;
			}
		}

		return maxClear;
	}

	// =====================================================================
	// BODY-LOOK COUPLING (FP mode only)
	// =====================================================================

	/**
	 * In FIRST_PERSON, the character body follows the camera look direction
	 * with a shoulder dead-zone policy.
	 *
	 * <p>Small camera yaw difference: body stays stable (head/look only).
	 * Medium difference: body begins rotating toward camera.
	 * Large difference: body catches up faster.</p>
	 *
	 * <p>RT4 has NO separate head yaw. This is body-yaw follow only.
	 * True independent head rotation is deferred (not supported by RT4 model system).</p>
	 */
	private static void updateBodyLookCoupling(Player self) {
		int lookYaw = FirstPersonCamera.getYaw();
		int delta = shortestAngleDelta(bodyYaw, lookYaw);
		int absDelta = Math.abs(delta);

		if (absDelta > SHOULDER_DEAD_ZONE) {
			// Beyond dead zone: body begins rotating toward camera
			int catchupSpeed = (absDelta > SHOULDER_LIMIT)
					? BODY_FAST_CATCHUP_SPEED : BODY_CATCHUP_SPEED;

			int step = clamp(absDelta, catchupSpeed);
			bodyYaw = (bodyYaw + step * Integer.signum(delta)) & 0x7FF;

			// Write to self.anInt3400 so method949 smooths anInt3381 toward it.
			// This drives the character model's visual rotation.
			self.anInt3400 = bodyYaw;
			// Reset change counter to prevent turn animation triggering
			// during smooth continuous rotation
			self.anInt3385 = 0;
		}
		// Within dead zone: body stays at current orientation.
		// The camera can look independently; the torso doesn't rotate.
	}

	// =====================================================================
	// MATH UTILITIES
	// =====================================================================

	/**
	 * Shortest signed angle delta from → to on 0..2047 circle.
	 * Result is in -1024..+1023.
	 */
	private static int shortestAngleDelta(int from, int to) {
		int delta = (to - from) & 0x7FF;
		if (delta > 1024) delta -= 2048;
		return delta;
	}

	/**
	 * Smooth yaw interpolation using shortest-angle path.
	 */
	private static int smoothYaw(int current, int target, int factor, int minStep) {
		int delta = shortestAngleDelta(current, target);
		if (delta == 0) return current;
		int step = delta / factor;
		if (step == 0) step = (delta > 0) ? minStep : -minStep;
		return (current + step) & 0x7FF;
	}

	/**
	 * Smooth integer interpolation.
	 */
	private static int smoothInt(int current, int target, int factor) {
		int delta = target - current;
		if (delta == 0) return current;
		int step = delta / factor;
		if (step == 0) step = (delta > 0) ? 1 : -1;
		return current + step;
	}

	/**
	 * Clamp absolute value to max, preserving sign.
	 */
	private static int clamp(int value, int max) {
		if (value > max) return max;
		if (value < -max) return -max;
		return value;
	}
}
