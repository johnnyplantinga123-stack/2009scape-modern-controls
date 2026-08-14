package rt4;

/**
 * Modern continuous movement controller (Phase 3B).
 *
 * <p>Owns self.xFine/zFine prediction, server synchronization, orientation,
 * and animation selection when a modern (non-ORIGINAL) camera mode is active.
 *
 * <h2>Architecture</h2>
 * <ul>
 *   <li>Q16 fixed-point sub-fine accumulators for smooth prediction.</li>
 *   <li>Camera-relative velocity via {@link MathUtils#sin}/{@link MathUtils#cos}.</li>
 *   <li>DDA tile-boundary crossing determines next server tile request.</li>
 *   <li>Bounded pending-tile ring buffer tracks multiple outstanding walk requests.</li>
 *   <li>Server-authoritative tile stored separately from legacy movement queue.</li>
 *   <li>Protocol.java hooks drain legacy queue to prevent accumulation.</li>
 * </ul>
 *
 * <h2>Authority</h2>
 * <p>This controller is the <b>sole NORMAL LOCOMOTION writer</b> for
 * self.xFine/zFine in modern mode. Authoritative exceptions:
 * force-move lerps, teleport, region rebuild.
 *
 * <h2>Mode Isolation</h2>
 * <ul>
 *   <li>ORIGINAL: no modern writes. Legacy method2247 owns everything.</li>
 *   <li>FIRST_PERSON: modern locomotion + FirstPersonCamera.</li>
 *   <li>THIRD_PERSON: same locomotion, independent camera lifecycle.</li>
 * </ul>
 *
 * <h2>Coordinate Convention</h2>
 * <ul>
 *   <li>Internal: LOCAL tile coordinates.</li>
 *   <li>Packet send: worldX = Camera.originX + localTileX.</li>
 *   <li>self.xFine/zFine: LOCAL fine coordinates (tile &lt;&lt; 7 + offset).</li>
 * </ul>
 */
public final class ModernMovementController {

	// ---- WASD key codes ----
	private static final int KEY_W = 33;
	private static final int KEY_A = 48;
	private static final int KEY_S = 49;
	private static final int KEY_D = 50;
	private static final int KEY_SHIFT = 81;

	// ---- Speed constants (fine units per client tick at 50Hz) ----
	private static final int WALK_SPEED = 4;
	private static final int RUN_SPEED = 8;

	// ---- Reconciliation ----
	/** ~2 seconds = ~3.3 server ticks (600ms each). Diagnostic, not blind snap. */
	private static final int RECONCILE_TIMEOUT_TICKS = 100;
	/** Max tile divergence before forced rebase regardless of timeout. */
	private static final int MAX_DIVERGENCE_TILES = 3;

	// ---- Pending request ring buffer ----
	/** Walk ~640ms/tile, Run ~320ms/tile, Server tick ~600ms. Up to 3 outstanding. */
	private static final int PENDING_CAPACITY = 4;

	// ==== Q16 POSITION (FINE-GRAIN PREDICTION) ====
	/** Q16 sub-fine accumulators. self.xFine = (int)(predictedSubX >> 16). */
	private static long predictedSubX;
	private static long predictedSubZ;
	private static int velocityXQ16;
	private static int velocityZQ16;

	// ==== SERVER AUTHORITATIVE STATE (LOCAL tile coords) ====
	private static int lastServerReportedTileX = -1;
	private static int lastServerReportedTileZ = -1;
	private static int lastServerReportTick = -1;

	// ==== PENDING REQUEST RING BUFFER (LOCAL tile coords) ====
	private static final int[] pendingTileX = new int[PENDING_CAPACITY];
	private static final int[] pendingTileZ = new int[PENDING_CAPACITY];
	private static int pendingHead;
	private static int pendingTail;

	// ==== ORIENTATION ====
	/** Updated by modern controller; method949 smooths anInt3381 toward it. */
	private static int targetOrientationAngle;

	// ==== MOVEMENT-SPACE HEADING (Phase 3C round #5, P4) ====
	/**
	 * Stable movement-space heading in CAMERA convention (0=+Z,512=-X,
	 * 1024=-Z,1536=+X). This is the CHASE/FREE movement basis — maintained
	 * by THIS controller, never read back from the visual camera yaw.
	 * Authority chain: WASD → movement heading → velocity → body yaw →
	 * chase camera follows body. The camera yaw never feeds back into
	 * locomotion (breaks the camera→body→movement→camera loop).
	 */
	private static int movementHeading;
	/** Whether the previous tick computed velocity from the FP look yaw. */
	private static boolean wasFirstPersonLastTick;

	// ==== FLAGS ====
	private static boolean initialized;
	private static boolean suspended;

	// ==== Round P4B: F11 EXIT resync ====
	// Round #8 P10: REMOVED time-based post-exit drain. The 150-tick blanket
	// drain was causing ORIGINAL click-to-walk to be swallowed after F11.
	// Now isDrainingServerSteps() == isModernQ16Owner() — no time-based window.
	// Residual in-flight steps are handled by the vanilla queue naturally.
	/** Last DDA walk target sent to the server (F12 "lastSentTile"). */
	private static int lastSentTileX = -1;
	private static int lastSentTileZ = -1;

	/** Reusable movement intent (avoids per-tick allocation). */
	private static final MovementIntent intent = new MovementIntent();

	/** Movement animation state machine. */
	private enum MovementState { IDLE, WALK, RUN }
	private static MovementState lastMovementState = MovementState.IDLE;

