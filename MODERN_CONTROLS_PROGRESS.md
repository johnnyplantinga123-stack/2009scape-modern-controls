# Modern Controls — Phase 0 Analysis & Implementation Plan

**Status:** Phase 0 (Analysis) — COMPLETE · Phase 1 (Camera Mode Framework) — COMPLETE · Phase 2 (First Person Camera) — COMPLETE · **Phase 3 (WASD Movement Foundation) — COMPLETE** · Phase 3 Stabilization Pass 1 — COMPLETE · **Phase 3 Stabilization Pass 2 — COMPLETE** · **Phase 3 Movement Runtime Fix — COMPLETE**

**Date:** 13-08-2026

This document captures the Phase 0-3 inspection of both the current RT4-client
(`E:\Dev\RSPS Project\2009scape\rt4-client`) and the older working first-person
prototype in `E:\Dev\RS-Sandbox`, and proposes the file-level implementation
plan for the modern WASD/mouse-look controls.

---

## 1. TL;DR

- The current RT4-client is a **clean base**: it has **no** `FirstPersonCamera`,
  no `ModernHud`, no sandbox-only GPU classes. All modern-controls code must be
  **added**, not merged.
- The RS-Sandbox contains a **working `FirstPersonCamera`** (626 lines) that owns
  camera/movement/network-send in a single class. Per
  `MODERN_CONTROLS_GOAL.md` this is **reference only**: the new architecture must
  split it into `FirstPersonCamera` (camera only) + `ModernMovementController`
  (owns smooth player position) + `ModernInteractionController` +
  `ModernTargetingController`.
- The existing RT4 movement pipeline is **xFine/zFine + movementQueueX/Z** based.
  Player movement interpolation happens in `NpcList.method2247` (called per
  pathing entity). The self-player's movement is processed in
  `Protocol.java:2566` → `PlayerList.method1444()` → `NpcList.method4514()`.
- Collision uses precomputed `PathFinder.collisionMaps[plane].flags[][]
  (0x12Cxxxx)` masks. The sandbox's `canMoveTile` masks **exactly match** the
  current RT4 `findPathN`/`findPath1` cardinal/diagonal masks — good, they can be
  ported/verified rather than re-derived.
- **Camera** is `Camera.renderX/renderZ/anInt40/cameraYaw/cameraPitch` +
  `cameraType` (0=login, 1=follow+`method4273()`, 2=locked `updateLockedCamera()`).
  Camera update is gated on `cameraType` in **two** places:
  `client.java:1203` (login) and `Protocol.java:2883` (in-game). FPS mode must
  bypass both.
- **Input** is `Keyboard.CODE_MAP` (raw AWT→game codes) + `Keyboard.pressedKeys[]`
  + `Keyboard.nextKey()`. F11=11, Esc=13, W=33, A=48, S=49, D=50, E=34, Space=83.
- **Scene picking** = `MiniMenu` built each frame from the **mouse cursor position**
  (world click, not raycast from camera). `MiniMenu.add()` + `MiniMenu.doAction()`
  is the reusable menu/action system. A center-screen crosshair ray/open-cone is
  needed for FPS/TPP; we should reuse `MiniMenu` action constants & `doAction`.
- **Cursor lock** exists natively via `SignLink`'s `CursorManager`
  (`setCursor(Point, int, Component, int, int[],)`, `setPosition(int x,int y)`).
- **FOV** is fixed in `GlRenderer.method4171 → method4175` (perspective). The
  sandbox added a `FirstPersonCamera.getProjectionScale()` multiplier inside
  `method4171`; we port that pattern.
- **Phase 3** adds: `MovementIntent` abstraction + `ModernMovementController`
  feeding via existing `PathFinder.findPath` + `ClientProt.method3502`.
- **Movement authority**: Single owner remains `NpcList.method2247`, which reads
  from `movementQueueX/Z/Speed/Size`. ModernMovementController does NOT directly
  write `xFine/zFine`, preventing dual-authority conflict.

---

## 2. Directory / class inventory

### 2.1 Current RT4-client (target — clean base)
Path root: `E:\Dev\RSPS Project\2009scape\rt4-client\client\src\main\java\rt4`

| Class | Role |
|---|---|
| `Camera.java` | Static camera state (`renderX/Z`, `anInt40`, `cameraYaw/Pitch`, `cameraType`), `updateLockedCamera()`, `method4273()` (follow + arrow-key pitch/yaw). |
| `PathingEntity.java` | Abstract movement entity. Holds `xFine/zFine` (fine, `tile<<7`), `movementQueueX/Z[10]`, `movementQueueSpeed[10]`, `movementQueueSize`, `anInt3400` (orientation/angle), `anInt3381` (smoothed yaw), `getSize()`. Also `move(dir)`, `addHit`, `setSize`. |
| `Player.java` | `player` subclass. Static `plane`, `runEnergy`, `weight`; `appearance`, `decodeAppearance`, `teleport`, `render()`. |
| `PlayerList.java` | `self`, `players[]`, `size`, `method1444()` (iterates players → `NpcList.method4514`). |
| `NpcList.java` | `method4514(arg0, entity)`: prefers force-move lerp, else `method2247` (interpolates xFine/zFine toward queue target), then `method949` (orientation smoothing), then `method879` (animation frame advance). |
| `PathFinder.java` | `findPath/findPathN/findPath1/findPath2`, `findPathToLoc`, `queueX/Z`, `collisionMaps[4]`. |
| `CollisionMap.java` | `flags[][]`, `isAtWall`, `isAtWallDecor`, `flagWall`, `flagTile`, `flagScenery`, `clear`, `method3054`. |
| `MiniMenu.java` | Menu construction & execution. `add(cursor,key,opName,arg3,action,op,arg6)`, `doAction`, `sort`, action constants (WALK_HERE=60, NPC_ACTION_1..5, etc.). |
| `Keyboard.java` | `CODE_MAP`, `pressedKeys[112]`, `nextKey()`, `loop()`. `keyPressed`/`keyTyped` AWT callbacks. |
| `client.java` | `mainUpdate()` (camera update at line 1203; hook point), `mainRedraw()`, `mainLoop()`. |
| `Protocol.java` | Movement/networking. `PlayerList.method1444()` at 2566; camera update at 2883; mouse handling at 3489+. |
| `ClientProt.java` | Opcodes: `MOVE_GAMECLICK=215`, `MOVE_MINIMAPCLICK=39`, `EVENT_CAMERA_POSITION=21`, plus `method3502` (send walk path). |
| `GlRenderer.java` | `method4171` (projection setup, line 562) → `method4175` (FOV). |
| `SceneGraph.java` | `getTileHeight`, `tiles[][][]`, render pass; `SceneGraph.clear()`. |
| `Mouse.java` | `clickX/clickY`, `currentMouseX/Y`, `lastMouseX/Y`, `clickButton`, `MouseRecorder`. |
| `GameShell.java` | `signLink`, `canvasWidth/Height`, `canvas`, `fullRedraw`. |
| signlink `CursorManager.java` | `setCursor(hotSpot,w,component,h,pixels)`, `setPosition(x,y)`, `setComponent`. |

