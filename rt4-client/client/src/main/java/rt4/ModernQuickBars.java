package rt4;

import java.util.Arrays;

/**
 * Persistent FIRST_PERSON quickbar assignments backed by existing vanilla
 * inventory/component actions. No custom gameplay packets are created here.
 */
public final class ModernQuickBars {

	public static final int ITEM_SLOT_COUNT = 6;
	public static final int ACTION_SLOT_COUNT = 10;

	public static final int MENU_ADD_ITEM = 1501;
	public static final int MENU_REMOVE_ITEM = 1502;
	public static final int MENU_ADD_ACTION = 1503;
	public static final int MENU_REMOVE_ACTION = 1504;

	private static final int INVENTORY_INTERFACE = 149;
	private static final int PRAYER_INTERFACE = 271;
	private static final int MAGIC_INTERFACE = 192;
	/** Cache prayer button children and their server-authoritative config varps. */
	private static final int[] PRAYER_BUTTONS = {
			5, 7, 9, 11, 13, 15, 17, 19, 21, 23, 25, 27, 29, 31,
			33, 35, 37, 39, 41, 43, 45, 47, 49, 51, 53, 55, 57
	};
	private static final int[] PRAYER_VARPS = {
			83, 84, 85, 862, 863, 86, 87, 88, 89, 90, 91, 864, 865, 92,
			93, 94, 95, 96, 97, 866, 867, 98, 99, 100, 1168, 1052, 1053
	};
	private static final int KEY_1 = 16;
	private static final int KEY_0 = 25;

	private static final int[] itemIds = new int[ITEM_SLOT_COUNT];
	private static final int[] actionComponentIds = new int[ACTION_SLOT_COUNT];
	private static final int[] actionCreatedIds = new int[ACTION_SLOT_COUNT];
	private static final boolean[] numberWasPressed = new boolean[ACTION_SLOT_COUNT];

	static {
		resetAssignments();
	}

	private ModernQuickBars() {
	}

	public static void resetAssignments() {
		Arrays.fill(itemIds, -1);
		Arrays.fill(actionComponentIds, -1);
		Arrays.fill(actionCreatedIds, -1);
	}

	public static void encode(Buffer buffer) {
		for (int itemId : itemIds) {
			buffer.p2(itemId < 0 ? 0xFFFF : itemId);
		}
		for (int slot = 0; slot < ACTION_SLOT_COUNT; slot++) {
			buffer.p4(actionComponentIds[slot]);
			buffer.p2(actionCreatedIds[slot] < 0 ? 0xFFFF : actionCreatedIds[slot]);
		}
	}

	public static void decode(Buffer buffer) {
		for (int slot = 0; slot < ITEM_SLOT_COUNT; slot++) {
			int itemId = buffer.g2();
			itemIds[slot] = itemId == 0xFFFF ? -1 : itemId;
		}
		for (int slot = 0; slot < ACTION_SLOT_COUNT; slot++) {
			actionComponentIds[slot] = buffer.g4();
			int createdId = buffer.g2();
			actionCreatedIds[slot] = createdId == 0xFFFF ? -1 : createdId;
			if (!isActionGroup(actionComponentIds[slot] >>> 16)) {
				actionComponentIds[slot] = -1;
				actionCreatedIds[slot] = -1;
			}
		}
	}

	public static void update(boolean higherPriorityInputConsumed) {
		boolean[] edges = new boolean[ACTION_SLOT_COUNT];
		for (int slot = 0; slot < ACTION_SLOT_COUNT; slot++) {
			int key = slot == 9 ? KEY_0 : KEY_1 + slot;
			boolean down = Keyboard.pressedKeys[key];
			edges[slot] = down && !numberWasPressed[slot];
			numberWasPressed[slot] = down;
		}

		if (higherPriorityInputConsumed || !isActive() || !ModernControlController.isGameplayInputAllowed()
				|| ModernDialogueKeyboard.hasActiveDialogue()
				|| Cs1ScriptRunner.aBoolean108 || FPContextMenuController.isMenuOpen()
				|| Keyboard.pressedKeys[Keyboard.KEY_CTRL]) {
			return;
		}

		boolean shift = Keyboard.pressedKeys[Keyboard.KEY_SHIFT];
		for (int slot = 0; slot < ACTION_SLOT_COUNT; slot++) {
			if (!edges[slot]) {
				continue;
			}
			if (shift) {
				activateAction(slot);
			} else if (slot < ITEM_SLOT_COUNT) {
				activateItem(slot);
			}
			return;
		}
	}