	// ---- Debug overlay accessors ----
	/** Returns last server-reported tile X (LOCAL). For debug overlay. */
	public static int getLastServerTileX() { return lastServerReportedTileX; }
	/** Returns last server-reported tile Z (LOCAL). For debug overlay. */
	public static int getLastServerTileZ() { return lastServerReportedTileZ; }
	/** Returns current pending request count. For debug overlay. */
	public static int getPendingCount() { return pendingTail - pendingHead; }
	/** Returns current target orientation angle. For debug overlay. */
	public static int getTargetOrientationAngle() { return targetOrientationAngle; }
	/** Returns predicted sub-fine X (Q16). For debug overlay. */
	public static long getPredictedSubX() { return predictedSubX; }
	/** Returns predicted sub-fine Z (Q16). For debug overlay. */
	public static long getPredictedSubZ() { return predictedSubZ; }
	/** Returns last computed velocity X (Q16). For debug overlay. */
	public static int getVelocityXQ16() { return velocityXQ16; }
	/** Returns last computed velocity Z (Q16). For debug overlay. */
	public static int getVelocityZQ16() { return velocityZQ16; }
	/** Returns last DDA-sent walk target X (LOCAL). For debug overlay (P4B). */
	public static int getLastSentTileX() { return lastSentTileX; }
	/** Returns last DDA-sent walk target Z (LOCAL). For debug overlay (P4B). */
	public static int getLastSentTileZ() { return lastSentTileZ; }

	private ModernMovementController() {
	}

	// =====================================================================
	// LIFECYCLE
	// =====================================================================

	/**
	 * ORIGINAL → MODERN transition.
	 * Correction 3: initialize from current self.xFine/zFine for seamless visual handoff.
	 * Do NOT snap to lastServerReportedTile center.
	 */
	public static void enterModernMode() {
		Player self = PlayerList.self;
		if (self == null) return;

		predictedSubX = ((long) self.xFine) << 16;
		predictedSubZ = ((long) self.zFine) << 16;
		velocityXQ16 = 0;
		velocityZQ16 = 0;

		// Initialize lastServerReportedTile from movement queue (LOCAL tiles)
		lastServerReportedTileX = self.movementQueueX[0];
		lastServerReportedTileZ = self.movementQueueZ[0];
		lastServerReportTick = client.loop;

		targetOrientationAngle = self.anInt3400;
		// P4: seed the stable movement heading from the current body facing
		// (body convention → camera convention).
		movementHeading = ModernCameraRig.bodyYawToCameraYaw(self.anInt3400);
		wasFirstPersonLastTick = false;
		lastMovementState = MovementState.IDLE;
		clearPending();
		initialized = true;
		suspended = false;
	}

	/**
	 * MODERN → ORIGINAL transition.
	 *
	 * <p><b>Round P4B: F11 exit scene/collision resync.</b> Source trace
	 * proved the staleness: while Q16 owns, {@link #update()} writes
	 * self.xFine/zFine every tick but never movementQueueX/Z, and vanilla
	 * {@link PathFinder} BFS originates exactly at movementQueueX[0]/Z[0].
	 * After exit, queue[0] is still wherever it stood when MODERN was
	 * entered, so click-to-walk paths radiate from a stale tile (blocked
	 * walkable tiles, wrong clipping near walls). A normal region load
	 * heals it because {@link LoginManager#method2463} ends in
	 * {@code self.teleport()} → {@link PathingEntity#method2683} — the
	 * vanilla authoritative entity reset (queue[0] + queueSize + xFine/zFine
	 * to the tile centre).
	 *
	 * <p>Modern code never mutates {@link PathFinder#collisionMaps}
	 * (exhaustive grep; only vanilla flagScenery/flagWall/flagTile and the
	 * ::noclip cheat do), so NO collision rebuild is needed — the smallest
	 * proven vanilla refresh is that exact teleport-style reset, reused
	 * here verbatim. Order of operations per the P4B brief:
	 * <ol>
	 *   <li>capture before-state for F12 diagnostics;</li>
	 *   <li>resolve the authoritative tile (last server-confirmed);</li>
	 *   <li>stop Q16 writes (velocity/intent/pending/initialized);</li>
	 *   <li>invoke the vanilla reset ({@code self.teleport(auth, true, auth)})
	 *       which syncs xFine/zFine AND movementQueueX[0]/Z[0];</li>
	 *   <li>open the post-exit drain window so residual in-flight steps are
	 *       consumed, not replayed;</li>
	 *   <li>only then is movement ownership handed to ORIGINAL.</li>
	 * </ol>
	 * MODERN FREE needs none of this: vanilla already owns and the queue
	 * is live (route = VANILLA_FREE_NOOP).
	 */
	public static void exitModernMode() {
		if (!initialized || PlayerList.self == null) {
			velocityXQ16 = 0;
			velocityZQ16 = 0;
			initialized = false;
			suspended = false;
			wasFirstPersonLastTick = false;
			intent.clear();
			return;
		}
		Player self = PlayerList.self;

		// ---- P4B step 1: capture before-state ----
		int beforeFineX = self.xFine;
		int beforeFineZ = self.zFine;
		int queue0BeforeX = self.movementQueueX[0];
		int queue0BeforeZ = self.movementQueueZ[0];
		int pendingBefore = getPendingCount();

		// ---- P4B step 2: authoritative tile (last server-confirmed) ----
		int authTileX = (lastServerReportedTileX >= 0) ? lastServerReportedTileX : self.xFine >> 7;
		int authTileZ = (lastServerReportedTileZ >= 0) ? lastServerReportedTileZ : self.zFine >> 7;

		// ---- P4B step 3: stop modern Q16 writes ----
		boolean q16Owned = !ModernCameraRig.isActive()
				|| ModernCameraRig.getRigState() != ModernCameraRig.RigState.FREE;
		velocityXQ16 = 0;
		velocityZQ16 = 0;
		intent.clear();
		clearPending();
		initialized = false;
		suspended = false;
		wasFirstPersonLastTick = false;
		lastMovementState = MovementState.IDLE;

		// ---- P4B step 4: vanilla-proven collision/pathfinding refresh ----
		String route;
		if (q16Owned) {
			// Reuse the exact vanilla authoritative reset used by the region
			// shift path (LoginManager.method2463 → self.teleport →
			// method2683 hard branch): movementQueueX[0]/Z[0], queueSize and
			// xFine/zFine all land on the authoritative tile centre, so
			// PathFinder BFS originates at the live player tile again.
			self.teleport(authTileX, true, authTileZ);
			// Round #8 P10: NO post-exit drain window. ORIGINAL click-to-walk
			// must work IMMEDIATELY after F11. Residual in-flight steps arrive
			// through the normal vanilla move() path and append cleanly.
			route = "VANILLA_TELEPORT_RESET";
		} else {
			// MODERN FREE: vanilla already owned locomotion — queue is live
			// and consistent. Touching it would cancel legitimate walking.
			route = "VANILLA_FREE_NOOP";
		}

		// ---- P4B F12 diagnostics + one-shot log ----
		DebugOverlay.f11ExitBeforeFineX = beforeFineX;
		DebugOverlay.f11ExitBeforeFineZ = beforeFineZ;
		DebugOverlay.f11ExitAuthTileX = authTileX;
		DebugOverlay.f11ExitAuthTileZ = authTileZ;
		DebugOverlay.f11ExitQueue0BeforeX = queue0BeforeX;
		DebugOverlay.f11ExitQueue0BeforeZ = queue0BeforeZ;
		DebugOverlay.f11ExitQueue0AfterX = self.movementQueueX[0];
		DebugOverlay.f11ExitQueue0AfterZ = self.movementQueueZ[0];
		DebugOverlay.f11ExitAfterFineX = self.xFine;
		DebugOverlay.f11ExitAfterFineZ = self.zFine;
		DebugOverlay.f11ExitServerTileX = lastServerReportedTileX;
		DebugOverlay.f11ExitServerTileZ = lastServerReportedTileZ;
		DebugOverlay.f11ExitLastSentTileX = lastSentTileX;
		DebugOverlay.f11ExitLastSentTileZ = lastSentTileZ;
		DebugOverlay.f11ExitPendingMoves = pendingBefore;
		DebugOverlay.f11ExitCollisionRefresh = q16Owned;
		DebugOverlay.f11ExitCollisionRefreshRoute = route;
		System.out.println("[F11-ORIGINAL-RESYNC] authTile=" + authTileX + "," + authTileZ
				+ " queue0Before=" + queue0BeforeX + "," + queue0BeforeZ
				+ " queue0After=" + self.movementQueueX[0] + "," + self.movementQueueZ[0]
				+ " collisionRefreshRoute=" + route);
	}

