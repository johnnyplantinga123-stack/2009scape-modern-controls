package rt4;

/**
 * Keyboard dialogue/choice support for MODERN controls (Phase 3C round #5, P7).
 *
 * <h2>Behavior</h2>
 * <ul>
 *   <li>SPACE executes the existing "Click here to continue" action via the
 *       exact same route as {@link MiniMenu#doAction} action 41
 *       ({@code UNKNOWN_41}): {@code method10(child, componentId)} packet +
 *       {@code Cs1ScriptRunner.aClass13_10} resume bookkeeping.</li>
 *   <li>Number keys 1..9 select the Nth visible dialogue choice using the
 *       existing button-click route (action 8 / {@code UNKNOWN_8}):
 *       {@code p1isaac(10); p4(componentId)}. Choices are collected from the
 *       currently rendered dialogue interface in top-to-bottom (y) order —
 *       fully generic, no dialogue is hardcoded.</li>
 * </ul>
 *
 * <h2>Why an interface scan instead of MiniMenu</h2>
 * <p>{@link MiniMenu#addComponentEntries} is only invoked while the mouse is
 * INSIDE a component's bounds ({@link Cs1ScriptRunner} mouse-bounds gate).
 * In first-person the cursor is centred in the viewport, far from the
 * chatbox, so Continue/choice entries never reach the menu arrays. This
 * controller therefore scans {@link InterfaceList#openInterfaces} directly,
 * applying the SAME predicates the menu builder uses
 * ({@code buttonType == 6} / {@code isResumePauseButtonEnabled()} for
 * continue, {@code buttonType == 1} for clickable buttons).</p>
 *
 * <h2>Input priority (brief §15)</h2>
 * <ol>
 *   <li>Chat text entry — this controller is fully inert while
 *       {@link ModernControlController#isChatInputActive()}.</li>
 *   <li>Dialogue/choice interfaces — handled here; when a key is consumed,
 *       {@link ModernControlController} skips the FP world-action keys.</li>
 *   <li>Right-click menu open ({@code Cs1ScriptRunner.aBoolean108}) — we
 *       never intercept while it is up.</li>
 * </ol>
 *
 * <p>MODERN-only this round (brief §17): ORIGINAL mode is never touched.</p>
 *
 * <p>STATUS: SOURCE VERIFIED (routes traced byte-for-byte), COMPILE VERIFIED
 * after build, RUNTIME UNVERIFIED.</p>
 */
public final class ModernDialogueKeyboard {

	/** SPACE in game keycode space (Keyboard.CODE_MAP[VK_SPACE] = 83). */
	private static final int KEY_SPACE = 83;

	/** Digit 1 in game keycode space (Keyboard.CODE_MAP[VK_1] = 16; N = 15+N). */
	private static final int KEY_1 = 16;

	/** Number of selectable digits (1..9). */
	private static final int MAX_CHOICES = 9;

	/** clientCode of the chatbox message layer component (SOURCE VERIFIED:
	 *  Cs1ScriptRunner render branch stores it in LoginManager.aClass13_13). */
	private static final int CHATBOX_LAYER_CLIENT_CODE = 1406;

	// ---- Edge detection state ----
	private static boolean spaceWasPressed;
	private static final boolean[] numWasPressed = new boolean[MAX_CHOICES];

	private ModernDialogueKeyboard() {
	}

	/**
	 * Per-tick update. Returns {@code true} when a key press was consumed by
	 * the dialogue layer, in which case the FP world-action layer
	 * ({@link ModernActionOverlay}) must NOT run this tick.
	 */
	public static boolean update() {
		boolean spaceDown = Keyboard.pressedKeys[KEY_SPACE];
		boolean spaceEdge = spaceDown && !spaceWasPressed;
		spaceWasPressed = spaceDown;

		boolean[] numEdge = new boolean[MAX_CHOICES];
		for (int n = 0; n < MAX_CHOICES; n++) {
			boolean down = Keyboard.pressedKeys[KEY_1 + n];
			numEdge[n] = down && !numWasPressed[n];
			numWasPressed[n] = down;
		}

		// Gates: MODERN only, never while typing in chat, never while the
		// right-click menu is open.
		if (!CameraMode.isModern() || ModernControlController.isChatInputActive()
				|| Cs1ScriptRunner.aBoolean108) {
			return false;
		}

		if (spaceEdge && tryContinue()) {
			return true;
		}
		for (int n = 0; n < MAX_CHOICES; n++) {
			if (numEdge[n] && tryChoice(n)) {
				return true;
			}
		}
		return false;
	}

	// ---- SPACE = continue ----

	/**
	 * Executes the existing Continue action for the first continue-capable
	 * component found in the open interfaces. Replicates the exact
	 * {@code doAction} UNKNOWN_41 route:
	 *
	 * <pre>
	 * method10(local15, local19);
	 * Cs1ScriptRunner.aClass13_10 = InterfaceList.method1418(local19, local15);
	 * InterfaceList.redraw(Cs1ScriptRunner.aClass13_10);
	 * </pre>
	 *
	 * where {@code local15 == intArgs1} (createdComponentId for if3, -1 for
	 * CS1 buttonType==6) and {@code local19 == intArgs2} (component id).
	 */
	private static boolean tryContinue() {
		// The existing route is a no-op while a resume is already pending.
		if (Cs1ScriptRunner.aClass13_10 != null) {
			return false;
		}
		Component c = findContinueComponent();
		if (c == null) {
			return false;
		}
		int child = c.if3 ? c.createdComponentId : -1;
		MiniMenu.method10(child, c.id);
		Cs1ScriptRunner.aClass13_10 = InterfaceList.method1418(c.id, child);
		InterfaceList.redraw(Cs1ScriptRunner.aClass13_10);
		return true;
	}

