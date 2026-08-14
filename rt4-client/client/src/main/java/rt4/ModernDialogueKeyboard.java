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
 * <h2>Round #6B/C updates</h2>
 * <ul>
 *   <li>P2: this class is the ONE source of truth for dialogue input
 *       authority — {@link #hasActiveDialogue()} /
 *       {@link #hasActiveChoiceDialogue()} gate the FP world-action layer
 *       so 1-9/E can never act on the world while a dialogue owns input.</li>
 *   <li>P3: choice collection now supports BOTH CS1 {@code buttonType==1}
 *       buttons and if3 components with server-enabled ops, executed via
 *       the exact existing routes ({@code p1isaac(10); p4(id)} for CS1,
 *       {@link ClientProt#method4512} for if3 — the same route as
 *       {@link MiniMenu#doAction} UNKNOWN_9/1003). Hidden components are
 *       skipped. A one-shot {@code [DLG-DIAG]} dump proves which
 *       components are visible while a dialogue is open.</li>
 * </ul>
 *
 * <h2>Round #7 updates</h2>
 * <ul>
 *   <li>P1: dialogue input authority is gated on the MODERN gameplay
 *       keyboard owner ({@link ModernControlController#isModernGameplayKeyboardOwner()}).
 *       In FREE the rig owns the keyboard with FULL VANILLA behaviour —
 *       SPACE/1-9 are ordinary vanilla input there and this controller is
 *       completely inert (vanilla has no keyboard dialogue shortcuts).</li>
 *   <li>P3: choice collection is now TWO-PASS, mirroring the proven-working
 *       {@link #findContinueComponent()} scan exactly (pass 1: chatbox-layer
 *       subs; pass 2: ANY open interface with the same predicates). Round
 *       #6B/C runtime proved SPACE continue works via pass 2 while 1..N
 *       found nothing — the choices live on the same non-chatbox-layer
 *       interface as the working continue component. Number keys invoke the
 *       exact vanilla mouse-click route (CS1: {@code p1isaac(10); p4(id)},
 *       if3: {@link ClientProt#method4512} with the identical argument
 *       order as {@link MiniMenu#doAction} UNKNOWN_9/1003).</li>
 * </ul>
 *
 * <h2>Round #7C updates</h2>
 * <ul>
 *   <li>P2/P5: the Round #7 "pass 2: scan ANY open interface" fallback in
 *       {@link #collectChatboxButtons} was REMOVED. Its if3 predicate
 *       (any component with any server-enabled op) matches ordinary
 *       always-open HUD components, making {@link #hasActiveDialogue()}
 *       true with NO dialogue open — which hard-blocked the FP world
 *       overlay everywhere (Round #7B user runtime: object AND NPC overlay
 *       gone). Choice collection is chatbox-layer-only again; the REAL
 *       dialogue rule is preserved via {@link #findContinueComponent()}
 *       (continue-capable components still block world actions).
 *   <li>P5: DIALOGUE_NUMERIC_ROUTE = AWAITING_RUNTIME_CLICK_TRACE. No
 *       captured [DIALOGUE-CLICK-TRACE] of a real manual mouse click exists
 *       yet, so no further predicates are guessed; the one-shot trace in
 *       {@link MiniMenu#doAction} stays armed for the next manual click.</li>
 * </ul>
 *
 * <h2>Round #7D updates</h2>
 * <ul>
 *   <li>P1: real 1..N dialogue keys via the cache-proven choice-interface
 *       family (228..238: child 0 = title, children 1..N = options in
 *       rendered top-to-bottom order — proven from 530_interface_names.txt
 *       and 498_interface_dump.txt). Detection is STRUCTURAL (an open
 *       interface id inside the family), never a generic op-scan, so
 *       always-open HUD interfaces cannot false-positive. Execution invokes
 *       the exact vanilla mouse-click routes (if3 op =
 *       {@link ClientProt#method4512}, byte-identical to
 *       {@link MiniMenu#doAction} UNKNOWN_9/1003; CS1 button =
 *       {@code p1isaac(10); p4(id)} with the method4265 gate, byte-identical
 *       to UNKNOWN_8). While a choice dialogue is open it owns 1..9.</li>
 * </ul>
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

	/**
	 * Round #7D P1: the numeric choice route is now the structural
	 * choice-family route (cache/source proven). Supersedes the Round #7C
	 * AWAITING_RUNTIME_CLICK_TRACE quarantine.
	 */
	private static final String NUMERIC_ROUTE_STATUS = "FAMILY_STRUCTURAL_7D";

	// ---- Edge detection state ----
	private static boolean spaceWasPressed;
	private static final boolean[] numWasPressed = new boolean[MAX_CHOICES];

	// ---- Round #6B/C P3: one-shot dialogue diagnostics ----
	/** True while the previous tick saw at least one dialogue choice. */
	private static boolean dialogueWasActive;

	/** Parallel to the collection buffer: if3 op index (0..4), -1 for CS1. */
	private static final int[] choiceOpIndex = new int[64];

	// ---- Round #7 P8: last executed choice (debug overlay) ----
	/** Last number key consumed by the dialogue layer (0 = none yet). */
	private static int lastNumberKey;
	/** Route name of the last executed choice ("" = none yet). */
	private static String lastChoiceRoute = "";
	/** Component id of the last executed choice (-1 = none yet). */
	private static int lastChoiceComponent = -1;
	/** doAction action code of the last executed choice (0 = none yet). */
	private static int lastChoiceActionCode;

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

		// Gates: MODERN gameplay keyboard owner only (Round #7 P1: FREE is
		// vanilla — this controller is completely inert there), never while
		// typing in chat, never while the right-click menu is open.
		if (!ModernControlController.isModernGameplayKeyboardOwner()
				|| ModernControlController.isChatInputActive()
				|| Cs1ScriptRunner.aBoolean108) {
			return false;
		}

		if (spaceEdge && tryContinue()) {
			return true;
		}

		// Round #7D P1: real 1..N dialogue keys via the cache-proven choice
		// interface family (228..238). While a choice dialogue is open the
		// dialogue OWNS 1..9 (the world layer gets nothing, even for keys
		// beyond the visible option count); with none open the keys fall
		// through to the FP world-action layer.
		if (findChoiceInterfaceId() >= 0) {
			for (int n = 1; n <= MAX_CHOICES; n++) {
				if (numEdge[n - 1]) {
					tryFamilyChoice(n);
					return true;
				}
			}
		}
		return false;
	}

	// ---- Round #6B/C P2: dialogue input authority (source of truth) ----

	/**
	 * True when ANY dialogue currently owns input: either a Continue-capable
	 * component exists or at least one choice option is visible. While true,
	 * the FP world-action layer ({@link ModernActionOverlay}) has ZERO input
	 * authority — 1-9 and E must only reach the dialogue. Same scan
	 * predicates as the SPACE/number execution routes, so this can never
	 * disagree with what the keys would actually execute.
	 */
	public static boolean hasActiveDialogue() {
		if (!ModernControlController.isModernGameplayKeyboardOwner()
				|| ModernControlController.isChatInputActive()
				|| Cs1ScriptRunner.aBoolean108) {
			return false;
		}
		if (Cs1ScriptRunner.aClass13_10 != null) {
			// A resume/continue is already pending — dialogue owns SPACE.
			return true;
		}
		if (findChoiceInterfaceId() >= 0) {
			return true;
		}
		if (findContinueComponent() != null) {
			return true;
		}
		Component[] choices = new Component[64];
		return collectChatboxButtons(choices) > 0;
	}

	/** True when a choice dialogue (one or more visible options) is open. */
	public static boolean hasActiveChoiceDialogue() {
		if (!ModernControlController.isModernGameplayKeyboardOwner()
				|| ModernControlController.isChatInputActive()
				|| Cs1ScriptRunner.aBoolean108) {
			return false;
		}
		if (findChoiceInterfaceId() >= 0) {
			return true;
		}
		Component[] choices = new Component[64];
		return collectChatboxButtons(choices) > 0;
	}

	/** Number of currently visible dialogue choice options. Debug overlay. */
	public static int getDialogueChoiceCount() {
		if (!ModernControlController.isModernGameplayKeyboardOwner()
				|| ModernControlController.isChatInputActive()
				|| Cs1ScriptRunner.aBoolean108) {
			return 0;
		}
		int iface = findChoiceInterfaceId();
		if (iface >= 0) {
			return countFamilyOptions(InterfaceList.components[iface]);
		}
		Component[] choices = new Component[64];
		return collectChatboxButtons(choices);
	}

	// ---- Round #7 P8: debug overlay accessors ----

	/** Last number key consumed by the dialogue layer (0 = none yet). */
	public static int getLastNumberKey() {
		return lastNumberKey;
	}

	/** Route name of the last executed choice ("" = none yet). */
	public static String getLastChoiceRoute() {
		return lastChoiceRoute;
	}

	/** Component id of the last executed choice (-1 = none yet). */
	public static int getLastChoiceComponent() {
		return lastChoiceComponent;
	}

	/**
	 * Round #7C P5: status of the 1..N dialogue-choice route. Shown on F12
	 * ({@code numericRoute}). Stays {@code AWAITING_RUNTIME_CLICK_TRACE}
	 * until a real manual mouse click is captured by
	 * {@code [DIALOGUE-CLICK-TRACE]} in {@link MiniMenu#doAction}.
	 */
	public static String getNumericRouteStatus() {
		return NUMERIC_ROUTE_STATUS;
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
	 *
	 * <p>Round #7C P5: NOT CALLED this round — the numeric route is
	 * {@code AWAITING_RUNTIME_CLICK_TRACE}. Preserved verbatim so the
	 * follow-up round can replace it with the real traced route.
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
			int op = choiceOpIndex[i];
			int j = i - 1;
			while (j >= 0 && choices[j].y > c.y) {
				choices[j + 1] = choices[j];
				choiceOpIndex[j + 1] = choiceOpIndex[j];
				j--;
			}
			choices[j + 1] = c;
			choiceOpIndex[j + 1] = op;
		}
		Component chosen = choices[choiceIndex];
		int opIdx = choiceOpIndex[choiceIndex];
		if (chosen.if3) {
			// Exact existing if3 op route — the same call MiniMenu.doAction
			// UNKNOWN_9/1003 performs for a mouse click (op = opIdx + 1).
			ClientProt.method4512(chosen.optionBase, chosen.createdComponentId, opIdx + 1, chosen.id);
			recordChoice(choiceIndex + 1, "IF3_METHOD4512", chosen.id);
			return true;
		}
		// CS1 button route — doAction UNKNOWN_8.
		boolean ok = true;
		if (chosen.clientCode > 0) {
			ok = MiniMenu.method4265(chosen);
		}
		if (!ok) {
			return false;
		}
		Protocol.outboundBuffer.p1isaac(10);
		Protocol.outboundBuffer.p4(chosen.id);
		recordChoice(choiceIndex + 1, "CS1_BUTTON", chosen.id);
		return true;
	}

	/** Round #7 P8: remember the last executed choice for the debug overlay. */
	private static void recordChoice(int numberKey, String route, int componentId) {
		lastNumberKey = numberKey;
		lastChoiceRoute = route;
		lastChoiceComponent = componentId;
	}

	// ---- Round #7D P1: structural choice-interface family ----

	/**
	 * Round #7D P1: the multi-choice dialogue interface family, proven from
	 * the 530 cache names (530_interface_names.txt: 228/230/232/234 =
	 * multi2..multi5, 236/237/238 = multivar2/4/5) and the 498 component dump
	 * (498_interface_dump.txt: every one of 228..238 has child 0 =
	 * "Select an Option" title and children 1..N = option1..optionN in
	 * rendered top-to-bottom order). 241..244 are npcchat continue dialogues
	 * and are deliberately NOT in this family.
	 */
	private static final int CHOICE_FAMILY_MIN = 228;
	private static final int CHOICE_FAMILY_MAX = 238;

	/** Max option children per family interface (option1..option5). */
	private static final int MAX_FAMILY_OPTIONS = 5;

	/** Not a clickable option child (hidden/null/title/non-button). */
	private static final int ROUTE_NONE = -2;

	/** CS1 {@code buttonType == 1} button child (doAction UNKNOWN_8). */
	private static final int ROUTE_CS1_BUTTON = -1;

	/**
	 * Returns the id of the currently OPEN choice-family interface with at
	 * least one visible clickable option child, or -1. Structural only — no
	 * op-scanning of arbitrary interfaces, so always-open HUD interfaces can
	 * never false-positive (the Round #7C failure mode).
	 */
	private static int findChoiceInterfaceId() {
		if (InterfaceList.components == null) {
			return -1;
		}
		for (ComponentPointer p = (ComponentPointer) InterfaceList.openInterfaces.head(); p != null; p = (ComponentPointer) InterfaceList.openInterfaces.next()) {
			int iface = p.interfaceId;
			if (iface < CHOICE_FAMILY_MIN || iface > CHOICE_FAMILY_MAX
					|| iface >= InterfaceList.components.length) {
				continue;
			}
			Component[] comps = InterfaceList.components[iface];
			if (comps != null && countFamilyOptions(comps) > 0) {
				return iface;
			}
		}
		return -1;
	}

	/** Visible option children = structural children 1..5 that are clickable. */
	private static int countFamilyOptions(Component[] comps) {
		int n = 0;
		for (int i = 1; i <= MAX_FAMILY_OPTIONS && i < comps.length; i++) {
			if (familyOptionRoute(comps[i]) != ROUTE_NONE) {
				n++;
			}
		}
		return n;
	}

	/**
	 * The vanilla click route a family child would take, per
	 * {@link MiniMenu#addComponentEntries}: CS1 {@code buttonType == 1}
	 * button, or if3 with a server-enabled op (returns the first enabled op
	 * index 0..4 — the primary left-click entry after MiniMenu.sort).
	 * {@link #ROUTE_NONE} when the child is not clickable.
	 */
	private static int familyOptionRoute(Component c) {
		if (c == null || c.hidden) {
			return ROUTE_NONE;
		}
		if (!c.if3) {
			return c.buttonType == 1 ? ROUTE_CS1_BUTTON : ROUTE_NONE;
		}
		for (int op = 0; op < 5; op++) {
			if (InterfaceList.getOp(c, op) != null) {
				return op;
			}
		}
		return ROUTE_NONE;
	}

	/**
	 * Executes option N (1-based; rendered top-to-bottom = child index order,
	 * proven from the cache dump) of the open choice-family interface via the
	 * EXACT vanilla mouse-click routes: if3 op =
	 * {@link ClientProt#method4512} (byte-identical to {@link MiniMenu#doAction}
	 * UNKNOWN_9/1003), CS1 button = {@code p1isaac(10); p4(id)} with the
	 * {@code method4265} clientCode gate (byte-identical to UNKNOWN_8). No
	 * invented packets, no mouse simulation, no hardcoded dialogue text.
	 */
	private static boolean tryFamilyChoice(int numberKey) {
		int iface = findChoiceInterfaceId();
		if (iface < 0) {
			return false;
		}
		Component[] comps = InterfaceList.components[iface];
		int seen = 0;
		for (int i = 1; i <= MAX_FAMILY_OPTIONS && i < comps.length; i++) {
			int route = familyOptionRoute(comps[i]);
			if (route == ROUTE_NONE) {
				continue;
			}
			if (++seen != numberKey) {
				continue;
			}
			Component c = comps[i];
			if (route >= 0) {
				// if3 op route — MiniMenu.doAction UNKNOWN_9/1003.
				ClientProt.method4512(c.optionBase, c.createdComponentId, route + 1, c.id);
				lastChoiceActionCode = MiniMenu.UNKNOWN_9;
				recordChoice(numberKey, "IF3_FAMILY_" + iface, c.id);
			} else {
				// CS1 button route — MiniMenu.doAction UNKNOWN_8.
				boolean ok = true;
				if (c.clientCode > 0) {
					ok = MiniMenu.method4265(c);
				}
				if (!ok) {
					return false;
				}
				Protocol.outboundBuffer.p1isaac(10);
				Protocol.outboundBuffer.p4(c.id);
				lastChoiceActionCode = MiniMenu.UNKNOWN_8;
				recordChoice(numberKey, "CS1_FAMILY_" + iface, c.id);
			}
			return true;
		}
		return false;
	}

	// ---- Round #7D P1: F12 accessors ----

	/** Active choice-family interface id (-1 = none open). */
	public static int getChoiceInterfaceId() {
		return findChoiceInterfaceId();
	}

	/** Child index of the Nth (1..5) visible option, -1 if absent. */
	public static int getChoiceChild(int n) {
		int iface = findChoiceInterfaceId();
		if (iface < 0) {
			return -1;
		}
		Component[] comps = InterfaceList.components[iface];
		int seen = 0;
		for (int i = 1; i <= MAX_FAMILY_OPTIONS && i < comps.length; i++) {
			if (familyOptionRoute(comps[i]) == ROUTE_NONE) {
				continue;
			}
			if (++seen == n) {
				return i;
			}
		}
		return -1;
	}

	/** doAction action code of the last executed choice (0 = none yet). */
	public static int getLastChoiceActionCode() {
		return lastChoiceActionCode;
	}

	/**
	 * Collects clickable dialogue option components in no particular order
	 * (caller sorts by y). Round #7C P2/P5: CHATBOX-LAYER-ONLY again — the
	 * Round #7 "pass 2: any open interface" fallback was removed because its
	 * if3 predicate (any server-enabled op) matched ordinary always-open HUD
	 * components and made {@link #hasActiveDialogue()} true with NO dialogue
	 * open, hard-blocking the FP world overlay everywhere.
	 * Two component families are supported — exactly the ones
	 * {@link MiniMenu#addComponentEntries} would turn into clickable menu
	 * entries:
	 * <ul>
	 *   <li>CS1 {@code buttonType == 1} buttons (classic dialogue choice
	 *       buttons; the server DialogueInterpreter maps the clicked child
	 *       index to the chosen topic).</li>
	 *   <li>if3 components with at least one server-enabled op
	 *       ({@link InterfaceList#getOp} predicate — the same predicate the
	 *       menu builder uses); the first enabled op index is recorded.</li>
	 * </ul>
	 * Hidden components are skipped. Round #6B/C P3.
	 */
	private static int collectChatboxButtons(Component[] out) {
		int count = 0;
		if (InterfaceList.components == null) {
			return 0;
		}
		// Chatbox-layer subs only (Round #7C: generic any-interface fallback
		// removed — it false-positived on always-open HUD components).
		for (ComponentPointer p = (ComponentPointer) InterfaceList.openInterfaces.head(); p != null; p = (ComponentPointer) InterfaceList.openInterfaces.next()) {
			Component layer = InterfaceList.getComponent((int) p.key);
			if (layer == null || layer.clientCode != CHATBOX_LAYER_CLIENT_CODE) {
				continue;
			}
			count = collectChoiceButtonsInInterface(p.interfaceId, out, count);
		}
		dumpDialogueDiagnostics(count);
		return count;
	}

	/**
	 * Appends the choice-capable components of one interface to the buffer
	 * (Round #7 P3 split-out helper for the two-pass collection). Duplicate
	 * component references are skipped.
	 */
	private static int collectChoiceButtonsInInterface(int iface, Component[] out, int count) {
		if (iface < 0 || iface >= InterfaceList.components.length) {
			return count;
		}
		Component[] comps = InterfaceList.components[iface];
		if (comps == null) {
			return count;
		}
		for (int i = 0; i < comps.length && count < out.length; i++) {
			Component c = comps[i];
			if (c == null || c.hidden) {
				continue;
			}
			if (!c.if3) {
				if (c.buttonType == 1) {
					choiceOpIndex[count] = -1;
					out[count++] = c;
				}
				continue;
			}
			// if3: first server-enabled op (same predicate as
			// InterfaceList.getOp / MiniMenu.addComponentEntries).
			for (int op = 0; op < 5; op++) {
				if (InterfaceList.getOp(c, op) != null) {
					choiceOpIndex[count] = op;
					out[count++] = c;
					break;
				}
			}
		}
		return count;
	}

	/**
	 * Round #6B/C P3: one-shot diagnostics on the dialogue-open edge. Dumps
	 * EVERY chatbox-layer component so the runtime proves which components
	 * correspond to rendered options (interface id, child id, buttonType,
	 * if3, text, option, y, createdComponentId, hidden). Not flooded: only
	 * on the inactive→active transition.
	 */
	private static void dumpDialogueDiagnostics(int choiceCount) {
		boolean active = choiceCount > 0 || findContinueComponent() != null
				|| Cs1ScriptRunner.aClass13_10 != null;
		if (active == dialogueWasActive) {
			return;
		}
		dialogueWasActive = active;
		if (!active) {
			return;
		}
		System.out.println("[DLG-DIAG] dialogue active — chatbox-layer components:");
		if (InterfaceList.components == null) {
			return;
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
			for (int i = 0; i < comps.length; i++) {
				Component c = comps[i];
				if (c == null) {
					continue;
				}
				System.out.println("[DLG-DIAG]   iface=" + iface + " child=" + i
						+ " buttonType=" + c.buttonType + " if3=" + c.if3
						+ " type=" + c.type + " y=" + c.y
						+ " createdComponentId=" + c.createdComponentId
						+ " hidden=" + c.hidden
						+ " text=\"" + plain(c.text) + "\""
						+ " option=\"" + plain(c.option) + "\"");
			}
		}
	}

	/** Minimal JagString → plain string for diagnostics (no tag stripping). */
	private static String plain(JagString js) {
		if (js == null) {
			return "";
		}
		StringBuilder sb = new StringBuilder(js.length);
		for (int i = 0; i < js.length; i++) {
			sb.append((char) (js.chars[i] & 0xFF));
		}
		return sb.toString();
	}
}
