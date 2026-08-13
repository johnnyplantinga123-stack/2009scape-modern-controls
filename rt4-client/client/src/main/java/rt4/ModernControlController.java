package rt4;

/**
 * Central modern-control dispatcher (Phase 1).
 *
 * <p>This is the single entry point called from the live game loop. It routes
 * per-frame updates based on the active {@link CameraMode.Mode}:
 *
 * <pre>
 * switch (cameraMode) {
 *     case ORIGINAL:
 *         runOriginalRuneScapeControls();
 *         break;
 *
 *     case FIRST_PERSON:
 *         ModernControlController.update();  // + FirstPersonCamera.update() (Phase 2)
 *         break;
 *
 *     case THIRD_PERSON:
 *         ModernControlController.update();  // + ThirdPersonCamera.update() (Phase 14)
 *         break;
 * }
 * </pre>
 *
 * <p>In Phase 1 only the {@code ORIGINAL} path runs real code; first/third
 * person cameras, WASD movement, targeting and interactions are added in later
 * phases. This keeps original RuneScape behaviour fully intact while the mode
 * framework and F11 cycling are in place.
 */
public final class ModernControlController {

	/**
	 * Distance (in tiles) used for nearby world interactions (doors, objects,
	 * ground items, nearby NPC talk/trade). Configured centrally here so the
	 * same value is never hardcoded in multiple places.
	 */
	public static final int MODERN_NEARBY_INTERACT_DISTANCE = 2;

	/**
	 * Wider distance (in tiles) used for combat-target <em>acquisition</em> via
	 * the crosshair (ranged/magic). This only controls which entity the player
	 * can <em>select</em>; actual attack/spell range and LOS stay with existing
	 * RuneScape code.
	 */
	public static final int MODERN_COMBAT_TARGET_DISTANCE = 10;

	private ModernControlController() {
	}

	/**
	 * Per-frame update. Called from {@link client#mainUpdate()} and/or the main
	 * loop. When in a modern mode, the corresponding controller duties are
	 * dispatched here (added in later phases); in {@code ORIGINAL} mode nothing
	 * is done so the original RuneScape code paths run untouched.
	 */
	public static void update() {
		switch (CameraMode.getCurrent()) {
			case ORIGINAL:
				// No modern override — original RuneScape controls run as-is.
				break;
		case FIRST_PERSON:
			// Phase 3+: ModernMovementController.update();
			FirstPersonCamera.update();
			break;
			case THIRD_PERSON:
				// Phase 14: ModernMovementController.update();
				//           ThirdPersonCamera.update();
				break;
		}
	}

	/**
	 * Returns whether gameplay input (modern movement/interaction) is currently
	 * allowed. In Phase 1 this is conservative: modern input is never injected
	 * yet, so it always returns {@code true} for the frameworks that will use it
	 * later. Future phases will gate on chat focus, dialogs, cutscenes, etc.
	 *
	 * @return {@code true} when modern WASD/E/click may act on the world.
	 */
	public static boolean isGameplayInputAllowed() {
		return true;
	}
}