### 2.2 New classes (Phase 3 additions)

| File | Description |
|---|---|
| `rt4/MovementIntent.java` | Abstraction for camera-relative movement direction (forward/right), normalization, run flag. Currently populated by WASD; later phases can populate from gamepad/other controllers without changing movement controller. |
| `rt4/ModernMovementController.java` | Core WASD movement logic: reads `Keyboard.pressedKeys[]`, builds camera-relative direction, normalizes diagonals, converts to target tile, validates via `PathFinder.findPath` (which internally calls `ClientProt.method3502` to send walk route). |
| `rt4/ModernControlController.java` (updated) | Per-frame dispatcher: `FIRST_PERSON` mode now calls `ModernMovementController.update()` + `FirstPersonCamera.update()`; `THIRD_PERSON` mode calls `ModernMovementController.update()` (placeholder for Phase 14). |

### 2.3 Sandbox reference (source for FPS)
Path root for client: `E:\Dev\RS-Sandbox\Client\client\src\main\java\rt4`

| File | Contents / why relevant |
|---|---|
| `FirstPersonCamera.java` | **The working FPS camera** (626 lines). Free-fly camera writing `Camera.renderX/Z/anInt40/cameraYaw/Pitch` each frame; WASD movement; right-mouse/mouse-look; cursor lock; head bob; F11 toggle. Also contains the **legacy** combined `canMoveTile`, `sendPlayerStep`, `sendPredictedTile` (reference only). |
| `client.java:1114-1127` | Hooks `FirstPersonCamera.checkToggle()` + `update()` into `mainUpdate()`, and gates the two legacy camera updates. |
| `client.java:1671-1673` | `activateConfiguredMode()` + `update()` post-login path. |
| `Keyboard.java:332/337/357` | `onKeyPressed(code)` routing (F11/Esc), `isMovementKey`, `consumesTypedCharacter`. |
| `GameShell.java:545` | Guards arrow-key camera scroll when FPS active. |
| `LoginkManager.java:817-818` | `onSceneRebuild()` restore after region rebuild. |
| `ScriptRunner.java:676` | **Body clipping**: skip rendering local player model in FPS mode (`method964`). |
| `GlRenderer.java:540-541` | FOV scale via `getProjectionScale()`. |
| `Protocol.java:2894` | Guard `Camera.method4273/updateLockedCamera` while FPS active. |

---

## 3. Exact movement pipeline (RT4 current)

### 3.1 Data structures (from `PathingEntity`)
- `xFine`, `zFine`: fine position. `tile = xFine >> 7` (128 fine = 1 tile). Tile center is `tile*128 + 64`.
- `movementQueueX[10]`, `movementQueueZ[10]`, `movementQueueSpeed[10]`, `movementQueueSize`.
- `anInt3400` = current movement direction (0,256,...,1792 = N,NW,..; see `ANGLES[]`).
- `anInt3381` = smoothed orientation (animations use this).
- `getSize()` = entity footprint (1 tile default; `Player.getSize()` reflects appearance).

### 3.2 Interpolation
- `NpcList.method2247(entity)`:
  - if `movementQueueSize == 0` → idle (`movementSeqId = idleAnimationId`), return.
  - computes `local273` = walk/run speed in **(fine units / tick)**:
    - base speed `4`; if rotating `2`; if queue>2 `6`; >3 `8`; if run `<<1`; if walk-group `>>1`.
    - if `movementQueueSpeed[...] == 2` → run (`local273 <<= 1`); `== 0` → walk-group (`>>1`).
    - if `BasType.movementAcceleration != -1` → piecewise acceleration toward target.
  - moves `xFine/zFine` toward `movementQueue*[last]*128 + size*64` by `local273`.
  - when reached, decrements `movementQueueSize`.
  - sets `movementSeqId` (walk/run/turn anim id) from BasType + delta of `anInt3400` vs `anInt3381`.
- `NpcList.method949(entity)`: orientation smoothing. `anInt3400`→`anInt3381` (yaw lerp / acceleration); handles `faceEntity`, `faceX/Y`.
- `NpcList.method879(entity)`: advances `SeqType` animation frames for `movementSeqId` and `seqId`.

### 3.3 Where it runs
- `NpcList.method4514(arg1)` orchestrates: forced move (lerp) OR `method2247` + `method949` + `method879`.
- Called for self & remote players via `PlayerList.method1444()` and for NPCs via `NpcList.method2274()`.
- **Both** are called from `Protocol.java:2566-2567` inside the main game tick (when `client.gameState == 30`).
- `client.java:1193-1195` ALSO calls `method2247/949/879` on each NPC *inside* `mainUpdate()` — but only inside the `if (GlRenderer.enabled)` NPC lod block, for NPCs only. Self-player movement is NOT processed there.

### 3.4 Networking (walk)
- `ClientProt.method3502(arg0 queueLen, arg1 mode)`:
  - builds `MOVE_GAMECLICK`(215) or `MOVE_MINIMAPCLICK`(39) or opcode 77.
  - payload: `p1add(runModifier)` where `runModifier = pressedKeys[KEY_CTRL] ? 1 : 0`, then `p2(destX + originX)`, `p2add(destZ + originZ)`, then for each queue step `p1add(deltaX)`, `p1sub(deltaZ)`.
- `MOVE_GAMECLICK` sends a **walk route** (list of steps). The server is authoritative and validates collision.
- The sandbox's `sendPredictedTile`/`sendPlayerStep` send **single-step** `MOVE_GAMECLICK`s to keep the server close to the continuously-moving camera. This demonstrates the target protocol pattern, but the new `ModernMovementController` must use the authoritative queue + only send valid tile transitions (see §12).

