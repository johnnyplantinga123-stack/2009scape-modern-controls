package rt4;

/** Dedicated camera-relative FIRST_PERSON equipment presentation pass. */
public final class FirstPersonViewModel {

	private static final int WEAPON_SLOT = 3;
	private static final int SHIELD_SLOT = 5;
	private static final int DIAGNOSTIC_INTERVAL = 50;
	public static final int COMPONENT_ARMS = 0;
	public static final int COMPONENT_WEAPON = 1;
	public static final int COMPONENT_SHIELD = 2;
	public static final int COMPONENT_COMBINED = 3;

	/* Shared FIRST_PERSON presentation offsets; item assets remain unchanged. */
	private static final int WEAPON_SCREEN_X = 24;
	private static final int WEAPON_SCREEN_Y = 96;
	private static final int WEAPON_DEPTH = 280;
	private static final int SHIELD_SCREEN_X = -24;
	private static final int SHIELD_SCREEN_Y = 104;
	private static final int SHIELD_DEPTH = 300;

	/** Kept as a runtime switch for the old proof trace; normal FP offsets are now used. */
	public static boolean DEBUG_VISIBILITY_PROOF = false;

	private static int lastWeaponId = Integer.MIN_VALUE;
	private static int lastShieldId = Integer.MIN_VALUE;
	private static int lastAnimationId = Integer.MIN_VALUE;
	private static int lastDiagnosticLoop = -DIAGNOSTIC_INTERVAL;

	private static boolean hookReached;
	private static boolean active;
	private static boolean appearanceReady;
	private static boolean modelBuilt;
	private static boolean submitted;
	private static boolean worldModelSubmitted;
	private static boolean culled;
	private static boolean nearClipped;
	private static int weaponId = -1;
	private static int shieldId = -1;
	private static int componentSlot3;
	private static int componentSlot4;
	private static int componentSlot5;
	private static int componentSlot6;
	private static int componentSlot9;
	private static int vertexCount = -1;
	private static int triangleCount = -1;
	private static int armsVertices;
	private static int armsTriangles;
	private static int weaponVertices;
	private static int weaponTriangles;
	private static int shieldVertices;
	private static int shieldTriangles;
	private static int combinedVertices;
	private static int combinedTriangles;
	private static int armsIdentity;
	private static int weaponIdentity;
	private static int shieldIdentity;
	private static int combinedIdentity;
	private static int animationId = -1;
	private static int cameraX;
	private static int cameraY;
	private static int cameraZ;
	private static int modelX;
	private static int modelY;
	private static int modelZ;
	private static int worldModelX;
	private static int worldModelY;
	private static int worldModelZ;
	private static int minDepth;
	private static int maxDepth;
	private static int minScreenX;
	private static int maxScreenX;
	private static int minScreenY;
	private static int maxScreenY;
	private static String reason = "not_started";
	private static String modelSource = "none";

	private FirstPersonViewModel() {
	}

	public static boolean isActive(Player player) {
		return player != null
				&& PlayerList.self == player
				&& CameraMode.isModern()
				&& ModernCameraRig.isFirstPersonRigState()
				&& FirstPersonCamera.isActive();
	}

	/** Starts the trace before Player.render can take an appearance early return. */
	public static void begin(Player player, boolean frameActive, boolean ready) {
		reset();
		hookReached = true;
		active = frameActive;
		appearanceReady = ready;
		updateCameraState();
		if (!frameActive) {
			reason = "inactive_mode";
		} else if (!ready) {
			reason = "appearance_null";
		}
		if (frameActive && player != null && player.appearance != null) {
			readAppearance(player.appearance);
		}
	}

	/** Records the full normal build result without making it the FP render source. */
	public static void recordModel(String source, Model model) {
		if (active && model == null && "full".equals(source)) {
			reason = "full_model_build_null";
		}
	}

	/**
	 * Starts the immediate equipment pass. PlayerAppearance animation copies use
	 * a shared RT4 scratch model, so each finished item is submitted before the
	 * following appearance build can overwrite that scratch instance.
	 */
	public static void beginRender(Player player) {
		if (!isActive(player)) {
			return;
		}
		updateCameraState();
		resetCameraSpaceBounds();
	}

