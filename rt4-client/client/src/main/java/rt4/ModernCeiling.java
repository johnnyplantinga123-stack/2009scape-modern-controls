package rt4;

/**
 * Round #7 / #7B: first-person ceiling underside (gate + diagnostics).
 *
 * <h2>P4 source finding — CEILING_SOURCE_RESULT = UPPER_FLOOR_SINGLE_SIDED</h2>
 * <ul>
 *   <li>The engine/cache contains NO dedicated ceiling/underside geometry.</li>
 *   <li>Upper-plane floor surfaces ({@link PlainTile} / {@link ShapedTile})
 *       are single-sided: the software rasterizer culls them via the
 *       screen-space winding test in {@link SceneGraph#method2610}, and the
 *       GL path culls them via {@code GL_CULL_FACE}/{@code GL_BACK} on the
 *       baked {@link GlTile} vertex buffers.</li>
 *   <li>Roof LOC models are likewise single-sided.</li>
 * </ul>
 *
 * <h2>Round #7B implementation — structural coverage pass</h2>
 * <p>P0 root cause of the Round #7 crash (exact stacktrace:
 * {@code SceneGraph.modernCeilingUnderside:3995 →
 * java.lang.ArrayIndexOutOfBoundsException: 1}): the per-tile hook indexed
 * {@code tiles[plane+1]} while the bound scene was the UNDERWATER scene,
 * whose tile array has only ONE level. The Round #7B pass
 * ({@code SceneGraph.modernCeilingPass}, called once per frame at the end of
 * {@code SceneGraph.method2954}) guards against the underwater scene, uses
 * actual array lengths, and scans a bounded radius around the camera instead
 * of relying on which tiles the floor traversal happened to visit.</p>
 *
 * <p>The ceiling is generated per tile: when the tile ABOVE
 * ({@code tiles[plane+1][x][z]}) has a real plain or shaped floor tile, its
 * underside is drawn with reversed winding, reusing the upper tile's exact
 * texture/colour material. Triangles crossing the near plane are
 * clipped (Sutherland–Hodgman) instead of rejecting the whole tile. This is
 * FIRST_PERSON only ({@link #isEnabled()}), never global: ORIGINAL/CHASE/FREE
 * rendering is untouched, global culling state is never changed permanently,
 * and no ceiling is generated where no structural upper floor tile exists
 * (no outdoor/courtyard fake ceilings, none on plane &gt;= 3).</p>
 *
	 * <p>STATUS: bounded tile-based underside pass restored for the current
	 * ceiling round. Runtime remains unverified.</p>
 *
 * <h2>Current bounded ceiling round</h2>
 * <p>The pass is explicitly gated by {@link #RENDER_ENABLED} and remains
 * FIRST_PERSON-only; diagnostics expose the submitted tile/triangle counts.</p>
 */
public final class ModernCeiling {
	/** F10 toggles the temporary direct-live-mesh diagnostic. */
	private static final int KEY_F10 = 10;
	public static final int ISOLATION_DISABLED = 0;
	public static final int ISOLATION_DIRECT_LIVE_MESH = 1;
	/** Runtime-only diagnostic selection; defaults to the exact GL floor mesh. */
	private static volatile int isolationMode = ISOLATION_DIRECT_LIVE_MESH;

	/**
	 * Exact live-mesh underside pass. The legacy reconstructed tile pass is
	 * disabled; GL reissues the normal plane-above GlTile draw payload with its
	 * face orientation reversed.
	 */
	public static final boolean RENDER_ENABLED = true;
	/** Enable exact upper-floor candidate tracing for a runtime ceiling audit. */
	public static final boolean DEBUG_TILE_TRACE = false;
	/** Per-triangle console output is reserved for invalid geometry only. */
	public static final boolean DEBUG_UPCLIP = false;

	// ---- Round #7 diagnostics (read by DebugOverlay) ----
	/** Whether the player's tile has a real floor tile on the plane above. */
	public static boolean diagOverheadTilePresent;
	/** Plane of the overhead floor (-1 if none/above max plane). */
	public static int diagOverheadPlane = -1;
	/** TILE_FLAG_UNDER_ROOF on the player's current tile. */
	public static boolean diagUnderRoofFlag;
	/** Texture id of the overhead floor tile (-1 if untextured/absent). */
	public static int diagTextureId = -1;
	/** REAL_UNDERSIDE / GENERATED / NONE for this frame's player tile. */
	public static String diagSourceMode = "NONE";

	// ---- Round #7B P1 coverage diagnostics (read by DebugOverlay) ----
	/** Upper-floor tiles the coverage scan considered this frame. */
	public static int diagCandidateTiles;
	/** Candidate tiles that contributed at least one drawn triangle. */
	public static int diagDrawnTiles;
	/** Tiles rejected because ALL their vertices are behind the near plane. */
	public static int diagNearRejectedTiles;
	/** Individual vertices behind the near plane (before clipping). */
	public static int diagBehindCameraVertices;
	/** Drawn plain (flat quad) ceiling tiles this frame. */
	public static int diagPlainTiles;
	/** Drawn shaped (stairs/slope) ceiling tiles this frame. */
	public static int diagShapedTiles;
	/** Tiles/triangles skipped by geometry safety validation this frame. */
	public static int diagSkippedTiles;
	/** Last bounded skip reason, useful in the F12 ceiling block/logs. */
	public static String diagLastSkipReason = "NONE";
	/** Total ceiling triangles submitted to a rasterizer this frame. */
	public static int diagTrianglesDrawn;
	/** Submitted triangles whose projected bounding box cannot touch the viewport. */
	public static int diagOffscreenTriangles;
	/** Projection failures that were rejected before rasterization. */
	public static int diagInvalidProjection;