	public static void addInventoryMenuEntry(Component component, int inventorySlot, int itemId) {
		if (!isActive() || component == null || component.id >>> 16 != INVENTORY_INTERFACE || itemId < 0) {
			return;
		}
		ObjType type = ObjTypeList.get(itemId);
		boolean assigned = findItemSlot(itemId) >= 0;
		MiniMenu.add(-1, itemId,
				JagString.concatenate(new JagString[]{MiniMenu.aClass100_32, type.name}),
				inventorySlot, (short) (assigned ? MENU_REMOVE_ITEM : MENU_ADD_ITEM),
				JagString.parse(assigned ? "Remove from quickbar" : "Add to quickbar"), component.id);
	}

	public static void addActionComponentMenuEntry(Component component) {
		if (!isActive() || !isAssignableAction(component)) {
			return;
		}
		boolean assigned = findActionSlot(component.id, component.createdComponentId) >= 0;
		JagString target = actionDisplayName(component);
		MiniMenu.add(-1, 0L, target, component.createdComponentId,
				(short) (assigned ? MENU_REMOVE_ACTION : MENU_ADD_ACTION),
				JagString.parse(assigned ? "Remove from action bar" : "Add to action bar"), component.id);
	}

	public static boolean handleMenuAction(int actionCode, int intArg1, int intArg2, int key) {
		if (actionCode == MENU_ADD_ITEM) {
			assignItem(key);
			return true;
		}
		if (actionCode == MENU_REMOVE_ITEM) {
			removeItem(key);
			return true;
		}
		if (actionCode == MENU_ADD_ACTION) {
			assignAction(intArg2, intArg1);
			return true;
		}
		if (actionCode == MENU_REMOVE_ACTION) {
			removeAction(intArg2, intArg1);
			return true;
		}
		return false;
	}

	public static int getAssignedItemId(int slot) {
		return slot >= 0 && slot < ITEM_SLOT_COUNT ? itemIds[slot] : -1;
	}

	public static int getAvailableItemId(int slot) {
		Component inventory = findInventoryComponent();
		int assignedId = getAssignedItemId(slot);
		int inventorySlot = findInventorySlot(inventory, assignedId);
		return inventorySlot < 0 ? -1 : inventory.objTypes[inventorySlot] - 1;
	}

	public static Component getActionComponent(int slot) {
		if (slot < 0 || slot >= ACTION_SLOT_COUNT || actionComponentIds[slot] < 0) {
			return null;
		}
		int group = actionComponentIds[slot] >>> 16;
		if (!InterfaceList.load(group)) {
			return null;
		}
		return InterfaceList.method1418(actionComponentIds[slot], actionCreatedIds[slot]);
	}

	public static Sprite getActionSprite(int slot) {
		Component component = getActionComponent(slot);
		if (component == null || component.type != 5) {
			return null;
		}
		// The selected cache sprite for several prayers is deliberately dark.
		// Keep the icon readable here and communicate the active state through the
		// action-bar slot highlight instead.
		Sprite sprite = component.method489(false);
		return sprite == null ? component.method489(true) : sprite;
	}

	public static boolean isActionActive(int slot) {
		Component component = getActionComponent(slot);
		if (component == null) {
			return false;
		}
		if (component.id >>> 16 == PRAYER_INTERFACE) {
			int varp = prayerVarp(component.id & 0xFFFF);
			return varp >= 0 && varp < VarpDomain.activeVarps.length
					&& VarpDomain.activeVarps[varp] != 0;
		}
		return Cs1ScriptRunner.isTrue(component);
	}

	private static int prayerVarp(int button) {
		for (int i = 0; i < PRAYER_BUTTONS.length; i++) {
			if (PRAYER_BUTTONS[i] == button) {
				return PRAYER_VARPS[i];
			}
		}
		return -1;
	}

	public static boolean isItemAssigned(int itemId) {
		return itemId >= 0 && findItemSlot(itemId) >= 0;
	}

	public static boolean isItemEquipped(int slot) {
		int assignedId = getAssignedItemId(slot);
		if (assignedId < 0 || PlayerList.self == null || PlayerList.self.appearance == null) {
			return false;
		}
		for (int appearanceSlot = 0; appearanceSlot < 12; appearanceSlot++) {
			int equippedId = PlayerList.self.appearance.getEquippedObjectId(appearanceSlot);
			if (equippedId >= 0 && sameItemFamily(assignedId, equippedId)) {
				return true;
			}
		}
		return false;
	}

