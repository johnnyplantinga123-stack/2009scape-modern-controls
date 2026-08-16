package rt4;

/**
 * Center-screen target acquisition for MODERN FP/CHASE gameplay.
 *
 * <p>Each frame (during render), gathers visible entity candidates,
 * projects them to screen coordinates, scores by angular deviation
 * from screen center, and selects the best target.</p>
 *
 * <p>Scoring: primary = screen-center distance, secondary = world distance.
 * A centered distant NPC may beat a nearby off-center NPC.</p>
 *
 * <p>This only SELECTS targets. Execution routes through existing
 * RuneScape action semantics (MiniMenu action codes, PathFinder,
 * Protocol packets). No hitscan, no new damage system.</p>
 *
 * <p>TODO 073/074 — ModernTarget model + candidate projection/scoring.</p>
 */
public final class ModernTargetingController {

	/** Maximum number of candidates gathered per frame. */
	private static final int MAX_CANDIDATES = 64;

	/** Maximum world distance (tiles) for target acquisition. */
	public static final int MAX_ACQUISITION_DISTANCE = 20;

	/** Minimum Z depth after rotation to be considered visible. */
	private static final int MIN_DEPTH = 50;

	/**
	 * Hysteresis bonus: a replacement target must improve the normalized score
	 * by this amount before it replaces the current target.  Scores produced by
	 * {@link #scoreTarget(ModernTarget)} are normalized to roughly [0, 1]; the
	 * old value of 5.0 therefore made a live target effectively permanent and
	 * prevented the crosshair from switching to a newly frontmost entity.
	 */
	private static final double HYSTERESIS_MARGIN = 0.05;

	/** Candidate pool (pre-allocated to avoid GC pressure). */
	private static final ModernTarget[] candidates = new ModernTarget[MAX_CANDIDATES];
	private static int candidateCount = 0;

	/** Current selected target. null = no target. */
	private static ModernTarget currentTarget = null;

	/** Frame counter for lifecycle invalidation. */
	private static int lastUpdateFrame = -1;

	static {
		for (int i = 0; i < MAX_CANDIDATES; i++) {
			candidates[i] = new ModernTarget();
		}
	}

	private ModernTargetingController() {
	}

	/**
	 * Returns the current best target, or null if none.
	 * Only valid during MODERN FP/CHASE.
	 */
	public static ModernTarget getCurrentTarget() {
		return currentTarget;
	}

	/**
	 * Clears the current target (e.g. on plane change, scene rebuild, death).
	 */
	public static void clearTarget() {
		currentTarget = null;
		candidateCount = 0;
	}