	/** Per-frame latch so counters reset exactly once per client loop. */
	private static int diagLoop = -1;
	/** Whether the CURRENT candidate tile is a plain tile. */
	private static boolean pendingPlain;
	/** Whether the CURRENT candidate tile already contributed a triangle. */
	private static boolean currentTileDrawn;
	/** Current candidate identity, retained solely for bounded runtime tracing. */
	private static int currentTileX;
	private static int currentTileZ;
	private static int currentPlane;
	private static boolean currentCandidateActive;
	private static int summaryStartLoop = -1;
	private static int summaryCandidateTiles;
	private static int summarySubmittedTriangles;
	private static int summaryNearRejected;
	private static int summaryBackfaceRejected;
	private static int summaryClipped3;
	private static int summaryClipped4;
	private static int summaryInvalidProjection;
	private static int summaryMissingCoverageTiles;
	private static int summaryOffscreenTriangles;
	private static int directMeshSummaryStartLoop = -1;
	private static int directMeshSummaryBatches;
	private static int directFloorTraceStartLoop = -1;
	private static int directFloorTracePlainTriangles;
	private static int directFloorTraceShapedTriangles;
	private static int directFloorTraceNormalSubmitted;
	private static int directFloorTraceUndersideSubmitted;
	private static int directFloorTracePlane = -1;
	private static int directFloorTraceTileX = -1;
	private static int directFloorTraceTileZ = -1;
	private static String directFloorTraceSourceBatch = "none";
	private static int directRoofMeshSummaryStartLoop = -1;
	private static int directRoofMeshSummaryBatches;
	private static int directRoofTraceStartLoop = -1;
	private static boolean directRoofTraceCandidateFound;
	private static boolean directRoofTraceNormalRendered;
	private static boolean directRoofTraceRemovedByVanilla;
	private static boolean directRoofTraceHookReached;
	private static boolean directRoofTraceSubmitted;
	/** Model draw groups observed inside the normal and underside entity calls. */
	private static int directRoofTraceNormalIndexGroups;
	private static int directRoofTraceNormalTriangles;
	private static int directRoofTraceUndersideIndexGroups;
	private static int directRoofTraceUndersideTriangles;
	/** 0 outside a traced entity call, 1 normal roof, 2 direct underside. */
	private static int directRoofModelSubmissionMode;
	private static String directRoofTraceSourceType = "none";
	private static String directRoofTraceReason = "no_candidate";
	// Retained for the F12 diagnostics that report the immediate overhead state.
	private static final int VIEW_SCAN_RADIUS_TILES = 14;
	private static int viewScanLoop = -1;
	private static boolean floorAboveInCameraView;
	private static boolean roofTileInCameraView;
	/**
	 * Per-frame conservative upper-floor visibility.  The normal SceneGraph
	 * latch is based on the terrain visibility volume and can lose ceiling edge
	 * tiles when the first-person camera is looking almost vertically upward.
	 */
	private static Tile[][][] conservativeCoverageTiles;
	private static int[][][] conservativeCoverageLoops;
	private static int conservativeCoveragePreparedLoop = -1;

	private ModernCeiling() {
	}

	/**
	 * Whether the ceiling underside pass is active. FIRST_PERSON only —
	 * CHASE/FREE/ORIGINAL keep untouched vanilla rendering (P7).
	 */
	public static boolean isEnabled() {
		return isolationMode != ISOLATION_DISABLED
				&& CameraMode.isModern() && ModernCameraRig.isFirstPersonRigState();
	}

	/** Called from the AWT key boundary. F10 changes only the temporary test mode. */
	public static void onKeyPressed(int keyCode) {
		if (keyCode != KEY_F10) return;
		isolationMode = isolationMode == ISOLATION_DISABLED
				? ISOLATION_DIRECT_LIVE_MESH : ISOLATION_DISABLED;
		System.out.println("[MODERN-CEILING-MODE] mode=" + isolationMode
				+ " source=" + getIsolationModeName()
				+ " (F10 toggles disabled/direct-live-mesh)");
	}

	public static int getIsolationMode() {
		return isolationMode;
	}

	public static String getIsolationModeName() {
		switch (isolationMode) {
			case ISOLATION_DISABLED: return "DISABLED";
			default: return "DIRECT_LIVE_MESH";
		}
	}

	/**
	 * The plane check is kept at the live floor-batch draw site. Every higher
	 * live plane is eligible: a multi-storey building can have several real
	 * floor/ceiling layers above the player. The exact existing GlTile geometry
	 * and depth buffer still decide the final pixels; the underside pass adds a
	 * small screen-safe overscan when the ground-oriented visibility latch drops
	 * an upward-facing tile at the edge of an extreme pitch.
	 */
	static boolean rendersLiveGlMeshForPlane(int floorPlane) {
		return RENDER_ENABLED && isEnabled() && Player.plane >= 0
				&& floorPlane > Player.plane && SceneGraph.tiles != null
				&& floorPlane < SceneGraph.tiles.length;
	}