	/** Submits one freshly built real appearance component immediately. */
	public static void renderComponent(Player player, Model model, int component,
			int sinPitch, int cosPitch, int sinYaw, int cosYaw, int arg9,
			ParticleSystem particles) {
		if (!isActive(player)) {
			return;
		}
		recordComponent(component, model);
		if (model == null) {
			return;
		}
		// Model.render expects BODY-convention yaw.  FP camera yaw is a different
		// convention; use the existing involution instead of pointing the real
		// equipment away from the camera and losing it to backface culling.
		int modelYaw = ModernCameraRig.cameraYawToBodyYaw(FirstPersonCamera.getYaw());
		int screenX;
		int screenY;
		int depth;
		if (component == COMPONENT_WEAPON) {
			screenX = WEAPON_SCREEN_X;
			screenY = WEAPON_SCREEN_Y;
			depth = WEAPON_DEPTH;
		} else if (component == COMPONENT_SHIELD) {
			screenX = SHIELD_SCREEN_X;
			screenY = SHIELD_SCREEN_Y;
			depth = SHIELD_DEPTH;
		} else {
			// The current stable round intentionally defers the third-person arm rig.
			return;
		}
		int[] offset = cameraLocalToWorld(screenX, screenY, depth,
				sinPitch, cosPitch, sinYaw, cosYaw);
		modelX = offset[0];
		modelY = offset[1];
		modelZ = offset[2];
		renderPart(model, modelYaw, offset, sinPitch, cosPitch, sinYaw, cosYaw, arg9, particles);
	}

	/** Completes diagnostics after all immediately rendered components. */
	public static void finishRender() {
		vertexCount = weaponVertices + shieldVertices;
		triangleCount = weaponTriangles + shieldTriangles;
		modelSource = vertexCount == 0 ? "none" : "separate_weapon_shield";
		finishCameraSpaceBounds();
		if (!modelBuilt) {
			reason = "viewmodel_build_null";
		} else if (vertexCount <= 0 || triangleCount <= 0) {
			reason = "viewmodel_empty_geometry";
		} else if (!submitted) {
			reason = "no_component_submitted";
		} else if (nearClipped) {
			reason = "submitted_near_clip_risk";
		} else if (culled) {
			reason = "submitted_behind_or_outside_camera";
		} else {
			reason = "submitted_camera_relative";
		}
	}

	/** Records the separate headless local world-body used when looking down. */
	public static void recordWorldModel(Model model, int relativeX, int relativeY, int relativeZ) {
		if (!active) {
			return;
		}
		worldModelSubmitted = model != null;
		worldModelX = relativeX;
		worldModelY = relativeY;
		worldModelZ = relativeZ;
	}

	public static void diagnose(Player player, Model model, int currentAnimationId,
			boolean debugVisible) {
		if (!active || player == null) {
			return;
		}
		animationId = currentAnimationId;
		if (player.appearance != null) {
			readAppearance(player.appearance);
		}
		boolean changed = weaponId != lastWeaponId
				|| shieldId != lastShieldId
				|| animationId != lastAnimationId;
		if (!debugVisible && !changed && client.loop - lastDiagnosticLoop < DIAGNOSTIC_INTERVAL) {
			return;
		}
		lastWeaponId = weaponId;
		lastShieldId = shieldId;
		lastAnimationId = animationId;
		lastDiagnosticLoop = client.loop;
		System.out.println("[FP-VIEWMODEL] active=" + active
				+ " hookReached=" + hookReached
				+ " appearanceReady=" + appearanceReady
				+ " weaponId=" + weaponId
				+ " shieldId=" + shieldId
				+ " componentSlots=3:" + componentSlot3 + ",4:" + componentSlot4
				+ ",5:" + componentSlot5 + ",6:" + componentSlot6 + ",9:" + componentSlot9
				+ " handsAvailable=false"
				+ " modelBuilt=" + modelBuilt
				+ " modelSource=" + modelSource
				+ " vertexCount=" + vertexCount
				+ " triangleCount=" + triangleCount
				+ " componentGeometry=arms:" + armsVertices + "/" + armsTriangles
				+ ",weapon:" + weaponVertices + "/" + weaponTriangles
				+ ",shield:" + shieldVertices + "/" + shieldTriangles
				+ ",combined:" + combinedVertices + "/" + combinedTriangles
				+ " componentIdentity=arms:" + armsIdentity
				+ ",weapon:" + weaponIdentity
				+ ",shield:" + shieldIdentity
				+ ",combined:" + combinedIdentity
				+ " immediateSubmission=true"
				+ " equipmentOnly=true"
				+ " armsDeferred=true"
				+ " torsoExcluded=true"
				+ " viewOffset=weapon:" + WEAPON_SCREEN_X + "," + WEAPON_SCREEN_Y + "," + WEAPON_DEPTH
				+ ";shield:" + SHIELD_SCREEN_X + "," + SHIELD_SCREEN_Y + "," + SHIELD_DEPTH
				+ " animationId=" + animationId
				+ " cameraX=" + cameraX
				+ " cameraY=" + cameraY
				+ " cameraZ=" + cameraZ
				+ " modelX=" + modelX
				+ " modelY=" + modelY
				+ " modelZ=" + modelZ
				+ " worldModelSubmitted=" + worldModelSubmitted
				+ " worldModelX=" + worldModelX
				+ " worldModelY=" + worldModelY
				+ " worldModelZ=" + worldModelZ
				+ " cameraSpaceX=" + minScreenX + ".." + maxScreenX
				+ " cameraSpaceY=" + minScreenY + ".." + maxScreenY
				+ " cameraSpaceDepth=" + minDepth + ".." + maxDepth
				+ " submitted=" + submitted
				+ " culled=" + culled
				+ " nearClipped=" + nearClipped
				+ " proofMode=" + DEBUG_VISIBILITY_PROOF
				+ " reason=" + reason);
	}

