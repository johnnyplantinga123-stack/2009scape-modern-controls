# FP Interaction UX -- Nearest Overlay + Scrollable Vanilla Context Menu

## Summary

Two complementary FIRST_PERSON interaction paths:
- **Path A (Quick Overlay):** Crosshair overlay for targets within 2 tiles, nearest-ray-hit selection, E/1/2/3 execution via `MiniMenu.doAction()`
- **Path B (Context Menu):** Right-click opens the REAL vanilla MiniMenu at crosshair center; mouse wheel selects entries; left-click executes selected entry

Plus the **P4B Hotfix:** Remove the broad 150-tick post-exit step drain.

---

## Task 1 -- New File: `FPContextMenuController.java`

Create `rt4-client/client/src/main/java/rt4/FPContextMenuController.java`

**State fields:**
- `private static boolean menuOpen` -- FP context menu is active
- `private static int selectedIndex` -- currently highlighted menu entry (0-based into MiniMenu arrays)
- `private static boolean rightClickWasDown` -- edge detection for right mouse button
- `private static boolean leftClickWasDown` -- edge detection for left mouse button

**Public API:**
- `static boolean isMenuOpen()` -- consumed by rig, overlay, movement, diagnostics
- `static int getSelectedIndex()`
- `static void update()` -- called from `ModernControlController.updateInteractionLayer()`
- `static void processWheelInput()` -- called from `ModernCameraRig.processWheelInput()` gate
- `static void close()` -- closes menu, resets state

**update() logic (tick-rate, called BEFORE method843):**
1. Read `Mouse.clickButton` for right-click (value 2) and left-click (value 1) edge detection
2. **Open:** If `CameraMode.isModern() && rig == FIRST_PERSON && !menuOpen && right-click edge && !dialogue && !chatInput && !CTRL cursor && cursor locked`:
   - Set `ScriptRunner.anInt3751 = viewportCenterX` and `ScriptRunner.anInt1892 = viewportCenterY`
   - Set `Mouse.anInt5850 = viewportCenterX` and `Mouse.anInt5895 = viewportCenterY` (so method3901 positions menu at crosshair)
   - Set `menuOpen = true`, `selectedIndex = 0`
   - The vanilla pipeline (method1841 -> addEntries -> sort) already built entries for the crosshair position this frame