	/**
	 * True only for a real higher-plane tile that falls inside the conservative
	 * first-person ceiling frustum for this frame.  GlTile consults this solely
	 * while it reissues the exact upper-floor payload with reversed culling.
	 */
	static boolean needsConservativeCeilingCoverage(int plane, int x, int z) {
		if (!RENDER_ENABLED || !isEnabled() || Player.plane < 0 || plane <= Player.plane
				|| SceneGraph.tiles == null || plane >= SceneGraph.tiles.length) {
			return false;
		}
		prepareConservativeCoverage();
		int stamp = client.loop + 1;
		return conservativeCoverageLoops != null && plane < conservativeCoverageLoops.length
				&& conservativeCoverageLoops[plane] != null
				&& x >= 0 && x < conservativeCoverageLoops[plane].length
				&& conservativeCoverageLoops[plane][x] != null
				&& z >= 0 && z < conservativeCoverageLoops[plane][x].length
				&& conservativeCoverageLoops[plane][x][z] == stamp;
	}

	private static void prepareConservativeCoverage() {
		Tile[][][] tiles = SceneGraph.tiles;
		if (tiles == null || Player.plane < 0) {
			return;
		}
		if (conservativeCoveragePreparedLoop == client.loop && conservativeCoverageTiles == tiles) {
			return;
		}
		conservativeCoveragePreparedLoop = client.loop;
		if (conservativeCoverageTiles != tiles || conservativeCoverageLoops == null) {
			conservativeCoverageTiles = tiles;
			conservativeCoverageLoops = new int[tiles.length][][];
			for (int plane = 0; plane < tiles.length; plane++) {
				Tile[][] level = tiles[plane];
				if (level == null) continue;
				conservativeCoverageLoops[plane] = new int[level.length][];
				for (int x = 0; x < level.length; x++) {
					conservativeCoverageLoops[plane][x] = level[x] == null ? new int[0] : new int[level[x].length];
				}
			}
		}
		for (int plane = Player.plane + 1; plane < tiles.length; plane++) {
			Tile[][] level = tiles[plane];
			if (level == null) continue;
			int x0 = Math.max(0, LightingManager.anInt987);
			int x1 = Math.min(level.length - 1, LightingManager.anInt15 - 1);
			if (x0 > x1) continue;
			for (int x = x0; x <= x1; x++) {
				Tile[] row = level[x];
				if (row == null) continue;
				int z0 = Math.max(0, LightingManager.anInt4698);
				int z1 = Math.min(row.length - 1, LightingManager.anInt4866 - 1);
				for (int z = z0; z <= z1; z++) {
					// A sloped roof can be a pure Scenery tile with no upper-floor
					// GlTile. Keep it in the same conservative screen mask so the
					// roof-model underside pass does not inherit ground-only culling.
					if (row[z] == null) continue;
					if (isConservativeCeilingTileInView(plane, x, z)) {
						conservativeCoverageLoops[plane][x][z] = client.loop + 1;
					}
				}
			}
		}
	}

	private static boolean isConservativeCeilingTileInView(int plane, int x, int z) {
		int x0 = x << 7;
		int z0 = z << 7;
		int x1 = x0 + 128;
		int z1 = z0 + 128;
		return isConservativeCeilingPointInView(x0 + 64, plane, z0 + 64)
				|| isConservativeCeilingPointInView(x0, plane, z0)
				|| isConservativeCeilingPointInView(x1, plane, z0)
				|| isConservativeCeilingPointInView(x0, plane, z1)
				|| isConservativeCeilingPointInView(x1, plane, z1);
	}

	private static boolean isConservativeCeilingPointInView(int fineX, int plane, int fineZ) {
		int fineY = SceneGraph.getTileHeight(plane, fineX, fineZ);
		int localX = fineX - Camera.renderX;
		int localZ = fineZ - Camera.renderZ;
		int localY = fineY - Camera.anInt40;
		int sinYaw = MathUtils.sin[Camera.cameraYaw];
		int cosYaw = MathUtils.cos[Camera.cameraYaw];
		int rotatedX = localX * cosYaw + localZ * sinYaw >> 16;
		int rotatedZ = localZ * cosYaw - localX * sinYaw >> 16;
		int sinPitch = MathUtils.sin[Camera.cameraPitch];
		int cosPitch = MathUtils.cos[Camera.cameraPitch];
		int depth = rotatedZ * cosPitch + localY * sinPitch >> 16;
		// Let hardware clipping resolve a triangle that crosses the near plane.
		if (depth < -256 || depth > GlobalConfig.VIEW_DISTANCE + 512) {
			return false;
		}
		int vertical = localY * cosPitch - rotatedZ * sinPitch >> 16;
		int screenDepth = Math.max(0, depth);
		// Deliberately wider than the normal camera cone. This is a small
		// overscan for tile corners, not an infinite or reconstructed roof.
		return Math.abs(rotatedX) <= screenDepth * 3 + 384
				&& Math.abs(vertical) <= screenDepth * 3 + 384;
	}

