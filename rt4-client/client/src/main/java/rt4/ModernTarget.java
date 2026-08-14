package rt4;

/**
 * Represents a candidate targeting entity for MODERN crosshair acquisition.
 *
 * <p>Stores stable identifiers (entity ID, tile coordinates, plane) rather
 * than raw object references, since entity arrays are invalidated across
 * scene rebuilds. The entity reference is cached for the current frame only
 * and must not be held across frames.</p>
 *
 * <p>Target types: NPC, PLAYER, OBJECT (scenery/loc), GROUND_ITEM.</p>
 *
 * <p>TODO 073 — ModernTarget model.</p>
 */
public final class ModernTarget {

	/** Target classification. */
	public enum TargetType {
		NPC,
		PLAYER,
		OBJECT,
		GROUND_ITEM
	}

	/** What kind of entity this target represents. */
	public TargetType type;

	/**
	 * Stable entity identifier.
	 * NPC: NpcList index. Player: PlayerList index.
	 * OBJECT: loc type ID. GROUND_ITEM: obj type ID.
	 */
	public int entityId;

	/** Tile coordinates (scene-local, 0..103). */
	public int tileX;
	public int tileZ;

	/** Plane (0..3). */
	public int plane;

	/**
	 * Fine-coordinate world position for projection.
	 * Q16 format: tile * 128 + 64 for center.
	 */
	public int xFine;
	public int zFine;

	/** Height offset for projection (e.g. half entity height). */
	public int yOffset;

	/** Projected screen X (-1 if behind camera / off-screen). */
	public int screenX;

	/** Projected screen Y (-1 if behind camera / off-screen). */
	public int screenY;

	/**
	 * Composite score: lower is better.
	 * Primary: angular deviation from screen center.
	 * Secondary: world distance.
	 */
	public double score;

	/**
	 * Cached entity reference for the current frame only.
	 * May be null. Do NOT hold across frames.
	 */
	public PathingEntity entityRef;

	/** Distance in tiles from self (approximate). */
	public int worldDistance;

	public void reset() {
		type = null;
		entityId = -1;
		tileX = 0;
		tileZ = 0;
		plane = 0;
		xFine = 0;
		zFine = 0;
		yOffset = 0;
		screenX = -1;
		screenY = -1;
		score = Double.MAX_VALUE;
		entityRef = null;
		worldDistance = Integer.MAX_VALUE;
	}
}