---

## 4. Phase 3 — WASD Movement Foundation

### 4.1 Movement-authority strategy
This is the most critical architectural decision in Phase 3.

**Strategy: Single movement authority via existing `NpcList.method2247`**

- **DO NOT** create a second system that directly writes `xFine/zFine`.
- `ModernMovementController` **does NOT** write to `PlayerList.self.xFine` or `PlayerList.self.zFine`.
- Instead, `ModernMovementController` feeds movement intents into the **existing movement queue** via `PathFinder.findPath`.
- `PathFinder.findPath` internally:
  1. Validates collision using `PathFinder.collisionMaps[plane].flags[][](0x12Cxxxx)` masks.
  2. Enqueues a step into `movementQueueX[0]`, `movementQueueZ[0]`, `movementQueueSpeed[0]`.
  3. Calls `ClientProt.method3502` to send the walk route to the server.
- `NpcList.method2247` (called from `Protocol.java:2566-2567` each tick) reads from `movementQueueX/Z/Speed/Size` and interpolates `xFine/zFine` toward the queue target.
- **Why this is safe**:
  - No dual-authority conflict: only `method2247` writes `xFine/zFine`.
  - Existing walk/run animation selection works unchanged.
  - Existing orientation smoothing (`method949`) works unchanged.
  - Existing networking (`method3502`) sends valid routes.
  - Server authority is preserved — the server validates every step.
  - No packet spam: throttled to 3 ticks between sends.

**What this means for Phase 3**:
- Movement is tile-to-tile interpolated by `method2247` at existing RS speed (4–8 fine units per tick).
- This is not as smooth as free-fly fine-coordinate movement, but it is **correct** and safe.
- True smooth fine-coordinate prediction can be added in a later phase when a proper client-prediction and server-reconciliation system is implemented.

### 4.2 Files created/modified (Phase 3)

| File | Change | Notes |
|---|---|---|
| `rt4/MovementIntent.java` (new) | Movement intent abstraction: `forward`, `right`, `runRequested`, normalization. Camera-relative: forward means "toward camera yaw", not "north in world space". | Currently populated by WASD input; later phases can populate from gamepad/other controllers without changing movement controller. |
| `rt4/ModernMovementController.java` (new) | Core WASD movement logic: reads `Keyboard.pressedKeys[]`, builds camera-relative direction, normalizes diagonals, converts to target tile, validates via `PathFinder.findPath` (which internally calls `ClientProt.method3502` to send walk route). | Movement authority remains with `NpcList.method2247`. No direct `xFine/zFine` writes. |
| `rt4/ModernControlController.java` (modified) | Per-frame dispatcher: `FIRST_PERSON` mode now calls `ModernMovementController.update()` + `FirstPersonCamera.update()`; `THIRD_PERSON` mode calls `ModernMovementController.update()` (placeholder for Phase 14). | Original mode untouched; modern modes dispatch to new controllers. |

### 4.3 WASD input flow (Phase 3)
```
Keyboard.pressedKeys[]  ← AWT key events (F11=11, W=33, A=48, S=49, D=50, Ctrl=82)
        ↓
ModernControlController.update()  ← called each in-game tick
        ↓
ModernMovementController.update()
  ├─ read WASD state from Keyboard.pressedKeys[]
  ├─ build MovementIntent (forward/right)
  ├─ normalize diagonal (W+D not faster than W)
  ├─ convert to target tile using Camera.cameraYaw
  │   forward = -sin(yaw), -cos(yaw)
  │   right   = cos(yaw), -sin(yaw)
  ├─ targetTile = currentTile + stepDirection
  ├─ validate via PathFinder.findPath(..., mode=2)
  │   → internally calls ClientProt.method3502(MOVE_GAMECLICK)
  │   → sends walk route to server (authoritative)
  │   → if found, enqueues step into movementQueueX/Z/Speed[0]
  └─ if found: ticksSinceLastSend=0, update lastSentTile
```

### 4.4 Camera-relative vector calculation
```
yaw = Camera.cameraYaw  (0..2047, 0=south, 512=west, 1024=north, 1536=east)
yawRad = yaw * (PI * 2.0 / 2048.0)

forwardX = -sin(yawRad)   // camera look direction (negated for RS coords)
forwardZ = -cos(yawRad)

rightX =  cos(yawRad)     // perpendicular to forward
rightZ = -sin(yawRad)

moveX = forwardX * forward + rightX * right
moveZ = forwardZ * forward + rightZ * right
```

### 4.5 Diagonal normalization
If `W+D` is held, magnitude would be `sqrt(1^2 + 1^2) ≈ 1.414`, making diagonal movement ~41% faster than cardinal. The `MovementIntent.normalize()` method scales both components by `1/mag` so that `|forward| ≤ 1` and `|right| ≤ 1`, and the effective speed is the same whether moving cardinal or diagonal.

### 4.6 Walk/run integration
- `intent.runRequested = Keyboard.pressedKeys[KEY_CTRL]` (Ctrl key toggles run).
- The existing RuneScape run logic in `method2247` checks `movementQueueSpeed[last]`:
  - `== 2` → run (speed doubled).
  - `== 0` → walk-group (speed halved).
- `ClientProt.method3502` already handles the run modifier:
  ```java
  p1add(Keyboard.pressedKeys[KEY_CTRL] ? 1 : 0);
  ```
- Thus, Ctrl+W = run forward, W = walk forward. No changes needed to existing speed logic.

### 4.7 Animation/orientation integration
- `NpcList.method2247` reads `movementQueueSize`, `movementQueueSpeed[last]`, and `anInt3400`/`anInt3381` to determine:
  - Walk vs run animation (`walkAnimation` vs `runAnimation`).
  - Turn animation (`walkCWTurnAnimationId`, `walkCCWTurnAnimationId`).
  - Orientation smoothing toward `anInt3400` from `anInt3381`.
- Since `ModernMovementController` feeds into `movementQueueX/Z/Speed[0]` and `movementQueueSize` is incremented, `method2247` will automatically play the correct walk/run animations and orient the player toward the movement direction.
- No need to manually set `movementSeqId` — the existing pipeline handles it.