	/**
	 * A roof LOC is a different live payload from the terrain-floor GlTile.
	 * Reissue it for each visible upper storey. The depth buffer resolves any
	 * overlap with the upper-floor path, while retaining roof-only geometry such
	 * as sloped and edge sections.
	 */
	static boolean rendersLiveRoofMeshForScenery(Scenery scenery) {
		if (!RENDER_ENABLED || !isEnabled() || !GlRenderer.enabled || scenery == null
				|| scenery.entity == null) {
			return false;
		}
		Player self = PlayerList.self;
		if (self == null || SceneGraph.tiles == null || scenery.level < Player.plane
				|| scenery.level >= SceneGraph.tiles.length) {
			return false;
		}
		// SceneGraph's tile visibility latch and Entity.render's own frustum test
		// remain authoritative. A single scenery anchor point can be behind the
		// eye while part of a large higher-storey roof is still on-screen.
		return isLiveRoofSearchCandidate(scenery);
	}

	/** Called once for the surface render before live floor/roof submissions. */
	static void beginDirectRenderFrame() {
		if (RENDER_ENABLED && isEnabled()) {
			scanLiveRoofCandidates();
			emitDirectRoofTraceIfDue();
		}
	}

	/**
	 * Compact proof for the player tile's contribution to the exact GlTile path.
	 * The count comes from the same indexed fan groups that method1944 submits;
	 * it distinguishes a missing source batch from face/depth results.
	 */
	static void noteDirectFloorMeshBatch(GlTile batch, Tile[][][] sceneTiles, int floorPlane, boolean underside) {
		Player self = PlayerList.self;
		if (batch == null || self == null) return;
		int x = self.xFine >> 7;
		int z = self.zFine >> 7;
		int triangles = batch.getVisibleTriangleCountForTile(sceneTiles, floorPlane, x, z);
		if (triangles == 0) return;
		Tile tile = sceneTiles[floorPlane][x][z];
		directFloorTracePlane = floorPlane;
		directFloorTraceTileX = x;
		directFloorTraceTileZ = z;
		directFloorTraceSourceBatch = "texture=" + batch.texture + ",blend=" + batch.blend;
		if (tile.shapedTile != null) directFloorTraceShapedTriangles += triangles;
		else directFloorTracePlainTriangles += triangles;
		if (underside) directFloorTraceUndersideSubmitted += triangles;
		else directFloorTraceNormalSubmitted += triangles;
		if (directFloorTraceStartLoop == -1) {
			directFloorTraceStartLoop = client.loop;
			return;
		}
		if (client.loop - directFloorTraceStartLoop < 50) return;
		System.out.println("[DIRECT-FLOOR-MESH] tile=" + directFloorTraceTileX + ',' + directFloorTraceTileZ
				+ " plane=" + directFloorTracePlane
				+ " plainTriangles=" + directFloorTracePlainTriangles
				+ " shapedTriangles=" + directFloorTraceShapedTriangles
				+ " normalSubmitted=" + directFloorTraceNormalSubmitted
				+ " undersideSubmitted=" + directFloorTraceUndersideSubmitted
				+ " sourceBatch=" + directFloorTraceSourceBatch);
		directFloorTraceStartLoop = client.loop;
		directFloorTracePlainTriangles = 0;
		directFloorTraceShapedTriangles = 0;
		directFloorTraceNormalSubmitted = 0;
		directFloorTraceUndersideSubmitted = 0;
	}

	/** Records the real SceneGraph renderer decision for a roof LOC. */
	static void noteRoofRenderDecision(Scenery scenery, boolean removedByVanilla) {
		if (!isRoofTraceCandidate(scenery)) return;
		directRoofTraceCandidateFound = true;
		directRoofTraceSourceType = roofSourceDescription(scenery);
		directRoofTraceRemovedByVanilla |= removedByVanilla;
		if (removedByVanilla) directRoofTraceReason = "scene_occlusion";
	}

	static void noteRoofNormalRendered(Scenery scenery) {
		if (!isRoofTraceCandidate(scenery)) return;
		directRoofTraceCandidateFound = true;
		directRoofTraceNormalRendered = true;
		directRoofTraceSourceType = roofSourceDescription(scenery);
	}

	/**
	 * Marks an exact live roof entity call so {@link GlModel} can report every
	 * indexed material group it submits. This observes the existing renderer;
	 * it never rebuilds or filters model geometry.
	 */
	static boolean beginLiveRoofModelSubmission(Scenery scenery, boolean underside) {
		if (!isRoofTraceCandidate(scenery)) return false;
		directRoofModelSubmissionMode = underside ? 2 : 1;
		return true;
	}

	static void endLiveRoofModelSubmission(boolean active) {
		if (active) directRoofModelSubmissionMode = 0;
	}

	/** True only while the reissued underside Entity.render call is active. */
	static boolean isLiveRoofUndersideSubmission() {
		return directRoofModelSubmissionMode == 2;
	}

	/** Called only by GlModel at its normal material/index-group draw loop. */
	static void noteLiveRoofModelDraw(int indexGroups, int triangles) {
		if (directRoofModelSubmissionMode == 1) {
			directRoofTraceNormalIndexGroups += indexGroups;
			directRoofTraceNormalTriangles += triangles;
		} else if (directRoofModelSubmissionMode == 2) {
			directRoofTraceUndersideIndexGroups += indexGroups;
			directRoofTraceUndersideTriangles += triangles;
		}
	}

	static void noteRoofUndersideHookReached(Scenery scenery) {
		directRoofTraceHookReached = true;
		directRoofTraceCandidateFound = true;
		directRoofTraceSourceType = roofSourceDescription(scenery);
		directRoofTraceReason = "live_scenery";
	}