	/** FIRST_PERSON ↔ THIRD_PERSON: locomotion unchanged, camera only. */
	public static void onModernModeSwitch() {
		// No prediction reset needed.
	}

	// =====================================================================
	// Round #6B/C P7-P11: MODERN FREE = VANILLA LOCOMOTION
	// =====================================================================

	/**
	 * Whether THIS controller (modern continuous Q16 prediction) currently
	 * owns self locomotion. Round #6B/C: the rig's FREE state hands movement
	 * authority back to vanilla click-to-walk (movement queue + method2247),
	 * so "modern" alone is no longer sufficient — only FP/CHASE own Q16.
	 *
	 * <p>Call sites that previously used {@code CameraMode.isModern()} for
	 * movement ownership now use this predicate:
	 * <ul>
	 *   <li>{@link NpcList#method4514} — self method2247 skip gate.</li>
	 *   <li>{@link Protocol#readSelfPlayerInfo} — server-step queue drains.</li>
	 *   <li>{@link #update()} — Q16 prediction gate.</li>
	 * </ul>
	 * Exactly ONE movement owner at any time: ORIGINAL → vanilla; MODERN
	 * FREE → vanilla ({@code VANILLA_FREE}); MODERN FP/CHASE → this
	 * controller ({@code MODERN_Q16}).
	 */
	public static boolean isModernQ16Owner() {
		if (!CameraMode.isModern()) {
			return false;
		}
		// Rig active in FREE = vanilla locomotion owns movement.
		// Rig inactive keeps the proven Phase 3B behaviour (Q16 owns).
		return !ModernCameraRig.isActive()
				|| ModernCameraRig.getRigState() != ModernCameraRig.RigState.FREE;
	}

	/**
	 * Round #8 P10: gate for the Protocol server-step drain hooks.
	 * Returns true ONLY while Q16 owns locomotion. The time-based post-exit
	 * drain window has been REMOVED — ORIGINAL click-to-walk must work
	 * IMMEDIATELY after F11. Residual in-flight steps are handled by the
	 * vanilla queue naturally (they arrive through the normal vanilla move()
	 * path and append cleanly to the live queue).
	 */
	public static boolean isDrainingServerSteps() {
		return isModernQ16Owner();
	}

	/** Current locomotion owner for the debug overlay (P14). */
	public static String getMovementOwner() {
		if (!CameraMode.isModern()) {
			return "ORIGINAL";
		}
		return isModernQ16Owner() ? "MODERN_Q16" : "VANILLA_FREE";
	}

