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
 * <p>Visual camera smoothing (pivot, yaw, pitch, boom distance) is RENDER-timed
 * and frame-rate independent: alpha = 1 - exp(-rate * dt), using
 * {@link GameShell#renderDelta}. Logic ticks only update the authoritative
 * state (desiredDistance, safe zoom, rig transitions); the final camera
 * transform happens once per render in {@link #renderUpdate()} (Phase 3C
 * round #4, P2/P3).</p>
 *
 * <h2>Orientation Fields (RT4)</h2>
 * <ul>
 *   <li>{@code PathingEntity.anInt3400} — target orientation (BODY convention: 0=-Z,512=-X,1024=+Z,1536=+X,
 *       proven from NpcList.method2247 movement mapping and NpcList.method949 positive-multiplier atan2)</li>
 *   <li>{@code PathingEntity.anInt3381} — smoothed orientation (animations use this)</li>
 *   <li>{@code PathingEntity.anInt3376} — orientation speed (default 32)</li>
 *   <li>{@code PathingEntity.anInt3385} — orientation change counter (turn animation trigger)</li>
 * </ul>
 *
 * <h2>Yaw Conventions (Phase 3C round #4, SOURCE VERIFIED)</h2>
 * <p>Camera convention (Camera.method3849: cameraYaw = atan2(dx,dz) * -325.949):
 * 0=NORTH(+Z), 512=WEST(-X), 1024=SOUTH(-Z), 1536=EAST(+X).
 * Body convention differs: 0=-Z, 512=-X, 1024=+Z, 1536=+X.
 * All rig-internal yaw fields (bodyYaw/chaseYaw/chaseYawTarget/freeYaw) are in
 * CAMERA convention; conversion to/from {@code anInt3400} MUST go through
 * {@link #cameraYawToBodyYaw(int)} / {@link #bodyYawToCameraYaw(int)} (involution
 * (1024 - yaw) &amp; 0x7FF, verified at all 4 cardinal directions).</p>
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
	/** Maximum desired distance for CHASE (rig-specific max; Phase 3C round #5 P5). */
	private static final int MAX_DISTANCE = 1350;
	/**
	 * FREE-only maximum desired distance (Phase 3C round #5, P5).
	 * RuneLite-style clearly extended zoom. Only reachable while the rig is
	 * in FREE state; CHASE stays clamped at {@link #MAX_DISTANCE}.
	 * RUNTIME UNVERIFIED — first experiment value, tune after user testing.
	 */
	private static final int FREE_MAX_DISTANCE = 2200;

	/** Scroll wheel step per notch (in ZOOM space). Matches vanilla step. */
	private static final int WHEEL_STEP = 50;

	// ---- Smoothing (render-timed, frame-rate-independent; Phase 3C round #4) ----
	// Rates calibrated to match the old 50Hz feel:
	//   factor-6 distance smoothing @50Hz  ≈ 9/s exponential rate
	//   factor-3 yaw smoothing @50Hz       ≈ 20/s exponential rate
	//   1/16 pivot follow @50Hz            ≈ 3.2/s exponential rate
	/** Exponential rate (per second) for visual boom distance approach. */
	private static final double DIST_RATE_PER_S = 9.0;
	/** Exponential rate (per second) for visual yaw approach. */
	private static final double YAW_RATE_PER_S = 20.0;
	/** Exponential rate (per second) for visual pitch approach. */
	private static final double PITCH_RATE_PER_S = 9.0;
	/** Exponential rate (per second) for visual pivot position follow. */
	private static final double PIVOT_RATE_PER_S = 3.2;

	// ---- Chase camera geometry ----
	/** Camera pitch in chase mode (0..2047). ~45° downward look. */
	private static final int CHASE_PITCH = 256;
	/** Camera pitch in free mode. Slightly steeper for overview. */
	private static final int FREE_PITCH = 300;

	// ---- Body-look coupling (FP rig state only) ----
	/**
	 * Shoulder dead-zone: body doesn't rotate until camera yaw differs
	 * by more than this amount from body yaw. ~5.5 degrees (360° = 2048 units).
	 * Small dead zone so FP body closely follows look direction.
	 */
	private static final int SHOULDER_DEAD_ZONE = 32;
	/**
	 * Maximum yaw difference before body snaps faster. Beyond this, the
	 * catch-up rate increases. ~35 degrees.
	 */
	private static final int SHOULDER_LIMIT = 128;
	/** Normal body yaw catch-up speed (units per 50Hz tick). */
	private static final int BODY_CATCHUP_SPEED = 48;
	/** Fast body yaw catch-up speed when beyond SHOULDER_LIMIT. */
	private static final int BODY_FAST_CATCHUP_SPEED = 96;

	// ---- State fields ----
	/** User's desired camera distance (scroll wheel controls this). Maps to vanilla ZOOM space. */
	private static int desiredDistance = 600;  // = VANILLA_ZOOM_DEFAULT
	/** Actual camera distance (smoothly approaches desired; compressed by walls). */
	private static int actualDistance = 600;
	/** Safe distance (maximum permitted by obstruction). desired ≥ safe ≥ actual. */
	private static int safeDistance = FREE_MAX_DISTANCE;
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

	// ---- Smooth camera position (render-timed visual pivot; Phase 3C round #4) ----
	/** Visual pivot X (fine coords, double accumulator to avoid truncation stall). */
	private static double visPivotX;
	/** Visual pivot Z (fine coords, double accumulator). */
	private static double visPivotZ;
	/** Visual chase/free yaw (camera convention, smoothed per render). */
	private static double visYawD;
	/** Visual pitch (smoothed per render). */
	private static double visPitchD;
	/** Visual boom distance (smoothed per render; source of actualDistance). */
	private static double visDistanceD;
	/** Whether the visual state has been seeded (prevents first-frame snap). */
	private static boolean visInitialized;
	/**
	 * Set on FP→CHASE transition: the next render seeds the visual camera from
	 * the live Camera fields (the FP eye camera) — the ONLY permitted use of
	 * previous camera position as input (explicit transition initialization).
	 */
	private static boolean seedVisualFromCamera;
	/** Obstruction-limited maximum zoom (zoom space), computed each logic tick. */
	private static int safeZoomLimit = 100000;

	// ---- Middle mouse orbit (FREE rig state only) ----
	/** Previous mouse X when middle mouse was last processed (render-timed delta). */
	private static int prevMiddleMouseX;
	/** Previous mouse Y when middle mouse was last processed (render-timed delta). */
	private static int prevMiddleMouseY;
	/** Whether the previous frame had middle mouse held (prevents delta spike on re-press). */
	private static boolean prevMiddleHeld;

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

	/** Returns the current safe camera distance (obstruction-limited). */
	public static int getSafeDistance() {
		return safeDistance;
	}

	/** Returns the FP body yaw (for movement controller in FP rig state). */
	public static int getBodyYaw() {
		return bodyYaw;
	}

	/** Returns the render-timed visual yaw (camera convention, 0..2047). */
	public static int getVisualYaw() {
		return ((int) visYawD) & 0x7FF;
	}

	/**
	 * Converts a CAMERA-convention yaw (0=+Z,512=-X,1024=-Z,1536=+X; proven from
	 * Camera.method3849's negative-multiplier atan2) to the BODY-convention used
	 * by {@code PathingEntity.anInt3400} (0=-Z,512=-X,1024=+Z,1536=+X; proven from
	 * NpcList.method2247's movement→orientation mapping and NpcList.method949's
	 * positive-multiplier atan2).
	 *
	 * <p>Verified at all 4 cardinal directions. The mapping is an involution:
	 * applying it twice returns the original value, so the same function serves
	 * both directions.
	 */
	public static int cameraYawToBodyYaw(int cameraYaw) {
		return (1024 - cameraYaw) & 0x7FF;
	}

	/**
	 * Converts a BODY-convention yaw ({@code anInt3400}) to CAMERA convention.
	 * Same involution as {@link #cameraYawToBodyYaw(int)}.
	 */
	public static int bodyYawToCameraYaw(int bodyYaw) {
		return (1024 - bodyYaw) & 0x7FF;
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
		safeDistance = FREE_MAX_DISTANCE;
		safeZoomLimit = 100000;
		// Re-anchor yaw to current player orientation (body -> camera convention)
		chaseYawTarget = bodyYawToCameraYaw(self.anInt3400);
		chaseYaw = chaseYawTarget;
		// Re-anchor visual camera state (render-timed fields)
		visPivotX = self.xFine;
		visPivotZ = self.zFine;
		visDistanceD = desiredDistance;
		seedVisualFromCamera = false;
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

		// 2. State transitions based on distance (with hysteresis)
		updateStateTransitions();

		// 2b. Lifecycle self-heal (Phase 3C round #5, P3): rigState and
		// FirstPersonCamera.active must ALWAYS agree. Repeated FP<->CHASE
		// cycles must never strand either side (no stale one-shot state).
		if (rigState == RigState.FIRST_PERSON && !FirstPersonCamera.isActive()) {
			FirstPersonCamera.activate();
			writeFpCameraImmediate(self);
		} else if (rigState != RigState.FIRST_PERSON && FirstPersonCamera.isActive()) {
			FirstPersonCamera.deactivate();
			Camera.cameraType = 0; // Rig still owns camera in CHASE/FREE
		}

		// 3. Update camera based on rig state
		switch (rigState) {
			case FIRST_PERSON:
				// FirstPersonCamera.update() already wrote Camera fields.
				// We just need cameraType=0 (set above).
				// Keep the obstruction-limited safe distance fresh so the
				// visual boom (visDistanceD) stays honest for the FP<->CHASE
				// transition thresholds (Phase 3C round #5, P2).
				{
					int fpPivotX = self.xFine;
					int fpPivotZ = self.zFine;
					int fpPivotY = SceneGraph.getTileHeight(Player.plane, fpPivotX, fpPivotZ) - 50;
					int fpExitYaw = bodyYawToCameraYaw(self.anInt3400);
					int fpDesiredZoom = desiredDistance + CHASE_PITCH * 3;
					safeZoomLimit = checkObstruction(fpPivotX, fpPivotZ, fpPivotY, fpExitYaw, CHASE_PITCH, fpDesiredZoom);
					safeDistance = safeZoomLimit - CHASE_PITCH * 3;
					if (safeDistance < MIN_DISTANCE) safeDistance = MIN_DISTANCE;
					if (safeDistance > FREE_MAX_DISTANCE) safeDistance = FREE_MAX_DISTANCE;
				}
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
			// (stored in CAMERA convention; convert from body field)
			bodyYaw = bodyYawToCameraYaw(self.anInt3400);
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
			bodyYaw = bodyYawToCameraYaw(self.anInt3400);
			chaseYaw = bodyYawToCameraYaw(self.anInt3400);
			chaseYawTarget = bodyYawToCameraYaw(self.anInt3400);
			// Initialize visual pivot/yaw to player state (render-timed fields)
			visPivotX = self.xFine;
			visPivotZ = self.zFine;
			visYawD = chaseYaw;
			visPitchD = CHASE_PITCH;
			visDistanceD = desiredDistance;
			visInitialized = true;
		}

		// Default: enter CHASE (not FP). User scrolls to reach FP/FREE.
		rigState = RigState.CHASE;

		actualDistance = desiredDistance;
		safeDistance = FREE_MAX_DISTANCE;
		safeZoomLimit = 100000;
		seedVisualFromCamera = false;
		chasePitch = CHASE_PITCH;
		initialized = true;
	}

	private static void deactivate() {
		active = false;
		initialized = false;
		// Phase 3C round #5 (P3): no stale one-shot state may survive a
		// MODERN -> ORIGINAL -> MODERN cycle.
		seedVisualFromCamera = false;
		visInitialized = false;
		// CameraType restoration is handled by onExitModernMode()
	}

	// =====================================================================
	// SCROLL WHEEL INPUT
	// =====================================================================

	private static void processWheelInput() {
		if (MouseWheel.wheelRotation == 0) return;

		// Wheel ownership: if the mouse is over a scrollable UI component
		// (not the viewport), skip camera zoom so the UI can scroll.
		// Uses the viewport component (clientCode 1337) reference from the
		// previous frame's interface processing. This is a simple heuristic;
		// a full per-component scrollable-area check requires complex nested
		// coordinate math and is deferred.
		if (isMouseOverScrollableUI()) return;

		int rotation = MouseWheel.wheelRotation;
		debugLastWheelRotation = rotation;

		// Scroll IN (rotation < 0) → reduce distance (zoom in)
		// Scroll OUT (rotation > 0) → increase distance (zoom out)
		// Rig-specific maximum (Phase 3C round #5, P5): FREE gets the
		// extended zoom range; CHASE/FP stay clamped at MAX_DISTANCE.
		desiredDistance += rotation * WHEEL_STEP;
		int maxDesired = (rigState == RigState.FREE) ? FREE_MAX_DISTANCE : MAX_DISTANCE;
		if (desiredDistance < MIN_DISTANCE) desiredDistance = MIN_DISTANCE;
		if (desiredDistance > maxDesired) desiredDistance = maxDesired;
	}

	/**
	 * Checks if the mouse is likely over a scrollable UI component rather
	 * than the game viewport.
	 *
	 * <p>Uses the viewport component ({@code InterfaceList.aClass13_26},
	 * clientCode 1337) from the previous frame's interface processing.
	 * If the mouse is outside the viewport bounds but inside the canvas,
	 * it's probably over a side panel or other scrollable UI.</p>
	 */
	public static boolean isMouseOverScrollableUI() {
		Component viewport = InterfaceList.aClass13_26;
		if (viewport == null) return false;
		int mx = Mouse.lastMouseX;
		int my = Mouse.lastMouseY;
		// If mouse is outside the viewport bounds, UI likely owns the wheel
		return mx < viewport.x || mx >= viewport.x + viewport.width
				|| my < viewport.y || my >= viewport.y + viewport.height;
	}

	// =====================================================================
	// STATE TRANSITIONS (with hysteresis)
	// =====================================================================

	private static void updateStateTransitions() {
		RigState previous = rigState;

		// Phase 3C round #5 (P2): the CHASE<->FP boundary is driven by the
		// RENDERED visual boom (visDistanceD), not by desiredDistance. The
		// user perceives ONE coherent transition: FP behavior activates at
		// exactly the moment the camera visually reaches the eye position
		// (and exits only once the visual boom has expanded past the
		// hysteresis threshold). Occlusion-compressed cameras therefore
		// enter FP at the eye position and self-correct on exit.
		switch (rigState) {
			case FIRST_PERSON:
				// Exit FP only once the visual boom has begun expanding past
				// the hysteresis threshold. Escape hatch: if the user has
				// scrolled all the way out (desired ≥ FREE_ENTER), force exit
				// even if obstruction is still compressing the visual boom.
				if (visDistanceD >= FP_EXIT_DISTANCE
						|| desiredDistance >= FREE_ENTER_DISTANCE) {
					rigState = RigState.CHASE;
					// Initialize chase yaw from current body/camera direction
					chaseYaw = bodyYaw;
					chaseYawTarget = bodyYaw;
					chasePitch = CHASE_PITCH;
					// §7: do NOT reset actualDistance here. Spatial state stays
					// continuous; the render-timed boom smoothly grows outward.
					// Seed the visual camera from the live FP eye camera so the
					// first CHASE render frame starts exactly where FP ended.
					seedVisualFromCamera = true;
				}
				break;

			case CHASE:
				// Enter FP exactly when the visual camera reaches the eye
				// position (P2: semantic and visual thresholds coincide).
				if (visDistanceD <= FP_ENTER_DISTANCE) {
					rigState = RigState.FIRST_PERSON;
					// FP camera takes over; bodyYaw preserved for smooth handoff.
					// Clear any stale one-shot seed (P3 lifecycle hygiene).
					seedVisualFromCamera = false;
				} else if (desiredDistance >= FREE_ENTER_DISTANCE) {
					rigState = RigState.FREE;
					// Initialize free camera from the VISUAL chase orientation
					// (continuity: no pop at the CHASE→FREE boundary)
					freeYaw = ((int) visYawD) & 0x7FF;
					freePitch = clamp((int) visPitchD, 383);
					if (freePitch < 128) freePitch = 128;
					seedVisualFromCamera = false;
				}
				break;

			case FREE:
				if (desiredDistance <= FREE_EXIT_DISTANCE) {
					rigState = RigState.CHASE;
					// Seed chaseYaw from current free camera to avoid pop
					chaseYaw = freeYaw;
					// Smoothly acquire character orientation (body -> camera convention)
					chaseYawTarget = (PlayerList.self != null)
							? bodyYawToCameraYaw(PlayerList.self.anInt3400) : chaseYaw;
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
		DebugOverlay.lastCameraWriter = "fp_immediate_transition";
	}

	// =====================================================================
	// CHASE CAMERA — uses Camera.method555 (the proven RT4 transform)
	// =====================================================================

	/**
	 * Chase camera — LOGIC STATE ONLY (Phase 3C round #4).
	 *
	 * <p>Tick duties:
	 * <ol>
	 *   <li>Derive the desired chase yaw target from body orientation.</li>
	 *   <li>Compute the obstruction-limited safe zoom for this tick.</li>
	 * </ol>
	 *
	 * <p>The final camera transform happens ONCE PER RENDER in
	 * {@link #renderUpdate()}:
	 * <pre>
	 *   visual pivot + visual yaw + visual pitch + visual boom
	 *   → ONE Camera.method555() → Camera fields (OUTPUT only)
	 * </pre>
	 * Camera.renderX/renderZ are never read back as persistent input (§4).
	 */
	private static void updateChase(Player self) {
		// Target yaw = character body orientation (body -> camera convention)
		chaseYawTarget = bodyYawToCameraYaw(self.anInt3400);
		chaseYaw = chaseYawTarget;
		chasePitch = CHASE_PITCH;

		// Obstruction: maximum clear zoom toward the desired camera position
		int pivotX = self.xFine;
		int pivotZ = self.zFine;
		int pivotY = SceneGraph.getTileHeight(Player.plane, pivotX, pivotZ) - 50;
		int desiredZoom = desiredDistance + CHASE_PITCH * 3;
		safeZoomLimit = checkObstruction(pivotX, pivotZ, pivotY, chaseYawTarget, CHASE_PITCH, desiredZoom);
		safeDistance = safeZoomLimit - CHASE_PITCH * 3;
		if (safeDistance < MIN_DISTANCE) safeDistance = MIN_DISTANCE;
		if (safeDistance > FREE_MAX_DISTANCE) safeDistance = FREE_MAX_DISTANCE;
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

		// Middle mouse orbit — render-timed mouse delta applied to free camera.
		// Uses Mouse.currentMouseX/Y (live AWT position) for render-rate deltas.
		processMiddleMouseOrbit();

		// LOGIC STATE ONLY (Phase 3C round #4): compute the obstruction-limited
		// safe zoom for this tick. The final camera transform happens ONCE PER
		// RENDER in renderUpdate(). FREE distance authority is the rig's
		// desired/safe/actual distance (§10: one FREE distance authority).
		int pivotX = self.xFine;
		int pivotZ = self.zFine;
		int pivotY = SceneGraph.getTileHeight(Player.plane, pivotX, pivotZ) - 50;
		int desiredZoom = desiredDistance + freePitch * 3;
		safeZoomLimit = checkObstruction(pivotX, pivotZ, pivotY, freeYaw, freePitch, desiredZoom);
		safeDistance = safeZoomLimit - freePitch * 3;
		if (safeDistance < MIN_DISTANCE) safeDistance = MIN_DISTANCE;
		if (safeDistance > FREE_MAX_DISTANCE) safeDistance = FREE_MAX_DISTANCE;
	}

	// =====================================================================
	// MIDDLE MOUSE ORBIT (FREE rig state)
	// =====================================================================

	/**
	 * Processes middle-mouse-drag camera orbit for FREE rig state.
	 *
	 * <p>Uses {@code Mouse.currentMouseX/Y} (live AWT position) against stored
	 * previous-frame position for render-rate delta. This is the same approach
	 * as the original RT4 arrow-key camera input in
	 * {@code GameShell.mainInputLoop()} but driven by mouse delta instead of
	 * key codes.</p>
	 *
	 * <p>Sensitivity is tuned to feel similar to classic RS middle-mouse orbit.
	 * The yaw/pitch clamps match the FREE arrow-key ranges.</p>
	 */
	private static void processMiddleMouseOrbit() {
		boolean middleHeld = Mouse.pressedButton == 2;

		if (!middleHeld) {
			// Reset reference on release so next press doesn't jump
			if (prevMiddleHeld) {
				prevMiddleHeld = false;
			}
			prevMiddleMouseX = Mouse.currentMouseX;
			prevMiddleMouseY = Mouse.currentMouseY;
			return;
		}

		if (!prevMiddleHeld) {
			// First frame of middle mouse press — initialise reference, no delta
			prevMiddleMouseX = Mouse.currentMouseX;
			prevMiddleMouseY = Mouse.currentMouseY;
			prevMiddleHeld = true;
			return;
		}

		int dx = Mouse.currentMouseX - prevMiddleMouseX;
		int dy = Mouse.currentMouseY - prevMiddleMouseY;
		prevMiddleMouseX = Mouse.currentMouseX;
		prevMiddleMouseY = Mouse.currentMouseY;

		if (dx == 0 && dy == 0) return;

		// Sensitivity: classic RS middle mouse orbits roughly 1 yaw unit per 2px.
		// Render-timed scaling is NOT needed here because currentMouseX/Y already
		// reflects the actual mouse position at this render instant — the delta
		// is inherently frame-rate-independent (larger delta on slower frames,
		// smaller delta on faster frames, same total rotation for same physical motion).
		freeYaw -= dx / 2;
		freePitch += dy / 2;
		freeYaw &= 0x7FF;
		if (freePitch < 128) freePitch = 128;
		if (freePitch > 383) freePitch = 383;
	}

	// =====================================================================
	// RENDER-TIMED VISUAL UPDATE (Phase 3C round #4, P2/P3)
	// =====================================================================

	/**
	 * Per-RENDER visual update (frame-rate independent). Called from
	 * {@link GameShell#mainInputLoop()} on the render path.
	 *
	 * <p>Pipeline (§5 — one chase transform per render):
	 * <pre>
	 *   logic state (tick):  desiredDistance, safeZoomLimit, yaw/pitch targets
	 *   visual state (render): visPivotX/Z, visYawD, visPitchD, visDistanceD
	 *   → ONE Camera.method555() → Camera fields (OUTPUT only)
	 * </pre>
	 *
	 * <p>Camera.renderX/renderZ are never read back as persistent input (§4):
	 * the player/pivot is the authority, and the camera world position is
	 * DERIVED every render from pivot + yaw + pitch + boom distance. The only
	 * exception is the explicit FP→CHASE transition seed ({@code seedVisualFromCamera}).
	 *
	 * <p>Interpolation is frame-rate independent: alpha = 1 - exp(-rate * dt).
	 */
	public static void renderUpdate() {
		if (!active || !CameraMode.isModern()) return;
		Player self = PlayerList.self;
		if (self == null) return;

		double dt = (double) GameShell.renderDelta / 1_000_000_000.0;
		if (dt < 0.0) dt = 0.0;
		if (dt > 0.25) dt = 0.25; // Safety clamp for long frames

		if (rigState == RigState.FIRST_PERSON) {
			// FirstPersonCamera owns Camera writes at tick timing in FP.
			// Keep the visual boom continuous for the FP↔CHASE handoff (§7):
			// no distance resets at rig threshold crossings.
			visDistanceD = approachDouble(visDistanceD,
					Math.min(desiredDistance, safeDistance), DIST_RATE_PER_S, dt);
			actualDistance = (int) visDistanceD;
			return;
		}

		// Re-assert camera ownership at RENDER timing (beats packet/region
		// rebuild races that may re-set cameraType=1 between ticks).
		Camera.cameraType = 0;

		if (!visInitialized) {
			visPivotX = self.xFine;
			visPivotZ = self.zFine;
			visYawD = (rigState == RigState.FREE) ? freeYaw : chaseYawTarget;
			visPitchD = (rigState == RigState.FREE) ? freePitch : CHASE_PITCH;
			visDistanceD = desiredDistance;
			visInitialized = true;
		}

		if (seedVisualFromCamera) {
			// Explicit FP→CHASE transition seeding: start the visual camera
			// exactly where the FP eye camera was; the boom grows outward
			// smoothly (spatial continuity, §7).
			seedVisualFromCamera = false;
			visPivotX = self.xFine;
			visPivotZ = self.zFine;
			visYawD = Camera.cameraYaw;
			visPitchD = Camera.cameraPitch;
			visDistanceD = 0;
		}

		double targetPivotX = self.xFine;
		double targetPivotZ = self.zFine;
		double targetYaw = (rigState == RigState.FREE) ? freeYaw : chaseYawTarget;
		double targetPitch = (rigState == RigState.FREE) ? freePitch : CHASE_PITCH;

		// Teleport safety (same threshold as Camera.method4273)
		if (Math.abs(visPivotX - targetPivotX) > 500 || Math.abs(visPivotZ - targetPivotZ) > 500) {
			visPivotX = targetPivotX;
			visPivotZ = targetPivotZ;
		}

		// Visual pivot follow (smooth lag like method4273)
		visPivotX = approachDouble(visPivotX, targetPivotX, PIVOT_RATE_PER_S, dt);
		visPivotZ = approachDouble(visPivotZ, targetPivotZ, PIVOT_RATE_PER_S, dt);

		// Visual yaw (shortest-angle path)
		visYawD = approachAngleDouble(visYawD, targetYaw, YAW_RATE_PER_S, dt);

		// Visual boom distance — THE zoom. Smooth per render (P3).
		// Upper bound is the FREE max so a FREE→CHASE exit never snaps the
		// visual boom (it smoothly converges to the CHASE range; Phase 3C
		// round #5, P5).
		visDistanceD = approachDouble(visDistanceD,
				Math.min(desiredDistance, safeDistance), DIST_RATE_PER_S, dt);
		if (visDistanceD < MIN_DISTANCE) visDistanceD = MIN_DISTANCE;
		if (visDistanceD > FREE_MAX_DISTANCE) visDistanceD = FREE_MAX_DISTANCE;
		actualDistance = (int) visDistanceD;

		// Near-FP blend factor: CHASE converges to the eye camera as the boom
		// approaches FP range (pitch→horizon, zoom→0, pivot→eye height).
		double nearFpT = 1.0;
		if (rigState == RigState.CHASE) {
			nearFpT = (visDistanceD - FP_ENTER_DISTANCE)
					/ (double) (FP_EXIT_DISTANCE - FP_ENTER_DISTANCE);
			if (nearFpT < 0.0) nearFpT = 0.0;
			if (nearFpT > 1.0) nearFpT = 1.0;
			targetPitch *= nearFpT;
		}
		visPitchD = approachDouble(visPitchD, targetPitch, PITCH_RATE_PER_S, dt);

		int pivotX = (int) visPivotX;
		int pivotZ = (int) visPivotZ;
		int terrainH = SceneGraph.getTileHeight(Player.plane, pivotX, pivotZ);
		// Pivot height converges to eye height (terrainH - 200) near FP.
		int pivotY = terrainH - 50 - (int) ((1.0 - nearFpT) * 150.0);

		double zoom = (visDistanceD + visPitchD * 3.0) * nearFpT;
		if (zoom < 0) zoom = 0;
		// Never clip through walls: obstruction limit computed at tick timing.
		if (zoom > safeZoomLimit) zoom = safeZoomLimit;
		if (nearFpT >= 1.0 && zoom < 100) zoom = 100;

		int yawI = ((int) visYawD) & 0x7FF;
		int pitchI = (int) visPitchD;
		if (pitchI < 0) pitchI = 0;
		if (pitchI > 512) pitchI = 512;

		// ONE transform per render → Camera fields are OUTPUT only (§4/§5).
		Camera.method555(pivotX, Rasteriser.screenUpperY, pivotY,
				(int) zoom, yawI, pivotZ, pitchI);
		DebugOverlay.lastCameraWriter = (rigState == RigState.FREE)
				? "rig_render_free" : "rig_render_chase";

		// Keep pivot fields coherent (never read back as input authority).
		Camera.cameraX = Camera.renderX;
		Camera.cameraZ = Camera.renderZ;
		Camera.yawTarget = yawI;
		Camera.pitchTarget = pitchI;
	}

	/**
	 * Frame-rate independent exponential approach (scalar).
	 * alpha = 1 - exp(-ratePerS * dt).
	 */
	private static double approachDouble(double current, double target, double ratePerS, double dt) {
		if (dt <= 0.0) return current;
		return current + (target - current) * (1.0 - Math.exp(-ratePerS * dt));
	}

	/**
	 * Frame-rate independent exponential approach on the 0..2047 angle circle
	 * (shortest-angle path).
	 */
	private static double approachAngleDouble(double current, double target, double ratePerS, double dt) {
		double delta = (target - current) % 2048.0;
		if (delta > 1024.0) delta -= 2048.0;
		if (delta < -1024.0) delta += 2048.0;
		double result = current + delta * (1.0 - Math.exp(-ratePerS * dt));
		if (result < 0) result += 2048.0;
		if (result >= 2048.0) result -= 2048.0;
		return result;
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

			// Compute the sample height FIRST: far/high cameras (e.g. FREE
			// overview zoom) must pass OVER ground-level collision flags and
			// walls. Flag/wall blocking only applies near the terrain (P5).
			int terrainH = SceneGraph.getTileHeight(Player.plane, sampleX, sampleZ);
			// Interpolate the boom Y for height check
			int boomYAtSample = boomY * frac >> 16;
			int camYAtSample = pivotY - boomYAtSample; // method555: anInt40 = targetY - boomY
			boolean nearGround = camYAtSample > terrainH - 200;

			// Check collision flags
			if (nearGround && Player.plane >= 0 && Player.plane < 4
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
			// anInt3400 uses the BODY convention; convert from camera convention.
			self.anInt3400 = cameraYawToBodyYaw(bodyYaw);
			DebugOverlay.lastBodyYawWriter = "fp_body_coupling";
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
	 * Clamp absolute value to max, preserving sign.
	 */
	private static int clamp(int value, int max) {
		if (value > max) return max;
		if (value < -max) return -max;
		return value;
	}
}