	private static boolean isRoofTraceCandidate(Scenery scenery) {
		if (!RENDER_ENABLED || !isEnabled() || scenery == null || scenery.entity == null) return false;
		return isLiveRoofSearchCandidate(scenery);
	}

	private static boolean isLiveRoofSearchCandidate(Scenery scenery) {
		Player self = PlayerList.self;
		if (self == null || SceneGraph.tiles == null || scenery.level < Player.plane
				|| scenery.level >= SceneGraph.tiles.length) return false;
		int shape = (int) (scenery.key >>> 14 & 0x3FL);
		return shape >= LocType.ROOF_STRAIGHT && shape <= LocType.ROOFEDGE_SQUARECORNER;
	}

	/**
	 * Roof LOCs are stored on their origin/footprint tiles, not necessarily on
	 * the player tile that carries TILE_FLAG_UNDER_ROOF. Inspect the full live
	 * SceneGraph visibility window: roof ridges/corners can be visible from more
	 * than four tiles away, especially when the player looks upward.
	 */
	private static void scanLiveRoofCandidates() {
		Player self = PlayerList.self;
		if (self == null || SceneGraph.tiles == null) return;
		for (int plane = Player.plane; plane < SceneGraph.tiles.length; plane++) {
			Tile[][] level = SceneGraph.tiles[plane];
			if (level == null) continue;
			int x0 = Math.max(0, LightingManager.anInt987);
			int x1 = Math.min(level.length - 1, LightingManager.anInt15 - 1);
			if (x0 > x1) continue;
			for (int x = x0; x <= x1; x++) {
				Tile[] row = level[x];
				if (row == null) continue;
				int z0 = Math.max(0, LightingManager.anInt4698);
				int z1 = Math.min(row.length - 1, LightingManager.anInt4866 - 1);
				for (int z = z0; z <= z1; z++) {
					Tile tile = row[z];
					if (tile == null) continue;
					for (int i = 0; i < tile.sceneryLen; i++) {
						Scenery scenery = tile.scenery[i];
						// A multi-tile roof is indexed on each covered tile. Its origin
						// may be outside this local window, so never require origin == x,z.
						if (scenery != null && isRoofTraceCandidate(scenery)) {
							directRoofTraceCandidateFound = true;
							directRoofTraceSourceType = roofSourceDescription(scenery);
							directRoofTraceReason = "live_scenery_cluster";
						}
					}
				}
			}
		}
	}

	private static String roofSourceDescription(Scenery scenery) {
		return "Scenery@p" + scenery.level + ':' + scenery.xMin + ',' + scenery.zMin
				+ "-" + scenery.xMax + ',' + scenery.zMax;
	}

	private static void emitDirectRoofTraceIfDue() {
		if (directRoofTraceStartLoop == -1) {
			directRoofTraceStartLoop = client.loop;
			return;
		}
		if (client.loop - directRoofTraceStartLoop < 50) return;
		Player self = PlayerList.self;
		int x = self == null ? -1 : self.xFine >> 7;
		int z = self == null ? -1 : self.zFine >> 7;
		boolean roofFlag = Player.plane >= 0 && Player.plane < SceneGraph.renderFlags.length
				&& x >= 0 && z >= 0 && x < SceneGraph.renderFlags[Player.plane].length
				&& z < SceneGraph.renderFlags[Player.plane][x].length
				&& (SceneGraph.renderFlags[Player.plane][x][z] & plugin.api.API.TILE_FLAG_UNDER_ROOF) != 0;
		System.out.println("[DIRECT-ROOF-TRACE] playerTile=" + x + ',' + z
				+ " plane=" + Player.plane + " roofFlag=" + roofFlag
				+ " floorAbove=" + hasLiveFloorAbovePlayer()
				+ " roofCandidateFound=" + directRoofTraceCandidateFound
				+ " roofSourceType=" + directRoofTraceSourceType
				+ " normalRoofRendered=" + directRoofTraceNormalRendered
				+ " roofRemovedByVanilla=" + directRoofTraceRemovedByVanilla
				+ " undersideHookReached=" + directRoofTraceHookReached
				+ " undersideSubmitted=" + directRoofTraceSubmitted
				+ " normalIndexGroups=" + directRoofTraceNormalIndexGroups
				+ " normalTriangles=" + directRoofTraceNormalTriangles
				+ " undersideIndexGroups=" + directRoofTraceUndersideIndexGroups
				+ " undersideTriangles=" + directRoofTraceUndersideTriangles
				+ " undersideCull=disabled"
				+ " reason=" + directRoofTraceReason);
		directRoofTraceStartLoop = client.loop;
		directRoofTraceCandidateFound = false;
		directRoofTraceNormalRendered = false;
		directRoofTraceRemovedByVanilla = false;
		directRoofTraceHookReached = false;
		directRoofTraceSubmitted = false;
		directRoofTraceNormalIndexGroups = 0;
		directRoofTraceNormalTriangles = 0;
		directRoofTraceUndersideIndexGroups = 0;
		directRoofTraceUndersideTriangles = 0;
		directRoofTraceSourceType = "none";
		directRoofTraceReason = "no_candidate";
	}

