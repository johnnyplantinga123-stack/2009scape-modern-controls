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
	/** Authentic CollisionMap occupancy mask used by the vanilla pathfinder. */
	private static final int BLOCKED_TILE_MASK = 0x1240100;
	private static final int EAST_WALL = 0x80;
	private static final int WEST_WALL = 0x8;
	private static final int POSITIVE_Z_WALL = 0x20;
	private static final int NEGATIVE_Z_WALL = 0x2;
	/** Fine-unit inset for the player's collision footprint at a wall edge. */
	private static final int FINE_FOOTPRINT_INSET = 16;
	/** Short proven-valid history used for local collision recovery. */
	private static final int VALID_FINE_HISTORY_CAPACITY = 16;
	private static final int MAX_FINE_RECOVERY_DISTANCE = 128;

	// ---- Reconciliation ----
	/** ~2 seconds = ~3.3 server ticks (600ms each). Diagnostic, not blind snap. */
	private static final int RECONCILE_TIMEOUT_TICKS = 100;
	/** Max tile divergence before forced rebase regardless of timeout. */
	private static final int MAX_DIVERGENCE_TILES = 3;

	// ---- Pending request ring buffer ----
	/** Walk ~640ms/tile, Run ~320ms/tile, Server tick ~600ms. Up to 3 outstanding. */
	private static final int PENDING_CAPACITY = 4;
	/** Hard cap on local prediction while server reporting is stalled. */
	private static final int MAX_PREDICTION_LEAD_TILES = PENDING_CAPACITY;

	// ==== Q16 POSITION (FINE-GRAIN PREDICTION) ====
	/** Q16 sub-fine accumulators. self.xFine = (int)(predictedSubX >> 16). */
	private static long predictedSubX;
	private static long predictedSubZ;
	private static int lastValidFineX;
	private static int lastValidFineZ;
	private static boolean lastValidInitialized;
	private static final int[] validFineHistoryX = new int[VALID_FINE_HISTORY_CAPACITY];
	private static final int[] validFineHistoryZ = new int[VALID_FINE_HISTORY_CAPACITY];
	private static int validFineHistoryHead;
	private static int validFineHistoryCount;
	private static int velocityXQ16;
	private static int velocityZQ16;

	// ==== SERVER AUTHORITATIVE STATE (LOCAL tile coords) ====
	private static int lastServerReportedTileX = -1;
	private static int lastServerReportedTileZ = -1;
	/**
	 * Exact fine coordinates are only present in a far-teleport update. Normal
	 * player-step updates carry a tile/direction, so they must never be
	 * represented as a made-up tile-centre fine coordinate in diagnostics.
	 */
	private static int lastExactServerFineX = -1;
	private static int lastExactServerFineZ = -1;
	private static int lastServerReportTick = -1;
	/**
	 * Local movement anchor used for validating the next Q16 request.  This is
	 * deliberately separate from lastServerReportedTile: during VANILLA_FREE,
	 * queue[0] is the vanilla route's current server-side position, but it is
	 * not necessarily a Q16 acknowledgement received by onServerStep().
	 */
	private static int movementAnchorTileX = -1;
	private static int movementAnchorTileZ = -1;
	private static int lastPacketDiagnosticTick = -100;
	private static boolean lastPacketAccepted;
	private static String lastPacketReason = "none";
	private static int lastPacketTargetX = -1;
	private static int lastPacketTargetZ = -1;
	/** Throttle a held doorway/corner rejection to one record per ~second. */
	private static int lastDoorParityLogTick = -100;
	private static int lastDoorParitySourceX = Integer.MIN_VALUE;
	private static int lastDoorParitySourceZ = Integer.MIN_VALUE;
	private static int lastDoorParityTargetX = Integer.MIN_VALUE;
	private static int lastDoorParityTargetZ = Integer.MIN_VALUE;
	private static String lastDoorParityStage = "";
	/** Classification carried from the first authoritative divergence to rebase logging. */
	private static String lastReconcileSource = "reconcile";

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
		lastValidFineX = self.xFine;
		lastValidFineZ = self.zFine;
		lastValidInitialized = isFinePositionValid(self.xFine, self.zFine, self.getSize());
		clearValidFineHistory();
		if (lastValidInitialized) rememberValidFine(self.xFine, self.zFine);
		if (!lastValidInitialized) {
			int[] recovered = findNearestValidFine(self.xFine, self.zFine, self.getSize());
			if (recovered != null) {
				lastValidFineX = recovered[0];
				lastValidFineZ = recovered[1];
				predictedSubX = ((long) lastValidFineX) << 16;
				predictedSubZ = ((long) lastValidFineZ) << 16;
				self.xFine = lastValidFineX;
				self.zFine = lastValidFineZ;
				lastValidInitialized = true;
				rememberValidFine(lastValidFineX, lastValidFineZ);
			}
		}
		velocityXQ16 = 0;
		velocityZQ16 = 0;

		// Initialize lastServerReportedTile from movement queue (LOCAL tiles)
		lastServerReportedTileX = self.movementQueueX[0];
		lastServerReportedTileZ = self.movementQueueZ[0];
		lastExactServerFineX = -1;
		lastExactServerFineZ = -1;
		movementAnchorTileX = lastServerReportedTileX;
		movementAnchorTileZ = lastServerReportedTileZ;
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
	 * <p>The movement queue is kept at the last server-confirmed LOCAL tile by
	 * {@link #onServerStep(int, int)} while Q16 owns visual prediction. The
	 * actual F11 transition is executed by {@link CameraMode#processPendingCycle()}
	 * on the client game thread, so this handoff cannot interleave with a Q16
	 * write or {@link Protocol#readSelfPlayerInfo()} relative server step.
	 * No scene, collision-map or teleport reset is required. Order:
	 * <ol>
	 *   <li>capture before-state for F12 diagnostics;</li>
	 *   <li>stop Q16 writes (velocity/intent/pending/initialized);</li>
	 *   <li>leave the live vanilla queue, fine position, scene and collision
	 *       maps untouched;</li>
	 *   <li>hand movement ownership to ORIGINAL.</li>
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
		int predictedTileBeforeX = (int) (predictedSubX >> 16) >> 7;
		int predictedTileBeforeZ = (int) (predictedSubZ >> 16) >> 7;
		int distanceFromServerBefore = lastServerReportedTileX < 0 ? -1
				: Math.max(Math.abs(predictedTileBeforeX - lastServerReportedTileX),
						Math.abs(predictedTileBeforeZ - lastServerReportedTileZ));
		String pendingTilesBefore = pendingTilesSummary();

		// ---- Last server-confirmed LOCAL tile (diagnostics only) ----
		int authTileX = (lastServerReportedTileX >= 0) ? lastServerReportedTileX : self.xFine >> 7;
		int authTileZ = (lastServerReportedTileZ >= 0) ? lastServerReportedTileZ : self.zFine >> 7;

		// ---- Stop modern Q16 writes ----
		boolean q16Owned = !ModernCameraRig.isActive()
				|| ModernCameraRig.getRigState() != ModernCameraRig.RigState.FREE;
		// Q16 prediction is deliberately ahead of the server queue. ORIGINAL's
		// vanilla mover starts from queue[0], so leaving the predicted fine
		// position in place makes its first click path interpolate from a
		// different tile. Rebase to the authoritative queue tile at the owner
		// handoff; this is the same tile-centre state vanilla uses after a
		// normal server update and is bounded to at most the prediction lead.
		if (q16Owned) {
			int authoritativeX = self.movementQueueX[0];
			int authoritativeZ = self.movementQueueZ[0];
			int authoritativeFineX = authoritativeX * 128 + self.getSize() * 64;
			int authoritativeFineZ = authoritativeZ * 128 + self.getSize() * 64;
			if (Math.max(Math.abs((beforeFineX >> 7) - authoritativeX),
					Math.abs((beforeFineZ >> 7) - authoritativeZ)) > 1) {
				logSnapbackCause("F11", beforeFineX, beforeFineZ,
						authoritativeFineX, authoritativeFineZ,
						isFinePositionValid(beforeFineX, beforeFineZ, self.getSize()));
			}
			self.xFine = authoritativeFineX;
			self.zFine = authoritativeFineZ;
			// Drop any pre-Q16 or residual vanilla route while retaining queue[0].
			// The next ORIGINAL click must be the only live route after F11.
			self.method2689();
		}
		velocityXQ16 = 0;
		velocityZQ16 = 0;
		intent.clear();
		clearPending();
		initialized = false;
		suspended = false;
		wasFirstPersonLastTick = false;
		lastMovementState = MovementState.IDLE;

		// ---- Main-thread ownership handoff; preserve live vanilla state ----
		String route;
		if (q16Owned) {
			// Protocol's Q16 drain has already kept queue[0] at the last
			// confirmed server tile. Do not manufacture a client teleport or
			// mutate queue/fine/collision state during the ownership change.
			// Residual server steps now append through the normal vanilla path.
			route = "MAIN_THREAD_AUTHORITATIVE_REBASE";
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
		System.out.println("[F11-ORIGINAL-HANDOFF] authTile=" + authTileX + "," + authTileZ
				+ " playerTile=" + (beforeFineX >> 7) + "," + (beforeFineZ >> 7)
				+ " predictedTile=" + predictedTileBeforeX + "," + predictedTileBeforeZ
				+ " serverTile=" + lastServerReportedTileX + "," + lastServerReportedTileZ
				+ " movementAnchor=" + movementAnchorTileX + "," + movementAnchorTileZ
				+ " pendingCount=" + pendingBefore + " pendingTiles=" + pendingTilesBefore
				+ " distanceFromServer=" + distanceFromServerBefore
				+ " lastPacketAccepted=" + lastPacketAccepted
				+ " lastPacketReason=" + lastPacketReason
				+ " lastPacketTarget=" + lastPacketTargetX + "," + lastPacketTargetZ
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
	 * <p><b>Arbitration rule:</b> a client queue clear is not enough. The server
	 * has its own WalkingQueue and can continue a previously submitted route.
	 * Before clearing the local queue, send the existing MOVE_GAMECLICK packet
	 * to the vanilla queue head. On the server this enters the normal
	 * WorldspaceWalk/MovementPulse path, which stops the old pulse and resets the
	 * server queue before installing the one-tile no-op route. Any packet already
	 * in flight is then handled by the Q16 server-step drain and cannot run the
	 * vanilla mover independently.
	 */
	public static void onExitFreeMode() {
		Player self = PlayerList.self;
		if (self == null) {
			return;
		}
		// Capture queue[0] before method2689(). While vanilla owns movement this
		// is the latest server-position/route head, not the interpolated xFine.
		int vanillaRouteHeadX = self.movementQueueX[0];
		int vanillaRouteHeadZ = self.movementQueueZ[0];
		boolean hadVanillaRoute = self.movementQueueSize > 0;
		movementAnchorTileX = vanillaRouteHeadX;
		movementAnchorTileZ = vanillaRouteHeadZ;
		if (hadVanillaRoute) {
			int worldX = Camera.originX + vanillaRouteHeadX;
			int worldZ = Camera.originZ + vanillaRouteHeadZ;
			ClientProt.sendModernWalkPacket(worldX, worldZ, false);
			System.out.println("[MODERN-MOVE] ROUTE_REPLACE: FREE -> CHASE"
					+ " target=" + vanillaRouteHeadX + "," + vanillaRouteHeadZ
					+ " previousServerTile=" + lastServerReportedTileX + "," + lastServerReportedTileZ);
		}
		// 1: cancel vanilla auto-path (client side) after the server replacement
		// packet has been queued.
		self.method2689();
		// 2-4: seed prediction from live state (no snap).
		predictedSubX = ((long) self.xFine) << 16;
		predictedSubZ = ((long) self.zFine) << 16;
		velocityXQ16 = 0;
		velocityZQ16 = 0;
		// Do not promote interpolated/predicted xFine to server-confirmed state.
		// lastServerReportedTile changes only from an actual server step,
		// teleport, or scene rebuild.
		// 5-6: heading from current body facing; discard stale modern state.
		movementHeading = ModernCameraRig.bodyYawToCameraYaw(self.anInt3400);
		targetOrientationAngle = self.anInt3400;
		wasFirstPersonLastTick = false;
		lastMovementState = MovementState.IDLE;
		clearPending();
		suspended = false;
		initialized = true;
		System.out.println("[MODERN-MOVE] HANDOFF: FREE -> CHASE (modern Q16 owns)"
				+ " predictedTile=" + (self.xFine >> 7) + "," + (self.zFine >> 7)
				+ " anchorTile=" + movementAnchorTileX + "," + movementAnchorTileZ
				+ " serverTile=" + lastServerReportedTileX + "," + lastServerReportedTileZ);
	}

	/**
	 * Region rebuild adjusts all entity xFine/zFine by -deltaOrigin*128.
	 * self.xFine/zFine are externally overwritten — rebase prediction from them.
	 */
	public static void onSceneRebuild() {
		if (!initialized) return;
		Player self = PlayerList.self;
		if (self == null) return;
		int oldFineX = (int) (predictedSubX >> 16);
		int oldFineZ = (int) (predictedSubZ >> 16);
		if (Math.max(Math.abs((oldFineX >> 7) - (self.xFine >> 7)),
				Math.abs((oldFineZ >> 7) - (self.zFine >> 7))) > 1) {
			logSnapbackCause("region_rebuild", oldFineX, oldFineZ, self.xFine, self.zFine,
					isFinePositionValid(self.xFine, self.zFine, self.getSize()));
		}

		predictedSubX = ((long) self.xFine) << 16;
		predictedSubZ = ((long) self.zFine) << 16;
		velocityXQ16 = 0;
		velocityZQ16 = 0;

		lastServerReportedTileX = self.xFine >> 7;
		lastServerReportedTileZ = self.zFine >> 7;
		movementAnchorTileX = lastServerReportedTileX;
		movementAnchorTileZ = lastServerReportedTileZ;
		lastValidFineX = self.xFine;
		lastValidFineZ = self.zFine;
		lastValidInitialized = isFinePositionValid(self.xFine, self.zFine, self.getSize());
		clearValidFineHistory();
		if (lastValidInitialized) rememberValidFine(lastValidFineX, lastValidFineZ);
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
		// A regular player-update step identifies a tile only. Any older exact
		// teleport fine point is no longer authoritative after this step.
		lastExactServerFineX = -1;
		lastExactServerFineZ = -1;
		movementAnchorTileX = localTileX;
		movementAnchorTileZ = localTileZ;
		lastServerReportTick = client.loop;
		boolean hadPending = !pendingEmpty();
		boolean matchedPending = consumePendingExact(localTileX, localTileZ);
		lastReconcileSource = hadPending && !matchedPending ? "stale_movement_route" : "server_movement";
		reconcile();
	}

	/**
	 * Far teleport directly overwrote self.xFine/zFine.
	 * Rebase prediction from externally-updated fine coordinates.
	 */
	public static void onServerTeleportFine(int fineX, int fineZ) {
		Player self = PlayerList.self;
		int oldFineX = (int) (predictedSubX >> 16);
		int oldFineZ = (int) (predictedSubZ >> 16);
		predictedSubX = ((long) fineX) << 16;
		predictedSubZ = ((long) fineZ) << 16;
		velocityXQ16 = 0;
		velocityZQ16 = 0;
		lastServerReportedTileX = fineX >> 7;
		lastServerReportedTileZ = fineZ >> 7;
		lastExactServerFineX = fineX;
		lastExactServerFineZ = fineZ;
		movementAnchorTileX = lastServerReportedTileX;
		movementAnchorTileZ = lastServerReportedTileZ;
		lastValidFineX = fineX;
		lastValidFineZ = fineZ;
		lastValidInitialized = isFinePositionValid(fineX, fineZ, self.getSize());
		clearValidFineHistory();
		if (lastValidInitialized) rememberValidFine(fineX, fineZ);
		lastServerReportTick = client.loop;
		clearPending();
		System.out.println("[MODERN-MOVE] AUTHORITATIVE_TELEPORT_FINE"
				+ " fromFine=" + oldFineX + "," + oldFineZ
				+ " toFine=" + fineX + "," + fineZ
				+ " serverTile=" + lastServerReportedTileX + "," + lastServerReportedTileZ);
		if (Math.max(Math.abs((oldFineX >> 7) - (fineX >> 7)),
				Math.abs((oldFineZ >> 7) - (fineZ >> 7))) > 1) {
			logSnapbackCause("teleport", oldFineX, oldFineZ, fineX, fineZ,
					isFinePositionValid(fineX, fineZ, self.getSize()));
		}
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

		// Keep every Q16 step anchored to a valid local position. If an older
		// prediction already embedded the player, recover only to the last valid
		// point (or a nearby valid tile), never to an arbitrary world location.
		int currentFineX = (int) (predictedSubX >> 16);
		int currentFineZ = (int) (predictedSubZ >> 16);
		if (!isFinePositionOccupancyValid(currentFineX, currentFineZ, self.getSize())) {
			int[] recovered = findRecentValidFine(currentFineX, currentFineZ, self.getSize());
			if (recovered == null && lastValidInitialized
					&& isFinePositionValid(lastValidFineX, lastValidFineZ, self.getSize())
					&& fineDistance(lastValidFineX, lastValidFineZ, currentFineX, currentFineZ)
					<= MAX_FINE_RECOVERY_DISTANCE) {
				recovered = new int[]{lastValidFineX, lastValidFineZ};
			}
			if (recovered == null) recovered = findNearestValidFine(currentFineX, currentFineZ, self.getSize());
			if (recovered == null) return;
			currentFineX = recovered[0];
			currentFineZ = recovered[1];
			predictedSubX = ((long) currentFineX) << 16;
			predictedSubZ = ((long) currentFineZ) << 16;
			self.xFine = currentFineX;
			self.zFine = currentFineZ;
			lastValidFineX = currentFineX;
			lastValidFineZ = currentFineZ;
			lastValidInitialized = true;
			rememberValidFine(currentFineX, currentFineZ);
			DebugOverlay.collisionRecovery = true;
		} else {
			DebugOverlay.collisionRecovery = false;
		}
		DebugOverlay.lastValidFineX = lastValidFineX;
		DebugOverlay.lastValidFineZ = lastValidFineZ;

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

		// A full/stale pending ring is not permission to keep travelling. Bound
		// local prediction while packet reporting or acknowledgements are stalled.
		int predictedTileX = currentFineX >> 7;
		int predictedTileZ = currentFineZ >> 7;
		// Pending requests are intentions, not acknowledgements. The safety
		// budget must therefore be measured from the last actual server tile.
		int authorityTileX = lastServerReportedTileX >= 0 ? lastServerReportedTileX : movementAnchorTileX;
		int authorityTileZ = lastServerReportedTileZ >= 0 ? lastServerReportedTileZ : movementAnchorTileZ;
		int distanceFromAuthority = Math.max(Math.abs(predictedTileX - authorityTileX),
				Math.abs(predictedTileZ - authorityTileZ));
		if (distanceFromAuthority >= MAX_PREDICTION_LEAD_TILES) {
			velocityXQ16 = 0;
			velocityZQ16 = 0;
			if (client.loop - lastPacketDiagnosticTick >= 25) {
				lastPacketDiagnosticTick = client.loop;
				System.out.println("[MODERN-MOVE] PREDICTION_PAUSED reason=authority_budget"
						+ " anchor=" + movementAnchorTileX + "," + movementAnchorTileZ
						+ " serverTile=" + lastServerReportedTileX + "," + lastServerReportedTileZ
						+ " predictedTile=" + predictedTileX + "," + predictedTileZ
						+ " pendingTail=" + pendingTail + " pendingCount=" + getPendingCount()
						+ " distanceFromServer=" + distanceFromAuthority);
			}
		}

		// ---- Apply Q16 prediction through the live vanilla CollisionMap ----
		int desiredX = velocityXQ16;
		int desiredZ = velocityZQ16;
		CollisionResult resolved = resolveCollision(currentFineX, currentFineZ,
				desiredX, desiredZ, self.getSize());
		velocityXQ16 = resolved.deltaX;
		velocityZQ16 = resolved.deltaZ;
		DebugOverlay.desiredDeltaX = desiredX;
		DebugOverlay.desiredDeltaZ = desiredZ;
		DebugOverlay.resolvedDeltaX = resolved.deltaX;
		DebugOverlay.resolvedDeltaZ = resolved.deltaZ;
		DebugOverlay.movementBlockedX = resolved.blockedX;
		DebugOverlay.movementBlockedZ = resolved.blockedZ;
		DebugOverlay.movementCollisionFlags = resolved.flags;
		DebugOverlay.fullMoveValid = resolved.fullAllowed;
		DebugOverlay.xOnlyMoveValid = resolved.xAllowed;
		DebugOverlay.zOnlyMoveValid = resolved.zAllowed;

		// A fine step is a transaction.  Collision resolution alone is not
		// enough: a tile boundary may only be entered when that tile can be
		// represented by the current authority/pending chain.  Validate and
		// enqueue the report before committing predictedSub/self.xFine.
		long candidateSubX = predictedSubX + resolved.deltaX;
		long candidateSubZ = predictedSubZ + resolved.deltaZ;
		int candidateFineX = (int) (candidateSubX >> 16);
		int candidateFineZ = (int) (candidateSubZ >> 16);
		int currentTileX = currentFineX >> 7;
		int currentTileZ = currentFineZ >> 7;
		int candidateTileX = candidateFineX >> 7;
		int candidateTileZ = candidateFineZ >> 7;
		boolean candidateFineValid = isFineStepValid(currentFineX, currentFineZ,
				candidateFineX, candidateFineZ, self.getSize());
		// Capture the first decision, before any local entry-point recovery can
		// obscure a vanilla-vs-fine disagreement. Successful crossings are one
		// record per tile; a held rejection is throttled below.
		if (candidateTileX != currentTileX || candidateTileZ != currentTileZ || !candidateFineValid) {
			logDoorParity(currentTileX, currentTileZ, candidateTileX, candidateTileZ,
					currentFineX, currentFineZ, candidateFineX, candidateFineZ, self.getSize(),
					resolved.blockedX, resolved.blockedZ, "fine_validation",
					candidateFineValid ? "accepted" : finePositionRejectReason(candidateFineX, candidateFineZ, self.getSize()));
		}
		if (!candidateFineValid && (candidateTileX != currentTileX || candidateTileZ != currentTileZ)
				&& vanillaTileRouteAllowed(currentTileX, currentTileZ, candidateTileX, candidateTileZ, self.getSize())) {
			int[] legalEntry = findFineEntryInVanillaTile(candidateTileX, candidateTileZ,
					candidateFineX, candidateFineZ, self.getSize());
			if (legalEntry != null) {
				candidateFineX = legalEntry[0];
				candidateFineZ = legalEntry[1];
				candidateSubX = ((long) candidateFineX) << 16;
				candidateSubZ = ((long) candidateFineZ) << 16;
				resolved = new CollisionResult((int) (candidateSubX - predictedSubX),
						(int) (candidateSubZ - predictedSubZ), resolved.blockedX, resolved.blockedZ,
						resolved.flags, resolved.fullAllowed, resolved.xAllowed, resolved.zAllowed);
				velocityXQ16 = resolved.deltaX;
				velocityZQ16 = resolved.deltaZ;
				candidateFineValid = true;
				System.out.println("[MODERN-MOVE] FINE_ENTRY_REBASED reason=vanilla_tile_reachable"
						+ " plane=" + Player.plane + " sourceTile=" + currentTileX + "," + currentTileZ
						+ " destinationTile=" + candidateTileX + "," + candidateTileZ
						+ " fine=" + candidateFineX + "," + candidateFineZ);
			}
		}
		if (!candidateFineValid) {
			resolved = new CollisionResult(0, 0, true, true, resolved.flags,
					false, false, false);
			velocityXQ16 = 0;
			velocityZQ16 = 0;
		} else if ((candidateTileX != currentTileX || candidateTileZ != currentTileZ)
				&& !prepareTileTransition(candidateTileX, candidateTileZ)) {
			logDoorParity(currentTileX, currentTileZ, candidateTileX, candidateTileZ,
					currentFineX, currentFineZ, candidateFineX, candidateFineZ, self.getSize(),
					resolved.blockedX, resolved.blockedZ, "authority_packet", lastPacketReason);
			resolved = new CollisionResult(0, 0, true, true, resolved.flags,
					false, false, false);
			velocityXQ16 = 0;
			velocityZQ16 = 0;
		}
		predictedSubX += resolved.deltaX;
		predictedSubZ += resolved.deltaZ;
		self.xFine = (int) (predictedSubX >> 16);
		self.zFine = (int) (predictedSubZ >> 16);
		if (!isFineStepValid(currentFineX, currentFineZ, self.xFine, self.zFine, self.getSize())) {
			predictedSubX = ((long) lastValidFineX) << 16;
			predictedSubZ = ((long) lastValidFineZ) << 16;
			self.xFine = lastValidFineX;
			self.zFine = lastValidFineZ;
			velocityXQ16 = 0;
			velocityZQ16 = 0;
			resolved = new CollisionResult(0, 0, true, true, resolved.flags,
					false, false, false);
		}
		lastValidFineX = self.xFine;
		lastValidFineZ = self.zFine;
		lastValidInitialized = true;
		rememberValidFine(self.xFine, self.zFine);
		DebugOverlay.lastValidFineX = lastValidFineX;
		DebugOverlay.lastValidFineZ = lastValidFineZ;

		// ---- DDA tile boundary crossing → server sync ----
		// Tile transitions were validated and queued before the commit above.
		// Keep DDA as a harmless deduplicating backstop for externally changed
		// fine coordinates, but never let it be the first packet validation.
		if (resolved.deltaX != 0 || resolved.deltaZ != 0) performDDACheck();
		if ((resolved.blockedX || resolved.blockedZ || DebugOverlay.collisionRecovery)
				&& client.loop % 25 == 0) {
			System.out.println("[MODERN-COLLISION] currentFine=" + currentFineX + "," + currentFineZ
					+ " desiredFineDelta=" + (desiredX >> 16) + "," + (desiredZ >> 16)
					+ " resolvedFineDelta=" + (resolved.deltaX >> 16) + "," + (resolved.deltaZ >> 16)
					+ " currentTile=" + (currentFineX >> 7) + "," + (currentFineZ >> 7)
					+ " attemptedTile=" + (self.xFine >> 7) + "," + (self.zFine >> 7)
					+ " blockedX=" + resolved.blockedX + " blockedZ=" + resolved.blockedZ
					+ " full=" + resolved.fullAllowed + " xOnly=" + resolved.xAllowed
					+ " zOnly=" + resolved.zAllowed + " flags=0x" + Integer.toHexString(resolved.flags)
					+ " lastValid=" + lastValidFineX + "," + lastValidFineZ);
		}

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

	/** Result of one fine-coordinate collision resolution step. */
	private static final class CollisionResult {
		private final int deltaX;
		private final int deltaZ;
		private final boolean blockedX;
		private final boolean blockedZ;
		private final int flags;
		private final boolean fullAllowed;
		private final boolean xAllowed;
		private final boolean zAllowed;

		private CollisionResult(int deltaX, int deltaZ, boolean blockedX, boolean blockedZ, int flags,
				boolean fullAllowed, boolean xAllowed, boolean zAllowed) {
			this.deltaX = deltaX;
			this.deltaZ = deltaZ;
			this.blockedX = blockedX;
			this.blockedZ = blockedZ;
			this.flags = flags;
			this.fullAllowed = fullAllowed;
			this.xAllowed = xAllowed;
			this.zAllowed = zAllowed;
		}
	}

	/**
	 * Resolves a short WASD step against the same tile flags used by PathFinder.
	 * The full vector is attempted first; if a corner or object blocks it, each
	 * axis is attempted independently so movement slides along walls. A diagonal
	 * step can therefore never bypass two perpendicular blocked edges.
	 */
	private static CollisionResult resolveCollision(int fineX, int fineZ, int desiredX, int desiredZ, int size) {
		CollisionMap map = Player.plane >= 0 && Player.plane < PathFinder.collisionMaps.length
				? PathFinder.collisionMaps[Player.plane] : null;
		if (map == null) {
			return new CollisionResult(desiredX, desiredZ, false, false, 0, true, true, true);
		}
		int flags = sampleFlags(map, fineX >> 7, fineZ >> 7);
		int fullX = desiredX;
		int fullZ = desiredZ;
		boolean fullAllowed = canMoveFine(map, fineX, fineZ, fineX + (fullX >> 16),
				fineZ + (fullZ >> 16), size);
		if (fullAllowed) {
			return new CollisionResult(fullX, fullZ, false, false, flags, true, true, true);
		}
		boolean xAllowed = desiredX != 0 && (canMoveFine(map, fineX, fineZ,
				fineX + (desiredX >> 16), fineZ, size)
				|| canMoveAwayFromBlockedEdge(map, fineX, fineZ,
						fineX + (desiredX >> 16), fineZ, size));
		boolean zAllowed = desiredZ != 0 && (canMoveFine(map, fineX, fineZ,
				fineX, fineZ + (desiredZ >> 16), size)
				|| canMoveAwayFromBlockedEdge(map, fineX, fineZ,
						fineX, fineZ + (desiredZ >> 16), size));
		int resolvedX = xAllowed ? desiredX : 0;
		int resolvedZ = zAllowed ? desiredZ : 0;
		return new CollisionResult(resolvedX, resolvedZ, desiredX != 0 && !xAllowed,
				desiredZ != 0 && !zAllowed, flags, false, xAllowed, zAllowed);
	}

	/**
	 * Allows escape from an over-constrained fine edge only when the candidate
	 * itself is fully valid and the input moves away from the blocked boundary.
	 * This is deliberately not a general collision bypass: destination validity
	 * remains mandatory and the direction must reduce the edge overlap margin.
	 */
	private static boolean canMoveAwayFromBlockedEdge(CollisionMap map, int fromFineX, int fromFineZ,
			int toFineX, int toFineZ, int size) {
		if (!isFinePositionValid(map, toFineX, toFineZ, size)) return false;
		int tileX = fineToCollisionTile(fromFineX, size);
		int tileZ = fineToCollisionTile(fromFineZ, size);
		if (tileX < 0 || tileZ < 0 || tileX + size > 104 || tileZ + size > 104) return false;
		int left = tileX * 128;
		int right = (tileX + size) * 128;
		int bottom = tileZ * 128;
		int top = (tileZ + size) * 128;
		if (toFineX < fromFineX && hasEastBoundaryBlock(map, tileX, tileZ, size)
				&& fromFineX >= right - FINE_FOOTPRINT_INSET) return true;
		if (toFineX > fromFineX && hasWestBoundaryBlock(map, tileX, tileZ, size)
				&& fromFineX <= left + FINE_FOOTPRINT_INSET) return true;
		if (toFineZ < fromFineZ && hasNorthBoundaryBlock(map, tileX, tileZ, size)
				&& fromFineZ >= top - FINE_FOOTPRINT_INSET) return true;
		return toFineZ > fromFineZ && hasSouthBoundaryBlock(map, tileX, tileZ, size)
				&& fromFineZ <= bottom + FINE_FOOTPRINT_INSET;
	}

	private static boolean canMoveFine(CollisionMap map, int fromFineX, int fromFineZ,
			int toFineX, int toFineZ, int size) {
		int fromX = fineToCollisionTile(fromFineX, size);
		int fromZ = fineToCollisionTile(fromFineZ, size);
		int toX = fineToCollisionTile(toFineX, size);
		int toZ = fineToCollisionTile(toFineZ, size);
		if (fromX < 0 || fromZ < 0 || fromX + size > 104 || fromZ + size > 104
				|| toX < 0 || toZ < 0 || toX + size > 104 || toZ + size > 104) return false;
		if (!isFineStepValid(map, fromFineX, fromFineZ, toFineX, toFineZ, size)) return false;
		int dx = toX - fromX;
		int dz = toZ - fromZ;
		if (dx == 0 && dz == 0) return true;
		// For the normal one-tile player, delegate wall/corner semantics to the
		// exact vanilla CollisionMap routine. This covers wall orientations and
		// diagonal side masks that are easy to invert in a local reimplementation.
		if (Math.abs(dx) <= 1 && Math.abs(dz) <= 1) {
			// For a size-1 player, method3054 is the authoritative vanilla
			// doorway/wall test. The old extra raw reciprocal-bit test could let a
			// neighbouring or stale wall bit bleed into an otherwise open gap.
			// Fine-position validity above still protects the actual footprint.
			if (!map.method3054(fromZ, toZ, toX, fromX)) return false;
			return size == 1 || canCrossBoundary(map, fromX, fromZ, toX, toZ, size);
		}
		if (Math.abs(dx) > 1 || Math.abs(dz) > 1) {
			return canMoveFine(map, fromFineX, fromFineZ,
					fromFineX + Integer.signum(dx) * 128, fromFineZ + Integer.signum(dz) * 128, size);
		}
		if (dx != 0 && dz != 0) {
			// Validate the two possible edge crossings independently. This keeps
			// the footprint check symmetric at a diagonal corner instead of only
			// sampling the source row/column.
			int stepX = fromX + dx;
			if (!canCrossBoundary(map, fromX, fromZ, stepX, fromZ, size)) return false;
			return canCrossBoundary(map, stepX, fromZ, stepX, fromZ + dz, size);
		}
		return canCrossBoundary(map, fromX, fromZ, toX, toZ, size);
	}

	private static boolean canCrossBoundary(CollisionMap map, int fromX, int fromZ,
			int toX, int toZ, int size) {
		int dx = Integer.signum(toX - fromX);
		int dz = Integer.signum(toZ - fromZ);
		if (dx != 0) {
			int sourceX = dx > 0 ? fromX + size - 1 : fromX;
			int destinationX = dx > 0 ? toX : toX + size - 1;
			int sourceMask = dx > 0 ? EAST_WALL : WEST_WALL;
			int reciprocalMask = dx > 0 ? WEST_WALL : EAST_WALL;
			for (int z = fromZ; z < fromZ + size; z++) {
				if ((sampleFlags(map, sourceX, z) & sourceMask) != 0
						|| (sampleFlags(map, destinationX, z) & reciprocalMask) != 0) return false;
			}
		}
		if (dz != 0) {
			int sourceZ = dz > 0 ? fromZ + size - 1 : fromZ;
			int destinationZ = dz > 0 ? toZ : toZ + size - 1;
			int sourceMask = dz > 0 ? POSITIVE_Z_WALL : NEGATIVE_Z_WALL;
			int reciprocalMask = dz > 0 ? NEGATIVE_Z_WALL : POSITIVE_Z_WALL;
			for (int x = fromX; x < fromX + size; x++) {
				if ((sampleFlags(map, x, sourceZ) & sourceMask) != 0
						|| (sampleFlags(map, x, destinationZ) & reciprocalMask) != 0) return false;
			}
		}
		return true;
	}

	private static boolean isFinePositionValid(int fineX, int fineZ, int size) {
		if (Player.plane < 0 || Player.plane >= PathFinder.collisionMaps.length) return false;
		CollisionMap map = PathFinder.collisionMaps[Player.plane];
		return isFinePositionValid(map, fineX, fineZ, size);
	}

	/**
	 * Validates the actual fine footprint, not only the tile containing its
	 * centre. A wall flag on an edge is blocking before the centre reaches the
	 * tile boundary, and a scenery/object flag must reject the whole occupied
	 * footprint. All flags here come from the client-owned CollisionMap, which
	 * is populated from the same rotated scene data used by vanilla PathFinder.
	 */
	private static boolean isFinePositionValid(CollisionMap map, int fineX, int fineZ, int size) {
		if (map == null) return false;
		int tileX = fineToCollisionTile(fineX, size);
		int tileZ = fineToCollisionTile(fineZ, size);
		if (tileX < 0 || tileZ < 0 || tileX + size > 104 || tileZ + size > 104
				|| !canOccupy(map, tileX, tileZ, size)) return false;
		return hasFineEdgeClearance(map, fineX, fineZ, tileX, tileZ, size);
	}

	private static boolean isFinePositionOccupancyValid(int fineX, int fineZ, int size) {
		if (Player.plane < 0 || Player.plane >= PathFinder.collisionMaps.length) return false;
		return isFinePositionOccupancyValid(PathFinder.collisionMaps[Player.plane], fineX, fineZ, size);
	}

	private static boolean isFinePositionOccupancyValid(CollisionMap map, int fineX, int fineZ, int size) {
		if (map == null) return false;
		int tileX = fineToCollisionTile(fineX, size);
		int tileZ = fineToCollisionTile(fineZ, size);
		return tileX >= 0 && tileZ >= 0 && tileX + size <= 104 && tileZ + size <= 104
				&& canOccupy(map, tileX, tileZ, size);
	}

	/** Same-tile movement must not sample adjacent boundary masks as collision. */
	private static boolean isFineStepValid(CollisionMap map, int fromFineX, int fromFineZ,
			int toFineX, int toFineZ, int size) {
		int fromTileX = fineToCollisionTile(fromFineX, size);
		int fromTileZ = fineToCollisionTile(fromFineZ, size);
		int toTileX = fineToCollisionTile(toFineX, size);
		int toTileZ = fineToCollisionTile(toFineZ, size);
		if (fromTileX == toTileX && fromTileZ == toTileZ) {
			return isFinePositionOccupancyValid(map, fromFineX, fromFineZ, size)
					&& isFinePositionOccupancyValid(map, toFineX, toFineZ, size);
		}
		return isFinePositionValid(map, fromFineX, fromFineZ, size)
				&& isFinePositionValid(map, toFineX, toFineZ, size);
	}

	private static boolean isFineStepValid(int fromFineX, int fromFineZ, int toFineX, int toFineZ, int size) {
		if (Player.plane < 0 || Player.plane >= PathFinder.collisionMaps.length) return false;
		return isFineStepValid(PathFinder.collisionMaps[Player.plane], fromFineX, fromFineZ,
				toFineX, toFineZ, size);
	}

	private static String finePositionRejectReason(int fineX, int fineZ, int size) {
		if (Player.plane < 0 || Player.plane >= PathFinder.collisionMaps.length) return "plane_invalid";
		CollisionMap map = PathFinder.collisionMaps[Player.plane];
		int tileX = fineToCollisionTile(fineX, size);
		int tileZ = fineToCollisionTile(fineZ, size);
		if (tileX < 0 || tileZ < 0 || tileX + size > 104 || tileZ + size > 104) return "fine_bounds";
		if (!canOccupy(map, tileX, tileZ, size)) {
			return "occupancy_flags=0x" + Integer.toHexString(sampleFlags(map, tileX, tileZ));
		}
		if (!hasFineEdgeClearance(map, fineX, fineZ, tileX, tileZ, size)) {
			return "edge_clearance inset=" + FINE_FOOTPRINT_INSET
					+ " flags=0x" + Integer.toHexString(sampleFlags(map, tileX, tileZ));
		}
		return "unknown";
	}

	/** Vanilla route parity check for one committed tile transition. */
	private static boolean vanillaTileRouteAllowed(int sourceX, int sourceZ, int targetX, int targetZ, int size) {
		if (Math.abs(targetX - sourceX) > 1 || Math.abs(targetZ - sourceZ) > 1) return false;
		CollisionMap map = Player.plane >= 0 && Player.plane < PathFinder.collisionMaps.length
				? PathFinder.collisionMaps[Player.plane] : null;
		return map != null && targetX >= 0 && targetZ >= 0
				&& targetX + size <= 104 && targetZ + size <= 104
				&& canOccupy(map, targetX, targetZ, size)
				&& map.method3054(sourceZ, targetZ, targetX, sourceX);
	}

	/**
	 * Read-only copy of PathFinder's size-one first-edge masks. This does not
	 * call findPath (which would send a packet); it reports whether vanilla's
	 * BFS may expand directly from source to destination. The normal player is
	 * size one, while larger entities retain the existing CollisionMap test.
	 */
	private static boolean vanillaPathFinderDirectReachable(CollisionMap map, int sourceX, int sourceZ,
			int targetX, int targetZ, int size) {
		if (map == null || targetX < 0 || targetZ < 0 || targetX >= 104 || targetZ >= 104
				|| sourceX < 0 || sourceZ < 0 || sourceX >= 104 || sourceZ >= 104) return false;
		int dx = targetX - sourceX;
		int dz = targetZ - sourceZ;
		if (Math.abs(dx) > 1 || Math.abs(dz) > 1) return false;
		if (dx == 0 && dz == 0) return true;
		if (size != 1) return map.method3054(sourceZ, targetZ, targetX, sourceX);
		int[][] flags = map.flags;
		if (dx == -1 && dz == 0) return (flags[sourceX - 1][sourceZ] & 0x12C0108) == 0;
		if (dx == 1 && dz == 0) return (flags[sourceX + 1][sourceZ] & 0x12C0180) == 0;
		if (dx == 0 && dz == -1) return (flags[sourceX][sourceZ - 1] & 0x12C0102) == 0;
		if (dx == 0 && dz == 1) return (flags[sourceX][sourceZ + 1] & 0x12C0120) == 0;
		if (dx == -1 && dz == -1) return (flags[sourceX - 1][sourceZ - 1] & 0x12C010E) == 0
				&& (flags[sourceX - 1][sourceZ] & 0x12C0108) == 0
				&& (flags[sourceX][sourceZ - 1] & 0x12C0102) == 0;
		if (dx == 1 && dz == -1) return (flags[sourceX + 1][sourceZ - 1] & 0x12C010E) == 0
				&& (flags[sourceX + 1][sourceZ] & 0x12C0180) == 0
				&& (flags[sourceX][sourceZ - 1] & 0x12C0102) == 0;
		if (dx == -1 && dz == 1) return (flags[sourceX - 1][sourceZ + 1] & 0x12C010E) == 0
				&& (flags[sourceX - 1][sourceZ] & 0x12C0108) == 0
				&& (flags[sourceX][sourceZ + 1] & 0x12C0120) == 0;
		return (flags[sourceX + 1][sourceZ + 1] & 0x12C010E) == 0
				&& (flags[sourceX + 1][sourceZ] & 0x12C0180) == 0
				&& (flags[sourceX][sourceZ + 1] & 0x12C0120) == 0;
	}

	/**
	 * Records the full first-person doorway/corner decision without treating a
	 * server tile as a fictitious fine coordinate. This is read-only: the normal
	 * CollisionMap and authority path remain the sole decision makers.
	 */
	private static void logDoorParity(int sourceX, int sourceZ, int destinationX, int destinationZ,
			int fromFineX, int fromFineZ, int candidateFineX, int candidateFineZ, int size,
			boolean blockedX, boolean blockedZ, String rejectStage, String rejectReason) {
		boolean sameCase = sourceX == lastDoorParitySourceX && sourceZ == lastDoorParitySourceZ
				&& destinationX == lastDoorParityTargetX && destinationZ == lastDoorParityTargetZ
				&& rejectStage.equals(lastDoorParityStage);
		boolean rejected = !"accepted".equals(rejectReason);
		if (rejected && sameCase && client.loop - lastDoorParityLogTick < 50) return;
		lastDoorParityLogTick = client.loop;
		lastDoorParitySourceX = sourceX;
		lastDoorParitySourceZ = sourceZ;
		lastDoorParityTargetX = destinationX;
		lastDoorParityTargetZ = destinationZ;
		lastDoorParityStage = rejectStage;
		CollisionMap map = Player.plane >= 0 && Player.plane < PathFinder.collisionMaps.length
				? PathFinder.collisionMaps[Player.plane] : null;
		boolean vanillaReachable = vanillaPathFinderDirectReachable(map, sourceX, sourceZ,
				destinationX, destinationZ, size);
		boolean modernGameplayReachable = vanillaTileRouteAllowed(sourceX, sourceZ,
				destinationX, destinationZ, size);
		boolean modernFineReachable = isFineStepValid(map, fromFineX, fromFineZ,
				candidateFineX, candidateFineZ, size);
		String serverFineIfKnown = lastExactServerFineX >= 0
				? lastExactServerFineX + "," + lastExactServerFineZ + "(far_teleport)"
				: "unknown(step_update_is_tile_only)";
		String direction = sourceX == destinationX && sourceZ == destinationZ
				? "same_tile"
				: sourceX + "," + sourceZ + "->" + destinationX + "," + destinationZ;
		System.out.println("[FP-DOOR-PARITY]"
				+ " direction=" + direction
				+ " currentFine=" + fromFineX + "," + fromFineZ
				+ " candidateFine=" + candidateFineX + "," + candidateFineZ
				+ " serverFineIfKnown=" + serverFineIfKnown
				+ " sourceTile=" + sourceX + "," + sourceZ
				+ " destinationTile=" + destinationX + "," + destinationZ
				+ " serverTile=" + lastServerReportedTileX + "," + lastServerReportedTileZ
				+ " predictedTile=" + ((int) (predictedSubX >> 16) >> 7) + "," + ((int) (predictedSubZ >> 16) >> 7)
				+ " anchorTile=" + movementAnchorTileX + "," + movementAnchorTileZ
				+ " pathQueueTile=" + PlayerList.self.movementQueueX[0] + "," + PlayerList.self.movementQueueZ[0]
				+ " sourceFlags=0x" + Integer.toHexString(sampleFlags(map, sourceX, sourceZ))
				+ " destinationFlags=0x" + Integer.toHexString(sampleFlags(map, destinationX, destinationZ))
				+ " playerSize=" + size
				+ " vanillaReachable=" + vanillaReachable
				+ " modernGameplayReachable=" + modernGameplayReachable
				+ " modernFineReachable=" + modernFineReachable
				+ " blockedX=" + blockedX + " blockedZ=" + blockedZ
				+ " collisionSourceTile=" + fineToCollisionTile(fromFineX, size) + "," + fineToCollisionTile(fromFineZ, size)
				+ " collisionCandidateTile=" + fineToCollisionTile(candidateFineX, size) + "," + fineToCollisionTile(candidateFineZ, size)
				+ " fineInset=" + FINE_FOOTPRINT_INSET + "/128"
				+ " rejectStage=" + rejectStage + " rejectReason=" + rejectReason);
	}

	/**
	 * Finds a small, valid fine entry inside a tile vanilla already accepts.
	 * This prevents the visual inset from making a legal gameplay tile
	 * unreachable while retaining fine collision once inside the tile.
	 */
	private static int[] findFineEntryInVanillaTile(int tileX, int tileZ, int nearX, int nearZ, int size) {
		int centerX = tileX * 128 + size * 64;
		int centerZ = tileZ * 128 + size * 64;
		int bestDistance = Integer.MAX_VALUE;
		int[] best = null;
		for (int dx = -48; dx <= 48; dx += 16) {
			for (int dz = -48; dz <= 48; dz += 16) {
				int candidateX = centerX + dx;
				int candidateZ = centerZ + dz;
				if (!isFinePositionValid(candidateX, candidateZ, size)) continue;
				int distance = Math.abs(candidateX - nearX) + Math.abs(candidateZ - nearZ);
				if (distance < bestDistance) {
					bestDistance = distance;
					best = new int[]{candidateX, candidateZ};
				}
			}
		}
		return best;
	}

	private static boolean hasFineEdgeClearance(CollisionMap map, int fineX, int fineZ,
			int tileX, int tileZ, int size) {
		int leftBoundary = tileX * 128;
		int rightBoundary = (tileX + size) * 128;
		int bottomBoundary = tileZ * 128;
		int topBoundary = (tileZ + size) * 128;
		int minFineX = leftBoundary + FINE_FOOTPRINT_INSET;
		int maxFineX = rightBoundary - FINE_FOOTPRINT_INSET;
		int minFineZ = bottomBoundary + FINE_FOOTPRINT_INSET;
		int maxFineZ = topBoundary - FINE_FOOTPRINT_INSET;

		// The outer footprint may not overlap a flagged wall edge. Check both
		// source and reciprocal destination flags because malformed/rotated cache
		// data can expose only one half of a wall pair.
		if (fineX < minFineX && hasWestBoundaryBlock(map, tileX, tileZ, size)) return false;
		if (fineX > maxFineX && hasEastBoundaryBlock(map, tileX, tileZ, size)) return false;
		if (fineZ < minFineZ && hasSouthBoundaryBlock(map, tileX, tileZ, size)) return false;
		if (fineZ > maxFineZ && hasNorthBoundaryBlock(map, tileX, tileZ, size)) return false;

		// Internal footprint edges matter for multi-tile entities and rotated
		// scenery. A blocked edge inside the footprint means the whole fine
		// position overlaps collision geometry.
		for (int x = tileX; x < tileX + size - 1; x++) {
			for (int z = tileZ; z < tileZ + size; z++) {
				if ((sampleFlags(map, x, z) & EAST_WALL) != 0
						|| (sampleFlags(map, x + 1, z) & WEST_WALL) != 0) return false;
			}
		}
		for (int x = tileX; x < tileX + size; x++) {
			for (int z = tileZ; z < tileZ + size - 1; z++) {
				if ((sampleFlags(map, x, z) & POSITIVE_Z_WALL) != 0
						|| (sampleFlags(map, x, z + 1) & NEGATIVE_Z_WALL) != 0) return false;
			}
		}
		return true;
	}

	/**
	 * Converts a fine position to the CollisionMap coordinate consumed by the
	 * vanilla size-1 route tests. A player is centered at {@code tile * 128 +
	 * 64}, so subtracting 64 delayed every size-1 collision transition by half
	 * a tile while packet/gameplay transitions already used {@code fine >> 7}.
	 * Larger transformed entities retain their vanilla lower-left footprint
	 * convention.
	 */
	private static int fineToCollisionTile(int fine, int size) {
		return size == 1 ? fine >> 7 : (fine - size * 64) >> 7;
	}

	private static boolean hasWestBoundaryBlock(CollisionMap map, int x, int z, int size) {
		for (int tz = z; tz < z + size; tz++) {
			if ((sampleFlags(map, x, tz) & WEST_WALL) != 0
					|| (sampleFlags(map, x - 1, tz) & EAST_WALL) != 0) return true;
		}
		return false;
	}

	private static boolean hasEastBoundaryBlock(CollisionMap map, int x, int z, int size) {
		for (int tz = z; tz < z + size; tz++) {
			if ((sampleFlags(map, x + size - 1, tz) & EAST_WALL) != 0
					|| (sampleFlags(map, x + size, tz) & WEST_WALL) != 0) return true;
		}
		return false;
	}

	private static boolean hasSouthBoundaryBlock(CollisionMap map, int x, int z, int size) {
		for (int tx = x; tx < x + size; tx++) {
			if ((sampleFlags(map, tx, z) & NEGATIVE_Z_WALL) != 0
					|| (sampleFlags(map, tx, z - 1) & POSITIVE_Z_WALL) != 0) return true;
		}
		return false;
	}

	private static boolean hasNorthBoundaryBlock(CollisionMap map, int x, int z, int size) {
		for (int tx = x; tx < x + size; tx++) {
			if ((sampleFlags(map, tx, z + size - 1) & POSITIVE_Z_WALL) != 0
					|| (sampleFlags(map, tx, z + size) & NEGATIVE_Z_WALL) != 0) return true;
		}
		return false;
	}

	private static int[] findNearestValidFine(int fineX, int fineZ, int size) {
		CollisionMap map = Player.plane >= 0 && Player.plane < PathFinder.collisionMaps.length
				? PathFinder.collisionMaps[Player.plane] : null;
		if (map == null) return null;
		int bestDistance = Integer.MAX_VALUE;
		int[] best = null;
		for (int dx = -MAX_FINE_RECOVERY_DISTANCE; dx <= MAX_FINE_RECOVERY_DISTANCE; dx += 16) {
			for (int dz = -MAX_FINE_RECOVERY_DISTANCE; dz <= MAX_FINE_RECOVERY_DISTANCE; dz += 16) {
				int candidateX = fineX + dx;
				int candidateZ = fineZ + dz;
				if (!isFinePositionValid(map, candidateX, candidateZ, size)) continue;
				int distance = Math.abs(dx) + Math.abs(dz);
				if (distance < bestDistance) {
					bestDistance = distance;
					best = new int[]{candidateX, candidateZ};
				}
			}
		}
		return best;
	}

	private static void clearValidFineHistory() {
		validFineHistoryHead = 0;
		validFineHistoryCount = 0;
	}

	private static void rememberValidFine(int fineX, int fineZ) {
		if (!lastValidInitialized && !isFinePositionValid(fineX, fineZ, PlayerList.self.getSize())) return;
		int index = (validFineHistoryHead + validFineHistoryCount) % VALID_FINE_HISTORY_CAPACITY;
		if (validFineHistoryCount == VALID_FINE_HISTORY_CAPACITY) {
			index = validFineHistoryHead;
			validFineHistoryHead = (validFineHistoryHead + 1) % VALID_FINE_HISTORY_CAPACITY;
		} else {
			validFineHistoryCount++;
		}
		validFineHistoryX[index] = fineX;
		validFineHistoryZ[index] = fineZ;
	}

	private static int[] findRecentValidFine(int fineX, int fineZ, int size) {
		int bestDistance = Integer.MAX_VALUE;
		int[] best = null;
		for (int i = 0; i < validFineHistoryCount; i++) {
			int index = (validFineHistoryHead + i) % VALID_FINE_HISTORY_CAPACITY;
			int candidateX = validFineHistoryX[index];
			int candidateZ = validFineHistoryZ[index];
			if (!isFinePositionValid(candidateX, candidateZ, size)) continue;
			int distance = fineDistance(candidateX, candidateZ, fineX, fineZ);
			if (distance <= MAX_FINE_RECOVERY_DISTANCE && distance < bestDistance) {
				bestDistance = distance;
				best = new int[]{candidateX, candidateZ};
			}
		}
		return best;
	}

	private static int fineDistance(int x1, int z1, int x2, int z2) {
		return Math.abs(x1 - x2) + Math.abs(z1 - z2);
	}

	private static boolean canOccupy(CollisionMap map, int x, int z, int size) {
		for (int tx = x; tx < x + size; tx++) {
			for (int tz = z; tz < z + size; tz++) {
				if ((map.flags[tx][tz] & BLOCKED_TILE_MASK) != 0) return false;
			}
		}
		return true;
	}

	private static boolean crossesX(CollisionMap map, int x, int z, int size, int wall) {
		for (int tz = z; tz < z + size; tz++) if ((map.flags[x][tz] & wall) != 0) return false;
		return true;
	}

	private static boolean crossesZ(CollisionMap map, int x, int z, int size, int wall) {
		for (int tx = x; tx < x + size; tx++) if ((map.flags[tx][z] & wall) != 0) return false;
		return true;
	}

	private static int sampleFlags(CollisionMap map, int x, int z) {
		return map != null && x >= 0 && x < map.flags.length && z >= 0 && z < map.flags[x].length
				? map.flags[x][z] : BLOCKED_TILE_MASK;
	}

	// =====================================================================
	// DDA TILE BOUNDARY DETECTION
	// =====================================================================

	/**
	 * Reports only the tile the collision-resolved prediction is currently in.
	 * The previous boundary look-ahead sent the next tile before the player had
	 * entered it, which made a blocked tile pending one tile too early.
	 */
	private static void performDDACheck() {
		int currentTileX = ((int) (predictedSubX >> 16)) >> 7;
		int currentTileZ = ((int) (predictedSubZ >> 16)) >> 7;
		maybeSendWalkRequest(currentTileX, currentTileZ);
	}

	// =====================================================================
	// SERVER SYNC — PENDING RING BUFFER
	// =====================================================================

	/**
	 * Send walk request if target tile is not already pending and not the
	 * last server-reported tile. Uses LOCAL coords internally, converts to
	 * WORLD only for the packet.
	 */
	private static boolean prepareTileTransition(int targetLocalTileX, int targetLocalTileZ) {
		return maybeSendWalkRequest(targetLocalTileX, targetLocalTileZ);
	}

	private static boolean maybeSendWalkRequest(int targetLocalTileX, int targetLocalTileZ) {
		// Dedup: don't send if this exact tile is already pending
		if (pendingContains(targetLocalTileX, targetLocalTileZ)) {
			logPacketDecision(false, "pending_duplicate", targetLocalTileX, targetLocalTileZ);
			return true;
		}

		// Don't send if target == last server reported (already confirmed)
		if ((targetLocalTileX == lastServerReportedTileX
				&& targetLocalTileZ == lastServerReportedTileZ)
				|| (targetLocalTileX == movementAnchorTileX
				&& targetLocalTileZ == movementAnchorTileZ)) {
			logPacketDecision(false, "already_authority", targetLocalTileX, targetLocalTileZ);
			return true;
		}

		// Validate local tile bounds
		if (targetLocalTileX < 0 || targetLocalTileX > 103
				|| targetLocalTileZ < 0 || targetLocalTileZ > 103) {
			logPacketDecision(false, "local_tile_out_of_bounds", targetLocalTileX, targetLocalTileZ);
			return false;
		}
		CollisionMap map = PathFinder.collisionMaps[Player.plane];
		String rejectionReason = reportedTileRejectionReason(map, targetLocalTileX, targetLocalTileZ,
				PlayerList.self.getSize());
		if (rejectionReason != null) {
			logPacketDecision(false, rejectionReason, targetLocalTileX, targetLocalTileZ);
			return false;
		}

		// Convert LOCAL → WORLD for packet (Correction 7: explicit coordinate space)
		int worldX = Camera.originX + targetLocalTileX;
		int worldZ = Camera.originZ + targetLocalTileZ;

		ClientProt.sendModernWalkPacket(worldX, worldZ, intent.runRequested);
		lastPacketAccepted = true;
		lastPacketReason = "validated";
		lastPacketTargetX = targetLocalTileX;
		lastPacketTargetZ = targetLocalTileZ;
		// P4B: track the last sent target for F11 EXIT diagnostics.
		lastSentTileX = targetLocalTileX;
		lastSentTileZ = targetLocalTileZ;

		// Phase 3B fix #3: diagnostic logging for server sync analysis
		System.out.println("[MODERN-MOVE] PACKET: packetAccepted=true reason=validated localTile=" + targetLocalTileX + "," + targetLocalTileZ
				+ " worldTile=" + worldX + "," + worldZ
				+ " run=" + intent.runRequested
				+ " predictedTile=" + ((int)(predictedSubX >> 16) >> 7) + "," + ((int)(predictedSubZ >> 16) >> 7)
				+ " serverTile=" + lastServerReportedTileX + "," + lastServerReportedTileZ
				+ " anchor=" + movementAnchorTileX + "," + movementAnchorTileZ
				+ " pendingTail=" + pendingTail
				+ " pendingLast=" + (pendingEmpty() ? "none" : pendingTileX[(pendingTail - 1) % PENDING_CAPACITY]
						+ "," + pendingTileZ[(pendingTail - 1) % PENDING_CAPACITY])
				+ " distanceFromServer=" + Math.max(Math.abs(((int) (predictedSubX >> 16) >> 7) - lastServerReportedTileX),
						Math.abs(((int) (predictedSubZ >> 16) >> 7) - lastServerReportedTileZ)));

		// Record in pending ring buffer (LOCAL coords)
		pendingAdd(targetLocalTileX, targetLocalTileZ);
		return true;
	}

	private static void logPacketDecision(boolean accepted, String reason, int targetX, int targetZ) {
		lastPacketAccepted = accepted;
		lastPacketReason = reason;
		lastPacketTargetX = targetX;
		lastPacketTargetZ = targetZ;
		if (accepted || client.loop - lastPacketDiagnosticTick >= 25) {
			lastPacketDiagnosticTick = client.loop;
			int predictedX = (int) (predictedSubX >> 16) >> 7;
			int predictedZ = (int) (predictedSubZ >> 16) >> 7;
			int authorityX = lastServerReportedTileX >= 0 ? lastServerReportedTileX : movementAnchorTileX;
			int authorityZ = lastServerReportedTileZ >= 0 ? lastServerReportedTileZ : movementAnchorTileZ;
			System.out.println("[MODERN-MOVE] PACKET_DECISION packetAccepted=" + accepted
					+ " reason=" + reason + " target=" + targetX + "," + targetZ
					+ " anchor=" + movementAnchorTileX + "," + movementAnchorTileZ
					+ " serverTile=" + lastServerReportedTileX + "," + lastServerReportedTileZ
					+ " predictedTile=" + predictedX + "," + predictedZ
					+ " pendingTail=" + pendingTail + " pendingCount=" + getPendingCount()
					+ " distanceFromServer=" + Math.max(Math.abs(predictedX - authorityX),
							Math.abs(predictedZ - authorityZ)));
		}
	}

	private static String pendingTilesSummary() {
		if (pendingEmpty()) return "none";
		StringBuilder result = new StringBuilder();
		for (int i = pendingHead; i < pendingTail; i++) {
			if (result.length() > 0) result.append('|');
			int index = i % PENDING_CAPACITY;
			result.append(pendingTileX[index]).append(',').append(pendingTileZ[index]);
		}
		return result.toString();
	}

	/** Final packet invariant: never report a tile the live CollisionMap rejects. */
	private static String reportedTileRejectionReason(CollisionMap map, int targetX, int targetZ, int size) {
		int fromX = movementAnchorTileX >= 0 ? movementAnchorTileX : lastServerReportedTileX;
		int fromZ = movementAnchorTileZ >= 0 ? movementAnchorTileZ : lastServerReportedTileZ;
		if (fromX < 0 || fromZ < 0) {
			fromX = targetX;
			fromZ = targetZ;
		}
		if (!pendingEmpty()) {
			int pendingIndex = (pendingTail - 1) % PENDING_CAPACITY;
			fromX = pendingTileX[pendingIndex];
			fromZ = pendingTileZ[pendingIndex];
		}
		if (map == null) return "collision_map_missing";
		if (!canOccupy(map, targetX, targetZ, size)) return "destination_occupied";
		if (fromX == targetX && fromZ == targetZ) return null;
		if (Math.abs(targetX - fromX) > 1 || Math.abs(targetZ - fromZ) > 1) return "not_adjacent_to_authority";
		if (!map.method3054(fromZ, targetZ, targetX, fromX)) return "vanilla_method3054_rejected";
		// Vanilla's size-1 route test is authoritative for open doorways. Do not
		// reject a passage solely because a neighbouring/stale raw reciprocal bit
		// disagrees; the fine footprint checks above still prevent wall clipping.
		if (size != 1 && !canCrossBoundary(map, fromX, fromZ, targetX, targetZ, size)) {
			return "reciprocal_edge_blocked";
		}
		return null;
	}

	private static boolean isValidReportedTile(CollisionMap map, int targetX, int targetZ, int size) {
		return reportedTileRejectionReason(map, targetX, targetZ, size) == null;
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
	private static boolean consumePendingExact(int serverTileX, int serverTileZ) {
		// Search for exact match in pending ring
		for (int i = pendingHead; i < pendingTail; i++) {
			int idx = i % PENDING_CAPACITY;
			if (pendingTileX[idx] == serverTileX && pendingTileZ[idx] == serverTileZ) {
				// Found: discard through and including this entry
				pendingHead = i + 1;
				return true;
			}
		}
		// No exact match: server reported a genuinely different tile.
		// Clear stale pending prediction (authoritative route divergence).
		clearPending();
		return false;
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

	private static void logSnapbackCause(String source, int oldFineX, int oldFineZ,
			int newFineX, int newFineZ, boolean currentFineValid) {
		int predictedFineX = (int) (predictedSubX >> 16);
		int predictedFineZ = (int) (predictedSubZ >> 16);
		System.out.println("[MODERN-SNAPBACK]"
				+ " beforeFine=" + oldFineX + "," + oldFineZ
				+ " afterFine=" + newFineX + "," + newFineZ
				+ " beforeTile=" + (oldFineX >> 7) + "," + (oldFineZ >> 7)
				+ " afterTile=" + (newFineX >> 7) + "," + (newFineZ >> 7)
				+ " predictedTile=" + (predictedFineX >> 7) + "," + (predictedFineZ >> 7)
				+ " serverTile=" + lastServerReportedTileX + "," + lastServerReportedTileZ
				+ " anchor=" + movementAnchorTileX + "," + movementAnchorTileZ
				+ " pending=" + pendingTilesSummary()
				+ " distanceTiles=" + Math.max(Math.abs((oldFineX >> 7) - (newFineX >> 7)),
						Math.abs((oldFineZ >> 7) - (newFineZ >> 7)))
				+ " cause=" + source
				+ " currentFineValid=" + currentFineValid
				+ " lastPacket=" + lastPacketAccepted + ":" + lastPacketTargetX + "," + lastPacketTargetZ
				+ " lastRejectReason=" + lastPacketReason);
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
		int targetFineX = serverFineX + offsetX;
		int targetFineZ = serverFineZ + offsetZ;
		if (Math.max(Math.abs((currentFineX >> 7) - (targetFineX >> 7)),
				Math.abs((currentFineZ >> 7) - (targetFineZ >> 7))) > 1) {
			logSnapbackCause(lastReconcileSource, currentFineX, currentFineZ,
					targetFineX, targetFineZ,
					isFinePositionValid(currentFineX, currentFineZ, PlayerList.self.getSize()));
		}
		System.out.println("[MODERN-MOVE] REBASE_APPLY fromFine=" + currentFineX + "," + currentFineZ
				+ " serverTile=" + lastServerReportedTileX + "," + lastServerReportedTileZ
				+ " targetFine=" + (serverFineX + offsetX) + "," + (serverFineZ + offsetZ)
				+ " pendingTail=" + pendingTail);
		predictedSubX = ((long) targetFineX) << 16;
		predictedSubZ = ((long) targetFineZ) << 16;
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