	/**
	 * CHASE → FREE handoff (Round #6B/C P9). Called from
	 * {@link ModernCameraRig#updateStateTransitions()} the tick the rig
	 * enters FREE. Order of operations:
	 * <ol>
	 *   <li>Modern continuous Q16 writes stop ({@link #update()} returns
	 *       early because {@link #isModernQ16Owner()} is now false).</li>
	 *   <li>Velocity/intent zeroed, stale pending walk requests cleared.</li>
	 *   <li>The vanilla movement queue is rebased to the CURRENT live tile
	 *       ({@code movementQueueX[0]}) and cleared via {@code method2689()}
	 *       so {@link NpcList#method2247} starts stationary at the player's
	 *       actual position — no tile snap (xFine/zFine untouched; mid-tile
	 *       offsets are safe, vanilla interpolation tolerates ≤256 fine).</li>
	 *   <li>Vanilla PathFinder/movement queue becomes the movement owner;
	 *       WASD is inert because {@link #update()} no longer runs.</li>
	 * </ol>
	 * Any server steps still in flight from prior Q16 walk requests arrive
	 * through the normal vanilla {@code move()} path and append cleanly.
	 */
	public static void onEnterFreeMode() {
		velocityXQ16 = 0;
		velocityZQ16 = 0;
		intent.clear();
		clearPending();
		Player self = PlayerList.self;
		if (self != null) {
			// Rebase queue[0] to the live tile BEFORE clearing so the next
			// vanilla move() computes its step from the real position.
			self.movementQueueX[0] = self.xFine >> 7;
			self.movementQueueZ[0] = self.zFine >> 7;
			self.method2689();
		}
		lastMovementState = MovementState.IDLE;
		wasFirstPersonLastTick = false;
		DebugOverlay.intentForwardPct = 0;
		DebugOverlay.intentRightPct = 0;
		System.out.println("[MODERN-MOVE] HANDOFF: CHASE -> FREE (vanilla locomotion owns)");
	}

	/**
	 * FREE → CHASE handoff (Round #6B/C P10). Called from
	 * {@link ModernCameraRig#updateStateTransitions()} the tick the rig
	 * leaves FREE. Order of operations:
	 * <ol>
	 *   <li>Vanilla auto-path ownership ends: the movement queue is cleared
	 *       ({@code method2689()}) so method2247 can no longer move self.</li>
	 *   <li>Modern prediction is seeded from the LIVE player state
	 *       (predictedSub from xFine/zFine — no tile snap).</li>
	 *   <li>Server-authoritative tile rebased to the live tile.</li>
	 *   <li>WASD enabled again; CHASE (this controller) owns movement.</li>
	 * </ol>
	 *
	 * <p><b>Arbitration rule (documented per brief P10):</b> if the player
	 * was walking a vanilla path in FREE and scrolls inward mid-path, the
	 * client queue is cleared immediately and manual WASD takes ownership.
	 * The server-side route is reset by the NEXT modern DDA walk packet
	 * ({@code sendModernWalkPacket} = MOVE_GAMECLICK). Any residual server
	 * steps for the cancelled path arrive through the Q16 drain hooks
	 * ({@link #onServerStep}) and are reconciled — never replayed as
	 * vanilla movement, because {@link NpcList#method4514} skips method2247
	 * for self while {@link #isModernQ16Owner()} is true.
	 */
	public static void onExitFreeMode() {
		Player self = PlayerList.self;
		if (self == null) {
			return;
		}
		// 1: cancel vanilla auto-path (client side).
		self.method2689();
		// 2-4: seed prediction from live state (no snap).
		predictedSubX = ((long) self.xFine) << 16;
		predictedSubZ = ((long) self.zFine) << 16;
		velocityXQ16 = 0;
		velocityZQ16 = 0;
		lastServerReportedTileX = self.xFine >> 7;
		lastServerReportedTileZ = self.zFine >> 7;
		lastServerReportTick = client.loop;
		// 5-6: heading from current body facing; discard stale modern state.
		movementHeading = ModernCameraRig.bodyYawToCameraYaw(self.anInt3400);
		targetOrientationAngle = self.anInt3400;
		wasFirstPersonLastTick = false;
		lastMovementState = MovementState.IDLE;
		clearPending();
		suspended = false;
		initialized = true;
		System.out.println("[MODERN-MOVE] HANDOFF: FREE -> CHASE (modern Q16 owns)"
				+ " tile=" + lastServerReportedTileX + "," + lastServerReportedTileZ);
	}

	/**
	 * Region rebuild adjusts all entity xFine/zFine by -deltaOrigin*128.
	 * self.xFine/zFine are externally overwritten — rebase prediction from them.
	 */
	public static void onSceneRebuild() {
		if (!initialized) return;
		Player self = PlayerList.self;
		if (self == null) return;

		predictedSubX = ((long) self.xFine) << 16;
		predictedSubZ = ((long) self.zFine) << 16;
		velocityXQ16 = 0;
		velocityZQ16 = 0;

		lastServerReportedTileX = self.xFine >> 7;
		lastServerReportedTileZ = self.zFine >> 7;
		lastServerReportTick = client.loop;
		// P4: re-anchor the movement heading after a region rebuild.
		movementHeading = ModernCameraRig.bodyYawToCameraYaw(self.anInt3400);
		clearPending();
	}

	// =====================================================================
	// SERVER HOOKS (called from Protocol.readSelfPlayerInfo)
	// =====================================================================

	/**
	 * Server confirmed a step to this LOCAL tile.
	 * Stores authoritative tile, consumes matching pending entries, reconciles.
	 */
	public static void onServerStep(int localTileX, int localTileZ) {
		// Phase 3B fix #3: diagnostic logging for server sync analysis
		int predTileX = (int) (predictedSubX >> 16) >> 7;
		int predTileZ = (int) (predictedSubZ >> 16) >> 7;
		System.out.println("[MODERN-MOVE] SERVER_STEP: localTile=" + localTileX + "," + localTileZ
				+ " predictedTile=" + predTileX + "," + predTileZ
				+ " prevServerTile=" + lastServerReportedTileX + "," + lastServerReportedTileZ
				+ " divergence=" + Math.max(Math.abs(predTileX - localTileX), Math.abs(predTileZ - localTileZ))
				+ " pending=" + (pendingTail - pendingHead));

		lastServerReportedTileX = localTileX;
		lastServerReportedTileZ = localTileZ;
		lastServerReportTick = client.loop;
		consumePendingExact(localTileX, localTileZ);
		reconcile();
	}

