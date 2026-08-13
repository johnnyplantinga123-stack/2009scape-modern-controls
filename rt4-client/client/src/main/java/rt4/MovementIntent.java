package rt4;

/**
 * Movement intent abstraction (Phase 3).
 *
 * <p>Represents a normalized movement direction intent, decoupled from the
 * input source. Currently populated by WASD keyboard input; later phases can
 * populate the same structure from gamepad/other controllers without changing
 * the movement controller.
 *
 * <p>The intent is camera-relative: {@code forward} means "toward camera yaw",
 * not "north in world space". The {@link ModernMovementController} converts
 * this to world-space movement.
 */
public final class MovementIntent {

	/** Forward component: -1 (backward) to +1 (forward). */
	public float forward;

	/** Right component: -1 (left) to +1 (right). */
	public float right;

	/** Whether the intent requests running. */
	public boolean runRequested;

	/**
	 * Returns whether any movement is requested (non-zero forward or right).
	 */
	public boolean hasMovement() {
		return forward != 0f || right != 0f;
	}

	/**
	 * Resets the intent to zero movement.
	 */
	public void clear() {
		forward = 0f;
		right = 0f;
		runRequested = false;
	}

	/**
	 * Normalizes the forward/right vector so diagonal movement is not faster
	 * than cardinal movement. If the magnitude exceeds 1, it is scaled down.
	 */
	public void normalize() {
		float mag = (float) Math.sqrt(forward * forward + right * right);
		if (mag > 1f) {
			forward /= mag;
			right /= mag;
		}
	}
}