3. **While open -- wheel selection:**
   - Read `MouseWheel.wheelRotation`; if nonzero: `selectedIndex += rotation; wrap to [0, MiniMenu.size-1]`; consume wheel (set `MouseWheel.wheelRotation = 0` AFTER reading so rig doesn't see it)
4. **While open -- left-click execution:**
   - If left-click edge: call `MiniMenu.doAction(selectedIndex)`, then `close()`
   - Also clear `Mouse.clickButton = 0` to prevent vanilla method843 from processing
5. **While open -- right-click close:**
   - If right-click edge while already open: `close()`
6. **While open -- WASD suspension:**
   - `ModernMovementController.readInput()` checks `FPContextMenuController.isMenuOpen()` and clears intent if true

**close() logic:**
- `menuOpen = false`
- `Cs1ScriptRunner.aBoolean108 = false` (if we set it -- actually the vanilla close in method843 handles this; we may not need to set it ourselves)
- Clear `Mouse.clickButton = 0` to prevent stale click processing

**Key design decision:** The vanilla menu rendering already works via `LoginManager.method1841()` which calls `MiniMenu.drawA()/drawB()` when `aBoolean108 == true`. For FP, we need to ensure that:
1. The menu is built for the crosshair position (done by setting ScriptRunner pick coords before menu rebuild)
2. The menu is rendered at the crosshair position (method3901 uses the same coords for positioning)
3. The vanilla close-on-mouse-check in method843 doesn't interfere (we handle close ourselves)

**Intercepting method843():** Add a guard at the top of `Protocol.method843()`:
```java
if (FPContextMenuController.isMenuOpen()) {
    // FP context menu owns mouse input -- handle close on left-click
    // and suppress all vanilla menu processing
    if (Mouse.clickButton == 1) {
        // Left click executes selected entry (handled in FPContextMenuController.update())
        // Clear click to prevent vanilla processing
        Mouse.clickButton = 0;
    }
    return;
}
```

**Visual highlight of selected entry:** The vanilla menu renders entries at known Y positions. After `MiniMenu.drawA()/drawB()`, draw a highlight rectangle at the selected entry's position. Add this to `FPContextMenuController.draw()`:
- Calculate Y of selected entry: `menuY + (MiniMenu.size - selectedIndex - 1) * 15 + 31` (matching method843's layout)
- Draw semi-transparent highlight rectangle using GlRaster/SoftwareRaster

---

## Task 2 -- Modify `ModernActionOverlay.java`

**P7 -- Range reduction (line 67):**
```java
// OLD: private static final int INTERACT_RANGE_TILES = 8;
private static final int INTERACT_RANGE_TILES = 2;
```

**P9 -- Nearest-ray-hit target selection (snapshot() method, lines ~413-452):**

Replace the current "last menu entry" selection with first-pick-tag selection:

1. Iterate `Model.aLongArray11[0..MiniMenu.anInt7-1]` (index 0 = nearest to camera)
2. For each pick tag, extract type bits `(int)(tag >> 29) & 0x3` and entity index `(int)(tag >>> 32)`
3. Resolve entity tile via `resolveEntryTile()`
4. Check range (<= 2 tiles)
5. Find matching menu entries (same key) for this target
6. Use first valid target found (nearest depth from camera)
7. Fall back to tile/player distance as secondary tie-breaker only if depth is ambiguous

The key change is in the `snapshot()` method: instead of walking `MiniMenu.size-1` down to 1 and picking the last whitelisted entry, walk `Model.aLongArray11` from index 0 upward and pick the first entity that has matching menu entries within range.

**P8 -- NPC + LOC overlay:** Already supported by the existing whitelist + the new nearest-pick selection. Both NPC and LOC picks appear in `Model.aLongArray11`.

---

## Task 3 -- Modify `ModernCameraRig.java`

**P3 -- Wheel ownership (processWheelInput(), line ~565):**

Add gate at the top of `processWheelInput()`:
```java
private static void processWheelInput() {
    if (FPContextMenuController.isMenuOpen()) {
        // FP context menu owns the wheel -- delegate to menu controller
        FPContextMenuController.processWheelInput();
        return;
    }
    // ... existing wheel zoom logic unchanged
}
```

This ensures exactly ONE consumer per wheel delta: context menu OR camera zoom, never both.

---

## Task 4 -- Modify `Protocol.method843()` (line ~3582)

Add FP context menu guard at the very start (after the inventory drag check):
```java
public static void method843() {
    if (InterfaceList.clickedInventoryComponent != null || Cs1ScriptRunner.aClass13_14 != null) {
        return;
    }
    // FP context menu intercept: suppress vanilla menu processing
    if (FPContextMenuController.isMenuOpen()) {
        // Handle vanilla menu close detection (mouse-out-of-bounds)
        // and left-click execution via FP controller
        return;
    }
    // ... existing vanilla logic unchanged
```

---

## Task 5 -- Modify `ModernControlController.java`

**updateInteractionLayer() (line ~202):**
```java
private static void updateInteractionLayer() {
    boolean uiConsumed = ModernDialogueKeyboard.update();
    if (!uiConsumed) {
        FPContextMenuController.update(); // NEW -- runs before overlay
        if (!FPContextMenuController.isMenuOpen()) {
            ModernActionOverlay.update(); // suppressed while menu is open
        }
    }
}
```

**isGameplayInputAllowed() (line ~220):**
```java
public static boolean isGameplayInputAllowed() {
    return !chatInputActive && !FPContextMenuController.isMenuOpen();
}
```

---

## Task 6 -- Modify `ModernMovementController.java`

**readInput() (line ~996):**
```java
private static void readInput() {
    intent.clear();
    if (FPContextMenuController.isMenuOpen()) return; // WASD suspended while menu open
    // ... existing WASD reading unchanged
```

**P4B Hotfix -- Remove broad post-exit step drain:**

1. Remove field `postExitDrainUntil` (line 107)
2. Remove field `POST_EXIT_DRAIN_TICKS` (line 108)
3. Add new field: `public static int residualModernStepsDiscarded` (for F12)
4. Modify `isDrainingServerSteps()` (line ~333):
   ```java
   public static boolean isDrainingServerSteps() {
       return isModernQ16Owner();
   }
   ```
   This removes the time-based drain entirely. The `self.teleport(authX, true, authZ)` in `exitModernMode()` already resets the movement queue, so no drain window is needed.
5. Remove the line in `exitModernMode()` that sets `postExitDrainUntil` (line 258)
6. Add diagnostic field for F12

---

## Task 7 -- Modify `DebugOverlay.java`

Add two new sections to the `draw()` method's `lines` array:

**FP CONTEXT MENU section (after DIALOGUE/TARGET):**
```
hdr("FP CONTEXT MENU")
lbl("open", FPContextMenuController.isMenuOpen())
lbl("menuSize", MiniMenu.size)
lbl("selectedIndex", FPContextMenuController.getSelectedIndex())
lbl("selectedOp", FPContextMenuController.getSelectedOp())
lbl("selectedTarget", FPContextMenuController.getSelectedTarget())
lbl("wheelConsumed", FPContextMenuController.wasWheelConsumed())
```

**CROSSHAIR TARGET section (enhance existing WORLD OVERLAY):**
```
hdr("CROSSHAIR TARGET")
lbl("pickCandidates", MiniMenu.anInt7)
// For first 2 candidates: name/type/depth
lbl("candidate0", candidate info from aLongArray11[0])
lbl("candidate1", candidate info from aLongArray11[1])
lbl("selected", ModernActionOverlay.getTargetName())
lbl("selectedDepth", pick index of selected target)
lbl("selectionReason", "NEAREST_RAY_HIT")
lbl("overlayRange", 2)
lbl("overlayVisible", ModernActionOverlay.isSnapshotValid())
lbl("residualModernStepsDiscarded", ModernMovementController.residualModernStepsDiscarded)
```

---

## Task 8 -- Build Verification

```
cd rt4-client
gradlew.bat :client:compileJava
```

Static review of all changes. Then STOP (per user instruction).

---

## Files Modified Summary

| File | Change |
|------|--------|
| `rt4/FPContextMenuController.java` | **NEW** -- FP right-click context menu controller |
| `rt4/ModernActionOverlay.java` | Range 8->2, nearest-ray-hit selection |
| `rt4/ModernCameraRig.java` | Wheel gate for FP context menu |
| `rt4/Protocol.java` | method843() FP intercept guard |
| `rt4/ModernControlController.java` | Interaction layer update order, gameplay input gate |
| `rt4/ModernMovementController.java` | WASD suspension, P4B drain removal |
| `rt4/DebugOverlay.java` | FP CONTEXT MENU + CROSSHAIR TARGET sections |

## Regression Safety Checklist

- ORIGINAL mode: no code paths changed (gates all check `CameraMode.isModern()` + `FIRST_PERSON`)
- FREE mode: unchanged (FP context menu only activates in FIRST_PERSON rig state)
- Vanilla right-click menu: unchanged when FP context menu is not open
- Dialogue priority: preserved (dialogue check runs before FP menu open)
- CTRL free-mouse: preserved (UI cursor check runs before FP menu open)
- Smooth camera zoom: unchanged when menu closed (single wheel consumer rule)
- `MiniMenu.doAction()` route: used exclusively for all execution (no custom packets)
- Ceiling renderer: not touched
- Combat: not touched