	private static void renderPart(Model model, int modelYaw, int[] offset,
			int sinPitch, int cosPitch, int sinYaw, int cosYaw, int arg9,
			ParticleSystem particles) {
		includeCameraSpaceBounds(model, modelYaw, offset[0], offset[1], offset[2],
				sinPitch, cosPitch, sinYaw, cosYaw);
		model.pickable = false;
		model.render(modelYaw, sinPitch, cosPitch, sinYaw, cosYaw,
				offset[0], offset[1], offset[2], -1L, arg9, particles);
		submitted = true;
	}

	private static void recordComponent(int component, Model model) {
		int vertices = countVertices(model);
		int triangles = countTriangles(model);
		int identity = model == null ? 0 : System.identityHashCode(model);
		modelBuilt |= model != null;
		if (component == COMPONENT_ARMS) {
			armsVertices = vertices;
			armsTriangles = triangles;
			armsIdentity = identity;
		} else if (component == COMPONENT_WEAPON) {
			weaponVertices = vertices;
			weaponTriangles = triangles;
			weaponIdentity = identity;
		} else if (component == COMPONENT_SHIELD) {
			shieldVertices = vertices;
			shieldTriangles = triangles;
			shieldIdentity = identity;
		} else if (component == COMPONENT_COMBINED) {
			combinedVertices = vertices;
			combinedTriangles = triangles;
			combinedIdentity = identity;
		}
	}

	private static int[] cameraLocalToWorld(int screenX, int screenY, int depth,
			int sinPitch, int cosPitch, int sinYaw, int cosYaw) {
		int rotatedZ = depth * cosPitch - screenY * sinPitch >> 16;
		int worldY = screenY * cosPitch + depth * sinPitch >> 16;
		// Invert SceneGraph's horizontal transform:
		// screenX = z*sinYaw + x*cosYaw
		// forward = z*cosYaw - x*sinYaw
		int worldX = cosYaw * screenX - sinYaw * rotatedZ >> 16;
		int worldZ = sinYaw * screenX + cosYaw * rotatedZ >> 16;
		return new int[]{worldX, worldY, worldZ};
	}

	private static void reset() {
		hookReached = false;
		active = false;
		appearanceReady = false;
		modelBuilt = false;
		submitted = false;
		worldModelSubmitted = false;
		culled = false;
		nearClipped = false;
		weaponId = -1;
		shieldId = -1;
		componentSlot3 = 0;
		componentSlot4 = 0;
		componentSlot5 = 0;
		componentSlot6 = 0;
		componentSlot9 = 0;
		vertexCount = -1;
		triangleCount = -1;
		armsVertices = 0;
		armsTriangles = 0;
		weaponVertices = 0;
		weaponTriangles = 0;
		shieldVertices = 0;
		shieldTriangles = 0;
		combinedVertices = 0;
		combinedTriangles = 0;
		armsIdentity = 0;
		weaponIdentity = 0;
		shieldIdentity = 0;
		combinedIdentity = 0;
		animationId = -1;
		modelX = 0;
		modelY = 0;
		modelZ = 0;
		worldModelX = 0;
		worldModelY = 0;
		worldModelZ = 0;
		minDepth = 0;
		maxDepth = 0;
		minScreenX = 0;
		maxScreenX = 0;
		minScreenY = 0;
		maxScreenY = 0;
		modelSource = "none";
		reason = "not_started";
	}