	/**
	 * Main per-frame update. Call during render phase after camera is set up
	 * but before UI drawing. Gathers candidates, projects, scores, selects.
	 */
	public static void update(int frameCounter) {
		if (!CameraMode.isModern()) {
			currentTarget = null;
			return;
		}
		ModernCameraRig.RigState state = ModernCameraRig.getRigState();
		if (state != ModernCameraRig.RigState.FIRST_PERSON
				&& state != ModernCameraRig.RigState.CHASE) {
			currentTarget = null;
			return;
		}
		if (PlayerList.self == null) {
			currentTarget = null;
			return;
		}
		// Hide during modal UI
		if (Cs1ScriptRunner.aBoolean108) {
			return;
		}

		lastUpdateFrame = frameCounter;
		candidateCount = 0;

		int selfPlane = Player.plane;

		// --- Gather NPC candidates ---
		for (int i = 0; i < NpcList.size && candidateCount < MAX_CANDIDATES; i++) {
			Npc npc = NpcList.npcs[NpcList.ids[i]];
			if (npc == null || npc.type == null) continue;
			if (!npc.isVisible()) continue;
			if (npc.type.multiNpcs != null) {
				NpcType resolved = npc.type.getMultiNpc();
				if (resolved == null) continue;
			}
			int tileX = npc.xFine >> 7;
			int tileZ = npc.zFine >> 7;
			int dist = chebyshevDistance(
					PlayerList.self.xFine >> 7, PlayerList.self.zFine >> 7,
					tileX, tileZ);
			if (dist > MAX_ACQUISITION_DISTANCE) continue;
			addCandidate(ModernTarget.TargetType.NPC, NpcList.ids[i],
					tileX, tileZ, selfPlane, npc.xFine, npc.zFine, 0, npc);
		}

		// --- Gather player candidates (exclude self) ---
		for (int i = 0; i < PlayerList.size && candidateCount < MAX_CANDIDATES; i++) {
			Player player = PlayerList.players[PlayerList.ids[i]];
			if (player == null || player == PlayerList.self) continue;
			if (!player.isVisible()) continue;
			int tileX = player.xFine >> 7;
			int tileZ = player.zFine >> 7;
			int dist = chebyshevDistance(
					PlayerList.self.xFine >> 7, PlayerList.self.zFine >> 7,
					tileX, tileZ);
			if (dist > MAX_ACQUISITION_DISTANCE) continue;
			addCandidate(ModernTarget.TargetType.PLAYER, PlayerList.ids[i],
					tileX, tileZ, selfPlane, player.xFine, player.zFine, 0, player);
		}

		// --- Gather ground item candidates ---
		int selfTileX = PlayerList.self.xFine >> 7;
		int selfTileZ = PlayerList.self.zFine >> 7;
		int minTX = Math.max(0, selfTileX - MAX_ACQUISITION_DISTANCE);
		int maxTX = Math.min(103, selfTileX + MAX_ACQUISITION_DISTANCE);
		int minTZ = Math.max(0, selfTileZ - MAX_ACQUISITION_DISTANCE);
		int maxTZ = Math.min(103, selfTileZ + MAX_ACQUISITION_DISTANCE);
		for (int tx = minTX; tx <= maxTX && candidateCount < MAX_CANDIDATES; tx++) {
			for (int tz = minTZ; tz <= maxTZ && candidateCount < MAX_CANDIDATES; tz++) {
				LinkedList stack = SceneGraph.objStacks[selfPlane][tx][tz];
				if (stack == null) continue;
				for (ObjStackNode node = (ObjStackNode) stack.tail();
				     node != null && candidateCount < MAX_CANDIDATES;
				     node = (ObjStackNode) stack.prev()) {
					int objId = node.value.type;
					int xFine = (tx << 7) + 64;
					int zFine = (tz << 7) + 64;
					addCandidate(ModernTarget.TargetType.GROUND_ITEM, objId,
							tx, tz, selfPlane, xFine, zFine, 0, null);
				}
			}
		}

		// --- Project and score all candidates ---
		for (int i = 0; i < candidateCount; i++) {
			projectTarget(candidates[i]);
			scoreTarget(candidates[i]);
		}

		// --- Select best target with hysteresis ---
		selectBest();
	}

	/**
	 * Adds a candidate to the pool.
	 */
	private static void addCandidate(ModernTarget.TargetType type, int entityId,
	                                 int tileX, int tileZ, int plane,
	                                 int xFine, int zFine, int yOffset,
	                                 PathingEntity entityRef) {
		if (candidateCount >= MAX_CANDIDATES) return;
		ModernTarget t = candidates[candidateCount];
		t.type = type;
		t.entityId = entityId;
		t.tileX = tileX;
		t.tileZ = tileZ;
		t.plane = plane;
		t.xFine = xFine;
		t.zFine = zFine;
		t.yOffset = yOffset;
		t.entityRef = entityRef;
		t.worldDistance = chebyshevDistance(
				PlayerList.self.xFine >> 7, PlayerList.self.zFine >> 7,
				tileX, tileZ);
		candidateCount++;
	}

	/**
	 * Projects world coordinates to screen using the same transform
	 * as the RT4 scene rendering pipeline.
	 *
	 * <p>Transform: world → camera-relative → yaw rotation → pitch rotation
	 * → perspective divide.</p>
	 *
	 * <p>Uses SceneGraph.cameraX/Y/Z (fine coords, set during scene render
	 * from Camera.renderX/anInt40/renderZ) and Camera.cameraPitch/Yaw.</p>
	 */
	private static void projectTarget(ModernTarget t) {
		// Height: terrain height at entity position, minus offset
		int elevation = SceneGraph.getTileHeight(t.plane, t.xFine, t.zFine) - t.yOffset;

		// Camera-relative position (all fine coordinates)
		int relX = t.xFine - SceneGraph.cameraX;
		int relY = elevation - SceneGraph.cameraY;
		int relZ = t.zFine - SceneGraph.cameraZ;

		// Yaw rotation (around Y axis)
		int sinYaw = MathUtils.sin[Camera.cameraYaw];
		int cosYaw = MathUtils.cos[Camera.cameraYaw];
		int rotatedX = (relZ * sinYaw + relX * cosYaw) >> 16;
		relZ = (relZ * cosYaw - relX * sinYaw) >> 16;
		relX = rotatedX;

		// Pitch rotation (around X axis)
		int sinPitch = MathUtils.sin[Camera.cameraPitch];
		int cosPitch = MathUtils.cos[Camera.cameraPitch];
		int rotatedY = (relY * cosPitch - relZ * sinPitch) >> 16;
		relZ = (relY * sinPitch + relZ * cosPitch) >> 16;
		relY = rotatedY;

		// Perspective projection
		if (relZ >= MIN_DEPTH) {
			// Fixed-mode projection (matches API.CalculateSceneGraphScreenPosition)
			t.screenX = 256 + ((relX << 9) / relZ);
			t.screenY = 167 + ((relY << 9) / relZ);
			// TODO: resizable/HD mode uses FOV-based projection
		} else {
			t.screenX = -1;
			t.screenY = -1;
		}
	}