	/**
	 * Far teleport directly overwrote self.xFine/zFine.
	 * Rebase prediction from externally-updated fine coordinates.
	 */
	public static void onServerTeleportFine(int fineX, int fineZ) {
		predictedSubX = ((long) fineX) << 16;
		predictedSubZ = ((long) fineZ) << 16;
		velocityXQ16 = 0;
		velocityZQ16 = 0;
		lastServerReportedTileX = fineX >> 7;
		lastServerReportedTileZ = fineZ >> 7;
		lastServerReportTick = client.loop;
		clearPending();
	}

	// =====================================================================
	// MAIN UPDATE
	// =====================================================================

	/**
	 * Per-tick update called from {@link ModernControlController#update()}.
	 * Execution order: this runs BEFORE Protocol.method1756() and NpcList.method4514().
	 */
	public static void update() {
		if (!initialized) return;
		// Round #6B/C P7: FREE hands locomotion back to vanilla — no Q16
		// writes, no WASD, no packets from this controller while FREE.
		if (!isModernQ16Owner()) return;
		Player self = PlayerList.self;
		if (self == null) return;
		DebugOverlay.movementUpdateTickCount++;
		if (!ModernControlController.isGameplayInputAllowed()) {
			intent.clear();
			return;
		}

		// ---- Correction 4: Force-move suspension ----
		// While force movement is active: no WASD, no packet, no prediction,
		// no self.xFine/zFine write, NO movementSeqId write, NO orientation write.
		// Let existing force-move/server animation and method879 operate normally.
		boolean forceMoveActive = (client.loop < self.forceMoveCyclesToStart)
				|| (self.forceMoveCyclesToDest >= client.loop);
		if (forceMoveActive) {
			velocityXQ16 = 0;
			velocityZQ16 = 0;
			suspended = true;
			return;
		}
		if (suspended) {
			// Force-move just ended — rebase prediction from externally-updated position
			predictedSubX = ((long) self.xFine) << 16;
			predictedSubZ = ((long) self.zFine) << 16;
			lastServerReportedTileX = self.xFine >> 7;
			lastServerReportedTileZ = self.zFine >> 7;
			lastServerReportTick = client.loop;
			clearPending();
			suspended = false;
		}

		// ---- Read WASD input ----
		readInput();

		// Round #6A (P2/P5): diagnostics — intent components, heading, velocity,
		// body target and chase yaw target for the F12 overlay. Written every
		// tick (cheap field stores); the overlay only renders them when visible.
		DebugOverlay.intentForwardPct = Math.round(intent.forward * 100f);
		DebugOverlay.intentRightPct = Math.round(intent.right * 100f);
		DebugOverlay.movementHeading = movementHeading;

		if (!intent.hasMovement()) {
			velocityXQ16 = 0;
			velocityZQ16 = 0;
			// P4: while idle, keep the stable movement heading in sync with
			// the body facing so the next WASD input starts from the current
			// orientation. No feedback loop: no input → no velocity here.
			if (!ModernCameraRig.isFirstPersonRigState()) {
				movementHeading = ModernCameraRig.bodyYawToCameraYaw(self.anInt3400);
			}
			wasFirstPersonLastTick = false;
			// State transition → IDLE
			if (lastMovementState != MovementState.IDLE) {
				lastMovementState = MovementState.IDLE;
				selectAnimationForState();
				// Review #2: In FP rig state (including scroll-FP within THIRD_PERSON
				// CameraMode), body-look coupling owns self.anInt3400. Do NOT snap it.
				if (!ModernCameraRig.isFirstPersonRigState()) {
					// Phase 3B fix #1: snap visual orientation to target so
					// method949 (orientation smoothing) does not see a yaw error
					// and replace the idle animation with walk/turn animation.
					self.anInt3381 = self.anInt3400;
					self.anInt3385 = 0;
				} else {
					// In FP rig state: sync smoothed to target to prevent method949
					// turn animation, but don't change anInt3400 (body-look owns it)
					self.anInt3385 = 0;
				}
			}
			// Defense in depth: method949 can overwrite movementSeqId with
			// walkAnimation when it detects idle + yaw error. The legacy path
			// (method2247) also sets idle every tick. This matches that behavior.
			else {
				self.movementSeqId = self.getBasType().idleAnimationId;
			}
			return;
		}

		// ---- Normalize diagonal ----
		intent.normalize();

		// ---- Compute camera-relative velocity ----
		// RT4 camera yaw convention (verified from Camera.method3849 line 324:
		//   cameraYaw = atan2(deltaX, deltaZ) * -325.949 & 0x7FF):
		//   yaw 0    = NORTH (+Z)
		//   yaw 512  = WEST  (-X)
		//   yaw 1024 = SOUTH (-Z)
		//   yaw 1536 = EAST  (+X)
		//
		// Correct orthonormal basis for this convention:
		//   Forward = (-sin[yaw], +cos[yaw])
		//   Right   = (+cos[yaw], +sin[yaw])
		//
		// Phase 3B fix #3: FIRST_PERSON uses live FP camera yaw.
		// Phase 3C round #5 (P4): CHASE/FREE use the stable movement-space
		// heading maintained by this controller — NOT the body orientation
		// and NOT the visual camera yaw. Body turns toward locomotion and
		// the camera follows the body; locomotion never reads the camera.
		// anInt3400 is in BODY convention; the heading is kept in CAMERA
		// convention so the proven movement basis below operates in a
		// single convention.
		int camYaw = CameraMode.getCameraRelativeYaw();
		int yaw;
		if (camYaw >= 0) {
			// FP: W/S/A/D relative to the live FP look direction (proven basis).
			yaw = camYaw;
			wasFirstPersonLastTick = true;
		} else {
			if (wasFirstPersonLastTick) {
				// FP→CHASE/FREE edge: body faced the FP look direction; sync
				// the movement heading from it for a seamless handoff.
				movementHeading = ModernCameraRig.bodyYawToCameraYaw(self.anInt3400);
				wasFirstPersonLastTick = false;
			}
			yaw = movementHeading;
		}
		boolean running = intent.runRequested;
		int speed = running ? RUN_SPEED : WALK_SPEED;

		// Use float multiplication to preserve fractional diagonal component
		float fwdMul = intent.forward * speed;
		float strMul = intent.right * speed;

		velocityXQ16 = (int) (fwdMul * (-MathUtils.sin[yaw & 2047])
				+ strMul * MathUtils.cos[yaw & 2047]);
		velocityZQ16 = (int) (fwdMul * MathUtils.cos[yaw & 2047]
				+ strMul * MathUtils.sin[yaw & 2047]);

		// ---- Apply Q16 prediction ----
		predictedSubX += velocityXQ16;
		predictedSubZ += velocityZQ16;
		self.xFine = (int) (predictedSubX >> 16);
		self.zFine = (int) (predictedSubZ >> 16);

		// ---- DDA tile boundary crossing → server sync ----
		performDDACheck();

		// ---- Orientation ----
		// Review #2: In FP rig state (including scroll-FP within THIRD_PERSON
		// CameraMode), body-look coupling (ModernCameraRig) owns self.anInt3400.
		// Do NOT overwrite it from velocity.
		// In CHASE/FREE rig state, movement direction determines body facing.
		if (!ModernCameraRig.isFirstPersonRigState()) {
			// Face movement direction. The RT4 angle convention uses a NEGATIVE
			// multiplier (same as Camera.method3849): angle = atan2(velX, velZ) * -325.949
			// This maps: north=0, west=512, south=1024, east=1536 (CAMERA convention).
			// anInt3400 uses the BODY convention, so convert before writing
			// (Phase 3C round #4 P1: writing camera-convention values directly
			// made the body face the opposite direction).
			if (velocityXQ16 != 0 || velocityZQ16 != 0) {
				int camAngle = (int) (Math.atan2(
						(double) velocityXQ16,
						(double) velocityZQ16) * -325.949D) & 0x7FF;
				targetOrientationAngle = ModernCameraRig.cameraYawToBodyYaw(camAngle);
				self.anInt3400 = targetOrientationAngle;
				DebugOverlay.lastBodyYawWriter = "movement_controller";
			}
		}

		// Round #6A (P2): temporary CHASE diagonal diagnostics (1 Hz while
		// actively moving in CHASE — not flooded during idle or FP).
		// Round #6B/C P12: extended with animation fields to trace the run
		// animation flicker (seq id, frame, state, queue size).
		if (!ModernCameraRig.isFirstPersonRigState() && client.loop % 50 == 0) {
			System.out.println("[MOVE-DEBUG] rig=" + ModernCameraRig.getRigState()
					+ " intentF=" + intent.forward + " intentR=" + intent.right
					+ " movementHeading=" + movementHeading
					+ " velocityX=" + velocityXQ16 + " velocityZ=" + velocityZQ16
					+ " bodyTarget=" + targetOrientationAngle
					+ " chaseTargetYaw=" + ModernCameraRig.getChaseYawTarget()
					+ " moveSeqId=" + self.movementSeqId
					+ " frame=" + self.anInt3407 + "/" + self.anInt3396
					+ " state=" + lastMovementState
					+ " run=" + intent.runRequested
					+ " queueSize=" + self.movementQueueSize
					+ " owner=" + getMovementOwner());
		}

		// ---- Animation state machine ----
		// Only change animation on state transition (not every tick).
		MovementState currentState = running ? MovementState.RUN : MovementState.WALK;
		if (currentState != lastMovementState) {
			lastMovementState = currentState;
			selectAnimationForState();
		}
	}