	/** One throttled proof that vanilla's exact roof LOC payload was reissued. */
	static void noteLiveRoofMesh(Scenery scenery) {
		diagSourceMode = "LIVE_GL_ROOF_MESH";
		directRoofTraceSubmitted = true;
		directRoofMeshSummaryBatches++;
		if (directRoofMeshSummaryStartLoop == -1) {
			directRoofMeshSummaryStartLoop = client.loop;
			return;
		}
		if (client.loop - directRoofMeshSummaryStartLoop < 50) return;
		System.out.println("[MODERN-ROOF-DIRECT-MESH] batches=" + directRoofMeshSummaryBatches
				+ " source=Scenery.Loc exact_normal_roof_payload");
		directRoofMeshSummaryStartLoop = client.loop;
		directRoofMeshSummaryBatches = 0;
	}

	private static boolean hasLiveFloorAbovePlayer() {
		Player self = PlayerList.self;
		if (self == null || SceneGraph.tiles == null) {
			return false;
		}
		int upperPlane = Player.plane + 1;
		int x = self.xFine >> 7;
		int z = self.zFine >> 7;
		if (upperPlane < 0 || upperPlane >= SceneGraph.tiles.length || SceneGraph.tiles[upperPlane] == null || x < 0 || z < 0
				|| x >= SceneGraph.tiles[upperPlane].length || SceneGraph.tiles[upperPlane][x] == null
				|| z >= SceneGraph.tiles[upperPlane][x].length) {
			return false;
		}
		Tile overhead = SceneGraph.tiles[upperPlane][x][z];
		return overhead != null && (overhead.plainTile != null || overhead.shapedTile != null);
	}

	private static boolean hasLiveFloorAbovePlayerOrInCameraView() {
		return hasLiveFloorAbovePlayer() || hasCameraViewCeilingState();
	}

	private static boolean shouldRenderRoofUndersides() {
		return hasCameraViewRoofState();
	}

	private static boolean hasCameraViewCeilingState() {
		refreshCameraViewState();
		return floorAboveInCameraView;
	}

	private static boolean hasCameraViewRoofState() {
		refreshCameraViewState();
		return roofTileInCameraView;
	}

	/**
	 * Finds existing upper-floor and roof tiles in the current camera cone.
	 * SceneGraph still performs the real visibility/depth work; this only
	 * enables the already-existing reversed-face submission in FP.
	 */
	private static void refreshCameraViewState() {
		if (viewScanLoop == client.loop) {
			return;
		}
		viewScanLoop = client.loop;
		floorAboveInCameraView = false;
		roofTileInCameraView = false;
		if (SceneGraph.tiles == null || SceneGraph.renderFlags == null || Player.plane < 0) {
			return;
		}
		int cameraTileX = Camera.renderX >> 7;
		int cameraTileZ = Camera.renderZ >> 7;
		int x0 = Math.max(0, cameraTileX - VIEW_SCAN_RADIUS_TILES);
		int x1 = Math.min(103, cameraTileX + VIEW_SCAN_RADIUS_TILES);
		int z0 = Math.max(0, cameraTileZ - VIEW_SCAN_RADIUS_TILES);
		int z1 = Math.min(103, cameraTileZ + VIEW_SCAN_RADIUS_TILES);
		int upperPlane = Player.plane + 1;
		for (int x = x0; x <= x1; x++) {
			for (int z = z0; z <= z1; z++) {
				if (!isTileInCameraView(x, z, upperPlane)) {
					continue;
				}
				if (!floorAboveInCameraView && hasUpperFloorTile(upperPlane, x, z)) {
					floorAboveInCameraView = true;
				}
				if (!roofTileInCameraView && isRoofFlaggedTile(Player.plane, x, z)) {
					roofTileInCameraView = true;
				}
				if (floorAboveInCameraView && roofTileInCameraView) {
					return;
				}
			}
		}
	}

	private static boolean hasUpperFloorTile(int plane, int x, int z) {
		if (plane < 0 || plane >= SceneGraph.tiles.length || SceneGraph.tiles[plane] == null
				|| x < 0 || x >= SceneGraph.tiles[plane].length || SceneGraph.tiles[plane][x] == null
				|| z < 0 || z >= SceneGraph.tiles[plane][x].length) {
			return false;
		}
		Tile tile = SceneGraph.tiles[plane][x][z];
		return tile != null && (tile.plainTile != null || tile.shapedTile != null);
	}

	private static boolean isRoofFlaggedTile(int plane, int x, int z) {
		return plane >= 0 && plane < SceneGraph.renderFlags.length
				&& SceneGraph.renderFlags[plane] != null
				&& x >= 0 && x < SceneGraph.renderFlags[plane].length
				&& SceneGraph.renderFlags[plane][x] != null
				&& z >= 0 && z < SceneGraph.renderFlags[plane][x].length
				&& (SceneGraph.renderFlags[plane][x][z] & plugin.api.API.TILE_FLAG_UNDER_ROOF) != 0;
	}

	private static boolean isSceneryInCameraView(Scenery scenery) {
		return isCameraViewPoint(scenery.anInt1699, scenery.anInt1706, scenery.anInt1703);
	}

	private static boolean isTileInCameraView(int x, int z, int upperPlane) {
		int fineX = (x << 7) + 64;
		int fineZ = (z << 7) + 64;
		return isCameraViewPoint(fineX, SceneGraph.getTileHeight(upperPlane, fineX, fineZ), fineZ);
	}

