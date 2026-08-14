package rt4;

/**
 * Modern camera rig (Phase 3C + Review #2).
 *
 * <p>Manages the continuous zoom/camera continuum inside MODERN control mode:
 * <pre>
 *   FIRST_PERSON  ← scroll out →  CHASE  ← scroll out →  FREE
 * </pre>
 *
 * <h2>Architecture</h2>
 * <ul>
 *   <li>One authoritative {@code desiredDistance} controlled by scroll wheel.</li>
 *   <li>Smooth {@code actualDistance} (50Hz tick-based exponential smoothing) toward desired.</li>
 *   <li>Hysteresis thresholds prevent mode flicker at FP/CHASE and CHASE/FREE boundaries.</li>
 *   <li>Chase/free camera uses {@link Camera#method555} — the proven RT4 camera transform —
 *       instead of a hand-written parallel implementation.</li>
 *   <li>Camera obstruction reduces effective zoom so the camera smoothly pulls in when
 *       walls are between the player and the desired position, and smoothly recovers
 *       when the path clears.</li>
 *   <li>FREE mode reuses classic RT4 camera input ({@code Camera.yawTarget/pitchTarget})
 *       and the classic transform. Modern WASD remains active.</li>
 * </ul>
 *
 * <h2>Camera/Control Separation</h2>
 * <p>CameraMode (ORIGINAL / THIRD_PERSON) is the CONTROL PROFILE.
 * Inside MODERN, this rig manages the CAMERA RIG state (FP / CHASE / FREE).
 * ORIGINAL remains a pristine legacy fallback, untouched by this rig.</p>
 *
 * <h2>Wheel Input Path</h2>
 * <p>JavaMouseWheel → MouseWheel.wheelRotation (set each mainLoop tick in client.java:1725).
 * This rig reads wheelRotation during the 50Hz game tick (ModernControlController.update()),
 * BEFORE the UI scroll processing (InterfaceList/ScriptRunner in the render pipeline).
 * Both camera zoom and UI scroll read the same MouseWheel.wheelRotation value.
 * UI scroll operates on component scrollY (separate variable from desiredDistance),
 * so there is no variable conflict — but both can react to the same wheel event.
 * Proper UI ownership (skip camera zoom when scrollable UI is under cursor) is TODO.</p>
 *
 * <h2>Smoothing Honesty</h2>
 * <p>Distance smoothing is 50Hz tick-based exponential smoothing (NOT frame-rate-independent
 * render smoothing). Camera yaw/pitch smoothing is also 50Hz tick-based. Only visual
 * camera position smoothing would benefit from render-timed integration (deferred).</p>
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

	// ---- Distance continuum (maps to vanilla ZOOM parameter space) ----
	// Vanilla 2009Scape: ZOOM default=600, min=100, max=1200, step=50/notch
	// (traced from legacy MouseWheel.java:32 and Camera.java:73)
	// RuneLite-style extension: +150 beyond vanilla max → 1350
	/** At or below this distance: FP mode. (= VANILLA_ZOOM_MIN) */
	private static final int FP_ENTER_DISTANCE = 100;
	/** At or above this distance (from FP): exit FP to CHASE. Hysteresis: > FP_ENTER. */
	private static final int FP_EXIT_DISTANCE = 200;
	/** At or above this distance (from CHASE): enter FREE. (= VANILLA_ZOOM_MAX) */
	private static final int FREE_ENTER_DISTANCE = 1200;
	/** At or below this distance (from FREE): exit FREE to CHASE. Hysteresis: < FREE_ENTER. */
	private static final int FREE_EXIT_DISTANCE = 1100;
	/** Minimum allowed desired distance (FP clamped). */
	private static final int MIN_DISTANCE = 0;
	/** Maximum allowed desired distance (FREE clamped). (= VANILLA_ZOOM_MAX + 150) */
	private static final int MAX_DISTANCE = 1350;

	/** Scroll wheel step per notch (in ZOOM space). Matches vanilla step. */
	private static final int WHEEL_STEP = 50;

	// ---- Smoothing (50Hz tick-based, NOT frame-rate-independent) ----
	/** Exponential smoothing factor for actual→desired distance (per 50Hz tick). */
	private static final int DISTANCE_SMOOTH_FACTOR = 6;
	/** Exponential smoothing factor for chase yaw (per 50Hz tick). */
	private static final int YAW_SMOOTH_FACTOR = 8;
	/** Minimum yaw step to prevent stalling at very small differences. */
	private static final int YAW_SMOOTH_MIN = 2;

	// ---- Chase camera geometry ----
	/** Camera pitch in chase mode (0..2047). ~45° downward look. */
	private static final int CHASE_PITCH = 256;
	/** Camera pitch in free mode. Slightly steeper for overview. */
	private static final int FREE_PITCH = 300;

	// ---- Body-look coupling (FP rig state only) ----
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
	/** Normal body yaw catch-up speed (units per 50Hz tick). */
	private static final int BODY_CATCHUP_SPEED = 24;
	/** Fast body yaw catch-up speed when beyond SHOULDER_LIMIT. */
	private static final int BODY_FAST_CATCHUP_SPEED = 64;

	// ---- State fields ----
	/** User's desired camera distance (scroll wheel controls this). Maps to vanilla ZOOM space. */
	private static int desiredDistance = 600;  // = VANILLA_ZOOM_DEFAULT
	/** Actual camera distance (smoothly approaches desired; compressed by walls). */
	private static int actualDistance = 600;
	/** Safe distance (maximum permitted by obstruction). desired ≥ safe ≥ actual. */
	private static int safeDistance = MAX_DISTANCE;
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
	/** Previous rig state for transition detection (FP camera lifecycle). */
	private static RigState prevRigState = RigState.CHASE;

	// ---- Smooth camera position (follows player like Camera.method4273) ----
	private static int smoothCameraX;
	private static int smoothCameraZ;

	// ---- Debug overlay state ----
	/** Temporary debug: last wheel rotation processed. */
	private static int debugLastWheelRotation;
	/** Temporary debug: frame counter for throttling debug output. */
	private static int debugFrameCounter;

	// ---- Saved camera state for ORIGINAL restoration ----
	/** Saved cameraType from ORIGINAL mode (captured once before modern mutation). */
	private static int savedCameraType = 1;
	private static int savedCameraPitch = 256;
	private static int savedPitchTarget = 256;
	private static int savedCameraYaw;
	private static int savedYawTarget;
	private static int savedCameraX;
	private static int savedCameraZ;
	private static int savedAnInt40;
	/** Whether the original camera state has been saved (prevents double-save). */
	private static boolean originalStateSaved = false;

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

	/** Returns the current actual camera distance. */
	public static int getActualDistance() {
		return actualDistance;
	}

	/** Returns the current desired camera distance. */
	public static int getDesiredDistance() {
		return desiredDistance;
	}

	/** Returns the FP body yaw (for movement controller in FP rig state). */
	public static int getBodyYaw() {
		return bodyYaw;
	}

	/**
	 * Returns whether the rig is in FIRST_PERSON state.
	 * Used by ModernMovementController to determine body orientation ownership.
	 */
	public static boolean isFirstPersonRigState() {
		return active && rigState == RigState.FIRST_PERSON;
	}

	/**
	 * Called when entering MODERN from ORIGINAL.
	 * Saves the full legacy camera state ONCE (before any modern mutation)
	 * for later restoration when returning to ORIGINAL.
	 */
	public static void onEnterModernMode() {
		if (!originalStateSaved) {
			savedCameraType = Camera.cameraType;
			savedCameraPitch = Camera.cameraPitch;
			savedPitchTarget = (int) Camera.pitchTarget;
			savedCameraYaw = Camera.cameraYaw;
			savedYawTarget = (int) Camera.yawTarget;
			savedCameraX = Camera.cameraX;
			savedCameraZ = Camera.cameraZ;
			savedAnInt40 = Camera.anInt40;
			originalStateSaved = true;
		}
		Camera.cameraType = 0;
	}

	/**
	 * Called when returning to ORIGINAL from MODERN.
	 * Restores the full saved legacy camera state so the vanilla camera
	 * returns exactly where it was before MODERN was entered.
	 */
	public static void onExitModernMode() {
		if (originalStateSaved) {
			Camera.cameraType = savedCameraType;
			Camera.cameraPitch = savedCameraPitch;
			Camera.pitchTarget = savedPitchTarget;
			Camera.cameraYaw = savedCameraYaw;
			Camera.yawTarget = savedYawTarget;
			Camera.cameraX = savedCameraX;
			Camera.cameraZ = savedCameraZ;
			Camera.anInt40 = savedAnInt40;
			originalStateSaved = false;
		}
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
		safeDistance = MAX_DISTANCE;
		// Re-anchor yaw to current player orientation
		chaseYawTarget = self.anInt3400;
		chaseYaw = chaseYawTarget;
		// Re-anchor smooth camera position
		smoothCameraX = self.xFine;
		smoothCameraZ = self.zFine;
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
	 * Per-tick update (50Hz). Called from {@link ModernControlController#update()}
	 * AFTER FirstPersonCamera.update() and BEFORE {@link ModernMovementController#update()}.
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
				// FirstPersonCamera.update() already wrote Camera fields.
				// We just need cameraType=0 (set above).
				break;
			case CHASE:
				updateChase(self);
				break;
			case FREE:
				updateFree(self);
				break;
		}

		// 4. Body-look coupling (FP rig state only)
		if (rigState == RigState.FIRST_PERSON) {
			updateBodyLookCoupling(self);
		} else {
			// In CHASE/FREE, body yaw tracks current orientation for smooth transition
			bodyYaw = self.anInt3400;
		}

		// 5. Throttled debug output (every 50 ticks = ~1 second)
		debugFrameCounter++;
		if (debugFrameCounter >= 50) {
			debugFrameCounter = 0;
			System.out.println("[CAMERA-RIG-DEBUG] state=" + rigState
					+ " desired=" + desiredDistance + " actual=" + actualDistance
					+ " safe=" + safeDistance
					+ " cameraType=" + Camera.cameraType
					+ " renderX=" + Camera.renderX + " renderZ=" + Camera.renderZ
					+ " anInt40=" + Camera.anInt40
					+ " cameraYaw=" + Camera.cameraYaw + " cameraPitch=" + Camera.cameraPitch
					+ " selfX=" + self.xFine + " selfZ=" + self.zFine
					+ " bodyYaw=" + bodyYaw + " anInt3400=" + self.anInt3400
					+ " anInt3381=" + self.anInt3381
					+ " chatActive=" + ModernControlController.isChatInputActive()
					+ " FPactive=" + FirstPersonCamera.isActive()
					+ " FPvalidPos=" + FirstPersonCamera.hasValidPosition()
					+ " wheelRot=" + debugLastWheelRotation
					+ " playerPlane=" + Player.plane
					+ " roofMode=" + ScriptRunner.method4047()
					+ " fpStructOverride=" + FirstPersonCamera.isActive()
					+ " allLevelsVisible=" + SceneGraph.allLevelsAreVisible());
		}
	}

	// =====================================================================
	// ACTIVATION / DEACTIVATION
	// =====================================================================

	private static void activate() {
		active = true;
		initialized = false;
		prevRigState = RigState.CHASE;
		// cameraType is already 0 (set by onEnterModernMode before this runs)
		Camera.cameraType = 0;

		Player self = PlayerList.self;
		if (self != null) {
			bodyYaw = self.anInt3400;
			chaseYaw = self.anInt3400;
			chaseYawTarget = self.anInt3400;
			// Initialize smooth camera position to player position
			smoothCameraX = self.xFine;
			smoothCameraZ = self.zFine;
		}

		// Default: enter CHASE (not FP). User scrolls to reach FP/FREE.
		rigState = RigState.CHASE;

		actualDistance = desiredDistance;
		safeDistance = MAX_DISTANCE;
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

		int rotation = MouseWheel.wheelRotation;
		debugLastWheelRotation = rotation;

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
					// Initialize actualDistance to FP_EXIT for smooth handoff
					// (not to desiredDistance which could be far away)
					actualDistance = FP_EXIT_DISTANCE;
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
			// FP camera lifecycle: activate/deactivate on rig state transitions
			if (rigState == RigState.FIRST_PERSON && previous != RigState.FIRST_PERSON) {
				// Entering FP: activate mouse-look, cursor lock
				FirstPersonCamera.activate();
				// Defensive FP camera write (Phase 3C runtime stabilisation).
				// FirstPersonCamera.update() ran BEFORE the rig state changed,
				// so it returned early (saw CHASE/FREE, not FP). The chase camera
				// from the previous tick is still in Camera fields. Write the FP
				// eye position NOW so the very first render frame in FP shows
				// the correct camera position — not a stale chase offset.
				writeFpCameraImmediate(PlayerList.self);
			} else if (previous == RigState.FIRST_PERSON && rigState != RigState.FIRST_PERSON) {
				// Exiting FP: deactivate mouse-look, unlock cursor
				FirstPersonCamera.deactivate();
				Camera.cameraType = 0; // Rig still owns camera in CHASE/FREE
			}

			System.out.println("[CAMERA-RIG] State: " + previous + " → " + rigState
					+ " desired=" + desiredDistance + " actual=" + actualDistance);
		}
		prevRigState = rigState;
	}

	// =====================================================================
	// FP IMMEDIATE CAMERA WRITE (defensive, transition-tick only)
	// =====================================================================

	/**
	 * Writes the FP camera position immediately on the transition tick.
	 *
	 * <p>When the rig transitions from CHASE/FREE to FP, FirstPersonCamera.update()
	 * has already run (before the rig) and returned early because the rig was in
	 * CHASE/FREE state. This method ensures the Camera fields are set to the FP
	 * eye position on the exact transition tick, preventing a stale chase camera
	 * offset from persisting for one frame.</p>
	 *
	 * <p>This is a safety net. On subsequent ticks, FirstPersonCamera.update()
	 * runs normally and owns Camera field writes.</p>
	 */
	private static void writeFpCameraImmediate(Player self) {
		if (self == null) return;
		if (Player.plane < 0 || Player.plane > 3) return;
		if (SceneGraph.tileHeights == null) return;

		int fineX = self.xFine;
		int fineZ = self.zFine;
		int tileX = fineX >> 7;
		int tileZ = fineZ >> 7;
		if (tileX < 0 || tileX > 103 || tileZ < 0 || tileZ > 103) return;

		int groundHeight = SceneGraph.getTileHeight(Player.plane, fineX, fineZ);

		Camera.renderX = fineX;
		Camera.renderZ = fineZ;
		Camera.anInt40 = groundHeight - 200; // EYE_HEIGHT = 200
		Camera.cameraYaw = FirstPersonCamera.getYaw();
		Camera.cameraPitch = 0; // Horizon
		Camera.yawTarget = Camera.cameraYaw;
		Camera.pitchTarget = Camera.cameraPitch;
		Camera.cameraX = fineX;
		Camera.cameraZ = fineZ;
		Camera.cameraType = 0; // Prevent legacy camera interference
	}

	// =====================================================================
	// CHASE CAMERA — uses Camera.method555 (the proven RT4 transform)
	// =====================================================================

	/**
	 * Chase camera: follows behind character body orientation.
	 * Uses {@link Camera#method555} for the camera transform, ensuring
	 * consistency with the RT4 rendering pipeline (including GL scaling).
	 *
	 * <p>Camera position is computed by method555 as:
	 * <pre>
	 *   renderX = targetX - sin(yaw) * cos(pitch) * zoom
	 *   renderZ = targetZ - cos(yaw) * cos(pitch) * zoom
	 *   anInt40 = targetY + sin(pitch) * zoom
	 * </pre>
	 * where zoom = actualDistance + pitch * 3 (actualDistance maps to vanilla ZOOM space).
	 */
	private static void updateChase(Player self) {
		// Target yaw = character body orientation
		chaseYawTarget = self.anInt3400;

		// Smooth yaw interpolation (shortest angle, 50Hz tick-based)
		chaseYaw = smoothYaw(chaseYaw, chaseYawTarget, YAW_SMOOTH_FACTOR, YAW_SMOOTH_MIN);

		// Smooth pitch transition
		chasePitch = smoothInt(chasePitch, CHASE_PITCH, 6);

		// Smooth distance (50Hz tick-based exponential smoothing)
		smoothDistance();

		// Smooth camera position follow (like Camera.method4273)
		updateSmoothCameraPosition(self);

		// Compute target/pivot point (near player feet, like original camera)
		int pivotX = smoothCameraX;
		int pivotZ = smoothCameraZ;
		int pivotY = SceneGraph.getTileHeight(Player.plane, pivotX, pivotZ) - 50;

		// Map desired distance to RT4 zoom parameter.
		// Original RT4: ZOOM(600) + pitchTarget(128..383)*3 = 984..1749
		// Our mapping: actualDistance directly maps to ZOOM space.
		int zoom = actualDistance + chasePitch * 3;
		if (zoom < 100) zoom = 100;

		// Camera obstruction: reduce effective zoom if wall between player and camera
		int clearDist = checkObstruction(pivotX, pivotZ, pivotY, chaseYaw, chasePitch, zoom);
		int effectiveZoom = zoom;
		if (clearDist < zoom && zoom > 0) {
			int ratio = (clearDist * 65536 / zoom);
			effectiveZoom = Math.max(100, zoom * ratio >> 16);
		}

		// Use the proven RT4 camera transform
		Camera.method555(pivotX, Rasteriser.screenUpperY, pivotY,
				effectiveZoom, chaseYaw, pivotZ, chasePitch);

		// Copy render camera position to smooth-follow fields
		Camera.cameraX = Camera.renderX;
		Camera.cameraZ = Camera.renderZ;
	}

	// =====================================================================
	// FREE CAMERA — uses Camera.method555 + classic input targets
	// =====================================================================

	/**
	 * Free camera: classic-style overview camera.
	 * Arrow keys control orbit (freeYaw/freePitch), matching the classic RT4
	 * arrow key input from {@code GameShell.mainInputLoop()}.
	 * Camera position computed by {@link Camera#method555}.
	 * Modern WASD remains active; movement uses body orientation.
	 *
	 * <p>Arrow key input uses {@code Keyboard.pressedKeys} (continuous polled state)
	 * instead of {@code InterfaceList.keyQueueSize} (event queue). This ensures
	 * smooth, continuous orbit while keys are held — matching the original RT4
	 * camera arrow key behavior. Input is scaled by {@code GameShell.renderDelta}
	 * for frame-rate-independent speed (same approach as {@code mainInputLoop}).</p>
	 */
	private static void updateFree(Player self) {
		// Arrow key camera orbit — continuous polled input with render-timed scaling.
		// Rates match the original 50Hz values (±4 pitch, ±16 yaw per tick)
		// scaled by renderDelta for frame-rate independence.
		// At 50Hz (20ms tick): scale ≈ 1.0, matching the original per-tick amounts.
		// At 60fps (16.67ms): scale ≈ 0.833, giving ~300 units/sec (same as 50Hz×4=200/s? no, 50*4=200/s, 60*3.33=200/s).
		double renderScale = (double) GameShell.updateDelta / 20_000_000.0;
		if (renderScale < 0.1) renderScale = 0.1;  // Safety clamp for very fast frames
		if (renderScale > 5.0) renderScale = 5.0;  // Safety clamp for very slow frames

		if (Keyboard.pressedKeys[Keyboard.KEY_UP]) {
			freePitch -= (int) (4 * renderScale);
		}
		if (Keyboard.pressedKeys[Keyboard.KEY_DOWN]) {
			freePitch += (int) (4 * renderScale);
		}
		if (Keyboard.pressedKeys[Keyboard.KEY_LEFT]) {
			freeYaw -= (int) (16 * renderScale);
		}
		if (Keyboard.pressedKeys[Keyboard.KEY_RIGHT]) {
			freeYaw += (int) (16 * renderScale);
		}

		// Clamp pitch (same range as Camera.clampCameraAngle)
		if (freePitch < 128) freePitch = 128;
		if (freePitch > 383) freePitch = 383;
		freeYaw &= 0x7FF;

		// Smooth distance
		smoothDistance();

		// Smooth camera position follow
		updateSmoothCameraPosition(self);

		// Compute target/pivot point
		int pivotX = smoothCameraX;
		int pivotZ = smoothCameraZ;
		int pivotY = SceneGraph.getTileHeight(Player.plane, pivotX, pivotZ) - 50;

		// Map desired distance to RT4 zoom parameter
		int zoom = actualDistance + freePitch * 3;
		if (zoom < 100) zoom = 100;

		// Camera obstruction
		int clearDist = checkObstruction(pivotX, pivotZ, pivotY, freeYaw, freePitch, zoom);
		int effectiveZoom = zoom;
		if (clearDist < zoom && zoom > 0) {
			int ratio = (clearDist * 65536 / zoom);
			effectiveZoom = Math.max(100, zoom * ratio >> 16);
		}

		// Use the proven RT4 camera transform
		Camera.method555(pivotX, Rasteriser.screenUpperY, pivotY,
				effectiveZoom, freeYaw, pivotZ, freePitch);

		// Copy render camera position to smooth-follow fields
		Camera.cameraX = Camera.renderX;
		Camera.cameraZ = Camera.renderZ;
	}

	// =====================================================================
	// SMOOTH CAMERA POSITION (follows player like Camera.method4273)
	// =====================================================================

	/**
	 * Smooth camera position that follows the player with slight lag.
	 * Replicates the smooth-follow behavior of the classic RT4 follow camera
	 * ({@link Camera#method4273}) so the chase/free camera doesn't snap
	 * instantly to the player's position during movement.
	 */
	private static void updateSmoothCameraPosition(Player self) {
		int targetX = self.xFine;
		int targetZ = self.zFine;

		// Teleport safety (same threshold as Camera.method4273)
		if (smoothCameraX - targetX < -500 || smoothCameraX - targetX > 500
				|| smoothCameraZ - targetZ < -500 || smoothCameraZ - targetZ > 500) {
			smoothCameraX = targetX;
			smoothCameraZ = targetZ;
		}

		// Smooth follow (1/16 per tick, like method4273)
		if (smoothCameraZ != targetZ) {
			smoothCameraZ += (targetZ - smoothCameraZ) / 16;
		}
		if (smoothCameraX != targetX) {
			smoothCameraX += (targetX - smoothCameraX) / 16;
		}
	}

	// =====================================================================
	// DISTANCE SMOOTHING
	// =====================================================================

	/**
	 * Exponential smoothing of actualDistance toward the target distance
	 * (min of desiredDistance and safeDistance).
	 *
	 * <p>This is 50Hz tick-based smoothing (NOT frame-rate-independent).
	 * The smoothing factor controls how many ticks it takes to converge.
	 */
	private static void smoothDistance() {
		int targetDist = Math.min(desiredDistance, safeDistance);
		int delta = targetDist - actualDistance;
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
	 * checks terrain height and collision flags. Uses directional wall flags
	 * from {@link CollisionMap} to detect wall edges between tiles (not just
	 * fully-occupied tiles).</p>
	 *
	 * <p>This is CAMERA collision, NOT player movement collision (Phase 4).</p>
	 */
	private static int checkObstruction(int pivotX, int pivotZ, int pivotY,
			int yaw, int pitch, int zoom) {
		// Compute the desired camera position using method555 math
		// to know where the camera WOULD be placed.
		int invPitch = 2048 - pitch & 0x7FF;
		int invYaw = 2048 - yaw & 0x7FF;

		int boomX = 0;
		int boomZ = zoom;
		int boomY = 0;

		if (invPitch != 0) {
			boomY = MathUtils.sin[invPitch] * -zoom >> 16;
			boomZ = MathUtils.cos[invPitch] * zoom >> 16;
		}
		if (invYaw != 0) {
			boomX = MathUtils.sin[invYaw] * boomZ >> 16;
			boomZ = boomZ * MathUtils.cos[invYaw] >> 16;
		}

		int camX = pivotX - boomX;
		int camZ = pivotZ - boomZ;

		int deltaX = camX - pivotX;
		int deltaZ = camZ - pivotZ;
		int fineDist = (int) Math.sqrt((long) deltaX * deltaX + (long) deltaZ * deltaZ);

		if (fineDist < 64) return fineDist; // Too close to bother checking

		// Step along the pivot→camera line, checking collision at each tile
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
				int prevFrac = (i - 1) * 65536 / steps;
				int prevX = pivotX + (deltaX * prevFrac >> 16);
				int prevZ = pivotZ + (deltaZ * prevFrac >> 16);
				int dx = prevX - pivotX;
				int dz = prevZ - pivotZ;
				maxClear = (int) Math.sqrt((long) dx * dx + (long) dz * dz);
				break;
			}

			// Check collision flags
			if (Player.plane >= 0 && Player.plane < 4
					&& PathFinder.collisionMaps != null
					&& PathFinder.collisionMaps[Player.plane] != null) {
				int flags = PathFinder.collisionMaps[Player.plane].flags[tileX][tileZ];

				// Full tile collision (scenery, decor, blocked tile)
				// 0x100 = scenery (non-projectile-blocking), 0x20000 = fully blocked
				// 0x40000 = ground decor, 0x200000 = flagged tile
				if ((flags & 0x240100) != 0) {
					int prevFrac = (i - 1) * 65536 / steps;
					int prevX = pivotX + (deltaX * prevFrac >> 16);
					int prevZ = pivotZ + (deltaZ * prevFrac >> 16);
					int dx = prevX - pivotX;
					int dz = prevZ - pivotZ;
					maxClear = (int) Math.sqrt((long) dx * dx + (long) dz * dz);
					break;
				}

				// Directional wall check: if the camera path crosses a tile edge
				// that has a wall, block the camera. Use the same directional
				// masks as PathFinder: N=0x102, S=0x120, W=0x108, E=0x180.
				if (i > 1) {
					int prevFrac = (i - 1) * 65536 / steps;
					int prevSampleX = pivotX + (deltaX * prevFrac >> 16);
					int prevSampleZ = pivotZ + (deltaZ * prevFrac >> 16);
					int prevTileX = prevSampleX >> 7;
					int prevTileZ = prevSampleZ >> 7;

					if (prevTileX != tileX || prevTileZ != tileZ) {
						// Camera path crossed a tile boundary. Check if the
						// destination tile has a wall facing the crossing direction.
						int crossDx = tileX - prevTileX;
						int crossDz = tileZ - prevTileZ;
						int wallMask;
						if (crossDx > 0) wallMask = 0x108;      // Entering from west: check W wall
						else if (crossDx < 0) wallMask = 0x180;  // Entering from east: check E wall
						else if (crossDz > 0) wallMask = 0x102;  // Entering from south: check S wall
						else wallMask = 0x120;                    // Entering from north: check N wall

						if ((flags & wallMask) != 0) {
							int dx = prevSampleX - pivotX;
							int dz = prevSampleZ - pivotZ;
							maxClear = (int) Math.sqrt((long) dx * dx + (long) dz * dz);
							break;
						}
					}
				}
			}

			// Check terrain height (camera shouldn't be below ground)
			int terrainH = SceneGraph.getTileHeight(Player.plane, sampleX, sampleZ);
			// Interpolate the boom Y for height check
			int boomYAtSample = boomY * frac >> 16;
			int camYAtSample = pivotY - boomYAtSample; // method555: anInt40 = targetY - boomY
			if (camYAtSample > terrainH - 30) {
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
	// BODY-LOOK COUPLING (FP rig state only)
	// =====================================================================

	/**
	 * In FIRST_PERSON rig state, the character body follows the camera look
	 * direction with a shoulder dead-zone policy.
	 *
	 * <p>Small camera yaw difference: body stays stable.
	 * Medium difference: body begins rotating toward camera.
	 * Large difference: body catches up faster.</p>
	 *
	 * <p>RT4 has NO separate head yaw. This is body-yaw follow only.
	 * True independent head rotation is deferred (not supported by RT4 model system).</p>
	 *
	 * <p>This method is the SOLE WRITER of self.anInt3400 when the rig is in
	 * FP state. ModernMovementController must NOT overwrite it (guarded by
	 * checking {@link #isFirstPersonRigState()}).</p>
	 */
	private static void updateBodyLookCoupling(Player self) {
		int lookYaw = FirstPersonCamera.getYaw();
		int delta = shortestAngleDelta(bodyYaw, lookYaw);
		int absDelta = Math.abs(delta);

		if (absDelta > SHOULDER_DEAD_ZONE) {
			int catchupSpeed = (absDelta > SHOULDER_LIMIT)
					? BODY_FAST_CATCHUP_SPEED : BODY_CATCHUP_SPEED;

			int step = clamp(absDelta, catchupSpeed);
			bodyYaw = (bodyYaw + step * Integer.signum(delta)) & 0x7FF;

			// Write to self.anInt3400 so method949 smooths anInt3381 toward it.
			self.anInt3400 = bodyYaw;
			// Reset change counter to prevent turn animation triggering
			self.anInt3385 = 0;
		}
		// Within dead zone: body stays at current orientation.
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