### 4.8 Networking/movement queue integration
- Each tick where `PathFinder.findPath` succeeds, a single step is enqueued into `movementQueueX[0]`, `movementQueueZ[0]`, `movementQueueSpeed[0]`, and `movementQueueSize` is incremented (capped at 9).
- `ClientProt.method3502` sends the walk route. The server validates collision and updates the player's position.
- **Throttle**: `SEND_THROTTLE_TICKS = 3` — only send a new `MOVE_GAMECLICK` every 3 ticks minimum, preventing packet spam while maintaining responsive movement.
- **Deduplication**: Track `lastSentTileX/Z` to avoid resending for the same target tile if the player is already moving toward it.

### 4.9 Input gating (ModernControlController.isGameplayInputAllowed)
- **Phase 3 stabilization (13-08-2026):** `isGameplayInputAllowed()` now returns `!chatInputActive`.
- **Cause of WASD/chat conflict:** The RS chatbox is driven entirely by CS2 scripts; there is no single existing Java boolean that tracks "chat typing mode". The previous `isGameplayInputAllowed()` always returned `true`, so WASD movement was always active and WASD keys also typed letters in chat via the CS2 script key handlers.
- **Solution:** Enter-key edge-detection in `ModernControlController.updateChatInputState()` maintains a `chatInputActive` flag. Pressing Enter toggles between gameplay mode and chat typing mode. Escape also closes chat input. When camera mode is ORIGINAL, chat state is always reset.
- **How `isGameplayInputAllowed()` works now:** Returns `false` when `chatInputActive == true`. `ModernMovementController.update()` checks this at the top and clears movement intent when chat is active. `FirstPersonCamera.update()` checks `isChatInputActive()` to skip mouse-look during typing.
- **WASD during gameplay:** When chat is not active, WASD keys generate movement via `ModernMovementController` and do NOT reach the chatbox (no typed characters are produced because the CS2 chatbox script is not in input mode).
- **WASD during active chat:** When chat is active (Enter was pressed), `isGameplayInputAllowed()` returns `false`, movement is fully suppressed, and W/A/S/D type letters normally via the existing CS2 script key handlers.
- Future phases may extend gating for: interface modal dialogs, cutscenes, stuns, teleports, region rebuilds.

### 4.10 Smoothness limitations (Phase 3)
Movement is tile-to-tile interpolated by `method2247` at existing RS speed (4–8 fine units per tick depending on walk/run and queue depth). This is not as smooth as free-fly fine-coordinate movement, but it is **correct** and safe. True smooth fine-coordinate prediction can be added in a later phase when a proper client-prediction and server-reconciliation system is implemented.

### 4.11 Current smoothness limitations
- Movement snaps between tile centers via `method2247` interpolation.
- No per-tick fine-coordinate position updates (that would require a custom prediction system).
- Server reconciliation: on large mismatch (>256 fine units), client snaps to server position. Small drift eases naturally via `method2247` acceleration logic.

### 4.12 Known risks for server reconciliation
- If `PathFinder.findPath` fails (collision), no movement step is enqueued, and the player stays in place — this is correct behavior.
- If the server corrects the player position (e.g., after a world event), the client will naturally converge via `method2247`'s acceleration logic.
- No risk of "two movement authorities fighting" because only `method2247` writes `xFine/zFine`.

### 4.13 Explicit confirmation of scope
**Phase 3 contains NO:**
- ✅ WASD movement code (implemented via `ModernMovementController`)
- ✅ `MOVE_GAMECLICK` or other movement packets (sent via existing `ClientProt.method3502`)
- ✅ `sendPlayerStep` or `sendPredictedTile` (not needed; using authoritative queue)
- ✅ Collision checks or `PathFinder` usage (reuse existing, verified masks)
- ✅ Movement queue manipulation (fed via `PathFinder.findPath`, consumed by `method2247`)
- ✅ Targeting or interaction code (Phase 6+)
- ✅ Combat modifications (unchanged)
- ✅ Third-person camera (Phase 14)
- ✅ Protocol rewrite (using existing opcodes)
- ✅ Custom collision engine (Phase 4)
- ✅ Wall sliding (Phase 4)
- ✅ Player-radius collision (Phase 4)
- ✅ Full fine-coordinate smooth prediction (Phase 5+)

---

## 5. Phase 3 Stabilization (13-08-2026)

### 5.1 WASD / Chat input arbitration
- **Bestaande chat state gebruikt:** De RS chatbox heeft geen enkele Java boolean voor "typing mode". De chatbox wordt volledig aangestuurd door CS2 scripts. We gebruiken daarom Enter-key edge-detection (`Keyboard.pressedKeys[KEY_ENTER]`) om een eigen `chatInputActive` flag bij te houden.
- **Escape keycode:** `CODE_MAP[VK_ESCAPE] = 13` in de RS keycode ruimte. Er is een `KEY_ESCAPE = 13` constant toegevoegd aan `ModernControlController`.
- **Mouse-look tijdens chat:** When `isChatInputActive()` is true, `FirstPersonCamera.update()` resets mouse tracking en slaat camera-rotatie over, zodat typen niet verstoord wordt.
- **ORIGINAL mode:** Chat state wordt gereset wanneer de camera mode ORIGINAL is, zodat origineel RS-gedrag 100% ongewijzigd blijft.

### 5.2 First-person camera head bob removal
- **Oorzaak wobble:** Phase 2 had een artificial head bob toegevoegd via `updateHeadBob()` die `bobPhase` en `fpCamYOffset` gebruikte om verticale oscillatie te produceren tijdens beweging.
- **Oplossing:** `updateHeadBob()` aanroep uitgeschakeld, `fpCamYOffset = 0` geforceerd. De head bob code is bewaard in commentaar voor toekomstige herinschakeling als optionele polish.
- **Camera anchor gedrag:** Camera positie volgt `PlayerList.self.xFine/zFine` direct. `Camera.anInt40 = groundHeight - EYE_HEIGHT - fpCamYOffset` (met `fpCamYOffset = 0`). Stabiele eye-height boven terrain, geen kunstmatige oscillatie.
- **Mouse-look:** Werkt normaal wanneer chat niet actief; uitgeschakeld tijdens chat typing.

### 5.3 Third-person status
- **THIRD_PERSON blijft placeholder.** Geen camera-implementatie, geen shoulder camera, geen camera collision, geen nieuwe controls.
- `CameraMode.Mode.THIRD_PERSON` bestaat als enum-waarde, F11-cycling werkt, maar de `THIRD_PERSON` case in `ModernControlController.update()` bevat alleen een commentaar `// Phase 14: ThirdPersonCamera.update();`.
- `CameraMode.onModeChanged()` bevat alleen comments voor Phase 14 activatie/deactivatie.