	public static void moveItemSlot(int from, int to) {
		if (from < 0 || from >= ITEM_SLOT_COUNT || to < 0 || to >= ITEM_SLOT_COUNT || from == to) {
			return;
		}
		int itemId = itemIds[from];
		itemIds[from] = itemIds[to];
		itemIds[to] = itemId;
		save();
	}

	public static void clearItemSlot(int slot) {
		if (slot < 0 || slot >= ITEM_SLOT_COUNT || itemIds[slot] < 0) {
			return;
		}
		itemIds[slot] = -1;
		save();
	}

	public static void moveActionSlot(int from, int to) {
		if (from < 0 || from >= ACTION_SLOT_COUNT || to < 0 || to >= ACTION_SLOT_COUNT || from == to) {
			return;
		}
		int componentId = actionComponentIds[from];
		int createdId = actionCreatedIds[from];
		actionComponentIds[from] = actionComponentIds[to];
		actionCreatedIds[from] = actionCreatedIds[to];
		actionComponentIds[to] = componentId;
		actionCreatedIds[to] = createdId;
		save();
	}

	public static void clearActionSlot(int slot) {
		if (slot < 0 || slot >= ACTION_SLOT_COUNT || actionComponentIds[slot] < 0) {
			return;
		}
		actionComponentIds[slot] = -1;
		actionCreatedIds[slot] = -1;
		save();
	}

	public static void activateItem(int slot) {
		if (slot < 0 || slot >= ITEM_SLOT_COUNT || itemIds[slot] < 0) {
			return;
		}
		Component inventory = findInventoryComponent();
		int inventorySlot = findInventorySlot(inventory, itemIds[slot]);
		if (inventorySlot < 0) {
			feedback("That quickbar item is not in your inventory.");
			return;
		}
		int itemId = inventory.objTypes[inventorySlot] - 1;
		ObjType type = ObjTypeList.get(itemId);
		int opIndex = firstMeaningfulItemOp(type);
		if (opIndex < 0) {
			feedback("That item has no quickbar action.");
			return;
		}
		int[] actionCodes = {MiniMenu.OBJ_ACTION_1, MiniMenu.OBJ_EQUIP_ACTION,
				MiniMenu.OBJ_IN_COMPONENT_ACTION_4, MiniMenu.OBJ_ACTION_4, MiniMenu.OBJ_ACTION_5};
		MiniMenu.invokeExistingAction(actionCodes[opIndex], inventorySlot, inventory.id,
				itemId, JagString.concatenate(new JagString[]{MiniMenu.aClass100_32, type.name}));
	}

	public static void activateAction(int slot) {
		Component component = getActionComponent(slot);
		if (component == null) {
			return;
		}
		int group = component.id >>> 16;
		if (group == MAGIC_INTERFACE && MiniMap.getTargetVerb(component) != null) {
			MiniMenu.invokeExistingAction(MiniMenu.UNKNOWN_32, component.createdComponentId,
					component.id, 0L, component.optionBase);
			return;
		}
		if (component.if3) {
			for (int op = 0; op < 5; op++) {
				if (InterfaceList.getOp(component, op) != null) {
					MiniMenu.invokeExistingAction(MiniMenu.UNKNOWN_9, component.createdComponentId,
							component.id, op + 1L, component.optionBase);
					return;
				}
			}
		}
		if (component.buttonType == 1) {
			MiniMenu.invokeExistingAction(MiniMenu.UNKNOWN_8, 0, component.id, 0L, component.optionBase);
			return;
		}
		feedback("That prayer or spell is currently unavailable.");
	}

	private static void assignItem(int itemId) {
		if (findItemSlot(itemId) >= 0) {
			return;
		}
		for (int slot = 0; slot < ITEM_SLOT_COUNT; slot++) {
			if (itemIds[slot] < 0) {
				itemIds[slot] = itemId;
				save();
				feedback("Added to quickbar slot " + (slot + 1) + ".");
				return;
			}
		}
		feedback("The item quickbar is full.");
	}

	private static void removeItem(int itemId) {
		int slot = findItemSlot(itemId);
		if (slot >= 0) {
			itemIds[slot] = -1;
			save();
			feedback("Removed from quickbar.");
		}
	}

	private static void assignAction(int componentId, int createdId) {
		if (findActionSlot(componentId, createdId) >= 0) {
			return;
		}
		for (int slot = 0; slot < ACTION_SLOT_COUNT; slot++) {
			if (actionComponentIds[slot] < 0) {
				actionComponentIds[slot] = componentId;
				actionCreatedIds[slot] = createdId;
				save();
				feedback("Added to action bar slot " + (slot == 9 ? 0 : slot + 1) + ".");
				return;
			}
		}
		feedback("The prayer/magic action bar is full.");
	}

