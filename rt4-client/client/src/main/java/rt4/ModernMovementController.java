package rt4;

/**
 * Modern WASD movement controller (Phase 3).
 *
 * <h2>Movement Authority Strategy</h2>
 *
 * <p>This controller does <b>NOT</b> directly write to
 * {@link PathingEntity#xFine} or {@link PathingEntity#zFine}. Instead, it
 * feeds movement intents into the <b>existing movement queue</b> via
 * {@link PathFinder#findPath}, which is the same path used by click-to-move.
 *
 * <p>The single owner of {@code xFine/zFine} interpolation remains
 * {@link NpcList#method2247}, which reads from
 * {@code movementQueueX/Z/Speed/Size} and moves the entity toward the queue
 * target each tick. This ensures:
 *
 * <ul>
 *   <li>No dual-authority conflict (no two systems writing xFine/zFine).</li>
 *   <li>Existing walk/run animation selection works unchanged.</li>
 *   <li>Existing orientation smoothing ({@link NpcList#method949}) works unchanged.</li>
 *   <li>Existing networking ({@link ClientProt#method3502}) sends valid routes.</li>
 *   <li>Server authority is preserved — the server validates every step.</li>
 * </ul>
 *
 * <h2>How WASD Works</h2>
 *
 * <ol>
 *   <li>Read WASD held state from {@link Keyboard#pressedKeys}.</li>
 *   <li>Build a camera-relative {@link MovementIntent} (forward/right).</li>
 *   <li>Normalize diagonal input so W+D is not faster than W alone.</li>
 *   <li>Convert intent to a world-space target tile (1 tile ahead).</li>
 *   <li>Call {@link PathFinder#findPath} to validate collision and enqueue.</li>
 *   <li>{@code findPath} internally calls {@link ClientProt#method3502} to
 *       send the walk route to the server.</li>
 *   <li>{@link NpcList#method2247} interpolates xFine/zFine toward the target
 *       as it does for all pathing entities.</li>
 * </ol>
 *
 * <h2>Smoothness Limitation (Phase 3)</h2>
 *
 * <p>Movement is tile-to-tile interpolated by {@code method2247} at the
 * existing RuneScape speed (4–8 fine units per tick depending on walk/run
 * and queue depth). This is not as smooth as free-fly fine-coordinate
 * movement, but it is <b>correct</b> and safe. True smooth fine-coordinate
 * prediction can be added in a later phase when a proper client-prediction
 * and server-reconciliation system is implemented.
 *
 * <h2>What This Does NOT Do (Phase 3)</h2>
 *
 * <ul>
 *   <li>No custom collision engine (Phase 4).</li>
 *   <li>No wall sliding (Phase 4).</li>
 *   <li>No player-radius collision (Phase 4).</li>
 *   <li>No targeting/interaction (Phase 6+).</li>
 *   <li>No third-person camera (Phase 14).</li>
 *   <li>No combat modifications.</li>
 *   <li>No protocol changes.</li>
 * </ul>
 */
public final class ModernMovementController {

	// ---- WASD key codes (from Keyboard.CODE_MAP) ----
	private static final int KEY_W = 33;
	private static final int KEY_A = 48;
	private static final int KEY_S = 49;
	private static final int KEY_D = 50;
	private static final int KEY_CTRL = 82;

	/**
	 * Minimum interval (in game ticks) between sending movement packets.
	 * Prevents packet spam while still allowing responsive WASD.
	 * RuneScape runs at ~50ms per tick, so 3 ticks ≈ 150ms.
	 */
	private static final int SEND_THROTTLE_TICKS = 3;

	/** Reusable movement intent (avoids per-tick allocation). */
	private static final MovementIntent intent = new MovementIntent();

	/** Ticks since last movement packet was sent. */
	private static int ticksSinceLastSend = 0;

	/** Last tile X we sent a movement for (to avoid duplicate sends). */
	private static int lastSentTileX = -1;

	/** Last tile Z we sent a movement for. */
	private static int lastSentTileZ = -1;

	/** Whether WASD movement was active last tick (for edge detection). */
	private static boolean wasMoving = false;

	private ModernMovementController() {
	}

