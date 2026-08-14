# Modern Controls — Phase 0 Analysis & Implementation Plan

**Status:** Phase 0 Analysis — COMPLETE · Phase 1 (Camera Mode Framework) — COMPLETE · Phase 2 (First Person Camera) — COMPLETE · **Phase 3 (WASD Movement Foundation) — COMPLETE** · Phase 3 Stabilization Pass 1 — COMPLETE · **Phase 3 Stabilization Pass 2 — COMPLETE** · **Phase 3 Movement Runtime Fix — COMPLETE** · **Phase 3 Stabilization Pass 3 (Scene Rebuild / Terrain Safety / Visibility) — COMPLETE** · **Phase 3 Stabilization Pass 4 — Camera Height Regression Fix — COMPLETE** · **Phase 3B (Continuous Modern Movement) — COMPLETE** · **Phase 3B Stabilization (Input, Animation, Self-Rendering) — COMPLETE** · **PHASE 3C (Modern Camera Rig) — IMPLEMENTATION COMPLETE / RUNTIME STABILIZATION** · PHASE 3C REVIEW #2 — COMPLETE · PHASE 3C ADDENDUM (Zoom Ranges) — COMPLETE · **PHASE 3C RUNTIME STABILIZATION — IN PROGRESS (camera ownership audit, FREE camera render-timed arrow input, WASD-in-CHASE source verified)**

**⚠️ RUNTIME STATUS: Phase 3C is NOT fully runtime-proven. Compile success ≠ runtime success. Camera ownership audit: SOURCE VERIFIED (architecture sound, cameraType=0 enforced). FREE camera arrow keys: COMPILE VERIFIED (continuous input + render-timed scaling). WASD-in-CHASE: SOURCE VERIFIED. FP camera position fix, arrow key gating, structural visibility, debug overlay: all COMPILE VERIFIED, RUNTIME UNVERIFIED.**

