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

	// ==== FLAGS ====
	private static boolean initialized;
	private static boolean suspended;

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
		lastMovementState = MovementState.IDLE;
		clearPending();
		initialized = true;
		suspended = false;
	}

	/**
	 * MODERN → ORIGINAL transition.
	 * Correction 6: safe handoff without modifying movementQueueX/Z/Size.
	 * If predicted tile differs from server-reported, rebase fine to server tile center.
	 */
	public static void exitModernMode() {
		if (initialized && PlayerList.self != null) {
			Player self = PlayerList.self;
			int predictedTileX = (int) (predictedSubX >> 16) >> 7;
			int predictedTileZ = (int) (predictedSubZ >> 16) >> 7;

			if (predictedTileX != lastServerReportedTileX
					|| predictedTileZ != lastServerReportedTileZ) {
				// Rebase to authoritative server tile center using getSize() * 64
				self.xFine = (lastServerReportedTileX << 7) + self.getSize() * 64;
				self.zFine = (lastServerReportedTileZ << 7) + self.getSize() * 64;
			}
			// else: preserve current fine position to avoid unnecessary visible snap
		}

		velocityXQ16 = 0;
		velocityZQ16 = 0;
		initialized = false;
		suspended = false;
		intent.clear();
	}

	/** FIRST_PERSON ↔ THIRD_PERSON: locomotion unchanged, camera only. */
	public static void onModernModeSwitch() {
		// No prediction reset needed.
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
		if (!CameraMode.isModern()) return;
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

		if (!intent.hasMovement()) {
			velocityXQ16 = 0;
			velocityZQ16 = 0;
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
		// THIRD_PERSON uses player body heading (temporary, Phase 14 will replace).
		int camYaw = CameraMode.getCameraRelativeYaw();
		int yaw = (camYaw >= 0) ? camYaw : self.anInt3400;
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
			// This maps: north=0, west=512, south=1024, east=1536.
			if (velocityXQ16 != 0 || velocityZQ16 != 0) {
				targetOrientationAngle = (int) (Math.atan2(
						(double) velocityXQ16,
						(double) velocityZQ16) * -325.949D) & 0x7FF;
				self.anInt3400 = targetOrientationAngle;
				DebugOverlay.lastBodyYawWriter = "movement_controller";
			}
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