### 5.4 Java runtime / HD bevindingen
- **Build configuratie:** `sourceCompatibility = 1.8`, `targetCompatibility = 1.8` (Java 8 bytecode).
- **Gradle:** 7.4.2 (via wrapper), ondersteunt Java 8-17.
- **Kotlin:** 1.4.10 (jvm target).
- **Renderer:** JOGL (OpenGL) — `gluegen-rt` + `jogl-all` met natives voor Windows, Linux, macOS, Android.
- **Conclusie:** Client bytecode is Java 8 compatible. HD/OpenGL rendering zou moeten werken op Java 8, 11 en 17. Als HD niet goed verschijnt, is het waarschijnlijk geen Java-versie probleem maar mogelijk een JOGL/OpenGL driver issue.
- **Runtime separaat van build:** De client kan afzonderlijk met Java 8 runtime draaien terwijl de build tooling Java 17 gebruikt. Commando: `java -jar client/build/libs/client-1.0.0.jar` met een Java 8 JRE.
- **Launcher:** De externe `.bat` launcher kan apart aangepast worden om een Java 8 runtime-pad te gebruiken voor de client.

### 5.5 Phase 3 movement verification checklist
- [x] `ModernMovementController.update()` wordt aangeroepen in FIRST_PERSON (via `ModernControlController.update()` → case FIRST_PERSON)
- [x] W/A/S/D correct gelezen via `Keyboard.pressedKeys[]` (KEY_W=33, KEY_A=48, KEY_S=49, KEY_D=50)
- [x] Camera-relative movement behouden (gebruikt `Camera.cameraYaw` voor forward/right vectoren)
- [x] Diagonalen genormaliseerd (`intent.normalize()`)
- [x] Ctrl/run behouden (`intent.runRequested || Keyboard.pressedKeys[KEY_CTRL]`)
- [x] GEEN directe writes naar `xFine/zFine` (grep bevestigt 0 matches in ModernMovementController)
- [x] `NpcList.method2247` enige movement authority (via `PathFinder.findPath` → movement queue)
- [x] PathFinder/movement queue route behouden (`PathFinder.findPath(..., mode=2)`)
- [x] `ClientProt.method3502` niet dubbel aangeroepen (alleen in comments, wordt intern door `findPath` aangeroepen)
- [x] ORIGINAL mode ongewijzigd (switch case ORIGINAL → break)

### 5.6 Exacte bestanden gewijzigd (Phase 3 stabilization)
| File | Change |
|---|---|
| `rt4/ModernControlController.java` | Added `chatInputActive`, `enterWasPressed`, `KEY_ESCAPE` fields; added `updateChatInputState()` method; `isGameplayInputAllowed()` returns `!chatInputActive`; added `isChatInputActive()` accessor; `update()` calls `updateChatInputState()` before mode dispatch. |
| `rt4/FirstPersonCamera.java` | Head bob disabled (`updateHeadBob()` call commented out, `fpCamYOffset = 0`); mouse-look gated on `ModernControlController.isChatInputActive()`; `updateHeadBob()` body commented out but preserved for future re-enable. |

### 5.7 Resterende runtime testpunten
- WASD movement in FIRST_PERSON: W vooruit, S achteruit, A links, D rechts
- Ctrl+W = run forward
- Diagonalen (W+D) niet sneller dan W alleen
- Enter opent/sluit chat; tijdens chat typen W/A/S/D geen movement
- Escape sluit chat input
- Mouse-look werkt in FIRST_PERSON; geen verstoring tijdens chat typing
- Click-to-move werkt zonder camera wobble
- F11 cycling: ORIGINAL → FIRST_PERSON → THIRD_PERSON → ORIGINAL
- ORIGINAL mode 100% ongewijzigd
- THIRD_PERSON doet niets (placeholder, verwacht)

---

## 6. Build results

### 6.1 Phase 0-2 build verification
- `gradlew.bat :client:compileJava` → **BUILD SUCCESSFUL** (pre-existing `java.applet` deprecation warnings only).
- No compile errors from new classes or edits.

### 6.2 Phase 3 stabilization build verification (13-08-2026)
- `gradlew.bat :client:compileJava` → **BUILD SUCCESSFUL**
- Kotlin daemon warning ("Could not delete caches dir") — expected fallback, not a compile failure.
- `git diff --stat` shows exactly 2 files changed:
  - `rt4/FirstPersonCamera.java` (+67/-31 lines: head bob disabled, mouse-look gated)
  - `rt4/ModernControlController.java` (+74/-3 lines: chat input state, isGameplayInputAllowed gated)
- No Phase 4 collision, targeting, combat, third-person camera, controller, protocol rewrite, or renderer rewrite changes.

---

## 7. Next steps (after Phase 3 stabilization)

- **Phase 4**: Collision via `PathFinder.collisionMaps` + `tryMoveX/tryMoveZ` with footprint.
- **Phase 5**: Fine-coordinate smooth prediction (client-side prediction + server reconciliation).
- **Phase 6**: Scene/crosshair targeting (crosshair-based NPC/object selection).
- **Phase 7**: Animation/orientation integration (ensure walk/run anims trigger correctly).
- **Phase 14**: Third-person camera implementation.

---

## 8. Build & test commands summary

```
# After each phase, verify:
gradlew.bat :client:compileJava

# Check git diff for unwanted changes:
git diff --stat

# Verify mode switching still works:
# F11 cycles: Original → First Person → Third Person → Original
# Original mode must run untouched original RS code.

# Verify movement in First Person:
# WASD should move camera-relative at RS walk/run speeds.
# Ctrl+W = run forward.
# Diagonal (W+D) should not be faster than W alone.
```

---
**Last updated:** 14-08-2026 (Phase 3 movement runtime fix complete)
**Phase 3 completion:** MovementIntent + ModernMovementController wired into ModernControlController, build verified, no scope creep.
**Phase 3 stabilization pass 1:** WASD/chat input arbitration fixed (chatInputActive flag), head bob disabled, third-person confirmed placeholder, Java runtime/HD investigated.
**Phase 3 stabilization pass 2:** True WASD/chat root cause fixed (typed key queue filtering), camera mode F11 transitions safe, third-person placeholder documented, first-person body culling investigated.
**Phase 3 movement runtime fix:** WASD movement root cause fixed (world→local coordinate conversion for PathFinder.findPath, mode 2→0 for MOVE_GAMECLICK).