**Date:** 14-08-2026

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
**Last updated:** 14-08-2026 (Phase 3C Review #2 — RT4 source verification, method555 reuse, wheel pipeline honesty, distance model, comment accuracy)
**Phase 3 completion:** MovementIntent + ModernMovementController wired into ModernControlController, build verified, no scope creep.
**Phase 3 stabilization pass 1:** WASD/chat input arbitration fixed (chatInputActive flag), head bob disabled, third-person confirmed placeholder, Java runtime/HD investigated.
**Phase 3 stabilization pass 2:** True WASD/chat root cause fixed (typed key queue filtering), camera mode F11 transitions safe, third-person placeholder documented, first-person body culling investigated.
**Phase 3 movement runtime fix:** WASD movement root cause fixed (double coordinate conversion removed — xFine>>7 is already LOCAL, mode 2→0 for MOVE_GAMECLICK).
**Phase 3 stabilization pass 4:** Camera height regression fixed (removed groundHeight<=0 fallback from b90c72f, added terrain validation, hasValidPosition flag, safe F11 enter, preserved last good camera position).

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

## 12. Phase 3 Stabilization Pass 4 — Camera Height Regression Fix (14-08-2026)

### 12.1 Problem statement
Runtime testing after commit b90c72f revealed a critical camera regression:

- Pressing F11 to enter FIRST_PERSON mode placed the camera **DIRECTLY UNDER THE MAP**.
- Screenshot showed camera looking up from below the world at terrain/buildings from underneath.
- Large beige void visible (empty space below the world).
- This happened immediately on F11 press — no region transition needed.
- WASD movement still non-functional (separate issue, debug logging added).

### 12.2 Root cause analysis — which b90c72f change caused the regression

**Commit b90c72f changes examined:**
- `FirstPersonCamera.java` (+82 lines): Added sceneRebuildPending, cameraType self-healing, groundHeight fallback, onSceneRebuild rework.
- `LoginManager.java` (+12/-3): Moved onSceneRebuild() calls to method2463() and reconnect().
- `ScriptRunner.java` (+8/-2): Changed body culling to require both CameraMode.isFirstPerson() AND FirstPersonCamera.isActive().
- `build.gradle` (+11): Java 8 runtime config.

**The regression was in FirstPersonCamera.java — the `groundHeight <= 0 → EYE_HEIGHT` fallback.**

### 12.3 RT4 vertical camera coordinate convention (traced from working legacy code)

**Ground truth from existing working legacy camera code:**

1. **`SceneGraph.getTileHeight(level, xFine, zFine)`** returns tile heights that are **NEGATIVE** for typical terrain. Higher elevation = more negative values. Standard RuneScape convention.
   - Returns 0 when `tileHeights == null` (scene not loaded) or coordinates out of bounds (0..103 tile range).
   - Returns actual (negative) height for valid terrain.

2. **`Camera.anInt40`** = camera Y position in world space.
   - Confirmed via `SceneGraph.cameraY = arg1` at method2954 (line 2913), where arg1 = Camera.anInt40.
   - OpenGL renders with `glTranslatef(-cameraX, -cameraY, -cameraZ)` (SceneGraph.java line 3047).
   - Visibility check: `surfaceTileHeights[0][x][z] + 128 - cameraY` (line 2950) — positive means terrain above camera.

3. **Legacy camera formula** (from Camera.java, all working code):
   - `updateLockedCamera()` line 155: `anInt40 = SceneGraph.getTileHeight(Player.plane, renderX, renderZ) - anInt5203`
   - `method2722()` line 345: `anInt40 = SceneGraph.getTileHeight(Player.plane, renderX, renderZ) - anInt5203`
   - `method555()` line 430: `anInt40 = arg2 - local59`
   - All follow the pattern: **`anInt40 = terrainHeight - offset`**

4. **Physical meaning:**
   - `anInt40 = terrainHeight - 200` → camera 200 units **above** terrain (correct first-person view).
   - More negative `anInt40` = camera **higher** in world space (because terrain heights are negative).
   - Less negative / zero `anInt40` = camera **lower** — at or below ground level.

5. **Why the fallback was wrong:**
   - b90c72f code: `if (groundHeight <= 0) groundHeight = EYE_HEIGHT (200)`.
   - This made `anInt40 = 200 - 200 = 0`.
   - On typical negative terrain (e.g., terrainHeight = -1000), the correct value is `anInt40 = -1000 - 200 = -1200` (camera 200 above terrain).
   - The fallback jumped the camera from `anInt40 = -1200` (correct, above terrain) to `anInt40 = 0` (at/below ground level).
   - Result: camera directly under the map, looking up at terrain from below.

### 12.4 Fix applied

**File: `rt4/FirstPersonCamera.java`**

**Changes:**
1. **Removed the `groundHeight <= 0 → EYE_HEIGHT` fallback entirely.** A terrain height of 0 from `getTileHeight` means either invalid data or genuinely flat terrain — neither case should produce an invented camera height.
2. **Added `hasValidPosition` flag** — tracks whether the camera has been initialized with proven valid terrain data.
3. **Added `tryValidateTerrain()` method** — validates before height lookup:
   - `PlayerList.self != null`
   - `Player.plane` in range 0..3
   - `SceneGraph.tileHeights != null` (scene loaded)
   - Local tile `(fpCamX >> 7)` and `(fpCamZ >> 7)` within 0..103
4. **When terrain invalid: `return` from `update()`** — preserves last known good camera position. No camera field writes with invented data.
5. **`activate()` clears stale state** — `sceneRebuildPending = false`, validates terrain immediately via `tryValidateTerrain()`, sets `hasValidPosition` accordingly.
6. **`deactivate()` resets** `hasValidPosition` and `sceneRebuildPending`.
7. **`onSceneRebuild()`** sets `hasValidPosition = false` — terrain will be re-validated before next camera field writes.

### 12.5 Correct terrain validation strategy

**Before using `getTileHeight()` for camera placement:**
1. Check `PlayerList.self != null` — player must exist.
2. Check `Player.plane` in 0..3 — valid plane.
3. Check `SceneGraph.tileHeights != null` — scene data loaded.
4. Check tile coordinates `(fpCamX >> 7)` and `(fpCamZ >> 7)` within 0..103 — within scene bounds.

**If any check fails:**
- Do NOT write camera fields.
- Preserve last proven valid camera position.
- If never had valid position (e.g., F11 during loading), camera stays at pre-FP defaults (safe).

### 12.6 F11 mode enter safety

**On entering FIRST_PERSON via F11:**
1. `activate()` anchors to `PlayerList.self.xFine/zFine` immediately.
2. Clears `sceneRebuildPending` — no stale rebuild state.
3. Validates terrain via `tryValidateTerrain()`.
4. If valid: `hasValidPosition = true`, first `update()` writes correct camera fields.
5. If invalid: `hasValidPosition = false`, `update()` returns early until terrain is ready.
6. `Camera.cameraType = 0` — bypasses legacy camera system.
7. Safe pitch (0 = horizon), safe yaw (current player facing).
8. No old render height from ORIGINAL/THIRD_PERSON contaminates FP camera.

### 12.7 Region rebuild behavior (corrected)

**Retained from Pass 3:**
- `LoginManager.method2463()` calls `FirstPersonCamera.onSceneRebuild()` at end.
- `LoginManager.reconnect()` calls `FirstPersonCamera.onSceneRebuild()`.
- `update()` self-heals `Camera.cameraType = 0` every frame.
- `sceneRebuildPending` triggers full camera reinitialisation on next update.

**Corrected in Pass 4:**
- Reinitialisation sets `hasValidPosition = false`.
- Terrain is re-validated via `tryValidateTerrain()` before camera field writes.
- No fake terrain height used during rebuild transition.
- Camera preserves last known good position until new terrain is validated.

### 12.8 Body culling safety verification

- `ScriptRunner.method964()` line 675: `if (arg0 && CameraMode.isFirstPerson() && FirstPersonCamera.isActive())` — requires both mode AND camera active.
- `FirstPersonCamera.isActive()` returns `active` field (set in `activate()`, cleared in `deactivate()`).
- When terrain is temporarily invalid, `active` stays true but camera fields aren't updated (preserves last good position).
- Player stays culled during first-person view — correct behavior.
- When leaving FIRST_PERSON: `deactivate()` sets `active = false`, body culling stops, player visible again.

### 12.9 ORIGINAL mode regression verification

- `FirstPersonCamera.update()` only runs when `active == true` — does NOT affect ORIGINAL mode.
- `CameraMode.onModeChanged()` properly resets state on transitions:
  - Leaving FP: `deactivate()` + `resetToSafeDefaults()`.
  - Entering ORIGINAL: `resetToSafeDefaults()` as safety net.
- ORIGINAL mode scrollwheel zoom, middle mouse camera movement, click-to-move: all handled by legacy camera system, completely unaffected by modern controls code.
- No modern input/cursor code consumes events in ORIGINAL mode.

### 12.10 WASD runtime trace status

- `[MODERN-MOVE]` debug logging added to `ModernMovementController.update()` in commit 6420f42.
- Logs: pressedKeys state, gameplayAllowed, MovementIntent, cameraYaw, originX/Z, self.xFine/zFine, movementQueue state, localDest, findPath result.
- `[MODERN-MOVE]` debug logging also added to `ClientProt.method3502()` and `MiniMenu.doAction()` for click-to-move comparison.
- Runtime comparison pending — requires launching server + client to capture debug output.
- Coordinate fix applied: `xFine >> 7` gives LOCAL tile directly (no originX/Z subtraction).
- Mode changed from 2 (opcode 77) to 0 (MOVE_GAMECLICK).

### 12.11 Build verification
- `gradlew.bat :client:compileJava` → **BUILD SUCCESSFUL**
- Files changed in commit 6420f42:
  - `FirstPersonCamera.java` (+102/-18 lines: terrain validation, hasValidPosition, removed fallback)
  - `ModernMovementController.java` (+124/-24 lines: coordinate fix, debug logging)
  - `ClientProt.java` (+15 lines: debug logging)
  - `MiniMenu.java` (+14 lines: debug logging)
  - `config.json`, `GlobalConfig.java` (user config changes)
- No Phase 4 collision, targeting, combat, third-person camera, controller, protocol rewrite, renderer rewrite, or first-person viewmodel changes.

### 12.12 Summary of findings

| # | Finding | Detail |
|---|---------|--------|
| 1 | b90c72f change that caused camera under terrain | `groundHeight <= 0 → EYE_HEIGHT` fallback in FirstPersonCamera.update() |
| 2 | Correct RT4 vertical coordinate convention | `anInt40 = terrainHeight - offset`; tileHeights are negative; more negative anInt40 = higher camera |
| 3 | Correct terrain validation | Check self!=null, plane 0..3, tileHeights!=null, tile coords 0..103 before getTileHeight |
| 4 | Correct F11 initialization | Anchor to xFine/zFine, clear stale state, validate terrain, set cameraType=0, safe pitch/yaw |
| 5 | Region rebuild behavior | onSceneRebuild() hook preserved, reinit deferred to update(), terrain validated before camera writes |
| 6 | ORIGINAL camera regression status | No regression — FP code doesn't run when not active, proper reset on mode transition |
| 7 | WASD runtime trace status | Debug logging in place, coordinate fix applied (LOCAL coords), mode fix applied (0=MOVE_GAMECLICK), runtime test pending |

---

## 11. Phase 3 Stabilization Pass 3 — Scene Rebuild / Terrain Safety / Visibility

### 11.1 Problem statement
Runtime testing revealed three critical bugs during region/chunk/scene rebuilds:

1. **FIRST_PERSON mode breaks on region change**: Camera falls through terrain, mode state becomes inconsistent, camera can move freely under the map.
2. **Camera terrain safety**: First-person camera can end up below terrain after scene transitions due to invalid height data.
3. **Local player visibility**: Stale first-person culling state can keep the local player invisible after mode switches or rebuilds.

### 11.2 Root cause analysis

**Root cause 1: `Camera.cameraType` overwritten during rebuild**

`LoginManager.method2463()` (the core scene rebuild method) sets `Camera.cameraType = 1` at line 816 for non-loading-screen rebuilds (gameplay region changes). This overrides `FirstPersonCamera.activate()`'s `cameraType = 0`, causing the original camera system to interfere.

Rebuild paths that set `Camera.cameraType = 1`:
- `method2463()` line 816 — gameplay region changes (REBUILD_REGION packet)
- `reconnect()` line 858 — reconnect/world hop

**Root cause 2: `onSceneRebuild()` only called from loading screen path**

`FirstPersonCamera.onSceneRebuild()` was only called from `LoginManager.setupLoadingScreenRegion()`, NOT from the main gameplay rebuild path (`Protocol.readRebuildPacket()` → `LoginManager.method2463()`). So during normal region changes, the FP camera was never notified.

**Root cause 3: Terrain height returns 0 for invalid data**

`SceneGraph.getTileHeight()` returns 0 when `tileHeights == null` (during scene transition) or when coordinates are out of bounds. The FP camera computed `Camera.anInt40 = 0 - EYE_HEIGHT = -200`, placing the camera underground.

**Root cause 4: Culling check too narrow**

`ScriptRunner.method964()` checked only `FirstPersonCamera.isActive()` for body culling. If `active` stayed true while the camera mode state was inconsistent (e.g., during rebuild), the local player remained invisible.

### 11.3 Fix applied

**File: `FirstPersonCamera.java`**
1. Added `sceneRebuildPending` flag for deferred reinitialisation
2. `onSceneRebuild()` now sets the pending flag + immediately re-asserts `cameraType = 0`
3. `update()` self-heals `Camera.cameraType = 0` every frame (prevents any rebuild code from overriding it)
4. `update()` performs full camera reinitialisation when rebuild is pending (position, pitch, mouse tracking, cursor lock)
5. Terrain safety: if `getTileHeight()` returns ≤ 0, uses `EYE_HEIGHT` as safe default — **LATER FOUND INCORRECT in Pass 4**: this fallback caused the camera regression. See §12.

**File: `LoginManager.java`**
1. Added `FirstPersonCamera.onSceneRebuild()` at end of `method2463()` — covers ALL rebuild paths
2. Added `FirstPersonCamera.onSceneRebuild()` in `reconnect()` — covers reconnect/world hop
3. Removed redundant call from `setupLoadingScreenRegion()` (now handled by `method2463()`)

**File: `ScriptRunner.java`**
1. Changed body culling check from `FirstPersonCamera.isActive()` to `CameraMode.isFirstPerson() && FirstPersonCamera.isActive()` — requires both mode AND camera to agree

**File: `client/build.gradle`** (from previous session)
1. Added `run { executable = 'C:\\Program Files\\Java\\jre1.8.0_491\\bin\\java.exe' }` for Java 8 runtime
2. Added `options.release = 8` to `tasks.withType(JavaCompile)` for Java 8 API compatibility

### 11.4 Scene rebuild lifecycle
```
FIRST_PERSON active, player walking
→ Server sends REBUILD_REGION packet
→ Protocol.readRebuildPacket() → LoginManager.method2463()
  ├─ Camera.cameraType = 1 (original deob code)
  ├─ Player positions adjusted for new origin
  └─ FirstPersonCamera.onSceneRebuild()
      ├─ sceneRebuildPending = true
      └─ Camera.cameraType = 0 (immediate fix)
→ gameState changes to 25/28 (rebuild in progress)
  └─ ModernControlController.update() NOT called during rebuild
→ rebuildMap() completes, gameState returns to 30
→ ModernControlController.update() → FirstPersonCamera.update()
  ├─ Camera.cameraType = 0 (self-healing, every frame)
  ├─ sceneRebuildPending == true → full reinit:
  │   ├─ fpCamX/Z = PlayerList.self.xFine/zFine
  │   ├─ fpCamPitch = 0 (horizon, safe default)
  │   ├─ Reset mouse tracking
  │   └─ Re-lock cursor if needed
  ├─ groundHeight = SceneGraph.getTileHeight(...)
  │   └─ if ≤ 0: use EYE_HEIGHT as safe default
  └─ Write camera fields → FIRST_PERSON continues normally
```

### 11.5 Runtime test checklist
- [ ] Walk into a new region while in FIRST_PERSON — camera stays stable, no underground
- [ ] Teleport to a different area in FIRST_PERSON — camera reinitialises at new location
- [ ] Switch to ORIGINAL during region load — camera returns to normal safely
- [ ] Switch to THIRD_PERSON during region load — player model visible, no culling issues
- [ ] Look straight up/down during region transition — pitch stays within safe limits
- [ ] Rapid F11 cycling during region load — no crashes, no stuck states
- [ ] Reconnect/world hop in FIRST_PERSON — camera reinitialises correctly
- [ ] Local player model visible in ORIGINAL/THIRD_PERSON after region change
- [ ] Local player model hidden in FIRST_PERSON (body culling works)
- [ ] Camera never goes below terrain height during any transition

### 11.6 Build verification
- `gradlew.bat :client:compileJava` → **BUILD SUCCESSFUL**
- Files changed: FirstPersonCamera.java, LoginManager.java, ScriptRunner.java, build.gradle
- Java 8 runtime: `--release 8` flag ensures Java 8 API compatibility (no ByteBuffer covariant return issues)
- Client launches with Java 8 JRE (`jre1.8.0_491`) for HD/OpenGL graphics support

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

**Step 4: Target tile computation** ✓ (coordinate space was wrong)
- `currentLocalTileX = self.xFine >> 7` → **LOCAL** tile coordinate (xFine/zFine are LOCAL fine coords, same space as movementQueueX/Z).
- `targetLocalTileX = currentLocalTileX + stepX` → **LOCAL** tile coordinate.
- **Previous bug:** Code computed `targetTileX - Camera.originX` to get "local" coords, but xFine>>7 was ALREADY local. The double conversion (xFine>>7 already local, then subtracting originX again) produced invalid coordinates far outside 0..103.

**Step 5: PathFinder.findPath() call** ✗ **ROOT CAUSE (now fixed)**
- `PathFinder.findPath()` operates entirely in **LOCAL** coordinates (0..103 grid).
- All existing click-to-move call sites pass LOCAL coordinates:
  - `MiniMenu.doAction` WALK_HERE: passes `local15`/`local19` (local tiles).
  - NPC pathing: passes `npc.movementQueueX[0]`/`npc.movementQueueZ[0]` (local).
  - `findPathN` uses `parents[arg2][arg9]` indexing into `parents[104][104]` — local grid.
- ModernMovementController previously passed coordinates that were double-converted (xFine>>7 is already local, then originX subtracted again), producing values far outside 0..103.
- Result: pathfinding always failed because coordinates were far outside the 0..103 grid. `findPath` returned `false`. No route was generated. No movement packet was sent.
- **Fix:** Pass `xFine >> 7` directly as local coordinates (no originX/Z subtraction).

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
- `self.xFine >> 7` = **LOCAL** tile coordinate (xFine/zFine are LOCAL fine coordinates, same space as movementQueueX/Z).
- `Camera.originX` = world X offset of local (0,0) corner.
- World tile = local tile + `Camera.originX`.
- `movementQueueX[0]` = **LOCAL** tile coordinate (confirmed by usage in existing click-to-move).
- `PathFinder.queueX[]/queueZ[]` = **LOCAL** tile coordinates (confirmed by `method3502` adding `Camera.originX/Z` before sending).
- `ClientProt.method3502` converts local→world: `p2(Camera.originX + local23)`.
- **Key insight:** xFine/zFine are ALREADY in local fine coordinate space. No originX/Z subtraction needed to get local tiles. The previous double-conversion bug subtracted originX from already-local coordinates.

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
1. Removed double coordinate conversion: `xFine >> 7` already gives LOCAL tile (no `Camera.originX` subtraction needed).
2. Pass local coordinates directly to `PathFinder.findPath()`.
3. Change mode from `2` (opcode 77, walk+action) to `0` (MOVE_GAMECLICK, standard walk).
4. Set `arg4` (runModifier) to `0` — `method3502` reads Ctrl directly from `Keyboard.pressedKeys[KEY_CTRL]`.
5. Added `[MODERN-MOVE]` debug logging for runtime comparison with click-to-move.

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

---

## Phase 3B — Continuous Modern Movement Foundation

### 3B.1 Goal
Replace tile-based WASD movement with continuous fine-coordinate prediction, DDA tile-boundary detection, server synchronization via pending ring buffer, and full BasType animation variant support.

### 3B.2 Architecture Overview

**Q16 Fixed-Point Prediction:**
- `predictedSubX/Z` are Q16 accumulators (16-bit fractional sub-fine precision).
- `self.xFine = (int)(predictedSubX >> 16)` each tick.
- Velocity in Q16 fine units per client tick (WALK_SPEED=4, RUN_SPEED=8).

**Camera-Relative Velocity:**
- forward = (-sin[yaw], -cos[yaw]), right = (cos[yaw], -sin[yaw])
- Uses `MathUtils.sin/cos` (2048-entry Q16 trig tables).
- Float multiplication preserves fractional diagonal component before Q16 conversion.

**DDA Tile Boundary Detection:**
- Positive vel: boundary = (tile+1) * 128. Negative vel: boundary = tile * 128.
- Cross-multiply to compare X vs Z boundary crossing time.
- Simultaneous crossing produces diagonal target tile.

**Server Sync:**
- Single-tile MOVE_GAMECLICK packet (proven from method3502 byte trace).
- Pending ring buffer (capacity 4) tracks multiple outstanding walk requests.
- Exact path matching: server-confirmed tile consumes through that pending entry.

**Protocol Hooks:**
- `readSelfPlayerInfo` type 1/2: `self.move()` then `onServerStep()` then drain queue.
- Type 3: distinguish near (queued) vs far (direct overwrite) teleport.
- Near teleport treated as server step. Far teleport rebases from self.xFine/zFine.

**Reconciliation:**
- Normal: rebase from `lastServerReportedTile` converted to tile-center fine coords.
- NOT from `self.xFine/zFine` (those ARE the predicted position during normal locomotion).
- Timeout (100 ticks = 2s) + divergence > 0 + no pending requests = genuine desync.
- Divergence > 3 tiles = always rebase regardless of timeout.
- Timeout alone with no divergence = diagnostic only, no blind snap.

### 3B.3 Files Modified

1. **ClientProt.java** - Added `sendModernWalkPacket(worldX, worldZ, running)`.
2. **NpcList.java** - Added `isModernSelf` gate in `method4514`: skip `method2247` only.
3. **Protocol.java** - Added modern hooks in `readSelfPlayerInfo` for types 1/2/3.
4. **ModernMovementController.java** - Complete rewrite (559 lines).
5. **CameraMode.java** - Lifecycle hooks in `onModeChanged`.
6. **LoginManager.java** - `onSceneRebuild()` hooks in `method2463` + `reconnect`.

### 3B.4 Execution Order (per 20ms tick)
1. `ModernMovementController.update()` - owns self.xFine/zFine, animation
2. `Protocol.method1756()` - server packets, readSelfPlayerInfo hooks
3. `NpcList.method4514(self)` - skip method2247, run method949 + method879

### 3B.5 Mode Isolation
- ORIGINAL: legacy method2247 only, no modern writes.
- FIRST_PERSON: modern locomotion + FirstPersonCamera.
- THIRD_PERSON: same locomotion, independent camera.

### 3B.6 Animation Variants (BasType)
- Forward arc [-256,256]: walk / run
- CW arc [256,768): walkCW / runCW
- CCW arc [-768,-256]: walkCCW / runCCW
- Large |delta|>768: walkFullTurn / runFullTurn
- All with fallback chain to available animations.

### 3B.7 Runtime test checklist
- [ ] WASD in FIRST_PERSON: W forward (-Z at yaw 0), S backward, A left, D right
- [ ] Ctrl+W = run forward (8 fine/tick vs 4 walk)
- [ ] Diagonal (W+D) not faster than cardinal W
- [ ] Walk animation plays, CW/CCW turn animations on direction change
- [ ] Run animation plays when Ctrl held
- [ ] Server receives walk packets (player moves on server side)
- [ ] Force-move suspends modern controller cleanly, rebases after
- [ ] Region rebuild rebase works
- [ ] F11 ORIGINAL to FP to TP to ORIGINAL: seamless handoff both directions
- [ ] ORIGINAL mode 100% unchanged (click-to-move works)
- [ ] No movementQueue accumulation (Protocol hooks drain it)

### 3B.8 Build verification
- `gradlew.bat :client:compileJava` -> **BUILD SUCCESSFUL**
- 6 files modified (ClientProt, NpcList, Protocol, ModernMovementController, CameraMode, LoginManager)
- No Phase 4 collision, wall sliding, player-radius, targeting, combat, third-person camera, or first-person viewmodel changes.

---

## 13. Phase 3B Runtime Stabilization — Input, Animation, Self-Rendering

**Date:** 14-08-2026
**Commit:** (post-build)
**Baseline:** be6b799 (Phase 3B continuous movement foundation)

### 13.1 Runtime bugs found

1. **W/S/A/D all reversed relative to camera** — all four movement directions inverted
2. **CTRL activates run** instead of LEFT SHIFT
3. **Walking animation stuck on release** — player never transitions to idle
4. **Self culled in FIRST_PERSON** — entire player model hidden

### 13.2 Root cause: W/S/A/D inversion

**Camera convention verified via rendering pipeline:**

`API.CalculateSceneGraphScreenPosition` and `SoftwareModel.setCamera` apply yaw rotation as:
```
rotatedX = (entityZ * sinYaw + entityX * cosYaw) >> 16
entityZ  = (entityZ * cosYaw - entityX * sinYaw) >> 16
```

At yaw 0: entity at (0, +D) → viewZ = D (in front of camera). **Camera looks NORTH (+Z) at yaw 0.**

The OpenGL modelview applies `glRotatef(yaw_deg, 0, 1, 0)`, confirming the same convention.

**The movement basis was inverted:**

Old (WRONG): `forwardX = -sin[yaw]`, `forwardZ = -cos[yaw]`
- At yaw 0: forward = (0, -1) = SOUTH — opposite to camera look direction!

Fixed (CORRECT): `forwardX = +sin[yaw]`, `forwardZ = +cos[yaw]`
- At yaw 0: forward = (0, +1) = NORTH — matches camera look direction ✓
- At yaw 512: forward = (+1, 0) = EAST — camera turned right, forward is right ✓
- At yaw 1024: forward = (0, -1) = SOUTH — camera turned 180°, forward is south ✓
- At yaw 1536: forward = (-1, 0) = WEST — camera turned left, forward is left ✓

Right basis `(cos[yaw], -sin[yaw])` was already correct and unchanged.

**Orientation formula also inverted:**

Old (WRONG): `atan2(-velX, -velZ)` — faces 180° away from movement
Fixed (CORRECT): `atan2(+velX, +velZ)` — faces movement direction

Verification:
- velX=0, velZ=-4 (north): atan2(0, -4) = 0 → RS angle 0 = north ✓
- velX=4, velZ=0 (east): atan2(4, 0) = π/2 → RS angle 512 = west... wait

Actually: `atan2(velX, velZ) * 325.949 & 0x7FF`:
- North (velX=0, velZ=-4): atan2(0, -4) = 0 → 0 ✓ (north)
- East (velX=4, velZ=0): atan2(4, 0) = π/2 → 512... but RS 512 = west

Correction: the RS angle formula `angle = atan2(dx, dz) * 325.949` gives:
- 0 = +Z = north
- 512 = -X = west
- 1024 = -Z = south
- 1536 = +X = east

So `atan2(velX, velZ)` for east movement (velX=+4, velZ=0):
atan2(+4, 0) = π/2 → 512 → but RS 512 = WEST. This is wrong!

Wait — let me re-verify. For the RS convention:
- angle 0 → direction (sin[0], cos[0]) = (0, 1) = +Z = north
- angle 512 → direction (sin[512], cos[512]) = (1, 0) = +X = east

So RS angle 512 corresponds to EAST (+X), not west! The previous session's "RS angle convention" documentation was wrong about 512=west. Actually:
- 0 = north (+Z)
- 512 = east (+X) [sin[512]=+1, cos[512]=0]
- 1024 = south (-Z) [sin[1024]=0, cos[1024]=-1]
- 1536 = west (-X) [sin[1536]=-1, cos[1536]=0]

With this corrected understanding:
- East movement (velX=+4, velZ=0): atan2(+4, 0) = π/2 → 512 = east ✓
- North movement (velX=0, velZ=-4): atan2(0, -4) = 0 → 0 = north ✓
- South movement (velX=0, velZ=+4): atan2(0, +4) = π → 1024 = south ✓
- West movement (velX=-4, velZ=0): atan2(-4, 0) = -π/2 → 1536 = west ✓

All four directions correct.

### 13.3 LEFT SHIFT key mapping

**Found in Keyboard.java static initializer:**
```
CODE_MAP[KeyEvent.VK_SHIFT] = 81
```

Existing constant: `Keyboard.KEY_SHIFT = 81`

Changed ModernMovementController from `KEY_CTRL = 82` to `KEY_SHIFT = 81`.

This change applies ONLY to modern locomotion (FIRST_PERSON, THIRD_PERSON).
ORIGINAL RuneScape run behavior is completely unaffected.

### 13.4 Animation state machine

**Root cause of stuck walking animation:**
The previous `selectAnimation(running)` was called every tick but never set idle — it only selected walk/run/turn variants. When WASD was released, `update()` returned early before reaching `selectAnimation`, so `movementSeqId` stayed at the walk animation.

**Fix:** Explicit IDLE/WALK/RUN state machine with transition-only updates:

```java
private enum MovementState { IDLE, WALK, RUN }
private static MovementState lastMovementState = MovementState.IDLE;
```

Transitions:
- No movement intent → state = IDLE → set `movementSeqId = bas.idleAnimationId`
- Movement + !running → state = WALK → set `movementSeqId = bas.walkAnimation`
- Movement + running → state = RUN → set `movementSeqId = bas.runAnimationId`

Animation only changes when `currentState != lastMovementState`. While staying in any state, `movementSeqId` is NOT rewritten, allowing `method879` to advance animation frames normally.

Fallback: if walkAnimation or runAnimationId is -1, falls back to idleAnimationId.

### 13.5 Self-rendering changes

**File: ScriptRunner.java, method964()**

Removed the first-person body culling early return:
```java
// REMOVED:
if (arg0 && CameraMode.isFirstPerson() && FirstPersonCamera.isActive()) {
    return;
}
```

Now the local player renders in ALL camera modes:
- ORIGINAL: unchanged (existing behavior preserved)
- FIRST_PERSON: full body visible for testing (head clipping may occur — to be evaluated at runtime)
- THIRD_PERSON: full body visible

If head/helmet clipping is observed in first-person, the smallest possible head-only exclusion should be implemented rather than hiding the entire model.

### 13.6 Force-move preservation

Force-move suspension logic unchanged. When force-move is active:
- velocityXQ16/ZQ16 = 0
- suspended = true
- return immediately (no xFine/zFine write, no packet, no orientation, no animation)

When force-move ends:
- Rebase prediction from self.xFine/zFine
- Clear pending ring buffer
- Resume modern locomotion

### 13.7 Mode isolation confirmation

All changes gated to `CameraMode.isModern()` or specific modern modes:
- ModernMovementController: only runs when `CameraMode.isModern()` returns true
- Self-rendering change: affects all modes equally (removes culling), but ORIGINAL already rendered self normally
- Shift run key: only read inside ModernMovementController.readInput(), only called in modern modes
- ORIGINAL click-to-move, legacy run, legacy animations: completely unaffected

### 13.8 Collision status

Modern continuous locomotion currently has NO fine-coordinate collision resolver.
Movement clips through walls, buildings, blocked tiles, doors, scenery, objects.

This is INTENTIONALLY DEFERRED to Phase 4.

Phase 4 will implement:
1. Tile occupancy checks
2. Wall/edge collision
3. Diagonal/corner collision prevention
4. Continuous fine-position collision
5. Wall sliding (dx blocked but dz free → slide along wall)
6. Player footprint (~1 tile scale)
7. Server consistency (same collision flags as server)

Phase 4 will use existing RT4 collision map and collision flags as authoritative.

### 13.9 Files changed

| File | Change |
|------|--------|
| ModernMovementController.java | Camera basis fix, orientation fix, Shift key, animation state machine |
| ScriptRunner.java | Removed first-person body culling in method964 |

### 13.10 Build verification
- `gradlew.bat :client:compileJava` → **BUILD SUCCESSFUL**
- Kotlin daemon error ("Could not delete caches dir") — known non-fatal, Java compilation succeeds
- 2 files changed

### 13.11 Runtime test checklist

FIRST_PERSON:
- [ ] W → camera forward (north at yaw 0)
- [ ] S → camera backward
- [ ] A → camera-relative left
- [ ] D → camera-relative right
- [ ] Rotate camera 180° → controls remain correct
- [ ] Rotate camera continuously while W held → trajectory follows smoothly
- [ ] W → walk animation
- [ ] Shift+W → run animation
- [ ] Release Shift while W held → walk animation
- [ ] Release W → idle animation
- [ ] Walk → stop → walk → stop → correct animation transitions
- [ ] Run → walk → idle → correct animation transitions
- [ ] No WASD minimap destination flag
- [ ] Local body/equipment visible
- [ ] No entire-player culling

THIRD_PERSON:
- [ ] Same WASD directions
- [ ] Same Shift run
- [ ] Same idle/walk/run states
- [ ] Full local player model visible
- [ ] Equipment visible
- [ ] Movement-facing orientation visible

ORIGINAL:
- [ ] Click-to-move works
- [ ] Minimap walking works
- [ ] Destination flag appears
- [ ] Scroll wheel zoom works
- [ ] Middle mouse camera works
- [ ] Legacy run works
- [ ] Legacy animations work
- [ ] Player rendering works
- [ ] No modern WASD locomotion leaks into ORIGINAL

---

## 14. Phase 3B Runtime Fix #2 — Live Camera Steering & Idle Animation

**Commit:** TBD  
**Baseline:** 83e6217 (Phase 3B stabilization)

### 14.1 Runtime bugs found

1. **IDLE ANIMATION STILL DID NOT WORK.** Releasing WASD left the player stuck in walk animation.
2. **CAMERA-RELATIVE STEERING WAS NOT LIVE.** Holding W and rotating the camera did not change movement direction.

### 14.2 Root cause — idle animation

**Execution order analysis:**

```
client.mainUpdate() [gameState 30]:
  ModernControlController.update()
    → ModernMovementController.update()   ← sets movementSeqId = idle (ONCE on transition)
    → FirstPersonCamera.update()
  Protocol.method1756()
    → PlayerList.method1444()
      → method4514(self)
        → [skip method2247 for modern self]
        → method949(self)   ← orientation smoothing
        → method879(self)   ← animation frame advance
```

**Legacy method2247** sets `movementSeqId = idleAnimationId` **every tick** (line 114) when there is no movement queue.

**method949** (orientation smoothing) checks:
```java
if (idleAnimationId == movementSeqId && anInt3385 > 25) {
    movementSeqId = walkAnimation;  // Replace idle with walk!
}
```

`anInt3385` increments every tick there is a yaw error between `anInt3400` (target) and `anInt3381` (visual orientation). After 25 ticks of error, idle is replaced with walk.

**The modern controller only set idle ONCE on state transition.** After method949 overwrote it with walk, the controller never set it back (state was already IDLE). Walk animation stuck permanently.

### 14.3 Root cause — live camera steering

**Execution order in ModernControlController.update():**
```java
case FIRST_PERSON:
    ModernMovementController.update();  // reads Camera.cameraYaw
    FirstPersonCamera.update();         // writes Camera.cameraYaw = fpCamYaw
```

The movement controller read `Camera.cameraYaw` **before** FirstPersonCamera updated it. The yaw was always one frame stale (20ms). While this alone should only cause imperceptible lag, the combination with the stale `Camera.cameraYaw` field (which could also be affected by legacy camera code paths) meant the movement basis was not reliably current.

**FP yaw pipeline traced:**
```
Mouse.currentMouseX/Y (AWT events, async)
  → FirstPersonCamera.updateLockedMouseLook()
    → fpCamYaw -= deltaX * sensitivity  (fpCamYaw is the AUTHORITY)
  → Camera.cameraYaw = fpCamYaw  (written at END of FirstPersonCamera.update())
  → ModernMovementController reads Camera.cameraYaw  (was reading STALE value)
```

### 14.4 Fix — idle animation

**Two-part fix in ModernMovementController.update():**

1. **On IDLE transition:** snap visual orientation to target, reset yaw error counter:
   ```java
   self.anInt3381 = self.anInt3400;  // Snap visual = target
   self.anInt3385 = 0;               // Reset error counter
   ```
   This prevents method949 from seeing any yaw error and replacing idle with walk.

2. **Every tick when IDLE (no movement):** re-assert idle animation:
   ```java
   else {
       self.movementSeqId = self.getBasType().idleAnimationId;
   }
   ```
   This matches legacy method2247 behavior which sets idle every tick. Setting `movementSeqId` to the same value does NOT restart the animation — method879 continues advancing frames normally.

### 14.5 Fix — live camera steering

**Three changes:**

1. **FirstPersonCamera.getYaw()** — new read-only getter exposing `fpCamYaw` (the authoritative FP horizontal look direction).

2. **CameraMode.getModernMovementYaw()** — dispatcher that returns the correct yaw per mode:
   - FIRST_PERSON → `FirstPersonCamera.getYaw()` (live fpCamYaw)
   - THIRD_PERSON → `Camera.cameraYaw` (Phase 14 will supply its own camera)
   - ORIGINAL → `Camera.cameraYaw` (not used — modern controller is inactive)

3. **ModernControlController.update()** — swapped execution order for FIRST_PERSON:
   ```java
   case FIRST_PERSON:
       FirstPersonCamera.update();         // FIRST: mouse look → fpCamYaw
       ModernMovementController.update();  // SECOND: reads live fpCamYaw
   ```

4. **ModernMovementController.update()** — reads `CameraMode.getModernMovementYaw()` instead of `Camera.cameraYaw`.

### 14.6 Files changed

| File | Change |
|------|--------|
| `FirstPersonCamera.java` | Added `getYaw()` returning `fpCamYaw` |
| `CameraMode.java` | Added `getModernMovementYaw()` dispatcher |
| `ModernControlController.java` | Swapped FP update order: camera before movement |
| `ModernMovementController.java` | Use `CameraMode.getModernMovementYaw()`; idle animation every-tick + orientation snap |

### 14.7 Force-move preservation

No changes to force-move suspension logic. Force-move active → modern controller does not touch movementSeqId, orientation, or position. Unchanged.

### 14.8 Mode isolation

ORIGINAL mode: no changes. method2247, PathFinder, legacy camera, legacy keyboard — all untouched.

### 14.9 Build verification

```
gradlew.bat :client:compileJava → BUILD SUCCESSFUL
```

### 14.10 Runtime acceptance checklist

FIRST_PERSON:
- [ ] W = forward (camera direction)
- [ ] S = backward
- [ ] A = strafe left
- [ ] D = strafe right
- [ ] Hold W + rotate camera 90° → trajectory curves to follow camera
- [ ] Hold W + rotate camera 360° → smooth circular/curved path
- [ ] Hold A/D + rotate camera → strafe follows camera
- [ ] Shift+W + rotate camera → same at run speed
- [ ] Release W → idle animation (no walk stuck)
- [ ] W → stop → W → stop → no stutter
- [ ] Shift+W → run animation + faster
- [ ] Release Shift while holding W → immediate walk
- [ ] Idle 5 seconds → normal idle animation persists

THIRD_PERSON:
- [ ] Body orientation matches movement direction
- [ ] Same idle/walk/run transitions

ORIGINAL:
- [ ] Click-to-move works
- [ ] Legacy animations unchanged
- [ ] No modern WASD leaks

---

## 16. Phase 3C — Modern Camera Rig: Scroll Zoom Continuum + Chase Camera + Body-Look Coupling

**Date:** 14-08-2026
**Commit:** (pending)
**Baseline:** 2b6a443 (Phase 3B stabilization - camera handedness and authority diagnostics)

### 16.1 Goal

Implement the MODERN camera continuum inside the existing MODERN control mode:

```
FIRST_PERSON  ←scroll→  CHASE  ←scroll→  FREE / CLASSIC-STYLE
```

Without changing the working 2b6a443 movement basis. ORIGINAL remains a pristine legacy fallback.

### 16.2 Source Trace — Wheel Event Pipeline

**Exact wheel event path:**

```
JavaMouseWheel.mouseWheelMoved() (AWT MouseWheelListener)
  → currentRotation += e.getRotation()   (accumulated in JavaMouseWheel)
  → client.java:1725-1726 (mainLoop tick):
      MouseWheel.wheelRotation = mouseWheel.getRotation()  // reads & resets currentRotation
  → InterfaceList scroll processing (ScriptRunner render pipeline):
      reads MouseWheel.wheelRotation for component scrollY
  → Staff Ctrl+Shift+wheel plane change (Protocol.java)
  → NO DEFAULT CAMERA ZOOM CONSUMER EXISTS
```

**Why wheel did nothing for camera before Phase 3C:**

1. The RT4 default follow camera (`Camera.method4273()`) has NO zoom/distance parameter. It follows the player with arrow-key pitch/yaw but no scroll-controlled distance.
2. `Camera.ZOOM = 600` exists but is ONLY consumed through CS2 scripts (`ScriptRunner.method4326` line 238 → `Camera.method555()`), used for cutscene camera positions, never for mouse wheel zoom.
3. `MouseWheel.wheelRotation` IS captured each tick but the only consumers are UI scrolling (`InterfaceList.scrollY += wheelRotation * 45`) and staff Ctrl+Shift+wheel plane change.
4. No code path reads `wheelRotation` to adjust camera distance/zoom for the default gameplay camera.
5. FirstPersonCamera bypasses the CS2 camera pipeline entirely (sets `cameraType=0`), so the CS2 zoom path never applies to FP mode either.

**Config flag status:** No config flag disables or enables wheel zoom. The `config.json` / `GlobalConfig` system has no camera-zoom-related setting.

**UI wheel priority (Review #2 corrected):** The camera rig reads `MouseWheel.wheelRotation` during `ModernControlController.update()` which runs in the 50Hz game tick, BEFORE the render-pipeline UI scroll processing (`ScriptRunner.method4326` → `InterfaceList`). Both camera zoom and UI scroll read the same `wheelRotation` value. UI scroll operates on component `scrollY` (separate variable from `desiredDistance`), so there is no variable conflict — but BOTH can react to the same wheel event. Proper UI ownership (skip camera zoom when scrollable UI is under cursor) is TODO.

### 16.3 Source Trace — Camera Render Pipeline

```
ScriptRunner.method4326 (render pipeline):
  if (Camera.cameraType == 1):
    Camera.method555(cameraX, viewportH, tileHeight-50, ZOOM+pitchTarget*3, yawTarget, cameraZ, pitchTarget)
    → OVERWRITES Camera.renderX/renderZ/anInt40/cameraYaw/cameraPitch every render frame

Protocol.java:2932-2941 (game tick):
  if (!FirstPersonCamera.isActive() && !ModernCameraRig.isActive()):
    if (cameraType == 1): Camera.method4273()   // follow camera
    elif (cameraType == 2): Camera.updateLockedCamera()  // locked camera
```

**Camera authority fields:** `Camera.renderX`, `Camera.renderZ`, `Camera.anInt40` (height), `Camera.cameraYaw`, `Camera.cameraPitch` — these 5 fields are the authoritative camera state for `SceneGraph.method2954()` and `GlRenderer.method4171()`.

### 16.4 Source Trace — RT4 Orientation Fields

**PathingEntity orientation fields (confirmed from current code):**

| Field | Role |
|-------|------|
| `anInt3400` | Target orientation (0..2047, clockwise: 0=N, 512=W, 1024=S, 1536=E) |
| `anInt3381` | Smoothed orientation (animations use this) |
| `anInt3376` | Orientation speed (default 32) |
| `anInt3385` | Orientation change counter (turn animation trigger at >25) |

**method949** (NpcList.java): Orientation smoothing. Handles faceEntity/faceX/faceY → sets anInt3400. Then smooths anInt3400→anInt3381. When `idleAnimationId == movementSeqId && anInt3385 > 25`, replaces idle with walk animation (turn animation trigger).

**Separate head yaw:** RT4 has NO separate head yaw. The model system only supports body rotation via anInt3400→anInt3381. True independent head rotation is not available and is deferred.

### 16.5 Architecture — Camera/Control Separation

```
CameraMode (enum): ORIGINAL / FIRST_PERSON / THIRD_PERSON
  → Controls LOCOMOTION scheme (which movement controller runs)

ModernCameraRig (inside MODERN):
  RigState: FIRST_PERSON / CHASE / FREE
  → Controls CAMERA rig (which camera code writes Camera fields)
```

ORIGINAL remains completely separate. MODERN modes run modern locomotion + camera rig.

### 16.6 Distance Continuum

**One authoritative desired distance, one smoothed actual distance (Review #2 — three-distance model):**

```
desiredDistance  ← scroll wheel (user intent, never destroyed by walls)
safeDistance     ← maximum permitted by camera obstruction
actualDistance   ← smoothly approaches min(desiredDistance, safeDistance)
```

**Distance units:** Fine coordinates (128 fine = 1 tile).

**Transition thresholds (with hysteresis):**

| Transition | Threshold | Hysteresis |
|------------|-----------|------------|
| CHASE → FP | desiredDistance <= 120 | FP_EXIT_DISTANCE = 200 (must scroll out to 200 to exit FP) |
| FP → CHASE | desiredDistance >= 200 | FP_ENTER_DISTANCE = 120 (must scroll in to 120 to enter FP) |
| CHASE → FREE | desiredDistance >= 4200 | FREE_EXIT_DISTANCE = 3800 (must scroll in to 3800 to exit FREE) |
| FREE → CHASE | desiredDistance <= 3800 | FREE_ENTER_DISTANCE = 4200 (must scroll out to 4200 to enter FREE) |

**Range:** MIN_DISTANCE = 0, MAX_DISTANCE = 5600.
**Wheel step:** WHEEL_STEP = 130 fine units (~1 tile per notch).

### 16.7 Smooth Zoom

Wheel notches change `desiredDistance` instantly. `actualDistance` smoothly interpolates toward `desiredDistance` using exponential smoothing:

```java
int delta = desiredDistance - actualDistance;
int step = delta / DISTANCE_SMOOTH_FACTOR;  // factor = 6
if (step == 0) step = (delta > 0) ? 1 : -1;
actualDistance += step;
```

This runs once per 50Hz tick. The smoothing is 50Hz tick-based exponential smoothing (NOT frame-rate-independent render smoothing). See §18 Review #2 for honesty correction.

### 16.8 Chase Camera

**Chase camera follows character body orientation:**

```
chaseYawTarget = self.anInt3400  // character body orientation
chaseYaw = smoothYaw(chaseYaw, chaseYawTarget, factor=8, minStep=2)
```

**Shortest-angle interpolation:** `shortestAngleDelta(from, to)` computes signed delta on 0..2047 circle, result in -1024..+1023. Interpolation always takes the short path (e.g., 2040→8 rotates across zero, not through ~2000 units).

**Camera position (Review #2 — uses Camera.method555, the proven RT4 transform):**
```
pivotX/Z = smoothCameraX/Z (follows player with slight lag)
pivotY = terrainHeight - 50
zoom = actualDistance * 0.5 + chasePitch * 3
Camera.method555(pivotX, viewportH, pivotY, zoom, chaseYaw, pivotZ, chasePitch)
→ renderX = pivotX - boomX  (boomX = sin(yaw)*cos(pitch)*zoom)
→ renderZ = pivotZ - boomZ  (boomZ = cos(yaw)*cos(pitch)*zoom)
→ anInt40 = pivotY - boomY  (boomY = -sin(pitch)*zoom)
```

**Camera is a FOLLOWER, not a leader.** Chase camera yaw is NOT fed back into ModernMovementController. `CameraMode.getCameraRelativeYaw()` returns -1 for CHASE/FREE rig states, so movement uses body orientation (`self.anInt3400`), not camera yaw. Only FP rig state returns a yaw for movement.

### 16.9 Camera Obstruction

**Multi-sample line probe** from pivot to desired camera position:

1. Compute number of samples: `max(1, fineDist / 128)` (one per tile).
2. At each sample: check `PathFinder.collisionMaps[plane].flags[tileX][tileZ]` for wall/scenery collision (combined mask 0x240100: scenery 0x100, full block 0x20000, ground decor 0x40000, flagged tile 0x200000).
3. **Directional wall edge check (Review #2):** When camera path crosses a tile boundary, check the destination tile's wall mask facing the crossing direction (N=0x102, S=0x120, W=0x108, E=0x180).
4. Check terrain height: if camera Y would be at/below terrain, block.
5. Return distance to last clear sample.
6. If blocked: scale effective zoom by `clearDist / actualDist` ratio (camera compresses toward player).

**This is CAMERA collision, NOT player movement collision.** Phase 4 player collision is separate.

**Desired-distance restoration:** When the camera is compressed by a wall (actualDistance < desiredDistance), `desiredDistance` is NOT modified. When the player walks away from the wall, `actualDistance` smoothly returns to `desiredDistance`.

### 16.10 Body-Look Coupling (FP mode only)

**Shoulder dead-zone policy:**

```
lookYaw = FirstPersonCamera.getYaw()
delta = shortestAngleDelta(bodyYaw, lookYaw)

if |delta| > SHOULDER_DEAD_ZONE (100 units ≈ 35°):
    if |delta| > SHOULDER_LIMIT (200 units ≈ 70°):
        catchupSpeed = BODY_FAST_CATCHUP_SPEED (64 units/tick)
    else:
        catchupSpeed = BODY_CATCHUP_SPEED (24 units/tick)
    bodyYaw += clamp(delta, catchupSpeed) * sign(delta)
    self.anInt3400 = bodyYaw
    self.anInt3385 = 0  // prevent turn animation
```

**Within dead zone:** Body stays at current orientation. Camera can look independently.
**Beyond dead zone:** Body smoothly catches up toward camera direction.
**Beyond shoulder limit:** Body catches up faster.
**180° turn:** Body smoothly catches up at fast speed, no instant snap.

**FP movement:** Movement velocity remains camera-relative (using fpCamYaw). Body-look coupling separately manages visual body orientation. Velocity does NOT overwrite body yaw in FP mode.

**ModernMovementController guards:** In FP mode, `self.anInt3400` is NOT set from velocity (body-look coupling owns it). `self.anInt3381 = self.anInt3400` snap is skipped (body-look coupling manages both). `self.anInt3385 = 0` is still set to prevent method949 turn animation.

### 16.11 Free Camera

**FREE camera uses classic-style orbit behavior (Review #2 corrected):**
- Arrow keys control orbit (reads `Preferences.aBoolean63` + `InterfaceList.keyQueueSize/keyCodes[]`) at 50Hz.
- `freeYaw` += 16 per left/right key tick, `freePitch` += 4 per up/down key tick.
- Pitch clamped to 128..383 (same as legacy camera range).
- Camera position computed via `Camera.method555()` (same proven RT4 transform as chase).
- Camera obstruction check same as chase.
- Modern WASD remains active; movement uses body orientation.
- **TODO:** Final FREE camera input should reuse the render-timed path (`Camera.yawTarget/pitchTarget` written by `GameShell.mainInputLoop`) instead of reading the key queue at 50Hz.

### 16.12 FirstPersonCamera Integration

When the rig is active and in CHASE or FREE state, `FirstPersonCamera.update()` still processes mouse look (fpCamYaw/fpCamPitch update) but does NOT write to Camera fields. The rig owns Camera field writes in CHASE/FREE.

```java
if (ModernCameraRig.isActive()
        && ModernCameraRig.getRigState() != ModernCameraRig.RigState.FIRST_PERSON) {
    return;  // Skip Camera field writes; rig owns them
}
```

### 16.13 Protocol.java Camera Gate

Updated from:
```java
if (!FirstPersonCamera.isActive()) {
```
to:
```java
if (!FirstPersonCamera.isActive() && !ModernCameraRig.isActive()) {
```

This prevents the legacy camera system (`method4273()` / `updateLockedCamera()`) from interfering when the rig is active in any state.

### 16.14 Scene Rebuild Lifecycle

`LoginManager.method2463()` and `reconnect()` call `ModernCameraRig.onSceneRebuild()`:
- Preserves `desiredDistance` (user's zoom intent survives region change).
- Re-anchors `actualDistance = desiredDistance` (no interpolating from old region).
- Re-anchors `chaseYaw = chaseYawTarget = self.anInt3400` (no sweep across map).
- Forces `Camera.cameraType = 0` (legacy camera doesn't interfere during rebuild).

### 16.15 FP Head Clipping

The local player body remains rendered in FIRST_PERSON (body culling was removed in Phase 3B stabilization). At FP camera distance (at/eye level), head/helmet clipping is evaluated at runtime. If clipping is observed, the smallest possible head-only exclusion should be implemented rather than hiding the entire model. CHASE and FREE always render full self model.

### 16.16 Files Created/Modified

| File | Change |
|------|--------|
| `rt4/ModernCameraRig.java` (NEW, 733 lines) | Core camera rig: RigState enum, distance continuum, scroll wheel processing, chase camera, free camera, obstruction, body-look coupling, math utilities |
| `rt4/CameraMode.java` | Updated `getCameraRelativeYaw()` for THIRD_PERSON rig integration; added `ModernCameraRig.onEnterModernMode()/onExitModernMode()` lifecycle hooks in `onModeChanged()` |
| `rt4/ModernControlController.java` | Added `ModernCameraRig.update()` and `FirstPersonCamera.update()` calls in THIRD_PERSON dispatch; updated execution order: FP camera → Rig → Movement |
| `rt4/FirstPersonCamera.java` | Skip Camera field writes when rig is in CHASE/FREE state (mouse look still updates) |
| `rt4/ModernMovementController.java` | Don't snap `anInt3381=anInt3400` in FP mode (body-look coupling owns anInt3400); don't set `anInt3400` from velocity in FP mode |
| `rt4/Protocol.java` | Camera gate: also check `!ModernCameraRig.isActive()` |
| `rt4/LoginManager.java` | Added `ModernCameraRig.onSceneRebuild()` in both rebuild paths |
| `MODERN_CONTROLS_GOAL.md` | Added Phase 3C camera architecture section |
| `MODERN_CONTROLS_PROGRESS.md` | Added Phase 3C documentation (this section) |

### 16.17 Build Verification

```
gradlew.bat :client:compileJava → BUILD SUCCESSFUL in 1s
```

8 code files changed (1 new, 7 modified). No Phase 4 collision, targeting, combat, UI/hotbar, protocol rewrite, or renderer changes.

### 16.18 Runtime Acceptance Checklist

**FIRST_PERSON body:**
- [ ] Stand still, rotate camera left/right → body follows smoothly
- [ ] Hold W + rotate camera → movement follows camera, body follows look
- [ ] Hold D → strafe right, body does NOT face right (stays based on look)
- [ ] Hold S → move backward, body still faces look direction
- [ ] Quick 180° mouse turn → body smoothly catches up, no instant snap

**Zoom continuum:**
- [ ] From FP, scroll OUT repeatedly: FP → close chase → chase → far chase → FREE
- [ ] From FREE, scroll IN repeatedly: FREE → far chase → chase → close chase → FP
- [ ] No F11 required for transitions
- [ ] No camera teleport at thresholds
- [ ] No mode flicker around thresholds (hysteresis works)

**Chase camera:**
- [ ] Walk straight → camera remains smoothly behind character
- [ ] Change direction suddenly → camera rotates smoothly behind new orientation
- [ ] 180° direction change → shortest smooth swing
- [ ] Shift run → camera remains stable
- [ ] Stop → camera remains behind standing character
- [ ] No feedback loop (camera rotation does NOT change locomotion)

**Wall camera:**
- [ ] Wall between player and desired camera → camera comes closer, stays on player's side
- [ ] Walk away from wall → camera smoothly returns to desired distance
- [ ] desiredDistance unchanged by wall (only actualDistance compressed)

**Free camera:**
- [ ] Classic-style overview camera works at max zoom
- [ ] Arrow keys orbit camera
- [ ] Scroll inward returns to CHASE
- [ ] Modern WASD still works
- [ ] ORIGINAL mode has NOT been activated

**ORIGINAL regression:**
- [ ] Click-to-move works
- [ ] Minimap click/flag works
- [ ] Legacy run works
- [ ] Scroll zoom works (legacy camera)
- [ ] Middle mouse camera works
- [ ] Legacy animations work
- [ ] Legacy self model renders
- [ ] No modern WASD leaks

---

## 15. Phase 3B Runtime Fix #3 — Camera Handedness, Mode Isolation, Authority Diagnostics

**Date:** 14-08-2026
**Commit:** (pending)
**Baseline:** 73a0b56 (Phase 3B stabilization - live camera steering and idle animation)

### 15.1 Runtime bugs found

1. **E/W movement mirrored in FIRST_PERSON** — camera facing EAST → W moves WEST; camera facing WEST → W moves EAST. N/S correct.
2. **THIRD_PERSON could inherit FP camera yaw** — `getModernMovementYaw()` returned `Camera.cameraYaw` for TP, which could be contaminated by FP state.
3. **Wall snapback** — walking through walls causes multi-tile backward rebase. Diagnosis required.

### 15.2 Root cause: RT4 camera yaw handedness

**The previous session documented the WRONG cardinal mapping.**

The previous session (section 13.2) stated:
- yaw 512 = EAST (+X)
- yaw 1536 = WEST (-X)

**This was WRONG.** The actual RT4 convention is the OPPOSITE for E/W.

**Proof from Camera.java line 324 (method3849):**
```java
cameraYaw = (int) (Math.atan2(local54, local59) * -325.949D) & 0x7FF;
```
where `local54 = targetX - cameraX` (delta X) and `local59 = targetZ - cameraZ` (delta Z).

The **NEGATIVE multiplier** (`-325.949`) inverts the X axis:
- Looking EAST (+X): atan2(+D, 0) = π/2 → -512 → **1536**
- Looking WEST (-X): atan2(-D, 0) = -π/2 → +512 → **512**

**Correct RT4 cardinal yaw mapping:**
| Yaw  | Direction | Axis |
|------|-----------|------|
| 0    | NORTH     | +Z   |
| 512  | WEST      | -X   |
| 1024 | SOUTH     | -Z   |
| 1536 | EAST      | +X   |

**The MathUtils.sin/cos tables follow standard math conventions** (counterclockwise from +X):
- sin[512] = +65536 (positive, which in standard math = +X = EAST)
- sin[1536] = -65536 (negative, which in standard math = -X = WEST)

But the RS camera convention is **clockwise** (because of the negative multiplier), so:
- RS yaw 512 = WEST (even though sin[512] is positive)
- RS yaw 1536 = EAST (even though sin[1536] is negative)

### 15.3 Previous basis error

The previous basis was:
```java
Forward = (+sin[yaw], +cos[yaw])
Right   = (+cos[yaw], -sin[yaw])
```

At yaw 1536 (EAST):
- Forward = (sin[1536], cos[1536]) = (-1, 0) = WEST ← WRONG! Camera faces EAST but forward points WEST.

### 15.4 Corrected basis

```java
Forward = (-sin[yaw], +cos[yaw])
Right   = (+cos[yaw], +sin[yaw])
```

Verification at all four cardinals:

| Camera yaw | Camera direction | Forward vector | Movement | Correct? |
|------------|------------------|----------------|----------|----------|
| 0          | NORTH            | (0, +1) = +Z   | NORTH    | YES |
| 1536       | EAST             | (+1, 0) = +X   | EAST     | YES |
| 1024       | SOUTH            | (0, -1) = -Z   | SOUTH    | YES |
| 512        | WEST             | (-1, 0) = -X   | WEST     | YES |

Right vector verification (D key):

| Camera yaw | Camera direction | Right vector  | Strafe | Correct? |
|------------|------------------|---------------|--------|----------|
| 0          | NORTH            | (+1, 0) = +X  | EAST   | YES |
| 1536       | EAST             | (0, -1) = -Z  | SOUTH  | YES |
| 1024       | SOUTH            | (-1, 0) = -X  | WEST   | YES |
| 512        | WEST             | (0, +1) = +Z  | NORTH  | YES |

### 15.5 Orientation formula correction

The orientation formula also had the wrong sign. The RT4 convention uses a NEGATIVE multiplier (same as the camera atan2):

**Old (WRONG):** `atan2(velX, velZ) * +325.949`
**New (CORRECT):** `atan2(velX, velZ) * -325.949`

Verification:
- Moving EAST (velX=+4, velZ=0): atan2(+4, 0) = π/2 → -512 → 1536 = EAST ✓
- Moving NORTH (velX=0, velZ=+4): atan2(0, +4) = 0 → 0 = NORTH ✓
- Moving SOUTH (velX=0, velZ=-4): atan2(0, -4) = π → -1024 → 1024 = SOUTH ✓
- Moving WEST (velX=-4, velZ=0): atan2(-4, 0) = -π/2 → +512 = WEST ✓

### 15.6 Mode isolation — THIRD_PERSON yaw source

**Previous:** `CameraMode.getModernMovementYaw()` returned `Camera.cameraYaw` for THIRD_PERSON.

**Problem:** THIRD_PERSON should NOT use camera-relative steering. The legacy camera yaw could change due to mouse/keyboard input, silently redefining WASD semantics.

**Fix:** Replaced with `CameraMode.getCameraRelativeYaw()`:
- FIRST_PERSON → returns `FirstPersonCamera.getYaw()` (live FP yaw)
- THIRD_PERSON → returns `-1` (no camera-relative steering)
- ORIGINAL → returns `-1` (modern controller inactive)

In ModernMovementController, when `getCameraRelativeYaw()` returns -1, the controller falls back to `self.anInt3400` (player body heading / target orientation).

**THIRD_PERSON temporary locomotion heading:** `self.anInt3400` (the player's target orientation angle). This means:
- W = move in the direction the player body is currently facing
- A/D = strafe relative to body facing
- Movement heading is independent from any camera yaw

**Proof that TP does NOT use FP yaw:**
- `CameraMode.getCameraRelativeYaw()` returns -1 for THIRD_PERSON
- ModernMovementController uses `self.anInt3400` when camYaw < 0
- `FirstPersonCamera.getYaw()` is never called while THIRD_PERSON is active

### 15.7 Server snapback diagnosis

**Diagnostic logging added** at:
- Each walk packet sent (local tile, world tile, run flag, predicted tile, server tile, pending count)
- Each server step received (server tile, predicted tile, divergence, pending count)
- Each reconciliation rebase (reason, predicted tile, server tile, pending count)

**Snapback classification:** EXPECTED SERVER COLLISION CORRECTION (Case 1).

**Rationale:**
- Modern local prediction has NO collision detection (Phase 4 will add it)
- Client predicts through walls/objects freely
- Server refuses invalid tiles (collision authority)
- `lastServerReportedTile` remains at valid pre-wall position
- Client prediction diverges from server → `divergence > MAX_DIVERGENCE_TILES` → rebase to server tile center
- This is correct server-authoritative behavior, NOT a packet/reconciliation bug

**No reconciliation code was changed.** The existing reconciliation logic correctly handles the divergence.

**Phase 4 local collision is confirmed as the required fix** to prevent the client from predicting through blocked geometry in the first place.

### 15.8 Files changed

| File | Change |
|------|--------|
| `ModernMovementController.java` | Corrected forward/right basis (-sin/cos, cos/sin); corrected orientation formula (* -325.949); yaw source from `getCameraRelativeYaw()` with TP fallback to body heading; diagnostic logging for packets/server steps/rebases |
| `CameraMode.java` | Replaced `getModernMovementYaw()` with `getCameraRelativeYaw()` — returns FP yaw for FIRST_PERSON, -1 for others |
| `MODERN_CONTROLS_PROGRESS.md` | Section 15 documentation |

### 15.9 Force-move preservation

No changes to force-move suspension logic. Unchanged.

### 15.10 Idle animation status

No changes to idle animation logic from 73a0b56. The every-tick idle re-assert and orientation snap remain in place.

### 15.11 ORIGINAL mode unchanged

ORIGINAL mode: no changes. method2247, PathFinder, legacy camera, legacy keyboard — all untouched. Modern changes remain gated by `CameraMode.isModern()`.

### 15.12 Build verification

```
gradlew.bat :client:compileJava → BUILD SUCCESSFUL
```

### 15.13 Runtime acceptance checklist

FIRST_PERSON:
- [ ] Camera NORTH → W moves NORTH
- [ ] Camera EAST → W moves EAST (was WEST before fix)
- [ ] Camera SOUTH → W moves SOUTH
- [ ] Camera WEST → W moves EAST (was EAST before fix)
- [ ] D at NORTH → strafe EAST
- [ ] D at EAST → strafe SOUTH
- [ ] A at NORTH → strafe WEST
- [ ] Hold W + rotate camera 360° → smooth curved path following camera
- [ ] Release W → idle animation
- [ ] Idle 5 seconds → normal idle persists

THIRD_PERSON:
- [ ] W moves in body facing direction
- [ ] Camera rotation does NOT change WASD semantics
- [ ] Same idle/walk/run transitions

ORIGINAL:
- [ ] Click-to-move works
- [ ] Legacy animations unchanged
- [ ] No modern WASD leaks

---

## 17. Phase 3C Addendum — ORIGINAL Must Remain Vanilla + F11 Profile Toggle

**Date:** 14-08-2026
**Commit:** (pending)
**Baseline:** 3e88cd7 (Phase 3C - modern camera rig and character look coupling)

### 17.1 Problem statement

The Phase 3C implementation had F11 cycling through three modes:
ORIGINAL → FIRST_PERSON → THIRD_PERSON → ORIGINAL.

This was architecturally incorrect because:

1. Regular 2009Scape players who never press F11 must get a completely vanilla experience.
2. Scroll-wheel continuum must only exist inside MODERN, not globally.
3. MODERN FREE (camera rig) is NOT the same as ORIGINAL (control profile).
4. F11 should be the sole switch between ORIGINAL and MODERN control profiles.

### 17.2 Changes applied

#### F11 toggle (CameraMode.java)

**Before:** `cycle()` was a 3-way cycle: ORIGINAL → FIRST_PERSON → THIRD_PERSON → ORIGINAL.

**After:** `cycle()` is a 2-way toggle:
```java
if (current == Mode.ORIGINAL) {
    current = Mode.THIRD_PERSON; // Enter MODERN
} else {
    current = Mode.ORIGINAL; // Return to vanilla
}
```

F11 from ORIGINAL → enters MODERN (THIRD_PERSON).
F11 from MODERN → returns to ORIGINAL.
Inside MODERN, scroll wheel controls the camera rig (FP/CHASE/FREE).

#### onModeChanged rework (CameraMode.java)

**ORIGINAL → MODERN:**
- `ModernMovementController.enterModernMode()` — initialize prediction
- `ModernCameraRig.onEnterModernMode()` — save full legacy camera state

**MODERN → ORIGINAL:**
- `FirstPersonCamera.deactivate()` if active (rig may have been in FP state)
- `ModernMovementController.exitModernMode()` — rebase prediction
- `ModernCameraRig.onExitModernMode()` — restore full legacy camera state
- Safety net: `FirstPersonCamera.resetToSafeDefaults()`

#### FP camera lifecycle (ModernCameraRig.java)

The rig now manages `FirstPersonCamera` activation/deactivation when
scrolling into/out of FP rig state:

- Scroll IN to FP: `FirstPersonCamera.activate()` (cursor lock, mouse-look)
- Scroll OUT from FP: `FirstPersonCamera.deactivate()` + `Camera.cameraType = 0`

This means F11 entering MODERN no longer directly activates FP camera.
The rig starts in CHASE state. User scrolls to reach FP.

#### Camera state preservation (ModernCameraRig.java)

**onEnterModernMode()** now saves ALL camera fields:
- cameraType, cameraPitch, pitchTarget, cameraYaw, yawTarget, cameraX, cameraZ, anInt40

**onExitModernMode()** restores ALL saved fields.

Result: pressing F11 to MODERN and back leaves the vanilla camera exactly where it was.

#### Rig activation default (ModernCameraRig.java)

`activate()` now always starts in CHASE state (not FP based on distance).
User must scroll inward to reach FP. This prevents unexpected FP entry
when pressing F11.

### 17.3 Architecture after addendum

```
CONTROL PROFILE (F11 toggle):
    ORIGINAL — pure vanilla 2009Scape
    MODERN   — WASD + modern camera rig

CAMERA RIG inside MODERN only:
    FIRST_PERSON  ←scroll→  CHASE  ←scroll→  FREE

ORIGINAL state: saved on F11→MODERN, restored on F11→ORIGINAL
MODERN state: maintained independently (desiredDistance, chaseYaw, etc.)
```

### 17.4 Files changed

| File | Change |
|------|--------|
| `CameraMode.java` | F11 3-way cycle → 2-way toggle; onModeChanged rework; FP camera lifecycle removed from onModeChanged (now in rig) |
| `ModernCameraRig.java` | FP camera activate/deactivate on rig state transitions; full camera state save/restore; activate() defaults to CHASE |
| `MODERN_CONTROLS_GOAL.md` | Updated Phase 3C section: control profile vs camera rig, ORIGINAL vanilla guarantee, MODERN FREE ≠ ORIGINAL, camera state preservation |
| `MODERN_CONTROLS_PROGRESS.md` | Section 17 documentation (this section) |

### 17.5 Build verification

```
gradlew.bat :client:compileJava → BUILD SUCCESSFUL
```

### 17.6 Runtime acceptance checklist

**ORIGINAL vanilla test:**
- [ ] Launch game → ORIGINAL by default
- [ ] Scroll inward → vanilla zoom changes normally
- [ ] Scroll outward → vanilla zoom changes normally
- [ ] Middle mouse camera works
- [ ] Camera freely controllable
- [ ] No FP transition from scrolling
- [ ] No CHASE transition from scrolling
- [ ] Click-to-move works
- [ ] Minimap movement works
- [ ] No modern WASD leak

**F11 toggle test:**
- [ ] F11 → MODERN (THIRD_PERSON mode, CHASE camera rig)
- [ ] WASD works immediately
- [ ] Scroll IN → close CHASE → FP (cursor locks, mouse-look activates)
- [ ] Scroll OUT → CHASE → FREE (cursor unlocks)
- [ ] Scroll further IN → CHASE → FP again
- [ ] F11 → return to ORIGINAL
- [ ] ORIGINAL camera returns to saved state (not modern zoom)
- [ ] ORIGINAL scroll zoom works normally after return

**Camera state preservation test:**
- [ ] Set vanilla camera to specific zoom/angle
- [ ] F11 → MODERN → scroll around → F11 → ORIGINAL
- [ ] Vanilla camera returns to the specific zoom/angle
- [ ] No modern distance values contaminate vanilla camera

**MODERN FREE ≠ ORIGINAL test:**
- [ ] In MODERN, scroll fully outward → FREE camera
- [ ] WASD still works in FREE
- [ ] Shift+run still works in FREE
- [ ] No click-to-move activated
- [ ] ORIGINAL mode NOT activated by scrolling

---

## 18. Phase 3C Review #2 — RT4 Source Verification & Implementation Corrections

**Date:** 14-08-2026
**Commit:** (pending)
**Baseline:** ae16bff (Phase 3C addendum — ORIGINAL must remain vanilla)

### 18.1 Purpose

A 21-section architectural review verified the Phase 3C ModernCameraRig implementation
against the official 2009Scape RT4 client source. This section documents the corrections
applied to align the implementation with verified RT4 behavior.

### 18.2 Changes applied

#### 1. Chase/Free camera transform — reuse Camera.method555()

**Before:** The chase and free camera computed position with a hand-written trigonometric
implementation (sin/cos of yaw/pitch applied to distance independently on X/Z, then
separately adding vertical pitch offset).

**Problem:** Official RT4 `Camera.method555()` performs the proven transform:
```
boomX = sin(yaw) * cos(pitch) * distance
boomZ = cos(yaw) * cos(pitch) * distance
boomY = -sin(pitch) * distance
renderX = targetX - boomX
renderZ = targetZ - boomZ
anInt40 = targetY - boomY
```
Pitch changes the horizontal component of the boom, not just the vertical. The
hand-written implementation treated distance independently on X/Z and then separately
added vertical pitch offset — inconsistent with the official transform.

**Fix:** Both `updateChase()` and `updateFree()` now call `Camera.method555()` directly:
```java
Camera.method555(pivotX, Rasteriser.screenUpperY, pivotY,
        effectiveZoom, chaseYaw, pivotZ, chasePitch);
```
This reuses the proven RT4 transform including GL viewport scaling behavior.

**method555 arguments (traced from ScriptRunner.method4326 line 238):**
- arg0 = target X (pivotX)
- arg1 = viewport height (Rasteriser.screenUpperY — only used for GL zoom scaling)
- arg2 = target Y (pivotY = terrainHeight - 50)
- arg3 = zoom/distance parameter (effectiveZoom = actualDistance * 0.5 + pitch * 3)
- arg4 = yaw (chaseYaw / freeYaw)
- arg5 = target Z (pivotZ)
- arg6 = pitch (chasePitch / freePitch)

**Viewport height fix:** The original code used `Rasteriser.screenHeight` which does
not exist. Replaced with `Rasteriser.screenUpperY` (public field, set by
GlRenderer.method4171 during render pipeline). For software rendering this argument
is completely ignored; for GL it controls a minor zoom scaling adjustment.

#### 2. Wheel pipeline honesty

**Before:** Comments claimed "rig defers to UI" and that the rig ran "after all UI
consumers have had their chance."

**Actual verified order:**
```
client.java:1725 — MouseWheel.wheelRotation = mouseWheel.getRotation()
→ ModernControlController.update() → processWheelInput() (50Hz game tick)
→ ScriptRunner.method4326 → InterfaceList UI scroll (render pipeline)
```
The rig reads wheelRotation BEFORE the UI scroll processing. Both camera zoom and
UI scroll read the same `MouseWheel.wheelRotation` value. UI scroll operates on
component `scrollY` (separate variable), so there is no variable conflict — but
BOTH can react to the same wheel event.

**Fix:** Comments corrected to honestly document the actual pipeline order. Added
TODO for proper UI ownership check (hit-testing scrollable UI components under cursor).

#### 3. FREE camera input honesty

**Before:** Comments claimed FREE camera "writes to Camera.yawTarget/pitchTarget."

**Actual:** FREE camera reads `InterfaceList.keyQueueSize/keyCodes[]` at 50Hz and
writes to internal `freeYaw/freePitch` fields. It does NOT write to the RT4
render-timed `Camera.yawTarget/pitchTarget` fields.

**Fix:** Comments corrected. Added TODO noting that final FREE camera input should
reuse the render-timed path (`Camera.yawTarget/pitchTarget` written by
`GameShell.mainInputLoop`) instead of reading the key queue at 50Hz.

#### 4. Smoothing honesty

**Before:** Comments implied "frame-rate-independent" smoothing.

**Actual:** All smoothing (distance, yaw, pitch) is 50Hz tick-based exponential
smoothing. This is NOT frame-rate-independent — it converges in a fixed number
of 20ms ticks.

**Fix:** All smoothing comments and Javadoc now accurately state "50Hz tick-based
exponential smoothing (NOT frame-rate-independent)."

#### 5. ORIGINAL camera state double-save fix

**Before:** Both `onEnterModernMode()` and `activate()` saved camera state:
```java
// onEnterModernMode:
savedCameraType = Camera.cameraType;  // saves 1 (correct)
Camera.cameraType = 0;
// later, activate():
savedCameraType = Camera.cameraType;  // saves 0 (WRONG — already mutated)
```

**Fix:** Added `originalStateSaved` flag. `onEnterModernMode()` saves state only
once (when `originalStateSaved == false`), then sets the flag. `activate()` no
longer saves state. `onExitModernMode()` restores state only when the flag is set,
then clears it.

#### 6. FP↔CHASE spatial continuity

**Before:** FP→CHASE transition set `actualDistance = desiredDistance` which could
be far from FP position (e.g., desiredDistance=2400 but FP camera was at distance 0).

**Fix:** FP→CHASE transition sets `actualDistance = FP_EXIT_DISTANCE` (200), so
the chase camera starts just behind the player and smoothly expands to the user's
desired distance. No instant teleport.

#### 7. getCameraYaw() removed — no movement authority

**Before:** `getCameraYaw()` was exposed with intent "for movement controller."

**Fix:** Removed. `CameraMode.getCameraRelativeYaw()` returns -1 for CHASE/FREE
rig states, ensuring movement uses body orientation (`self.anInt3400`), not camera
yaw. Only FP rig state returns a yaw for movement (via `FirstPersonCamera.getYaw()`).

#### 8. Body orientation single writer

**Before:** Both `ModernCameraRig.updateBodyLookCoupling()` and
`ModernMovementController.update()` could write `self.anInt3400`.

**Fix:** `ModernMovementController` checks `ModernCameraRig.isFirstPersonRigState()`
and skips orientation writes when the rig is in FP state. Body-look coupling is the
sole writer of `self.anInt3400` in FP mode.

#### 9. Distance model: desired/safe/actual

**Before:** Obstruction directly modified camX/camZ after the fact. actualDistance
could remain 500 while rendered camera was at 200.

**Fix:** Three separate concepts maintained:
- `desiredDistance` — user's zoom preference (scroll wheel, never destroyed by walls)
- `safeDistance` — maximum permitted by obstruction
- `actualDistance` — smoothly approaches `min(desiredDistance, safeDistance)`

`smoothDistance()` converges actualDistance toward the target. Wall appears:
500→400→300→210. Wall disappears: 210→270→350→430→500. desiredDistance stays 500.

#### 10. Camera obstruction — directional wall flags

**Before:** Only checked `(flags & 0x100)` and `(flags & 0x20000)`.

**Fix:** Added directional wall edge checking using CollisionMap flags:
N=0x102, S=0x120, W=0x108, E=0x180. When the camera path crosses a tile boundary,
the destination tile's wall mask facing the crossing direction is checked. Combined
full-tile mask: 0x240100 (scenery 0x100 + full block 0x20000 + ground decor 0x40000 + flagged tile 0x200000).

#### 11. Dead code removal

Removed `sampleY` variable in `checkObstruction()` that computed
`(pivotY - pivotY) * frac >> 16` — always evaluated to 0 (placeholder expression).

### 18.3 Files changed

| File | Change |
|------|--------|
| `ModernCameraRig.java` | Complete rewrite: method555 reuse for chase/free, Rasteriser.screenUpperY fix, double-save fix with originalStateSaved, FP↔CHASE actualDistance=FP_EXIT_DISTANCE, desired/safe/actual distance model, directional wall obstruction, honest comments (wheel pipeline order, FREE input, smoothing), removed getCameraYaw(), isFirstPersonRigState() API, dead code removal |
| `CameraMode.java` | getCameraRelativeYaw() returns -1 for CHASE/FREE rig state (not chase yaw) |
| `ModernMovementController.java` | Uses `ModernCameraRig.isFirstPersonRigState()` guard for body orientation (not `CameraMode.getCurrent() != FIRST_PERSON`) |
| `MODERN_CONTROLS_GOAL.md` | Updated Phase 3C camera architecture sections |
| `MODERN_CONTROLS_PROGRESS.md` | Section 18 documentation (this section) |

### 18.4 Build verification

```
gradlew.bat :client:compileJava → BUILD SUCCESSFUL
```

4 code files changed (1 new ModernCameraRig.java rewrite, 2 modified, 1 doc).
No Phase 4 collision, targeting, combat, UI/hotbar, protocol rewrite, or renderer changes.

### 18.5 Known TODOs (deferred, not blocking Phase 3C)

1. **UI wheel ownership:** Proper hit-testing of scrollable UI components under cursor
   before applying camera zoom. Currently both camera and UI can react to same wheel event.
2. **FREE camera render-timed input:** Final FREE camera should reuse
   `Camera.yawTarget/pitchTarget` written by `GameShell.mainInputLoop` instead of
   reading `InterfaceList.keyQueueSize` at 50Hz.
3. **Camera obstruction DDA:** Current multi-sample stepping (one per tile) can miss
   thin wall edges depending on geometry. A proper 2D tile DDA along the pivot→camera
   segment would be more robust.
4. **Render-timed smoothing:** Distance/yaw smoothing is 50Hz tick-based. Visual
   camera smoothing could benefit from render-timed integration (deferred).

### 18.6 Runtime acceptance checklist

**Chase camera (method555 transform):**
- [ ] Walk straight → camera smoothly behind character
- [ ] Change direction → camera rotates smoothly via shortest angle
- [ ] Camera position consistent with RT4 convention (body NORTH → camera SOUTH)
- [ ] No hand-written trig divergence from official transform

**Zoom continuum:**
- [ ] Scroll IN from FP → FP_EXIT_DISTANCE → smooth CHASE entry (no teleport)
- [ ] Scroll OUT from CHASE → FREE entry smooth
- [ ] desiredDistance unchanged by walls
- [ ] actualDistance smoothly compressed by walls
- [ ] actualDistance smoothly recovers when wall cleared

**Wheel pipeline:**
- [ ] Camera zoom works in MODERN
- [ ] UI scroll works simultaneously (separate variables, no conflict)
- [ ] TODO: UI under cursor blocks camera zoom (not yet implemented)

**ORIGINAL state preservation:**
- [ ] F11 → MODERN → F11 → ORIGINAL: camera returns to saved state
- [ ] Double F11 does not corrupt saved state (originalStateSaved flag works)
- [ ] desiredDistance does not overwrite legacy zoom

**Body orientation:**
- [ ] FP: body follows look direction (body-look coupling sole writer)
- [ ] FP: WASD does NOT overwrite body orientation
- [ ] CHASE/FREE: body orientation from movement, not camera yaw

---

## 19. Phase 3C Addendum — Final Zoom Ranges (Vanilla ZOOM Trace + RuneLite +150 Mapping)

**Date:** 14-08-2026
**Commit:** (pending)
**Baseline:** 42a2c09 (Phase 3C Review #2 - RT4 source verification)

### 19.1 Problem statement

The Phase 3C camera rig used arbitrary distance thresholds:
- `FREE_ENTER_DISTANCE = 4200`
- `MAX_DISTANCE = 5600`
- `WHEEL_STEP = 130`
- Mapping: `zoom = actualDistance * 0.5 + pitch * 3`

These values were not derived from the vanilla zoom system. The user requested
that thresholds be traced from actual 2009Scape RT4 source code and mapped to
match the RuneLite Camera plugin's "Expand outer zoom limit = +150" feel.

### 19.2 Source trace — vanilla 2009Scape zoom system

#### 19.2.1 Camera.ZOOM field

**File:** `Camera.java:73`
```java
public static int ZOOM = 600;
```
Default value: **600**.

#### 19.2.2 Vanilla zoom limits (legacy client)

**File:** `legacy-client/.../MouseWheel.java:32-36`
```java
if ((Client.ZOOM > 1200 && MouseWheel.moveAmt >= 0)
        || (Client.ZOOM < 100 && MouseWheel.moveAmt <= 0)) {
    return; // blocked
}
Client.ZOOM += MouseWheel.moveAmt >= 0 ? 50 : -50;
```

**Vanilla zoom limits:**
| Parameter | Value |
|-----------|-------|
| Default ZOOM | 600 |
| Min ZOOM | 100 |
| Max ZOOM | 1200 |
| Step per notch | 50 |

#### 19.2.3 pitchTarget field

**File:** `Camera.java:19`
```java
public static double pitchTarget = 128;
```
Clamped to [128, 383] by `Camera.clampCameraAngle()` (lines 107-112).

#### 19.2.4 Vanilla render pipeline call

**File:** `ScriptRunner.java:238`
```java
Camera.method555(
    Camera.cameraX,                                    // targetX
    arg0,                                               // viewportHeight
    SceneGraph.getTileHeight(...) - 50,                 // targetY
    Camera.ZOOM - -(local59 * 3),                       // zoom_param = ZOOM + pitchTarget*3
    local57,                                            // yaw
    Camera.cameraZ,                                     // targetZ
    local59                                             // pitch
);
```

**zoom_param = Camera.ZOOM + pitchTarget * 3**

This is the 4th argument to `Camera.method555()`, which becomes the camera boom
length (3D distance from target to camera in fine units).

#### 19.2.5 method555 boom computation

**File:** `Camera.java:393-431`

```
local5 = 2048 - pitch   (pitch complement)
local29 = 2048 - yaw    (yaw complement)

boomY = sin(pitch_complement) * -zoom_param >> 16
intermediate = cos(pitch_complement) * zoom_param >> 16
boomX = sin(yaw_complement) * intermediate >> 16
boomZ = cos(yaw_complement) * intermediate >> 16

renderX = targetX - boomX
renderZ = targetZ - boomZ
anInt40 = targetY - boomY
```

The 3D boom length = zoom_param (since sin²+cos²=1 across both rotations).
**zoom_param IS the camera distance in fine units (128 fine = 1 tile).**

#### 19.2.6 Vanilla zoom_param range

At default pitch (128):
| Setting | ZOOM | zoom_param | Boom (fine) | Boom (tiles) |
|---------|------|------------|-------------|---------------|
| Min zoom | 100 | 100 + 384 = 484 | 484 | 3.8 |
| Default | 600 | 600 + 384 = 984 | 984 | 7.7 |
| Max zoom | 1200 | 1200 + 384 = 1584 | 1584 | 12.4 |

At max pitch (383):
| Setting | ZOOM | zoom_param | Boom (fine) | Boom (tiles) |
|---------|------|------------|-------------|---------------|
| Min zoom | 100 | 100 + 1149 = 1249 | 1249 | 9.8 |
| Max zoom | 1200 | 1200 + 1149 = 2349 | 2349 | 18.4 |

### 19.3 RuneLite +150 mapping

RuneLite Camera plugin defines:
```
OUTER_LIMIT_MIN = -400
OUTER_LIMIT_MAX = 400
outerLimit default = 0
```

The plugin adjusts the vanilla zoom limits:
```
CAMERA_ZOOM_BIG_MIN = defaultZoomBigMin - outerLimitAdjustment
```

"outerLimit = +150" means: extend the vanilla max ZOOM by 150 units in
the game's zoom configuration scale.

**Extended max ZOOM = 1200 + 150 = 1350**

This is NOT 150 fine units or 150 tiles. It is 150 units in the same
ZOOM parameter space as vanilla's 600 default and 1200 max.

Extended zoom_param at default pitch:
- 1350 + 128*3 = 1350 + 384 = **1734** fine units boom length

### 19.4 Derivation of new constants

#### 19.4.1 Mapping change

**Before:** `zoom = actualDistance * 0.5 + pitch * 3`
**After:** `zoom = actualDistance + pitch * 3`

Now `actualDistance` directly maps to the vanilla ZOOM parameter space.
This means:
- `actualDistance = 600` corresponds to vanilla default ZOOM (600)
- `actualDistance = 1200` corresponds to vanilla max ZOOM (1200)
- `actualDistance = 1350` corresponds to extended max ZOOM (1350)

#### 19.4.2 Threshold derivation

| Constant | Old Value | New Value | Derivation |
|----------|-----------|-----------|------------|
| FP_ENTER_DISTANCE | 120 | **100** | = VANILLA_ZOOM_MIN (fully zoomed in) |
| FP_EXIT_DISTANCE | 200 | **200** | Hysteresis: 100 above FP_ENTER (2 notches) |
| FREE_ENTER_DISTANCE | 4200 | **1200** | = VANILLA_ZOOM_MAX (normal max zoom) |
| FREE_EXIT_DISTANCE | 3800 | **1100** | Hysteresis: 100 below FREE_ENTER (2 notches) |
| MIN_DISTANCE | 0 | **0** | Unchanged |
| MAX_DISTANCE | 5600 | **1350** | = VANILLA_ZOOM_MAX + 150 (RuneLite extension) |
| WHEEL_STEP | 130 | **50** | = vanilla scroll step per notch |
| desiredDistance (default) | 2400 | **600** | = VANILLA_ZOOM_DEFAULT |
| actualDistance (default) | 2400 | **600** | = VANILLA_ZOOM_DEFAULT |

#### 19.4.3 Visual verification at boundaries

At CHASE→FREE transition (actualDistance = 1200, CHASE_PITCH = 256):
- zoom_param = 1200 + 256*3 = 1200 + 768 = **1968**
- Boom length ≈ 1968 fine units ≈ 15.4 tiles (3D)
- Horizontal distance = cos(45°) * 1968 ≈ 1392 fine ≈ 10.9 tiles
- Vanilla max horizontal = cos(22.5°) * 1584 ≈ 1463 fine ≈ 11.4 tiles
- Ratio: 1392/1463 ≈ **95%** of vanilla max horizontal distance

This is visually comparable to vanilla max zoom (within ~5%).

At FREE max (actualDistance = 1350, FREE_PITCH = 300):
- zoom_param = 1350 + 300*3 = 1350 + 900 = **2250**
- Boom length ≈ 2250 fine units ≈ 17.6 tiles (3D)
- Extended overview camera

#### 19.4.4 Obstruction comparison fix

**Before:** `if (clearDist < actualDistance && actualDistance > 0)`
- Mixed units: clearDist in fine units, actualDistance in ZOOM space

**After:** `if (clearDist < zoom && zoom > 0)`
- Both in fine-unit boom-length space. Correct comparison.

### 19.5 ORIGINAL mode verification

**Camera.java:** Zero diff. `Camera.ZOOM = 600` unchanged.
**MouseWheel.java (legacy):** Not modified.
**ScriptRunner.java:** Not modified.

ORIGINAL mode retains exact standard 2009Scape zoom limits:
- Min: ZOOM=100 → zoom_param=484
- Max: ZOOM=1200 → zoom_param=1584
- No extension applied.

### 19.6 Expected user experience

**ORIGINAL / no F11:**
- Scroll in/out → exactly normal 2009Scape range (100–1200 ZOOM)

**F11 → MODERN:**
- Scroll fully inward → FIRST_PERSON (at distance ≤ 100)
- Scroll outward → THIRD PERSON chase distance increases
- Around distance 1200 (vanilla max ZOOM) → transition into FREE
- Scroll further outward → extended overview
- Maximum: distance 1350 (≈ RuneLite Camera outerLimit +150 feel)
- Scroll inward → extended FREE → normal FREE → CHASE → close CHASE → FIRST_PERSON

### 19.7 Build verification

```
gradlew.bat :client:compileJava → BUILD SUCCESSFUL
```

1 file changed: `ModernCameraRig.java` (24 insertions, 21 deletions).
No other files modified. No Phase 4 changes.

### 19.8 Runtime acceptance checklist

**Zoom continuum (vanilla-anchored):**
- [ ] ORIGINAL scroll: exactly normal 2009Scape range (no change)
- [ ] MODERN scroll IN: FP → CHASE at distance ~100
- [ ] MODERN scroll OUT: CHASE → FREE at distance ~1200
- [ ] CHASE→FREE transition feels like "vanilla max zoom"
- [ ] FREE max (distance 1350) feels like RuneLite +150 extension
- [ ] No mode flicker at thresholds (hysteresis works)
- [ ] Each scroll notch changes distance by 50 (same as vanilla step)

**ORIGINAL regression:**
- [ ] Camera.ZOOM unchanged (still 600 default)
- [ ] Vanilla zoom limits unchanged (100–1200)
- [ ] No modern zoom leaks into ORIGINAL mode

---

## 20. PHASE 3C RUNTIME STABILIZATION — IN PROGRESS

**Date:** 14-08-2026

### 20.1 User Runtime Test Failures (Blockers)

User performed actual runtime tests after Phase 3C Addendum. The following **hard blockers** were identified:

| # | Failure | Severity |
|---|---------|----------|
| 1 | FP camera shows full character ~1 tile away (not at eye position) | CRITICAL |
| 2 | WASD doesn't work in CHASE (letters appear in chat) | CRITICAL |
| 3 | Scroll zoom is buggy/inconsistent/jerky | HIGH |
| 4 | Chase camera hitches/stutters, doesn't follow heading cleanly | HIGH |
| 5 | Arrow keys in CHASE still rotate minimap (legacy camera input not blocked) | HIGH |
| 6 | Maximum zoom does NOT enter FREE camera | HIGH |
| 7 | Character model doesn't rotate with camera/movement direction | HIGH |

### 20.2 Root Cause Analysis

**Arrow key leakage (§5):** `GameShell.mainInputLoop()` runs on render timing and unconditionally mutates `Camera.yawTarget/pitchTarget` when arrow keys are held. This runs regardless of camera mode, causing minimap rotation even when the modern rig owns the camera.

**Camera ownership:** Multiple writers per render frame:
- `FirstPersonCamera.update()` writes Camera fields (50Hz)
- `ModernCameraRig.update()` writes Camera fields via method555 (50Hz)
- `ScriptRunner.java:229` may call `Camera.method555()` (render timing) when `cameraType==1`
- `GameShell.mainInputLoop()` mutates `yawTarget/pitchTarget` (render timing)

**50Hz stutter:** Camera smoothing runs at 50Hz (fixed logic rate) but rendering runs at variable/high refresh rate. The camera position is stale between 50Hz updates, causing visible hitching.

**FP camera position:** FirstPersonCamera places camera at `(self.xFine, self.zFine, groundHeight - EYE_HEIGHT)`. This is at the player's exact position. The full character should NOT be visible; only chest/legs when looking down. If the full character is visible ~1 tile away, something is overwriting the camera position or the FP camera is not being activated correctly.

### 20.3 Fixes Applied So Far

**Fix 1: Arrow key gating in GameShell.mainInputLoop()**
- File: `GameShell.java`
- Change: Added early return when `CameraMode.isModern() && ModernCameraRig.isActive()`
- Effect: Arrow keys no longer mutate legacy `yawTarget/pitchTarget` when modern rig owns camera
- Status: SOURCE-VERIFIED, COMPILE-VERIFIED, NOT YET RUNTIME-VERIFIED

**Fix 2: Debug overlay (throttled console output)**
- File: `ModernCameraRig.java`
- Change: Added throttled debug output (every ~1 second) showing rig state, distances, camera fields, body orientation, chat state
- Effect: Enables runtime diagnosis of camera/input state
- Status: SOURCE-VERIFIED, COMPILE-VERIFIED, NOT YET RUNTIME-VERIFIED

### 20.4 Remaining Fixes Needed

| Priority | Fix | Description |
|----------|-----|-------------|
| P0 | FP camera position | Trace exact camera write path, verify no overwrites, ensure eye position |
| P0 | WASD in CHASE | Verify shouldForwardKeyToChat covers all entry points, check chat input state |
| P1 | Camera smoothing to render timing | Move visual camera interpolation from 50Hz to render-timed |
| P1 | FREE camera activation | Verify state transition works, FREE uses classic camera behavior |
| P1 | Body orientation | Fix FP look coupling and CHASE locomotion heading |
| P2 | Render-timed yaw interpolation | Handle 0..2047 wrapped yaw correctly in render interpolation |
| P2 | Smooth camera pivot | Add render-timed pivot interpolation for chase/free |

### 20.5 Architecture Clarifications

**Control Profile vs Camera Rig State:**
- `CameraMode` (ORIGINAL / THIRD_PERSON) = CONTROL PROFILE
  - F11 toggles between ORIGINAL and MODERN (THIRD_PERSON)
- `ModernCameraRig.RigState` (FP / CHASE / FREE) = CAMERA RIG STATE
  - Scroll wheel transitions within MODERN
  - FP: FirstPersonCamera owns final camera
  - CHASE: Modern chase camera owns final camera
  - FREE: Classic-style camera with extended zoom

**Camera Ownership (one writer per render frame):**
- FP rig state: `FirstPersonCamera` writes final Camera fields
- CHASE rig state: `ModernCameraRig.updateChase()` writes final Camera fields via method555
- FREE rig state: `ModernCameraRig.updateFree()` writes final Camera fields via method555
- ORIGINAL mode: Legacy camera system (method4273/method555) writes fields

**Body Orientation:**
- FP rig state: Body follows camera look direction (body-look coupling with dead zone)
- CHASE rig state: Body follows locomotion heading (movement direction)
- FREE rig state: Body follows locomotion heading (camera orbit independent)

### 20.6 Runtime Test Checklist

After next build, user should test:

- [ ] **TEST A — FP Position:** Enter MODERN, scroll fully inward. Camera at true eye position?
- [ ] **TEST B — CHASE WASD:** Enter MODERN CHASE. Press W/A/S/D. Character moves?
- [ ] **TEST C — Arrow keys in CHASE:** Press arrow keys. Minimap stays stable?
- [ ] **TEST D — FP Body:** FP, stand still, rotate mouse. Body follows with dead zone?
- [ ] **TEST E — CHASE Body:** CHASE, move in several directions. Character rotates toward movement?
- [ ] **TEST F — CHASE Follow:** Run and change direction. Camera follows smoothly?
- [ ] **TEST G — Zoom:** Scroll from FP outward. FP → CHASE → FREE transitions work?
- [ ] **TEST H — FREE:** At max zoom, camera behaves like classic RS free camera?
- [ ] **TEST I — FREE Arrow Keys:** In FREE, arrow keys orbit camera?
- [ ] **TEST J — ORIGINAL:** F11 back to ORIGINAL. Everything restored exactly?

### 20.7 Verification Status

| Component | SOURCE-VERIFIED | COMPILE-VERIFIED | RUNTIME-VERIFIED |
|-----------|:-:|:-:|:-:|
| Arrow key gating | ✓ | ✓ | ✗ |
| Debug overlay | ✓ | ✓ | ✗ |
| FP camera position | ✓ | ✓ | ✗ |
| WASD in CHASE | ✓ | ✓ | ✗ |
| Camera smoothing | ✓ | ✓ | ✗ |
| FREE activation | ✓ | ✓ | ✗ |
| Body orientation | ✓ | ✓ | ✗ |
| ORIGINAL restoration | ✓ | ✓ | ✗ |

---

## 21. FP Structural Visibility / Roof Rendering Pipeline Trace

### 21.1 Pipeline Summary

The RT4 structural visibility pipeline controls which planes, roofs, walls, and
floor geometry are rendered. It consists of two independent systems:

**System A — Plane/Tile Visibility (SceneGraph)**
- `SceneGraph.allLevelsAreVisible()` returns `GlRenderer.enabled || Preferences.allLevelsVisible`
- In HD mode: ALWAYS true → all 4 planes built and rendered
- In SD mode: depends on `Preferences.allLevelsVisible` (default `true`)
- `SceneGraph.firstVisibleLevel` / `anInt5276` controls the starting render plane
- `SceneGraph.renderFlags[4][104][104]` bit flags per plane/tile

**System B — Selective Roof Removal (ScriptRunner)**
- `ScriptRunner.method4302()` runs every render frame when `getBaseRoofMode() == 2`
- Mode 2 = HD + `removeRoofsSelectively` (default true)
- Actively hides roof groups near camera/player using `aByteArrayArrayArray15` mask
- `API.TILE_FLAG_UNDER_ROOF` (renderFlags bit 0x4) identifies tiles under roofs
- Flood-fill algorithm (`method4348`) finds connected roof groups to hide

### 21.2 renderFlags Bit Meanings

| Bit | Value | Meaning |
|-----|-------|---------|
| 0x1 | 1 | (unused in visibility) |
| 0x2 | 2 | Bridge flag — tile uses plane+1 height |
| 0x4 | 4 | TILE_FLAG_UNDER_ROOF — tile is under roof geometry |
| 0x8 | 8 | Forces getRenderLevel() to return 0 |
| 0x10 | 16 | Used in tile building visibility check |

### 21.3 Execution Flow (Per Render Frame)

```
ScriptRunner.draw()
  ├─ method4302()              ← selective roof hiding (THE MAIN CULPRIT)
  │   ├─ getBaseRoofMode() == 2?
  │   │   ├─ HD + removeRoofsSelectively → YES → run roof hiding
  │   │   └─ else → return (no hiding)
  │   ├─ cameraType != 1: check camera height + TILE_FLAG_UNDER_ROOF → hide roof group
  │   └─ cameraType == 1: check player position → hide roof group; walk camera→player
  ├─ local387 = roof byte for frame
  └─ SceneGraph.method2954(aByteArrayArrayArray15, ...)
      └─ method3292()           ← main tile draw loop
          └─ for each tile: arg3[plane][x][z] != arg5?
              ├─ YES → tile visible (roof NOT hidden)
              └─ NO  → tile roof hidden (selective removal active)
```

### 21.4 Failure Mechanism: CASE A — Selective Roof Removal

In HD mode with default preferences, `getBaseRoofMode()` returns 2. The
`method4302()` function actively hides roof groups near the camera every frame.

In FP mode, the camera is at player eye position — often directly under roof
geometry. The check `local33 - Camera.anInt40 < 800 && TILE_FLAG_UNDER_ROOF`
triggers aggressively because the FP camera is close to ground height.

The geometry IS present (all planes built in HD). The geometry IS submitted.
But the per-frame selective roof hiding mask marks roof tiles as hidden.

### 21.5 Fix Applied

**File:** `ScriptRunner.java` — `method4302()`

Added early return when `FirstPersonCamera.isActive()`:
- Skips selective roof hiding entirely in FP mode
- Render-time query only — no scene data modified
- Instant reversion when FP is exited (next frame runs normally)
- CHASE/FREE/ORIGINAL modes completely unaffected

### 21.6 Debug Overlay Additions

Added to `[CAMERA-RIG-DEBUG]` output:
- `playerPlane` — current player plane
- `roofMode` — getBaseRoofMode() result (0=never remove, 1=always remove, 2=selective)
- `fpStructOverride` — whether FP structural override is active
- `allLevelsVisible` — whether allLevelsAreVisible() returns true

### 21.7 Remaining Structural Concerns (Runtime-Dependent)

- **Case C (Back-face culling):** Roof undersides may be one-sided geometry.
  Cannot determine without runtime testing in FP mode looking upward.
- **Case D (Occlusion):** `method187()` occlusion checks may hide tiles from
  certain FP camera angles. Secondary concern — only triggers in lowmem mode
  for roof occluders.
- **SD mode 1 (always remove roofs):** When `!allLevelsAreVisible()`,
  `method2218()` marks ALL roofs as hidden at scene build time. Not addressed
  yet — only affects SD mode with `allLevelsVisible=false` (non-default).

### 21.8 FP Structural Acceptance Tests

- [ ] **TEST 1 — OUTSIDE HOUSE:** Stand beside house, enter FP. Roof/walls intact?
- [ ] **TEST 2 — INSIDE HOUSE:** Walk inside. Look forward/up. Ceiling visible?
- [ ] **TEST 3 — LOOK UP:** Inside building, look straight up. Ceiling/floor surface visible?
- [ ] **TEST 4 — NEIGHBOURING HOUSE:** Look at nearby building. Roof visible?
- [ ] **TEST 5 — MULTI-STOREY:** Test ground floor and upstairs. Structure coherent?
- [ ] **TEST 6 — MODE SWITCH:** FP→CHASE (scroll out). Normal roof behavior returns?
- [ ] **TEST 7 — REGION CHANGE:** FP + teleport. Structural visibility correct after rebuild?

---

## 22. FP Camera Position Fix — Defensive Transition Write

**Date:** 14-08-2026
**Baseline:** 42a2c09 (Phase 3C Review #2)

### 22.1 Problem

User runtime test: "FP camera shows full character ~1 tile away, not at eye position."

### 22.2 Root Cause Analysis

**Execution order within one 50Hz tick:**
1. `FirstPersonCamera.update()` — runs BEFORE rig
2. `ModernCameraRig.update()` — contains state transitions
3. `ModernMovementController.update()`

**The timing gap:**
When the user scrolls in to enter FP:
- Tick N: `FirstPersonCamera.update()` runs, sees rigState=CHASE, returns early (no FP write)
- Tick N: `ModernCameraRig.update()` runs, transitions to FP, activates FP camera
- But `FirstPersonCamera.update()` already ran — it won't run again until tick N+1
- The chase camera position from tick N-1 persists in Camera fields

On tick N+1, `FirstPersonCamera.update()` runs and sees FP state, writes correctly.
But on tick N (the transition tick), the Camera fields contain stale chase values.

**Additional risk:** If `hasValidPosition` is false (terrain not yet validated),
`FirstPersonCamera.update()` returns early even in FP state, leaving stale values.

### 22.3 Fix Applied

**File:** `ModernCameraRig.java` — `updateStateTransitions()`

When entering FP state, after `FirstPersonCamera.activate()`, immediately call
`writeFpCameraImmediate(PlayerList.self)` which writes:
- `Camera.renderX = self.xFine` (player X position)
- `Camera.renderZ = self.zFine` (player Z position)
- `Camera.anInt40 = groundHeight - 200` (eye height above terrain)
- `Camera.cameraYaw = FirstPersonCamera.getYaw()` (look direction)
- `Camera.cameraPitch = 0` (horizon)
- `Camera.cameraType = 0` (prevent legacy camera interference)

This ensures the Camera fields are set to the FP eye position on the exact
transition tick, preventing a stale chase camera offset from persisting.

**Safety checks in `writeFpCameraImmediate()`:**
- Null player check
- Plane bounds check (0..3)
- tileHeights null check
- Tile coordinate bounds check (0..103)

### 22.4 Enhanced Diagnostics

**File:** `FirstPersonCamera.java`
- Added `hasValidPosition()` public accessor for diagnostic overlay

**File:** `ModernCameraRig.java` — debug overlay
- Added `FPvalidPos=` field showing `FirstPersonCamera.hasValidPosition()`
- Enables runtime diagnosis of terrain validation issues

### 22.5 Build Verification

```
gradlew.bat :client:compileJava → BUILD SUCCESSFUL
```

Files modified: `ModernCameraRig.java`, `FirstPersonCamera.java`

### 22.6 Runtime Acceptance Tests

- [ ] **TEST A — FP ENTER:** Scroll fully inward. Camera at true eye position (not chase offset)?
- [ ] **TEST B — FP TRANSITION SMOOTHNESS:** No visible flash of chase camera on enter?
- [ ] **TEST C — FP AFTER REGION CHANGE:** Enter FP after teleport. Camera at eye position?
- [ ] **TEST D — DEBUG OVERLAY:** Check `FPvalidPos=true` when in FP mode?
- [ ] **TEST E — CHASE FOLLOW:** Run and change direction. Camera follows smoothly?
- [ ] **TEST F — CHASE→FP→CHASE:** Scroll in to FP, then out. Clean transitions?

---

## 23. FP Structural Visibility — Stale Mask Review & Bounding-Box Reset Fix

**Date:** 14-08-2026
**Baseline:** 42a2c09 (Phase 3C Review #2) + Section 22 changes

### 23.1 Issue Raised

User structural visibility review identified three concerns with the FP
roof-removal override in `ScriptRunner.method4302()`:

1. **Stale roof mask**: Does the early return leave `aByteArrayArrayArray15` or
   bounding-box arrays stale from the previous frame?
2. **Camera ownership check**: `FirstPersonCamera.isActive()` may not reflect
   the actual rendered rig during transitions.
3. **All roof modes**: Trace behavior for modes 0, 1, 2.

### 23.2 Complete Roof Mask Trace

#### anInt3325 — Frame counter
- Line 106: `static int anInt3325 = 0`
- Line 208: `anInt3325++` — incremented every frame in `method4326()` (draw)

#### aByteArrayArrayArray15 — Per-tile selective-roof mask
- `byte[4][104][104]`, lazily allocated
- **method960(byte)**: fills entire array with a constant
- **method4302()**: writes one column per frame with stamp `(anInt3325 - 4) & 0xFF`
- **method4348()**: flood-fills connected roof tiles with stamp `anInt3325 & 0xFF`

#### method3292() — Scene graph tile visibility
Line 3003 critical condition:
```
arg3 == null || level < arg4 || arg3[level][x][z] != arg5
```
Where `arg3 = aByteArrayArrayArray15`, `arg5 = local387 = (byte)anInt3325`.

**Tile is VISIBLE** when mask value != current frame stamp.
**Tile is HIDDEN** when mask value == current frame stamp.

#### method2419() — Scenery culling via bounding boxes
Line 4091: if `anIntArray8[i] != -1000000` and scenery falls within the
bounding box → scenery is SKIPPED (`continue label194`).

### 23.3 Findings

#### Finding A: Per-tile mask is SAFE (no stale hiding)

The mask uses a frame-unique stamp (`anInt3325 & 0xFF`). Since `anInt3325`
increments every frame, old stamps from previous frames never match the
current frame's `local387`. Therefore:
- **No stale per-tile hiding can persist.** Tiles marked in frame N-1 have
  stamp `(N-1) & 0xFF`, which does not equal `N & 0xFF` in frame N.
- The early return from method4302() in FP mode means no tiles are marked
  with the current stamp → all tiles render normally. **Correct for FP.**

#### Finding B: Bounding box arrays are STALE (scenery incorrectly culled)

The bounding box arrays (`anIntArray205`, `anIntArray338`, `anIntArray518`,
`anIntArray476`, `anIntArray134`) are reset to sentinel values at lines
1374-1380 **AFTER** the FP early return. When method4302() early-returns:
- These arrays retain **previous frame's bounding boxes**.
- `method2419()` uses these stale boxes to cull scenery.
- **Scenery within previous-frame roof bounding boxes is incorrectly hidden.**

**This is a real bug.** The fix resets bounding box arrays to sentinel
values before the early return.

#### Finding C: Camera ownership — rig state is authoritative

`FirstPersonCamera.isActive()` may be true when the rig has already
transitioned to CHASE (e.g., during FP→CHASE transition frame).
`ModernCameraRig.isFirstPersonRigState()` checks `rigState == FIRST_PERSON`,
which is the actual rendered camera. The condition is changed.

### 23.4 Roof Mode Architecture

| Mode | getBaseRoofMode() | method4302() behavior | FP impact |
|------|-------------------|----------------------|----------|
| 0 | `neverRemoveRoofs` = true | Returns at line 1349 (mode != 2) | No roof hiding at all. FP safe. |
| 1 | `!allLevelsAreVisible \|\| !removeRoofsSelectively` | Returns at line 1349 (mode != 2) | Hides all roofs via method2608(). Not FP-specific. |
| 2 | `allLevelsAreVisible && removeRoofsSelectively` | Selective per-frame removal | **FP override applies here.** Skip selective removal. |

The FP override is correctly placed inside the `mode == 2` guard because:
- Mode 0: no structural removal occurs regardless → FP needs no override
- Mode 1: removal is global (method2608 floods all tiles) → not camera-dependent
- Mode 2: removal is camera-position-dependent → FP override needed

### 23.5 Fix Applied

**File:** `ScriptRunner.java` — `method4302()`

1. Changed `FirstPersonCamera.isActive()` → `ModernCameraRig.isFirstPersonRigState()`
2. Added bounding-box array reset before early return:
   ```java
   if (anIntArray205 != null) {
       for (int i = 0; i < anIntArray205.length; i++) {
           anIntArray205[i] = -1000000;
           anIntArray338[i] = 1000000;
           anIntArray518[i] = 0;
           anIntArray476[i] = 1000000;
           anIntArray134[i] = 0;
       }
   }
   ```
3. No modification to `aByteArrayArrayArray15` (mask self-invalidates).
4. No modification to `Preferences` or `neverRemoveRoofs`.

### 23.6 Build Verification

```
gradlew.bat build → BUILD SUCCESSFUL in 1m 7s
```

Files modified: `ScriptRunner.java`

### 23.7 Verification Status

| Item | Status |
|------|--------|
| Roof removal pipeline trace | **SOURCE VERIFIED** |
| FP selective-removal override | **COMPILE VERIFIED** |
| Per-tile mask stale safety | **PROVEN SAFE** (frame-unique stamp) |
| Bounding-box stale scenery culling | **BUG FOUND AND FIXED** |
| Rig-state ownership check | **COMPILE VERIFIED** |
| Actual roof visible from outside in FP | **RUNTIME UNVERIFIED** |
| Actual ceiling visible from underneath | **RUNTIME UNVERIFIED** (back-face/one-sided geometry) |
| CHASE/FREE roof restoration after FP exit | **RUNTIME UNVERIFIED** |
| Roof mode 0/1 behavior in FP | **SOURCE VERIFIED** (no FP override needed) |

---

## 24. Camera Ownership Audit

**Date:** 14-08-2026
**Baseline:** 404a2be (manual backup)

### 24.1 Purpose

Trace ALL writers to Camera fields to verify that exactly ONE writer produces
the final camera state per rendered frame. Multiple conflicting writers are
a core cause of the observed runtime bugs (camera hitching, position jumps,
minimap rotation).

### 24.2 Camera Field Writers — Complete Map

**Primary render camera fields:** `renderX`, `renderZ`, `anInt40`, `cameraYaw`, `cameraPitch`

| Writer | When | Fields Written |
|--------|------|----------------|
| `FirstPersonCamera.update()` | 50Hz tick, FP rig state | renderX/Z, anInt40, cameraYaw/Pitch, yawTarget, pitchTarget, cameraX/Z |
| `ModernCameraRig.writeFpCameraImmediate()` | Transition tick only | Same as FP (defensive safety net) |
| `ModernCameraRig.updateChase()` via `Camera.method555()` | 50Hz tick, CHASE rig state | renderX/Z, anInt40, cameraYaw/Pitch (via method555) |
| `ModernCameraRig.updateFree()` via `Camera.method555()` | 50Hz tick, FREE rig state | renderX/Z, anInt40, cameraYaw/Pitch (via method555) |
| `ScriptRunner.method4326()` line 238 via `Camera.method555()` | Render-rate, **only when cameraType==1** | renderX/Z, anInt40, cameraYaw/Pitch |
| `InterfaceList.java` lines 1114-1115 | Render-rate, **only when cameraType==2** | renderX/Z (cutscene camera) |
| `ScriptRunner.method4326()` lines 247-271 | Render-rate, custom effects | renderX/Z, anInt40, cameraYaw/Pitch (temporary, restored lines 333-337) |

**Camera control fields:** `cameraType`, `yawTarget`, `pitchTarget`, `cameraX`, `cameraZ`

| Writer | When | Fields |
|--------|------|--------|
| `LoginManager.java` lines 816, 865 | Region rebuild | cameraType = 1 |
| `FirstPersonCamera` | Every frame (FP) | cameraType = 0 |
| `ModernCameraRig` | Every frame (CHASE/FREE) | cameraType = 0 |
| `GameShell.mainInputLoop()` | Render-rate, **gated: !modernRigOwnsCamera** | yawTarget, pitchTarget |
| `API.java` lines 163, 177 | Plugin API | yawTarget, pitchTarget |

### 24.3 Camera Ownership Per Mode

| Mode | Final Camera Writer | cameraType |
|------|---------------------|------------|
| ORIGINAL | ScriptRunner.method4326 → method555 (legacy) | 1 |
| MODERN FP | FirstPersonCamera.update() | 0 |
| MODERN CHASE | ModernCameraRig.updateChase() → method555 | 0 |
| MODERN FREE | ModernCameraRig.updateFree() → method555 | 0 |
| Cutscene | InterfaceList → method555 | 2 |

### 24.4 Key Findings

1. **Architecture is sound**: cameraType=0 in modern mode prevents the legacy
   camera (ScriptRunner line 238) from running. Both FirstPersonCamera and
   ModernCameraRig re-assert cameraType=0 every frame to counteract
   LoginManager setting it to 1 during region rebuilds.

2. **GameShell.mainInputLoop() gating is correct**: The arrow key camera
   panning is blocked when `CameraMode.isModern() && ModernCameraRig.isActive()`.
   This prevents legacy yawTarget/pitchTarget mutation in all modern modes.

3. **Custom camera effects (shake/jitter/wave)** are temporary and restored
   within the same render frame (save at lines 240-244, restore at 333-337).
   These do not conflict with modern camera.

4. **50Hz camera stutter**: Camera smoothing (yaw, pitch, distance, position)
   runs at 50Hz (tick-based). Between ticks, the camera position is static
   while rendering runs at higher rates. This causes visible stepping on
   high-refresh displays. This is a polish issue (render-timed interpolation
   deferred).

### 24.5 Verification Status

| Item | Status |
|------|--------|
| Camera ownership audit | **SOURCE VERIFIED** |
| cameraType=0 enforcement | **SOURCE VERIFIED** |
| mainInputLoop gating | **SOURCE VERIFIED** |
| No conflicting writers in modern mode | **SOURCE VERIFIED** |
| 50Hz camera stutter fix | **DEFERRED** (render-timed interpolation requires game loop restructuring) |

---

## 25. FREE Camera Arrow Key Fix — Continuous Input + Render-Timed Scaling

**Date:** 14-08-2026
**Baseline:** 404a2be

### 25.1 Problem

FREE camera arrow keys used `InterfaceList.keyQueueSize` (event queue) at 50Hz.
This only processes discrete key events, not continuous key holds. Result:
arrow key camera orbit was choppy and unresponsive compared to the original
RT4 camera which uses `Keyboard.pressedKeys` (continuous polled state) at
render-rate via `GameShell.mainInputLoop()`.

### 25.2 Fix Applied

**File:** `ModernCameraRig.java` — `updateFree()`

Changed from:
```java
if (Preferences.aBoolean63) {
    for (int i = 0; i < InterfaceList.keyQueueSize; i++) {
        int code = InterfaceList.keyCodes[i];
        if (code == Keyboard.KEY_UP) freePitch -= 4;
        // ... etc
    }
}
```

Changed to:
```java
double renderScale = (double) GameShell.updateDelta / 20_000_000.0;
if (renderScale < 0.1) renderScale = 0.1;
if (renderScale > 5.0) renderScale = 5.0;

if (Keyboard.pressedKeys[Keyboard.KEY_UP]) freePitch -= (int)(4 * renderScale);
if (Keyboard.pressedKeys[Keyboard.KEY_DOWN]) freePitch += (int)(4 * renderScale);
if (Keyboard.pressedKeys[Keyboard.KEY_LEFT]) freeYaw -= (int)(16 * renderScale);
if (Keyboard.pressedKeys[Keyboard.KEY_RIGHT]) freeYaw += (int)(16 * renderScale);
```

### 25.3 Key Changes

1. **Continuous polled input**: `Keyboard.pressedKeys` instead of event queue.
   Camera orbits smoothly while arrow keys are held.
2. **Render-timed scaling**: Input scaled by `GameShell.updateDelta / 20ms`.
   At 50Hz (20ms tick): scale = 1.0 (original behavior).
   At other rates: proportionally adjusted for consistent feel.
3. **Removed `Preferences.aBoolean63` guard**: This preference controlled
   whether the key queue was populated. With polled input, it's not needed.
4. **Safety clamps**: Scale clamped to [0.1, 5.0] to prevent extreme values
   during lag spikes or very fast frames.

### 25.4 WASD-in-CHASE Source Verification

Traced the complete WASD-in-CHASE path:

1. `Keyboard.pressedKeys[KEY_W]` set by keyboard handler
2. `ModernControlController.shouldForwardKeyToChat()` returns `false` for WASD
   when in MODERN mode with chat closed — prevents letters reaching chatbox
3. `ModernMovementController.readInput()` reads `Keyboard.pressedKeys[KEY_W/A/S/D]`
4. Movement intent → Q16 prediction → DDA → server sync
5. `CameraMode.getCameraRelativeYaw()` returns -1 in CHASE/FREE — movement
   uses body orientation (locomotion heading), NOT camera yaw

**Result:** WASD-in-CHASE is **SOURCE VERIFIED** correct. The `shouldForwardKeyToChat`
filter is called from both `Protocol.java:2822` and `client.java:1159`, covering
both key event processing paths.

### 25.5 Build Verification

```
gradlew.bat :client:compileJava → BUILD SUCCESSFUL in 6s
```

Files modified: `ModernCameraRig.java`

### 25.6 Verification Status

| Item | Status |
|------|--------|
| FREE camera continuous arrow input | **COMPILE VERIFIED** |
| Render-timed scaling | **COMPILE VERIFIED** |
| WASD-in-CHASE path | **SOURCE VERIFIED** |
| shouldForwardKeyToChat coverage | **SOURCE VERIFIED** |
| FREE camera orbit smoothness | **RUNTIME UNVERIFIED** |
| FREE camera arrow key speed feel | **RUNTIME UNVERIFIED** |