	/**
	 * Per-tick update. Called from {@link ModernControlController#update()}
	 * when a modern camera mode is active.
	 */
	public static void update() {
		if (PlayerList.self == null) {
			return;
		}
		if (!ModernControlController.isGameplayInputAllowed()) {
			intent.clear();
			return;
		}

		ticksSinceLastSend++;

		// Build movement intent from WASD keys
		readInput();

		if (!intent.hasMovement()) {
			wasMoving = false;
			return;
		}

		// Normalize diagonal input
		intent.normalize();

		// Convert camera-relative intent to world-space target tile
		Player self = PlayerList.self;
		int currentTileX = self.xFine >> 7;
		int currentTileZ = self.zFine >> 7;

		// Camera yaw: 0 = north, 512 = east, 1024 = south, 1536 = west
		// Forward vector: sin(yaw), cos(yaw) in RS coordinate space
		// Note: In RS, yaw 0 = south, 512 = west, 1024 = north, 1536 = east
		// But Camera.cameraYaw uses the same convention.
		int yaw = Camera.cameraYaw;
		double yawRad = yaw * (Math.PI * 2.0 / 2048.0);

		// Forward direction (camera look direction projected to ground)
		double forwardX = -Math.sin(yawRad);
		double forwardZ = -Math.cos(yawRad);

		// Right direction (perpendicular to forward)
		double rightX = Math.cos(yawRad);
		double rightZ = -Math.sin(yawRad);

		// Combine intent with direction vectors
		double moveX = forwardX * intent.forward + rightX * intent.right;
		double moveZ = forwardZ * intent.forward + rightZ * intent.right;

		// Determine target tile (1 tile in the movement direction)
		// Use sign to pick the dominant axis for a single-tile step
		int targetTileX = currentTileX;
		int targetTileZ = currentTileZ;

		// Pick the dominant direction for tile stepping
		double absX = Math.abs(moveX);
		double absZ = Math.abs(moveZ);

		if (absX > 0.3 || absZ > 0.3) {
			// Determine step direction
			int stepX = 0;
			int stepZ = 0;

			if (absX >= absZ) {
				// Primarily moving along X axis
				stepX = moveX > 0 ? 1 : -1;
				// Add Z component if significant
				if (absZ > 0.3) {
					stepZ = moveZ > 0 ? 1 : -1;
				}
			} else {
				// Primarily moving along Z axis
				stepZ = moveZ > 0 ? 1 : -1;
				// Add X component if significant
				if (absX > 0.3) {
					stepX = moveX > 0 ? 1 : -1;
				}
			}

			targetTileX = currentTileX + stepX;
			targetTileZ = currentTileZ + stepZ;
		}

		// Don't send if target is same as current (no movement needed)
		if (targetTileX == currentTileX && targetTileZ == currentTileZ) {
			return;
		}

		// Throttle: don't send too frequently
		if (ticksSinceLastSend < SEND_THROTTLE_TICKS) {
			return;
		}

		// Don't resend if we already sent for this target tile
		if (targetTileX == lastSentTileX && targetTileZ == lastSentTileZ) {
			return;
		}

		// Convert world tile to local tile (0..103 relative to camera origin).
		// PathFinder.findPath operates entirely in LOCAL coordinates — all
		// existing click-to-move call sites (MiniMenu.doAction WALK_HERE, NPC
		// pathing, etc.) pass local coordinates, not world coordinates.
		int localDestX = targetTileX - Camera.originX;
		int localDestZ = targetTileZ - Camera.originZ;

		// Clamp target to valid local map range
		if (localDestX < 0 || localDestX > 103 || localDestZ < 0 || localDestZ > 103) {
			return;
		}

		// Use existing PathFinder to validate collision and send movement.
		// findPath params: srcZ, angle, arg2, objectPathing, runModifier,
		//   destX, size, arg7, mode, destZ, srcX
		// mode=0 → MOVE_GAMECLICK (215), the standard walk packet.
		// mode=2 → opcode 77 (walk+action), used for NPC/object interactions.
		// We use mode=0 for basic WASD walking.
		boolean found = PathFinder.findPath(
				self.movementQueueZ[0],  // srcZ (local)
				0,                        // angle (0 = no specific approach angle)
				0,                        // arg2 (unused for single-tile step)
				false,                    // arg3 (not object pathing)
				0,                        // arg4 (runModifier — method3502 reads Ctrl directly)
				localDestX,               // destX (LOCAL tile coordinate)
				1,                        // size (1x1 for player)
				0,                        // arg7
				0,                        // mode (0 = MOVE_GAMECLICK)
				localDestZ,               // destZ (LOCAL tile coordinate)
				self.movementQueueX[0]    // srcX (local)
		);

		if (found) {
			ticksSinceLastSend = 0;
			lastSentTileX = targetTileX;
			lastSentTileZ = targetTileZ;
			wasMoving = true;
		}
	}

	/**
	 * Reads WASD key state and populates the movement intent.
	 */
	private static void readInput() {
		intent.clear();

		if (Keyboard.pressedKeys[KEY_W]) {
			intent.forward += 1f;
		}
		if (Keyboard.pressedKeys[KEY_S]) {
			intent.forward -= 1f;
		}
		if (Keyboard.pressedKeys[KEY_D]) {
			intent.right += 1f;
		}
		if (Keyboard.pressedKeys[KEY_A]) {
			intent.right -= 1f;
		}

		// Run toggle: Ctrl key or existing run-energy toggle
		intent.runRequested = Keyboard.pressedKeys[KEY_CTRL];
	}

	/**
	 * Resets movement state. Called on teleport, death, region change, etc.
	 */
	public static void reset() {
		intent.clear();
		ticksSinceLastSend = 0;
		lastSentTileX = -1;
		lastSentTileZ = -1;
		wasMoving = false;
	}

	/**
	 * Returns whether WASD movement is currently active (any key held).
	 */
	public static boolean isMoving() {
		return intent.hasMovement();
	}
}