	/** Conservative screen-cone test using the same transform as the client camera. */
	private static boolean isCameraViewPoint(int fineX, int fineY, int fineZ) {
		int localX = fineX - Camera.renderX;
		int localZ = fineZ - Camera.renderZ;
		int localY = fineY - Camera.anInt40;
		int sinYaw = MathUtils.sin[Camera.cameraYaw];
		int cosYaw = MathUtils.cos[Camera.cameraYaw];
		int rotatedX = localX * cosYaw + localZ * sinYaw >> 16;
		int rotatedZ = localZ * cosYaw - localX * sinYaw >> 16;
		int depth = rotatedZ * MathUtils.cos[Camera.cameraPitch] + localY * MathUtils.sin[Camera.cameraPitch] >> 16;
		// Keep roof eligibility aligned with the actual active render distance,
		// not the old 14-tile diagnostic scan. This matters for higher storeys
		// whose visible roof geometry is farther away along the camera ray.
		if (depth < 50 || depth > GlobalConfig.VIEW_DISTANCE) {
			return false;
		}
		int vertical = localY * MathUtils.cos[Camera.cameraPitch] - rotatedZ * MathUtils.sin[Camera.cameraPitch] >> 16;
		return Math.abs(rotatedX) <= depth * 2 + 128 && Math.abs(vertical) <= depth * 2 + 256;
	}

	/** One throttled proof that the exact plane-above GL batches were reissued. */
	static void noteLiveGlMeshBatch(int floorPlane) {
		diagSourceMode = "LIVE_GL_MESH";
		directMeshSummaryBatches++;
		if (directMeshSummaryStartLoop == -1) {
			directMeshSummaryStartLoop = client.loop;
			return;
		}
		if (client.loop - directMeshSummaryStartLoop < 50) return;
		System.out.println("[MODERN-CEILING-DIRECT-MESH] upperPlane=" + floorPlane
				+ " batches=" + directMeshSummaryBatches
				+ " source=GlTile exact_normal_floor_payload");
		directMeshSummaryStartLoop = client.loop;
		directMeshSummaryBatches = 0;
	}

	/** Legacy scan selectors are hard-disabled; do not revive reconstructed tiles. */
	static boolean rendersPlainTiles() {
		return false;
	}

	static boolean rendersShapedTiles() {
		return false;
	}

	/**
	 * Called once per frame at the start of the coverage pass; resets all
	 * per-frame counters (latched on {@code client.loop} so an early return
	 * doesn't double-reset within the same frame).
	 */
	static void beginCoverageFrame() {
		if (client.loop != diagLoop) {
			diagLoop = client.loop;
			diagCandidateTiles = 0;
			diagDrawnTiles = 0;
			diagNearRejectedTiles = 0;
			diagBehindCameraVertices = 0;
			diagPlainTiles = 0;
			diagShapedTiles = 0;
			diagTrianglesDrawn = 0;
			diagOffscreenTriangles = 0;
			diagInvalidProjection = 0;
			diagSkippedTiles = 0;
			diagLastSkipReason = "NONE";
			currentCandidateActive = false;
		}
	}

	/** Called by the coverage scan for every structurally valid tile. */
	static void noteCandidate(boolean plain, int tileX, int tileZ, int plane) {
		finishCurrentCandidate();
		diagCandidateTiles++;
		summaryCandidateTiles++;
		pendingPlain = plain;
		currentTileDrawn = false;
		currentCandidateActive = true;
		currentTileX = tileX;
		currentTileZ = tileZ;
		currentPlane = plane;
		traceTile(tileX, tileZ, plane, plain ? "plain" : "shaped", "candidate");
	}

	static void traceTile(int tileX, int tileZ, int plane, String kind, String status) {
		if (DEBUG_TILE_TRACE) {
			System.out.println("[MODERN-CEILING] tile=" + tileX + "," + tileZ
					+ " upperPlane=" + plane + " kind=" + kind + " status=" + status);
		}
	}

	static void noteUpClip(int tileX, int tileZ, int triangle, int verticesBefore,
			int verticesAfterClip, boolean nearRejected, boolean backfaceRejected, boolean rendered) {
		if (verticesAfterClip == 3) summaryClipped3++;
		if (verticesAfterClip == 4) summaryClipped4++;
		if (nearRejected) summaryNearRejected++;
		if (backfaceRejected) summaryBackfaceRejected++;
	}

	/** Called for every projected vertex that lies behind the near plane. */
	static void noteBehindVertex() {
		diagBehindCameraVertices++;
	}

	/** Called when a tile is rejected because all vertices are behind. */
	static void noteNearRejectedTile() {
		diagNearRejectedTiles++;
		summaryNearRejected++;
	}

	/** Called only after a triangle is actually submitted to a rasterizer. */
	static void noteTriangleSubmitted() {
		diagTrianglesDrawn++;
		summarySubmittedTriangles++;
		if (!currentTileDrawn) {
			currentTileDrawn = true;
			diagDrawnTiles++;
			if (pendingPlain) {
				diagPlainTiles++;
			} else {
				diagShapedTiles++;
			}
			traceTile(currentTileX, currentTileZ, currentPlane,
					pendingPlain ? "plain" : "shaped", "rendered");
		}
	}

	/** A submitted triangle was valid but cannot contribute to the viewport. */
	static void noteOffscreenTriangle() {
		diagOffscreenTriangles++;
		summaryOffscreenTriangles++;
	}