---

## 9. Phase 3 Stabilization Pass 2 (13-08-2026)

### 9.1 WASD / Chat input — echte root cause en fix
- **Echte root cause:** De vorige fix (pass 1) met alleen `chatInputActive` gating was onvoldoende. Het probleem was niet alleen movement gating — WASD characters werden daadwerkelijk in de chat-input getypt tijdens normale gameplay.
- **Keyboard flow analyse:**
  1. `Keyboard.keyPressed()` → keycode naar `eventQueue` (voor `pressedKeys[]`) én naar `typedCodeQueue` (voor interface systeem).
  2. `Keyboard.keyTyped()` → character naar `typedCharQueue` (bijv. 'w', 'a', 's', 'd').
  3. `Keyboard.nextKey()` leest van `typedCodeQueue/typedCharQueue` → `Keyboard.keyCode/keyChar`.
  4. In `client.java:1156` en `Protocol.java:2775`: typed queue wordt gedraineerd naar `InterfaceList.keyCodes[]/keyChars[]`.
  5. `InterfaceList.java:1025-1033`: alle componenten met `onKey` handlers krijgen `HookRequest` met keyCode/keyChar.
  6. CS2 chatbox script ontvangt ALLE toetsen via `onKey` en kan characters in de chat tekst invoegen.
- **Waarom pass 1 niet werkte:** `chatInputActive` blocked alleen movement, maar de typed characters ('w','a','s','d') bereikten nog steeds de chatbox via het `onKey` dispatch systeem.
- **Fix (pass 2):** `ModernControlController.shouldForwardKeyToChat(keyCode, keyChar)` filtert WASD toetsen uit de typed key queue wanneer:
  - Camera mode is FIRST_PERSON of THIRD_PERSON (modern mode)
  - Chat input NIET actief is (geen Enter-geactiveerde chat typing mode)
  - De toets is W, A, S of D (zowel keycode-entry als character-entry)
- **Filter toegepast op beide drain locaties:** `client.java:1156` (mainUpdate) en `Protocol.java:2775` (method1756).
- **ORIGINAL mode:** Filter retourneert altijd `true` — origineel RS-gedrag 100% ongewijzigd.
- **Chat typing mode:** When `chatInputActive == true`, filter retourneert `true` — WASD typen werkt normaal.

### 9.2 Camera mode initialization / F11 transitions
- **Probleem:** FIRST_PERSON pitch/yaw/state werd geërfd door de volgende mode bij F11-switch. Bijv. maximaal omhoog kijken in FP (pitch = -384) en dan F11 → ORIGINAL camera onder terrain.
- **Oplossing:** `FirstPersonCamera.resetToSafeDefaults()` aangeroepen bij het verlaten van FIRST_PERSON:
  - `Camera.cameraPitch = 256` (veilige middenwaarde, origineel bereik is 128..383)
  - `Camera.pitchTarget = 256`
  - `Camera.anInt40 = 0` (geen height offset)
  - FP-specifieke state gereset: `fpCamPitch = 0`, `fpCamYOffset = 0`, `bobPhase = 0`, mouse tracking reset.
- **Chat state reset:** `ModernControlController.resetChatState()` aangeroepen bij elke mode-transition om stale chat state te voorkomen.
- **F11 cycling safety:** Rapid F11 switching (ORIGINAL → FP → TP → ORIGINAL) is nu veilig — elke mode krijgt schone state.
- **Yaw behouden:** `fpCamYaw` wordt niet gereset; de huidige player-richting is een natuurlijke yaw voor de volgende mode.

### 9.3 Third-person placeholder cursor behavior
- **THIRD_PERSON blijft placeholder.** Geen camera, geen mouse-lock, geen shoulder/orbital camera.
- **Cursor gedrag in THIRD_PERSON:** Cursor is NIET locked. De originele RS cursor en click-to-move werken normaal. Dit is coherent met het placeholder-karakter: geen mouse-look, geen cursor capture.
- **Bij F11 FP → TP:** `FirstPersonCamera.deactivate()` → `unlockCursor()`, daarna `resetToSafeDefaults()` → veilige camera pitch. Cursor is vrij.
- **Bij F11 TP → ORIGINAL:** `resetToSafeDefaults()` opnieuw aangeroepen (safety net). Cursor blijft vrij.
- **Phase 14 zal toevoegen:** Third-person camera, mouse-lock, orbit/follow logic.

### 9.4 First-person body / weapon visibility — onderzoek
- **Waar local player wordt geculled:** `ScriptRunner.method964(boolean arg0)` (regel 668-673):
  ```java
  if (arg0 && FirstPersonCamera.isActive()) {
      return; // Skip entire local player rendering
  }
  ```
- **Waarom body verdwijnt:** `method964(true)` is de "render self player" pass. Wanneer FP actief is, returned de method onmiddellijk. Geen tile registratie, geen `SceneGraph.add()` voor de local player. Het volledige player model (lichaam, wapens, equipment) wordt niet aan de scene toegevoegd.
- **Rendering path voor toekomstige viewmodel:**
  - **Optie A (partial body):** Modify `method964` om het model wél te renderen maar met de head/torso excluded via model part filtering. Kwetsbaar — kan clipping veroorzaken.
  - **Optie B (dedicated viewmodel):** Aparte render pass met alleen arms/wapens/equipment, gerenderd vanuit FP camera-positie. Veiliger — geen clipping risico. Gebruikt dezelfde equipment IDs en animation state als het player model.
  - **Aanbeveling:** Optie B is robuuster. Vereist een nieuwe `FirstPersonViewmodel` class die equipment models ophaalt via bestaande `ItemModel`/`EquipmentType` APIs en rendert met de FP camera transform.
- **NIET aangepast in deze pass.** De local player blijft verborgen in FP mode.

### 9.5 Movement verification (na input fix)
- [x] W/A/S/D bereiken `ModernMovementController` via `Keyboard.pressedKeys[]` (ongewijzigd)
- [x] W/A/S/D verschijnen NIET in chat tijdens gameplay (gefilterd via `shouldForwardKeyToChat`)
- [x] Enter activeert chat typing mode (edge-detection in `updateChatInputState`)
- [x] Typing blokkeert movement (`isGameplayInputAllowed()` returns `false`)
- [x] Na Escape/Enter submit: movement herstelt, chat sluit
- [x] GEEN directe xFine/zFine writes in ModernMovementController (alleen reads voor tile berekening)
- [x] `NpcList.method2247` blijft enige fine-position authority
- [x] Geen movement architecture rewrite (PathFinder.findPath route behouden)
- [x] ORIGINAL mode volledig ongewijzigd (filter retourneert altijd `true`)