	// =====================================================================
	// DDA TILE BOUNDARY DETECTION
	// =====================================================================

	/**
	 * Digital Differential Analyzer: calculate which tile boundary the continuous
	 * trajectory crosses first, and send a walk request for that tile.
	 *
	 * <p>Correction 4: simultaneous X+Z boundary → diagonal target tile.
	 * <p>Correction 5: exact boundary math without -1 fudge.
	 */
	private static void performDDACheck() {
		int currentTileX = (int) (predictedSubX >> 16) >> 7;
		int currentTileZ = (int) (predictedSubZ >> 16) >> 7;

		int ticksX = Integer.MAX_VALUE;
		int ticksZ = Integer.MAX_VALUE;
		int nextTileX = currentTileX;
		int nextTileZ = currentTileZ;
		long distXQ16 = 0;
		long distZQ16 = 0;

		// X axis
		if (velocityXQ16 > 0) {
			// Positive: next boundary is RIGHT edge of current tile = (tile+1)*128
			int boundaryFine = (currentTileX + 1) << 7;
			distXQ16 = ((long) boundaryFine << 16) - predictedSubX;
			ticksX = (int) (distXQ16 / velocityXQ16);
			nextTileX = currentTileX + 1;
		} else if (velocityXQ16 < 0) {
			// Negative: next boundary is LEFT edge of current tile = tile*128
			int boundaryFine = currentTileX << 7;
			distXQ16 = predictedSubX - ((long) boundaryFine << 16);
			ticksX = (int) (distXQ16 / (-velocityXQ16));
			nextTileX = currentTileX - 1;
		}

		// Z axis
		if (velocityZQ16 > 0) {
			int boundaryFine = (currentTileZ + 1) << 7;
			distZQ16 = ((long) boundaryFine << 16) - predictedSubZ;
			ticksZ = (int) (distZQ16 / velocityZQ16);
			nextTileZ = currentTileZ + 1;
		} else if (velocityZQ16 < 0) {
			int boundaryFine = currentTileZ << 7;
			distZQ16 = predictedSubZ - ((long) boundaryFine << 16);
			ticksZ = (int) (distZQ16 / (-velocityZQ16));
			nextTileZ = currentTileZ - 1;
		}

		// Determine which boundary is crossed first
		int targetTileX = currentTileX;
		int targetTileZ = currentTileZ;
		boolean crossed = false;

		if (ticksX != Integer.MAX_VALUE && ticksZ != Integer.MAX_VALUE) {
			// Cross-multiply to compare without floating point:
			// ticksX < ticksZ  ⟺  distX * |velZ| < distZ * |velX|
			long crossX = distXQ16 * (long) Math.abs(velocityZQ16);
			long crossZ = distZQ16 * (long) Math.abs(velocityXQ16);
			if (crossX < crossZ) {
				targetTileX = nextTileX;
				crossed = true;
			} else if (crossZ < crossX) {
				targetTileZ = nextTileZ;
				crossed = true;
			} else {
				// SIMULTANEOUS: diagonal crossing (Correction 4)
				targetTileX = nextTileX;
				targetTileZ = nextTileZ;
				crossed = true;
			}
		} else if (ticksX != Integer.MAX_VALUE) {
			targetTileX = nextTileX;
			crossed = true;
		} else if (ticksZ != Integer.MAX_VALUE) {
			targetTileZ = nextTileZ;
			crossed = true;
		}

		if (crossed) {
			maybeSendWalkRequest(targetTileX, targetTileZ);
		}
	}