	private static void readAppearance(PlayerAppearance appearance) {
		weaponId = appearance.getEquippedObjectId(WEAPON_SLOT);
		shieldId = appearance.getEquippedObjectId(SHIELD_SLOT);
		componentSlot3 = appearance.getSelectedComponentValue(3);
		componentSlot4 = appearance.getSelectedComponentValue(4);
		componentSlot5 = appearance.getSelectedComponentValue(5);
		componentSlot6 = appearance.getSelectedComponentValue(6);
		componentSlot9 = appearance.getSelectedComponentValue(9);
	}

	private static int countVertices(Model model) {
		return model == null || model.getVertexCount() < 0 ? 0 : model.getVertexCount();
	}

	private static int countTriangles(Model model) {
		return model == null || model.getTriangleCount() < 0 ? 0 : model.getTriangleCount();
	}

	private static void updateCameraState() {
		cameraX = SceneGraph.cameraX;
		cameraY = SceneGraph.cameraY;
		cameraZ = SceneGraph.cameraZ;
	}

	private static void resetCameraSpaceBounds() {
		minDepth = Integer.MAX_VALUE;
		maxDepth = Integer.MIN_VALUE;
		minScreenX = Integer.MAX_VALUE;
		maxScreenX = Integer.MIN_VALUE;
		minScreenY = Integer.MAX_VALUE;
		maxScreenY = Integer.MIN_VALUE;
		nearClipped = false;
		culled = false;
	}

	private static void finishCameraSpaceBounds() {
		if (minDepth == Integer.MAX_VALUE) {
			minDepth = 0;
			maxDepth = 0;
			minScreenX = 0;
			maxScreenX = 0;
			minScreenY = 0;
			maxScreenY = 0;
			nearClipped = true;
			culled = true;
			return;
		}
		int near = GlRenderer.getNearClipDistance();
		culled = maxDepth <= near;
	}

	private static void includeCameraSpaceBounds(Model model, int modelYaw,
			int relativeX, int relativeY, int relativeZ, int sinPitch,
			int cosPitch, int sinYaw, int cosYaw) {
		if (model instanceof GlModel) {
			GlModel geometry = (GlModel) model;
			for (int i = 0; i < geometry.vertexCount; i++) {
				addModelVertex(geometry.vertexX[i], geometry.vertexY[i], geometry.vertexZ[i],
						modelYaw, relativeX, relativeY, relativeZ, sinPitch, cosPitch, sinYaw, cosYaw);
			}
		} else if (model instanceof SoftwareModel) {
			SoftwareModel geometry = (SoftwareModel) model;
			for (int i = 0; i < geometry.vertexCount; i++) {
				addModelVertex(geometry.vertexX[i], geometry.vertexY[i], geometry.vertexZ[i],
						modelYaw, relativeX, relativeY, relativeZ, sinPitch, cosPitch, sinYaw, cosYaw);
			}
		}
	}

	private static void addModelVertex(int localX, int localY, int localZ,
			int modelYaw, int relativeX, int relativeY, int relativeZ,
			int sinPitch, int cosPitch, int sinYaw, int cosYaw) {
		if (modelYaw != 0) {
			int rotatedX = localZ * MathUtils.sin[modelYaw] + localX * MathUtils.cos[modelYaw] >> 16;
			localZ = localZ * MathUtils.cos[modelYaw] - localX * MathUtils.sin[modelYaw] >> 16;
			localX = rotatedX;
		}
		int worldX = localX + relativeX;
		int worldY = localY + relativeY;
		int worldZ = localZ + relativeZ;
		int rotatedZ = worldZ * sinYaw + worldX * cosYaw >> 16;
		int screenX = worldZ * cosYaw - worldX * sinYaw >> 16;
		int screenY = worldY * cosPitch - rotatedZ * sinPitch >> 16;
		int depth = worldY * sinPitch + rotatedZ * cosPitch >> 16;
		if (screenX < minScreenX) minScreenX = screenX;
		if (screenX > maxScreenX) maxScreenX = screenX;
		if (screenY < minScreenY) minScreenY = screenY;
		if (screenY > maxScreenY) maxScreenY = screenY;
		if (depth < minDepth) minDepth = depth;
		if (depth > maxDepth) maxDepth = depth;
		int near = GlRenderer.getNearClipDistance();
		nearClipped |= depth <= near;
	}

	static {
		// Keep the static initializer explicit so the file remains easy to inspect
		// while this temporary visibility round is active.
	}
}