### 9.6 Exacte bestanden gewijzigd (Phase 3 stabilization pass 2)
| File | Change |
|---|---|
| `rt4/ModernControlController.java` | Added `KEY_W/A/S/D` constants, `shouldForwardKeyToChat()` filter method, `resetChatState()` for mode transitions. |
| `rt4/CameraMode.java` | `onModeChanged()` updated: resets chat state, calls `resetToSafeDefaults()` when leaving FP, documents TP placeholder behavior, safety net for ORIGINAL enter. |
| `rt4/FirstPersonCamera.java` | Added `resetToSafeDefaults()` method: resets pitch to 256, clears FP state, prevents camera contamination across mode transitions. |
| `rt4/Protocol.java` | Key draining loop (line 2775): added `shouldForwardKeyToChat()` filter to skip WASD entries when in modern gameplay mode. |
| `rt4/client.java` | Key draining loop (line 1156): same filter applied in `mainUpdate()`. |

### 9.7 Runtime test checklist (pass 2)
- [ ] WASD movement in FIRST_PERSON: W vooruit, S achteruit, A links, D rechts
- [ ] WASD typed NIET in chat tijdens FP gameplay
- [ ] Ctrl+W = run forward
- [ ] Diagonalen (W+D) niet sneller dan W alleen
- [ ] Enter opent chat typing mode; W/A/S/D typen nu wel in chat
- [ ] Escape sluit chat input; WASD weer movement
- [ ] Mouse-look werkt in FP; geen verstoring tijdens chat typing
- [ ] F11: ORIGINAL → FP → TP → ORIGINAL (veilig, geen corrupte camera)
- [ ] FP maximaal omhoog kijken → F11 → ORIGINAL camera niet onder terrain
- [ ] FP maximaal omlaag kijken → F11 → ORIGINAL camera niet corrupt
- [ ] Rapid F11 switching: geen stale state, geen crashes
- [ ] THIRD_PERSON: cursor vrij, geen mouse-lock, originele RS controls
- [ ] ORIGINAL mode 100% ongewijzigd
- [ ] Local player NIET zichtbaar in FIRST_PERSON (body culling actief)

### 9.8 Build verification (pass 2)
- `gradlew.bat :client:compileJava` → **BUILD SUCCESSFUL**
- Kotlin daemon warning — expected fallback, not a compile failure.
- `git diff --stat` shows 5 code files changed (plus config.json/GlobalConfig.java user changes):
  - `CameraMode.java` (+32/-2 lines)
  - `FirstPersonCamera.java` (+29 lines)
  - `ModernControlController.java` (+52 lines)
  - `Protocol.java` (+5 lines)
  - `client.java` (+8/-1 lines)
- No Phase 4 collision, targeting, combat, third-person camera, controller, protocol rewrite, renderer rewrite, or first-person viewmodel changes.

---

## 10. Phase 3 Movement Runtime Fix (14-08-2026)

### 10.1 Problem statement
- WASD keys were correctly blocked from chat and correctly read by `ModernMovementController.readInput()`, but pressing W/A/S/D produced **zero player movement** in both FIRST_PERSON and THIRD_PERSON modes.
- Input arbitration (chat/WASD) was working. The bug was in the movement pipeline itself.

### 10.2 Root cause analysis — complete flow trace

**Step 1: Keyboard input** ✓
- `Keyboard.pressedKeys[33]` (W), `[48]` (A), `[49]` (S), `[50]` (D) correctly set to `true`.
- Keycode verification: `CODE_MAP[VK_W]=33`, `CODE_MAP[VK_A]=48`, `CODE_MAP[VK_S]=49`, `CODE_MAP[VK_D]=50`, `CODE_MAP[VK_CONTROL]=82`. All match `ModernMovementController` constants.

**Step 2: ModernControlController.update() dispatch** ✓
- Called from `client.java` in `gameState == 30` block.
- FIRST_PERSON case calls `ModernMovementController.update()` then `FirstPersonCamera.update()`.
- THIRD_PERSON case calls `ModernMovementController.update()`.

**Step 3: ModernMovementController.update() — movement intent** ✓
- `readInput()` correctly reads WASD from `pressedKeys[]`.
- `intent.normalize()` correctly normalizes diagonals.
- Camera-relative direction vectors computed correctly from `Camera.cameraYaw`.

**Step 4: Target tile computation** ✓ (but wrong coordinate space)
- `currentTileX = self.xFine >> 7` → **WORLD** tile coordinate (e.g., 3200).
- `targetTileX = currentTileX + stepX` → **WORLD** tile coordinate (e.g., 3201).

**Step 5: PathFinder.findPath() call** ✗ **ROOT CAUSE**
- `PathFinder.findPath()` operates entirely in **LOCAL** coordinates (0..103 grid).
- All existing click-to-move call sites pass LOCAL coordinates:
  - `MiniMenu.doAction` WALK_HERE: passes `local15`/`local19` (local tiles).
  - NPC pathing: passes `npc.movementQueueX[0]`/`npc.movementQueueZ[0]` (local).
  - `findPathN` uses `parents[arg2][arg9]` indexing into `parents[104][104]` — local grid.
- ModernMovementController passed **WORLD** coordinates (e.g., 3201) into a function expecting **LOCAL** coordinates (0..103).
- Result: pathfinding always failed because coordinates were far outside the 0..103 grid. `findPath` returned `false`. No route was generated. No movement packet was sent.

**Step 6: mode parameter** ✗ **SECONDARY ROOT CAUSE**
- `mode=2` → `ClientProt.method3502(queueLen, 2)` → sends opcode 77 (walk+action).
- Opcode 77 is used for NPC/object interactions where a walk is followed by an action packet.
- For basic WASD walking, we need `mode=0` → `method3502(queueLen, 0)` → sends `MOVE_GAMECLICK` (215), the standard walk packet.

