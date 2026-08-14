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
 * underside is drawn with reversed winding, ~20% darker, reusing the upper
 * tile's texture/colour material. Triangles crossing the near plane are
 * clipped (Sutherland–Hodgman) instead of rejecting the whole tile. This is
 * FIRST_PERSON only ({@link #isEnabled()}), never global: ORIGINAL/CHASE/FREE
 * rendering is untouched, global culling state is never changed permanently,
 * and no ceiling is generated where no structural upper floor tile exists
 * (no outdoor/courtyard fake ceilings, none on plane &gt;= 3).</p>
 *
 * <p>STATUS: SOURCE VERIFIED (P0 stacktrace + root cause), COMPILE VERIFIED
 * after build, <b>RUNTIME FAILED (Round #7B user runtime)</b>: no visible
 * ceiling indoors + a giant horizontal slab artifact at certain camera
 * angles.</p>
 *
 * <h2>Round #7C — QUARANTINED</h2>
 * <p>{@link #RENDER_ENABLED} is {@code false}: the generated ceiling pass
 * submits ZERO geometry until a future round decides otherwise. The
 * implementation is preserved, not deleted. Flip the single flag to
 * re-enable. Diagnostics still report {@code rendererEnabled=N} and
 * {@code trianglesSubmitted=0} so the quarantine is visible on F12.</p>
 */
public final class ModernCeiling {

	/**
	 * Round #7C P1 quarantine gate: the Round #7/#7B generated ceiling pass
	 * is DISABLED (runtime failed: no ceiling + giant slab artifact). Single
	 * explicit source gate — the pass submits zero geometry while false.
	 * DO NOT re-enable without a fresh round decision.
	 */
	public static final boolean RENDER_ENABLED = false;

	/**
	 * Lightness multiplier scale (128 = 100%): 102 ≈ 80%, i.e. the ceiling
	 * underside renders ~20% darker than the upper floor's top surface.
	 */
	static final int DARKEN_MULTIPLIER = 102;

	/**
	 * Vertical offset (world units) below the upper floor surface to avoid
	 * z-fighting in the GL depth buffer.
	 */
	static final int GL_DEPTH_OFFSET = 2;

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
	/** Total ceiling triangles submitted to a rasterizer this frame. */
	public static int diagTrianglesDrawn;

	/** Per-frame latch so counters reset exactly once per client loop. */
	private static int diagLoop = -1;
	/** Whether the CURRENT candidate tile is a plain tile. */
	private static boolean pendingPlain;
	/** Whether the CURRENT candidate tile already contributed a triangle. */
	private static boolean currentTileDrawn;

	private ModernCeiling() {
	}

	/**
	 * Whether the ceiling underside pass is active. FIRST_PERSON only —
	 * CHASE/FREE/ORIGINAL keep untouched vanilla rendering (P7).
	 */
	public static boolean isEnabled() {
		return CameraMode.isModern() && ModernCameraRig.isFirstPersonRigState();
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
		}
	}

	/** Called by the coverage scan for every structurally valid tile. */
	static void noteCandidate(boolean plain) {
		diagCandidateTiles++;
		pendingPlain = plain;
		currentTileDrawn = false;
	}

	/** Called for every projected vertex that lies behind the near plane. */
	static void noteBehindVertex() {
		diagBehindCameraVertices++;
	}

	/** Called when a tile is rejected because all vertices are behind. */
	static void noteNearRejectedTile() {
		diagNearRejectedTiles++;
	}

	/** Called for every triangle actually submitted to a rasterizer. */
	static void noteTriangleDrawn() {
		diagTrianglesDrawn++;
		if (!currentTileDrawn) {
			currentTileDrawn = true;
			diagDrawnTiles++;
			if (pendingPlain) {
				diagPlainTiles++;
			} else {
				diagShapedTiles++;
			}
		}
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
		if (overhead.plainTile != null) {
			diagOverheadTilePresent = true;
			diagTextureId = overhead.plainTile.anInt4869;
			// CEILING_SOURCE_RESULT = UPPER_FLOOR_SINGLE_SIDED, so any drawn
			// ceiling is generated underside geometry (P6).
			diagSourceMode = "GENERATED";
		} else if (overhead.shapedTile != null
				&& overhead.shapedTile.anIntArray161 != null
				&& overhead.shapedTile.anIntArray161.length > 0) {
			diagOverheadTilePresent = true;
			diagTextureId = overhead.shapedTile.anIntArray161[0];
			diagSourceMode = "GENERATED";
		} else if (overhead.shapedTile != null) {
			diagOverheadTilePresent = true;
			diagSourceMode = "GENERATED";
		}
	}
}