	/**
	 * Finds a continue-capable component among the open interfaces.
	 * Dialogue interfaces (opened on the chatbox layer) are checked first.
	 */
	private static Component findContinueComponent() {
		// Pass 1: interfaces opened as a sub of the chatbox layer.
		for (ComponentPointer p = (ComponentPointer) InterfaceList.openInterfaces.head(); p != null; p = (ComponentPointer) InterfaceList.openInterfaces.next()) {
			Component layer = InterfaceList.getComponent((int) p.key);
			if (layer != null && layer.clientCode == CHATBOX_LAYER_CLIENT_CODE) {
				Component c = findContinueInInterface(p.interfaceId);
				if (c != null) {
					return c;
				}
			}
		}
		// Pass 2: any open interface (Continue is only offered where the
		// existing client would offer it — same predicates as
		// MiniMenu.addComponentEntries, minus the mouse-bounds gate).
		for (ComponentPointer p = (ComponentPointer) InterfaceList.openInterfaces.head(); p != null; p = (ComponentPointer) InterfaceList.openInterfaces.next()) {
			Component c = findContinueInInterface(p.interfaceId);
			if (c != null) {
				return c;
			}
		}
		return null;
	}

	private static Component findContinueInInterface(int interfaceId) {
		if (InterfaceList.components == null || interfaceId < 0
				|| interfaceId >= InterfaceList.components.length) {
			return null;
		}
		Component[] comps = InterfaceList.components[interfaceId];
		if (comps == null) {
			return null;
		}
		for (int i = 0; i < comps.length; i++) {
			Component c = comps[i];
			if (c != null && isContinueComponent(c)) {
				return c;
			}
		}
		return null;
	}

	/**
	 * Same predicates the existing menu builder uses for the Continue entry:
	 * CS1 {@code buttonType == 6} (MiniMenu.addComponentEntries) or if3
	 * {@code isResumePauseButtonEnabled()} (MiniMenu if3 branch).
	 */
	private static boolean isContinueComponent(Component c) {
		if (c.if3) {
			return InterfaceList.getServerActiveProperties(c).isResumePauseButtonEnabled();
		}
		return c.buttonType == 6;
	}

	// ---- Number keys = dialogue choice ----

	/**
	 * Selects the (choiceIndex)th visible dialogue option (0-based) using the
	 * existing button-click route (doAction UNKNOWN_8):
	 *
	 * <pre>
	 * boolean ok = true;
	 * if (component.clientCode > 0) ok = MiniMenu.method4265(component);
	 * if (ok) { Protocol.outboundBuffer.p1isaac(10); Protocol.outboundBuffer.p4(component.id); }
	 * </pre>
	 *
	 * Choices are scanned from the dialogue interface opened on the chatbox
	 * layer only (never arbitrary modal buttons), in rendered y order —
	 * i.e. the currently rendered choice order, fully generic.
	 */
	private static boolean tryChoice(int choiceIndex) {
		Component[] choices = new Component[64];
		int count = collectChatboxButtons(choices);
		if (count == 0 || choiceIndex >= count) {
			return false;
		}
		// Rendered order = top-to-bottom by component y.
		for (int i = 1; i < count; i++) {
			Component c = choices[i];
			int j = i - 1;
			while (j >= 0 && choices[j].y > c.y) {
				choices[j + 1] = choices[j];
				j--;
			}
			choices[j + 1] = c;
		}
		Component chosen = choices[choiceIndex];
		boolean ok = true;
		if (chosen.clientCode > 0) {
			ok = MiniMenu.method4265(chosen);
		}
		if (!ok) {
			return false;
		}
		Protocol.outboundBuffer.p1isaac(10);
		Protocol.outboundBuffer.p4(chosen.id);
		return true;
	}

	/**
	 * Collects clickable CS1 buttons ({@code buttonType == 1}) from
	 * interfaces opened as a sub of the chatbox layer (clientCode 1406).
	 * These are the classic dialogue choice buttons; the server-side
	 * DialogueInterpreter maps the clicked child index to the chosen topic.
	 */
	private static int collectChatboxButtons(Component[] out) {
		int count = 0;
		if (InterfaceList.components == null) {
			return 0;
		}
		for (ComponentPointer p = (ComponentPointer) InterfaceList.openInterfaces.head(); p != null; p = (ComponentPointer) InterfaceList.openInterfaces.next()) {
			Component layer = InterfaceList.getComponent((int) p.key);
			if (layer == null || layer.clientCode != CHATBOX_LAYER_CLIENT_CODE) {
				continue;
			}
			int iface = p.interfaceId;
			if (iface < 0 || iface >= InterfaceList.components.length) {
				continue;
			}
			Component[] comps = InterfaceList.components[iface];
			if (comps == null) {
				continue;
			}
			for (int i = 0; i < comps.length && count < out.length; i++) {
				Component c = comps[i];
				if (c != null && c.buttonType == 1) {
					out[count++] = c;
				}
			}
		}
		return count;
	}
}