	// =====================================================================
	// SERVER SYNC — PENDING RING BUFFER
	// =====================================================================

	/**
	 * Send walk request if target tile is not already pending and not the
	 * last server-reported tile. Uses LOCAL coords internally, converts to
	 * WORLD only for the packet.
	 */
	private static void maybeSendWalkRequest(int targetLocalTileX, int targetLocalTileZ) {
		// Dedup: don't send if this exact tile is already pending
		if (pendingContains(targetLocalTileX, targetLocalTileZ)) return;

		// Don't send if target == last server reported (already confirmed)
		if (targetLocalTileX == lastServerReportedTileX
				&& targetLocalTileZ == lastServerReportedTileZ) return;

		// Validate local tile bounds
		if (targetLocalTileX < 0 || targetLocalTileX > 103
				|| targetLocalTileZ < 0 || targetLocalTileZ > 103) return;

		// Convert LOCAL → WORLD for packet (Correction 7: explicit coordinate space)
		int worldX = Camera.originX + targetLocalTileX;
		int worldZ = Camera.originZ + targetLocalTileZ;

		ClientProt.sendModernWalkPacket(worldX, worldZ, intent.runRequested);
		// P4B: track the last sent target for F11 EXIT diagnostics.
		lastSentTileX = targetLocalTileX;
		lastSentTileZ = targetLocalTileZ;

		// Phase 3B fix #3: diagnostic logging for server sync analysis
		System.out.println("[MODERN-MOVE] PACKET: localTile=" + targetLocalTileX + "," + targetLocalTileZ
				+ " worldTile=" + worldX + "," + worldZ
				+ " run=" + intent.runRequested
				+ " predictedTile=" + ((int)(predictedSubX >> 16) >> 7) + "," + ((int)(predictedSubZ >> 16) >> 7)
				+ " serverTile=" + lastServerReportedTileX + "," + lastServerReportedTileZ
				+ " pending=" + (pendingTail - pendingHead));

		// Record in pending ring buffer (LOCAL coords)
		pendingAdd(targetLocalTileX, targetLocalTileZ);
	}

	/**
	 * Add to ring buffer. If buffer full, drop oldest.
	 */
	private static void pendingAdd(int localX, int localZ) {
		int idx = pendingTail % PENDING_CAPACITY;
		pendingTileX[idx] = localX;
		pendingTileZ[idx] = localZ;
		pendingTail++;
		if (pendingTail - pendingHead > PENDING_CAPACITY) {
			pendingHead++;
		}
	}

	/**
	 * Check if exact tile exists in pending ring.
	 */
	private static boolean pendingContains(int localX, int localZ) {
		for (int i = pendingHead; i < pendingTail; i++) {
			int idx = i % PENDING_CAPACITY;
			if (pendingTileX[idx] == localX && pendingTileZ[idx] == localZ) return true;
		}
		return false;
	}

	/**
	 * Correction 7: exact path matching.
	 * If the exact server-reported tile exists in pending ring,
	 * discard entries through and including that entry.
	 * If genuinely different, clear stale pending prediction.
	 */
	private static void consumePendingExact(int serverTileX, int serverTileZ) {
		// Search for exact match in pending ring
		for (int i = pendingHead; i < pendingTail; i++) {
			int idx = i % PENDING_CAPACITY;
			if (pendingTileX[idx] == serverTileX && pendingTileZ[idx] == serverTileZ) {
				// Found: discard through and including this entry
				pendingHead = i + 1;
				return;
			}
		}
		// No exact match: server reported a genuinely different tile.
		// Clear stale pending prediction (authoritative route divergence).
		clearPending();
	}

	private static void clearPending() {
		pendingHead = 0;
		pendingTail = 0;
	}

	private static boolean pendingEmpty() {
		return pendingHead >= pendingTail;
	}

	// =====================================================================
	// RECONCILIATION
	// =====================================================================