	/** Invalid projection detail is rare and is the only per-triangle console output. */
	static void noteInvalidProjection(int tileX, int tileZ, int triangle, int vertices) {
		diagInvalidProjection++;
		summaryInvalidProjection++;
		System.out.println("[MODERN-CEILING-INVALID-PROJECTION] cameraPitch=" + Camera.cameraPitch
				+ " tile=" + tileX + "," + tileZ + " triangle=" + triangle
				+ " verticesAfterClip=" + vertices);
	}

	static void noteSkipped(String reason) {
		diagSkippedTiles++;
		diagLastSkipReason = reason;
		traceTile(currentTileX, currentTileZ, currentPlane,
				pendingPlain ? "plain" : "shaped", "skip:" + reason);
	}

	static void noteCurrentTileNotDrawn(String reason) {
		traceTile(currentTileX, currentTileZ, currentPlane,
				pendingPlain ? "plain" : "shaped", reason);
	}

	/** Finishes the pass and prints one aggregated result roughly once a second. */
	static void finishCoverageFrame() {
		finishCurrentCandidate();
		if (summaryStartLoop == -1) {
			summaryStartLoop = client.loop;
			return;
		}
		if (client.loop - summaryStartLoop < 50) return;
		System.out.println("[MODERN-CEILING-SUMMARY] cameraPitch=" + Camera.cameraPitch
				+ " candidateTiles=" + summaryCandidateTiles
				+ " submittedTriangles=" + summarySubmittedTriangles
				+ " nearRejected=" + summaryNearRejected
				+ " backfaceRejected=" + summaryBackfaceRejected
				+ " clipped3=" + summaryClipped3
				+ " clipped4=" + summaryClipped4
				+ " invalidProjection=" + summaryInvalidProjection
				+ " missingCoverageTiles=" + summaryMissingCoverageTiles
				+ " offscreenTriangles=" + summaryOffscreenTriangles);
		summaryStartLoop = client.loop;
		summaryCandidateTiles = 0;
		summarySubmittedTriangles = 0;
		summaryNearRejected = 0;
		summaryBackfaceRejected = 0;
		summaryClipped3 = 0;
		summaryClipped4 = 0;
		summaryInvalidProjection = 0;
		summaryMissingCoverageTiles = 0;
		summaryOffscreenTriangles = 0;
	}

	private static void finishCurrentCandidate() {
		if (currentCandidateActive && !currentTileDrawn) summaryMissingCoverageTiles++;
		currentCandidateActive = false;
	}

	/** Ceiling triangles drawn during the current frame. */
	public static int getQuadsDrawn() {
		return diagTrianglesDrawn;
	}

	/**
	 * Refreshes the overhead-tile diagnostics for the PLAYER's current tile.
	 * Called from {@link DebugOverlay#draw()} so the F12 CEILING block shows
	 * meaningful values; the draw pass itself never allocates.
	 */
	public static void updateDiagnostics() {
		diagOverheadTilePresent = false;
		diagOverheadPlane = -1;
		diagUnderRoofFlag = false;
		diagTextureId = -1;
		diagSourceMode = "NONE";

		Player self = PlayerList.self;
		if (self == null || SceneGraph.tiles == null || SceneGraph.tileHeights == null) {
			return;
		}
		int x = self.xFine >> 7;
		int z = self.zFine >> 7;
		int plane = Player.plane;
		if (x < 0 || z < 0 || plane < 0
				|| plane >= SceneGraph.renderFlags.length
				|| SceneGraph.renderFlags[plane] == null
				|| x >= SceneGraph.renderFlags[plane].length
				|| SceneGraph.renderFlags[plane][x] == null
				|| z >= SceneGraph.renderFlags[plane][x].length) {
			return;
		}
		diagUnderRoofFlag = (SceneGraph.renderFlags[plane][x][z] & plugin.api.API.TILE_FLAG_UNDER_ROOF) != 0;

		int above = plane + 1;
		if (above >= SceneGraph.tiles.length || SceneGraph.tiles[above] == null
				|| x >= SceneGraph.tiles[above].length || SceneGraph.tiles[above][x] == null
				|| z >= SceneGraph.tiles[above][x].length) {
			return;
		}
		diagOverheadPlane = above;
		Tile overhead = SceneGraph.tiles[above][x][z];
		if (overhead == null) {
			return;
		}
		boolean directLiveMesh = GlRenderer.enabled && rendersLiveGlMeshForPlane(above);
		if (overhead.plainTile != null) {
			diagOverheadTilePresent = true;
			diagTextureId = overhead.plainTile.anInt4869;
			diagSourceMode = directLiveMesh ? "LIVE_GL_MESH" : "NONE";
		} else if (overhead.shapedTile != null
				&& overhead.shapedTile.anIntArray161 != null
				&& overhead.shapedTile.anIntArray161.length > 0) {
			diagOverheadTilePresent = true;
			diagTextureId = overhead.shapedTile.anIntArray161[0];
			diagSourceMode = directLiveMesh ? "LIVE_GL_MESH" : "NONE";
		} else if (overhead.shapedTile != null) {
			diagOverheadTilePresent = true;
			diagSourceMode = directLiveMesh ? "LIVE_GL_MESH" : "NONE";
		}
	}
}