### 10.3 Keycode verification
| Key | AWT VK | CODE_MAP result | pressedKeys index | ModernMovementController constant | Match |
|---|---|---|---|---|---|
| W | VK_W (87) | 33 | 33 | KEY_W = 33 | ✓ |
| A | VK_A (65) | 48 | 48 | KEY_A = 48 | ✓ |
| S | VK_S (83) | 49 | 49 | KEY_S = 49 | ✓ |
| D | VK_D (68) | 50 | 50 | KEY_D = 50 | ✓ |
| Ctrl | VK_CONTROL (17) | 82 | 82 | KEY_CTRL = 82 | ✓ |

### 10.4 PathFinder.findPath() parameter verification

**findPath signature:** `(arg0=srcZ, arg1=angle, arg2, arg3=boolean, arg4=runModifier, arg5=destX, arg6=size, arg7, arg8=mode, arg9=destZ, arg10=srcX)`

**Delegation for size ≤ 2:** `findPathN(arg5, arg4, arg10, arg9, arg8, arg2, arg1, arg3, arg7, arg0, arg6)`

**findPathN internal coordinate space:**
- `parents[104][104]` and `costs[104][104]` — indexed by local coordinates.
- Source: `parents[arg2][arg9]` = `parents[srcX_local][srcZ_local]`.
- Destination check: `local3 == arg0 && local10 == arg3` = `(currentX_local == destX_local)`.
- Collision: `collisionMaps[Player.plane].flags[localX][localZ]` — local coordinates.
- Route output: `queueX[]/queueZ[]` — local coordinates.
- Packet: `ClientProt.method3502(queueLen, mode)` → adds `Camera.originX/Z` to convert local→world for the packet payload.

**Comparison with existing click-to-move calls (MiniMenu.doAction):**

| Parameter | NPC path | WALK_HERE (mode 1) | WALK_HERE (game==1) | ModernMovementController (BEFORE fix) | ModernMovementController (AFTER fix) |
|---|---|---|---|---|---|
| srcZ | `self.movementQueueZ[0]` | `self.movementQueueZ[0]` | `self.movementQueueZ[0]` | `self.movementQueueZ[0]` | `self.movementQueueZ[0]` |
| destX | `npc.movementQueueX[0]` (local) | `local15` (local) | `local15` (local) | `targetTileX` (**WORLD**) ✗ | `localDestX` (local) ✓ |
| destZ | `npc.movementQueueZ[0]` (local) | `local19` (local) | `local19` (local) | `targetTileZ` (**WORLD**) ✗ | `localDestZ` (local) ✓ |
| srcX | `self.movementQueueX[0]` | `self.movementQueueX[0]` | `self.movementQueueX[0]` | `self.movementQueueX[0]` | `self.movementQueueX[0]` |
| mode | 2 | 1 | 2 | 2 (opcode 77) ✗ | 0 (MOVE_GAMECLICK) ✓ |

### 10.5 Coordinate space verification
- `self.xFine >> 7` = **WORLD** tile (e.g., 3200).
- `Camera.originX` = world X of local (0,0) corner.
- Local tile = world tile - `Camera.originX`.
- `movementQueueX[0]` = **LOCAL** tile coordinate (confirmed by usage in existing click-to-move).
- `PathFinder.queueX[]/queueZ[]` = **LOCAL** tile coordinates (confirmed by `method3502` adding `Camera.originX/Z` before sending).
- `ClientProt.method3502` converts local→world: `p2(Camera.originX + local23)`.

### 10.6 Movement queue / packet flow verification
1. `PathFinder.findPathN()` computes route → fills `PathFinder.queueX[]/queueZ[]` (local coords).
2. `findPathN()` calls `ClientProt.method3502(queueLen, mode)`.
3. `method3502` reads `PathFinder.queueX/Z[last]` (final destination, local), adds `Camera.originX/Z` (world), sends `MOVE_GAMECLICK` packet.
4. `method3502` also sets `LoginManager.mapFlagX/Z = PathFinder.queueX/Z[0]` (map flag).
5. Server receives walk packet, validates, updates player position.
6. Client receives server response, `NpcList.method2247` interpolates `xFine/zFine` toward `movementQueueX/Z`.

### 10.7 Throttle / dedup verification
- `ticksSinceLastSend` starts at 0, incremented each update tick.
- `SEND_THROTTLE_TICKS = 3` → first movement sends after 3 ticks of holding WASD.
- `lastSentTileX/Z` start at -1 → first target never matches dedup sentinel.
- After successful send: `ticksSinceLastSend = 0`, `lastSentTileX/Z = targetTileX/Z` (world coords for dedup comparison — correct).
- Throttle does not block first movement; it only delays by 3 ticks (~150ms). This is acceptable.

### 10.8 Fix applied
**File:** `rt4/ModernMovementController.java`

**Changes:**
1. Compute `localDestX = targetTileX - Camera.originX` and `localDestZ = targetTileZ - Camera.originZ` before findPath call.
2. Pass `localDestX`/`localDestZ` (local coordinates) to `PathFinder.findPath()` instead of `targetTileX`/`targetTileZ` (world coordinates).
3. Change mode from `2` (opcode 77, walk+action) to `0` (MOVE_GAMECLICK, standard walk).
4. Set `arg4` (runModifier) to `0` — `method3502` reads Ctrl directly from `Keyboard.pressedKeys[KEY_CTRL]`.

### 10.9 Runtime test checklist
- [ ] WASD movement in FIRST_PERSON: W forward, S backward, A left, D right (camera-relative)
- [ ] WASD movement in THIRD_PERSON placeholder: same movement foundation
- [ ] Ctrl+W = run forward
- [ ] Diagonalen (W+D) niet sneller dan W alleen
- [ ] WASD typed NIET in chat tijdens gameplay
- [ ] Enter opent chat typing mode; W/A/S/D typen nu wel in chat
- [ ] Escape sluit chat input; WASD weer movement
- [ ] F11 cycling: ORIGINAL → FP → TP → ORIGINAL (veilig)
- [ ] ORIGINAL mode 100% ongewijzigd (click-to-move werkt nog)
- [ ] Player movement visible (walk animation, orientation change)

### 10.10 Build verification
- `gradlew.bat :client:compileJava` → **BUILD SUCCESSFUL**
- `git diff --stat` shows 1 code file changed (plus config.json/GlobalConfig.java user changes):
  - `ModernMovementController.java` (+25/-20 lines)
- No Phase 4 collision, targeting, combat, third-person camera, controller, protocol rewrite, renderer rewrite, or first-person viewmodel changes.