	/**
	 * Scores a candidate. Lower is better.
	 *
	 * <p>Primary: screen-center distance (angular proxy).
	 * Secondary: world distance as tiebreaker.</p>
	 *
	 * <p>Off-screen candidates get a large penalty but are not excluded
	 * entirely (they may be just barely off-screen).</p>
	 */
	private static void scoreTarget(ModernTarget t) {
		if (t.screenX < 0 || t.screenY < 0) {
			t.score = 100000.0 + t.worldDistance;
			return;
		}

		// Viewport center
		int vpCX;
		int vpCY;
		Component viewport = InterfaceList.aClass13_26;
		if (viewport != null && viewport.width > 0 && viewport.height > 0) {
			vpCX = viewport.x + viewport.width / 2;
			vpCY = viewport.y + viewport.height / 2;
		} else {
			vpCX = GameShell.canvasWidth / 2;
			vpCY = GameShell.canvasHeight / 2;
		}

		int dx = t.screenX - vpCX;
		int dy = t.screenY - vpCY;
		double screenDist = Math.sqrt(dx * dx + dy * dy);

		// Normalize by viewport half-diagonal for [0..~1] range
		double halfDiag = Math.sqrt(
				(double) (viewport != null ? viewport.width : GameShell.canvasWidth)
						* (viewport != null ? viewport.width : GameShell.canvasWidth)
						+ (double) (viewport != null ? viewport.height : GameShell.canvasHeight)
						* (viewport != null ? viewport.height : GameShell.canvasHeight)) / 2.0;
		if (halfDiag < 1.0) halfDiag = 1.0;
		double normalizedScreen = screenDist / halfDiag;

		// World distance: secondary factor, scaled down
		double worldFactor = (double) t.worldDistance / MAX_ACQUISITION_DISTANCE;

		// Composite: 70% screen centering, 30% world proximity
		t.score = normalizedScreen * 0.7 + worldFactor * 0.3;
	}

	/**
	 * Selects the best scoring candidate as the current target.
	 * Applies hysteresis: if a current target exists, a new candidate
	 * must beat it by HYSTERESIS_MARGIN to become the new target.
	 */
	private static void selectBest() {
		ModernTarget best = null;
		double bestScore = Double.MAX_VALUE;

		for (int i = 0; i < candidateCount; i++) {
			if (candidates[i].score < bestScore) {
				bestScore = candidates[i].score;
				best = candidates[i];
			}
		}

		if (best == null) {
			currentTarget = null;
			return;
		}

		// Apply hysteresis if we have a current target
		if (currentTarget != null && currentTarget != best) {
			// Find the current target's score (it may have been re-scored)
			double currentScore = findCurrentTargetScore();
			if (bestScore >= currentScore - HYSTERESIS_MARGIN) {
				// New target isn't clearly better; keep current
				return;
			}
		}

		currentTarget = best;
	}

	/**
	 * Finds the score of the current target in the candidate pool.
	 */
	private static double findCurrentTargetScore() {
		if (currentTarget == null) return Double.MAX_VALUE;
		for (int i = 0; i < candidateCount; i++) {
			if (candidates[i] == currentTarget) {
				return candidates[i].score;
			}
		}
		return Double.MAX_VALUE;
	}

	/**
	 * Chebyshev distance (max of dx, dz) in tiles.
	 */
	private static int chebyshevDistance(int x1, int z1, int x2, int z2) {
		int dx = Math.abs(x1 - x2);
		int dz = Math.abs(z1 - z2);
		return Math.max(dx, dz);
	}
}