	/**
	 * Correction 2: reconciliation does NOT rebase from self.xFine/zFine during
	 * normal modern locomotion (those ARE the predicted position).
	 * Uses lastServerReportedTile → tile-center fine coords instead.
	 *
	 * <p>Correction 9: timeout alone does not mean server rejection.
	 * Requires actual divergence + no pending requests for correction.
	 */
	private static void reconcile() {
		if (!initialized) return;

		int predictedTileX = (int) (predictedSubX >> 16) >> 7;
		int predictedTileZ = (int) (predictedSubZ >> 16) >> 7;
		int dx = predictedTileX - lastServerReportedTileX;
		int dz = predictedTileZ - lastServerReportedTileZ;
		int divergence = Math.max(Math.abs(dx), Math.abs(dz));

		boolean timeoutExpired = (client.loop - lastServerReportTick) > RECONCILE_TIMEOUT_TICKS;

		if (divergence > MAX_DIVERGENCE_TILES) {
			// Large divergence — always rebase regardless of timeout
			DebugOverlay.lastMovementRebaseReason = "large_divergence_" + divergence;
			System.out.println("[MODERN-MOVE] REBASE: large divergence=" + divergence
					+ " predictedTile=" + predictedTileX + "," + predictedTileZ
					+ " serverTile=" + lastServerReportedTileX + "," + lastServerReportedTileZ
					+ " pending=" + (pendingTail - pendingHead));
			rebaseFromServerTile();
		} else if (timeoutExpired && divergence > 0 && pendingEmpty()) {
			// Timeout + actual divergence + no pending = genuine desync
			DebugOverlay.lastMovementRebaseReason = "timeout_divergence_" + divergence;
			System.out.println("[MODERN-MOVE] REBASE: timeout divergence=" + divergence
					+ " predictedTile=" + predictedTileX + "," + predictedTileZ
					+ " serverTile=" + lastServerReportedTileX + "," + lastServerReportedTileZ);
			rebaseFromServerTile();
		}
		// Timeout alone with divergence==0 → do nothing (server just hasn't updated)
		// Timeout with pending outstanding → do nothing (server is processing)
	}

	/**
	 * Rebase prediction from lastServerReported tile, preserving the fractional
	 * offset within the tile so the player doesn't snap to tile centre.
	 * This prevents the visible "snap to centre" hitching that occurred when
	 * the previous implementation always rebased to exact tile centre.
	 */
	private static void rebaseFromServerTile() {
		// Compute the current fine position relative to the server tile
		int currentFineX = (int) (predictedSubX >> 16);
		int currentFineZ = (int) (predictedSubZ >> 16);
		int serverFineX = (lastServerReportedTileX << 7) + 64;
		int serverFineZ = (lastServerReportedTileZ << 7) + 64;
		// Preserve the fractional offset from server tile centre
		// but clamp it to within the tile to prevent runaway prediction
		int offsetX = currentFineX - serverFineX;
		int offsetZ = currentFineZ - serverFineZ;
		// Clamp offset to ±128 (one tile radius) to prevent excessive divergence
		if (offsetX > 128) offsetX = 128;
		if (offsetX < -128) offsetX = -128;
		if (offsetZ > 128) offsetZ = 128;
		if (offsetZ < -128) offsetZ = -128;
		predictedSubX = ((long) (serverFineX + offsetX)) << 16;
		predictedSubZ = ((long) (serverFineZ + offsetZ)) << 16;
		velocityXQ16 = 0;
		velocityZQ16 = 0;
	}

	// =====================================================================
	// ANIMATION — STATE-MACHINE SELECTION
	// =====================================================================

	/**
	 * State-machine animation selection. Only called on movement state transitions
	 * (IDLE→WALK, WALK→RUN, etc.) to avoid restarting the animation every tick.
	 *
	 * <p>Uses BasType fields: idleAnimationId, walkAnimation, runAnimationId.
	 * Falls back to idleAnimationId if the walk/run animation is -1.
	 *
	 * <p>Round #6B/C P12: frame counters are reset when the selected
	 * SEQUENCE actually changes, so the new animation starts cleanly at
	 * frame 0 instead of inheriting a mid-sequence index from the previous
	 * animation (a source-plausible flicker candidate: an inherited frame
	 * index near/past the new sequence's end makes {@link NpcList#method879}
	 * wrap immediately). No per-tick restart — this only runs on state
	 * transitions. RUNTIME UNVERIFIED.
	 */
	private static void selectAnimationForState() {
		Player self = PlayerList.self;
		BasType bas = self.getBasType();
		int selectedAnim;

		switch (lastMovementState) {
			case IDLE:
				selectedAnim = bas.idleAnimationId;
				break;
			case RUN:
				selectedAnim = (bas.runAnimationId != -1) ? bas.runAnimationId : bas.idleAnimationId;
				break;
			case WALK:
			default:
				selectedAnim = (bas.walkAnimation != -1) ? bas.walkAnimation : bas.idleAnimationId;
				break;
		}

		if (self.movementSeqId != selectedAnim) {
			// Clean start for the new sequence (P12).
			self.anInt3407 = 0;
			self.anInt3396 = 0;
		}
		self.movementSeqId = selectedAnim;
	}

	// =====================================================================
	// INPUT
	// =====================================================================

	private static void readInput() {
		intent.clear();
		if (Keyboard.pressedKeys[KEY_W]) intent.forward += 1f;
		if (Keyboard.pressedKeys[KEY_S]) intent.forward -= 1f;
		if (Keyboard.pressedKeys[KEY_D]) intent.right += 1f;
		if (Keyboard.pressedKeys[KEY_A]) intent.right -= 1f;
		intent.runRequested = Keyboard.pressedKeys[KEY_SHIFT];
	}
}