	private static void removeAction(int componentId, int createdId) {
		int slot = findActionSlot(componentId, createdId);
		if (slot >= 0) {
			actionComponentIds[slot] = -1;
			actionCreatedIds[slot] = -1;
			save();
			feedback("Removed from action bar.");
		}
	}

	private static int findItemSlot(int itemId) {
		for (int slot = 0; slot < ITEM_SLOT_COUNT; slot++) {
			if (itemIds[slot] >= 0 && sameItemFamily(itemIds[slot], itemId)) {
				return slot;
			}
		}
		return -1;
	}

	private static int findActionSlot(int componentId, int createdId) {
		for (int slot = 0; slot < ACTION_SLOT_COUNT; slot++) {
			if (actionComponentIds[slot] == componentId && actionCreatedIds[slot] == createdId) {
				return slot;
			}
		}
		return -1;
	}

	private static Component findInventoryComponent() {
		if (!InterfaceList.load(INVENTORY_INTERFACE)
				|| InterfaceList.components[INVENTORY_INTERFACE] == null) {
			return null;
		}
		for (Component component : InterfaceList.components[INVENTORY_INTERFACE]) {
			if (component != null && component.type == 2 && component.objTypes != null) {
				return component;
			}
		}
		return null;
	}

	private static int findInventorySlot(Component inventory, int assignedId) {
		if (inventory == null || inventory.objTypes == null || assignedId < 0) {
			return -1;
		}
		for (int slot = 0; slot < inventory.objTypes.length; slot++) {
			int itemId = inventory.objTypes[slot] - 1;
			if (itemId >= 0 && sameItemFamily(assignedId, itemId)) {
				return slot;
			}
		}
		return -1;
	}

	private static boolean sameItemFamily(int firstId, int secondId) {
		if (firstId == secondId) {
			return true;
		}
		String first = potionFamilyName(ObjTypeList.get(firstId).name.toString());
		String second = potionFamilyName(ObjTypeList.get(secondId).name.toString());
		return first != null && first.equals(second);
	}

	private static String potionFamilyName(String name) {
		if (name == null || name.length() < 4 || name.charAt(name.length() - 1) != ')') {
			return null;
		}
		int open = name.lastIndexOf('(');
		if (open < 1 || open + 2 != name.length() - 1) {
			return null;
		}
		char dose = name.charAt(open + 1);
		return dose >= '1' && dose <= '4' ? name.substring(0, open).trim().toLowerCase() : null;
	}

	private static int firstMeaningfulItemOp(ObjType type) {
		if (type == null || type.iops == null) {
			return -1;
		}
		for (int op = 0; op < 5; op++) {
			if (type.iops[op] != null) {
				return op;
			}
		}
		return -1;
	}

	private static boolean isAssignableAction(Component component) {
		if (component == null || !isActionGroup(component.id >>> 16)) {
			return false;
		}
		if (component.type != 5 || component.spriteId == -1 && component.activeSpriteId == -1) {
			return false;
		}
		if (component.buttonType == 1 || MiniMap.getTargetVerb(component) != null) {
			return true;
		}
		if (component.if3) {
			for (int op = 0; op < 5; op++) {
				if (InterfaceList.getOp(component, op) != null) {
					return true;
				}
			}
		}
		return false;
	}

	private static JagString actionDisplayName(Component component) {
		if (component.optionBase != null && component.optionBase.length() > 0) {
			return component.optionBase;
		}
		if (component.optionSuffix != null && component.optionSuffix.length() > 0) {
			return component.optionSuffix;
		}
		if (component.text != null && component.text.length() > 0) {
			return component.text;
		}
		if (component.activeText != null && component.activeText.length() > 0) {
			return component.activeText;
		}
		return JagString.parse(component.id >>> 16 == PRAYER_INTERFACE ? "Prayer" : "Spell");
	}

	private static boolean isActionGroup(int group) {
		return group == PRAYER_INTERFACE || group == MAGIC_INTERFACE;
	}

	private static boolean isActive() {
		return CameraMode.isModern() && ModernCameraRig.isFirstPersonRigState();
	}

	private static void save() {
		if (GameShell.signLink != null) {
			Preferences.write(GameShell.signLink);
		}
	}

	private static void feedback(String message) {
		Chat.add(JagString.EMPTY, 0, JagString.parse(message));
	}
}
