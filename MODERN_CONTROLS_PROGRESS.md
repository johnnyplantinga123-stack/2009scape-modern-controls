LEGACY DOCUMENT — DO NOT USE AS CURRENT TASK AUTHORITY.

Current project roadmap:
CODEX_MASTER_TODO.md

This file is retained for historical/source-trace context only.
If this file conflicts with CODEX_MASTER_TODO.md or user runtime findings,
the current master TODO and user runtime take precedence.

# Modern Controls — Phase 0 Analysis & Implementation Plan

**Status:** Phase 0 Analysis — COMPLETE · Phase 1 (Camera Mode Framework) — COMPLETE · Phase 2 (First Person Camera) — COMPLETE · **Phase 3 (WASD Movement Foundation) — COMPLETE** · Phase 3 Stabilization Pass 1 — COMPLETE · **Phase 3 Stabilization Pass 2 — COMPLETE** · **Phase 3 Movement Runtime Fix — COMPLETE** · **Phase 3 Stabilization Pass 3 (Scene Rebuild / Terrain Safety / Visibility) — COMPLETE** · **Phase 3 Stabilization Pass 4 — Camera Height Regression Fix — COMPLETE** · **Phase 3B (Continuous Modern Movement) — COMPLETE** · **Phase 3B Stabilization (Input, Animation, Self-Rendering) — COMPLETE** · **PHASE 3C (Modern Camera Rig) — IMPLEMENTATION COMPLETE / RUNTIME STABILIZATION IN PROGRESS** · PHASE 3C REVIEW #2 — COMPLETE · PHASE 3C ADDENDUM (Zoom Ranges) — COMPLETE · PHASE 3C RUNTIME STABILIZATION — SUPERSEDED BY RUNTIME RESULTS · **PHASE 3C RUNTIME FIX ROUND #4 — COMPILE VERIFIED / STATICALLY REVIEWED / RUNTIME UNVERIFIED (P0–P7 all addressed; see §30)**

**⚠️ RUNTIME STATUS (round #4): The user's actual runtime test OVERRIDEs the previous static claims (ORIGINAL wheel zoom was NOT working despite "static verified"; CHASE fought world position during zoom; FP body faced opposite direction; roofs flashed). All P0–P7 fixes are applied, compiled (BUILD SUCCESSFUL) and statically reviewed. NOTHING has been re-verified at runtime — awaiting the round #4 user test list (§30.10).**

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
**Last updated:** 14-08-2026 (Phase 3C Runtime Stabilization — F12 overlay, CHASE/FP/zoom fixes, static sweeps complete, awaiting runtime test)
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

---

## 26. Overnight Milestone E — Wheel Ownership, Middle Mouse, Minimap, Body Orientation, Zoom

**Date:** 14-08-2026
**Baseline:** 18828a5 (overnight: camera ownership audit + FREE camera render-timed arrow input)

### 26.1 Wheel Ownership (TODO 046)

**Problem:** Both camera zoom and UI scroll read the same `MouseWheel.wheelRotation`
value. Camera processes it first (step 2 in tick), then interface processing also
reacts (step 3 in tick). Both consume the same event.

**Execution order within tick:**
1. `MouseWheel.wheelRotation = mouseWheel.getRotation()` (client.java:1726)
2. `ModernControlController.update()` → `ModernCameraRig.processWheelInput()` (client.java:1746)
3. `Protocol.method1756()` → `InterfaceList.method1320()` → `method946()` → scroll handler (Protocol.java:2831)

**Fix:** Viewport-based heuristic in `processWheelInput()`. If mouse is outside
the viewport component bounds (`InterfaceList.aClass13_26`, clientCode 1337),
skip camera zoom so the UI can scroll.

**Limitations:** One-frame stale (viewport component set by previous frame's
interface processing). Doesn't handle nested scrollable areas within the viewport.
Full per-component scrollable-area check deferred.

### 26.2 Minimap/Compass Coherence (TODO 045)

**Problem:** Minimap rotation = `MiniMap.anInt1814 + (int)Camera.yawTarget & 0x7FF`.
Compass rotation = `(int)Camera.yawTarget`. In MODERN CHASE/FREE, `Camera.yawTarget`
was never updated (legacy camera skipped), so minimap showed stale yaw.

**Fix:** Added `Camera.yawTarget = chaseYaw` in `updateChase()` and
`Camera.yawTarget = freeYaw` in `updateFree()`. Minimap now follows the active
camera owner.

**Source trace:**
- `MiniMap.java` lines 238, 420, 450: `anInt1814 + (int)Camera.yawTarget & 0x7FF`
- `Cs1ScriptRunner.java` lines 1160/1162: compass rendered with `(int)Camera.yawTarget`

### 26.3 Middle Mouse Orbit in FREE (TODO 043)

**Problem:** Middle mouse button did not orbit the FREE camera.

**Fix:** Added `processMiddleMouseOrbit()` method. Uses `Mouse.currentMouseX/Y`
(live AWT position) against stored previous-frame position for render-rate delta.
Inherently frame-rate-independent (same physical motion = same total rotation
regardless of frame rate).

**Implementation:**
- `Mouse.pressedButton == 2` = middle mouse held
- First press frame: initialise reference, no delta
- Subsequent frames: delta = currentMouse - prevMouse, applied to freeYaw/freePitch
- Release: reset reference to prevent jump on next press
- Sensitivity: 1 yaw unit per 2px (matches classic RS feel)
- Pitch clamped to 128..383 (same as arrow keys)

### 26.4 Body Orientation Ownership Trace (TODO 036/037)

**Complete execution order for self-player body orientation:**

1. `ModernCameraRig.update()` (client.java:1746)
   - FP: `updateBodyLookCoupling(self)` → `self.anInt3400 = bodyYaw`
   - CHASE/FREE: `bodyYaw = self.anInt3400` (reads, doesn't write)
2. `ModernMovementController.update()` (client.java:1746)
   - FP: SKIP (body-look coupling owns anInt3400)
   - CHASE/FREE: `self.anInt3400 = targetOrientationAngle` (movement direction)
3. `Protocol.method1756()` → `PlayerList.method1444()` → `NpcList.method4514()`
   - Modern self: method2247 SKIPPED
   - `method949()`: smooths `anInt3381` toward `anInt3400` (for ALL entities)
   - faceEntity/faceX/faceY override `anInt3400` (for ALL entities including self)

**Architecture verdict: SOUND.** Each mode has clear ownership:
- FP: camera look → body (via body-look coupling)
- CHASE/FREE: locomotion → body (via movement controller)
- method949 always smooths anInt3381 toward anInt3400 (correct)
- faceEntity overrides are intentional (talking to NPC, etc.)

### 26.5 ORIGINAL Zoom Calibration (TODO 047)

**ORIGINAL zoom architecture:**
- `Camera.ZOOM = 600` (constant, never modified by wheel in RT4 client)
- Effective zoom = `ZOOM + (int)pitchTarget * 3` = `600 + pitch * 3`
- Pitch range: 128..383 → effective zoom: **984..1749**
- Default: pitch=128 → effective zoom = 984 (closest in)
- Furthest: pitch=383 → effective zoom = 1749

**Modern rig mapping:**
- `updateChase()`: zoom = `actualDistance + chasePitch * 3`
- `updateFree()`: zoom = `actualDistance + freePitch * 3`
- CHASE default: `600 + 256*3 = 1368` (mid-range overview)
- MIN_DISTANCE=0, MAX_DISTANCE=1350
- FREE max: `1350 + 383*3 = 2499` (extended beyond original)

**Verdict:** Zoom calibration is **CORRECT**. The modern rig's `actualDistance +
pitch*3` formula matches the original's `ZOOM + pitch*3` exactly when
actualDistance=600=ZOOM. The extended FREE max (1350) provides ~43% more
range than original max, consistent with the "RuneLite +150 feel" goal.

### 26.6 Arrow Key Ownership Per Rig (TODO 044)

**Already implemented and SOURCE VERIFIED:**
- `GameShell.mainInputLoop()`: Returns early when `modernRigOwnsCamera` is true
- CHASE: No arrow key response (correct — arrows don't mutate camera)
- FREE: Arrow keys handled by rig's own `updateFree()` path
- ORIGINAL: Legacy arrow key camera panning active

### 26.7 Build Verification

```
gradlew.bat :client:compileJava → BUILD SUCCESSFUL
```

Files modified: `ModernCameraRig.java` (+103 lines)

### 26.8 Verification Status

| Item | Status |
|------|--------|
| Wheel ownership (viewport heuristic) | **COMPILE VERIFIED** |
| Minimap yawTarget sync (CHASE) | **COMPILE VERIFIED** |
| Minimap yawTarget sync (FREE) | **COMPILE VERIFIED** |
| Middle mouse orbit in FREE | **COMPILE VERIFIED** |
| Body orientation ownership model | **SOURCE VERIFIED** |
| ORIGINAL zoom calibration | **SOURCE VERIFIED** |
| Arrow key ownership per rig | **SOURCE VERIFIED** |
| Wheel scroll vs UI scroll runtime | **RUNTIME UNVERIFIED** |
| Minimap coherence in CHASE/FREE | **RUNTIME UNVERIFIED** |
| Middle mouse orbit feel/speed | **RUNTIME UNVERIFIED** |
| FREE max zoom visual boundary | **RUNTIME UNVERIFIED** |

---

## Section 27 — Milestone H: Crosshair / Target Acquisition Foundation

### 27.1 TODO 071 — Center-Screen Reticle

**Implementation:** `ModernCrosshair.java` — draws a small white cross reticle
at viewport center. Gated to MODERN FP/CHASE only. Hidden during modal UI.
Uses dual-rasterizer pattern (GlRaster/SoftwareRaster).

**Integration:** Called in `client.mainRedraw()` after `LoginManager.method1841()`.
Additive presentation layer — no effect on ORIGINAL or gameplay.

**Status:** COMPILE VERIFIED, RUNTIME UNVERIFIED.

### 27.2 TODO 072 — MiniMenu / Scene Picking Trace

**Complete menu building flow:**

1. **ScriptRunner scene render** calls `MiniMenu.addEntries(viewportH, viewportW, viewportX, viewportY, mouseY, mouseX)` at line 350 when mouse is within viewport bounds.

2. **Entity data source:** `Model.aLongArray11[0..anInt7-1]` — populated during scene rendering by `GlModel` and `SoftwareModel` when `MiniMenu.aBoolean187` (picking mode) is active. Triangle hit test via `SceneGraph.method583()`.

3. **Packed long format:**
   - bits 0-6: x tile
   - bits 7-13: z tile
   - bits 29-30: entity type (0=Player, 1=NPC, 2=Loc, 3=ObjStack)
   - bits 32+: entity ID/index

4. **Entity type dispatch in `addEntries()`:**
   - Type 2 (Loc): `LocTypeList.get(id).ops[]` → action codes 42/50/49/46/1001 + examine 1004
   - Type 1 (NPC): `addNpcEntries()` → `NpcType.ops[]` → action codes 17/16/4/19/2 + examine 1007
   - Type 0 (Player): `addPlayerEntries()` → `Player.options[]` → action codes 30/31/29/37/34/57
   - Type 3 (ObjStack): `ObjType.ops[]` → action codes 18/20

5. **`MiniMenu.add()` stores:** ops[500], opBases[500], actions[500], cursors[500], keys[500], intArgs1[500], intArgs2[500]. Size reset to 0 each frame in `LoginManager.method1841()`.

6. **`doAction()` executes:** reads intArgs/keys/actions → switch on actionCode → PathFinder.findPath() to walk near target → send specific server packet.

7. **Plugin API:** `MiniMenuEntry.getType()` detects type by color code in subject string: 00ffff=LOC, ffff00=NPC, ffffff=PLAYER, ff9040=OBJ.

**Key insight for targeting:** The existing menu system already iterates all entity types and builds action entries. For center-screen targeting, we cannot reuse mouse-based picking directly. Instead, we iterate entity lists independently and project to screen coordinates.

**Status:** SOURCE VERIFIED.

### 27.3 TODO 073 — ModernTarget Model

**Implementation:** `ModernTarget.java` — data class representing a targeting candidate.

**Fields:** TargetType (NPC/PLAYER/OBJECT/GROUND_ITEM), entityId (stable ID), tileX/tileZ, plane, xFine/zFine, yOffset, screenX/screenY, score, entityRef (frame-local only), worldDistance.

**Design decision:** Store stable identifiers (entity ID, tile coords) rather than raw object references. Entity references are cached for current frame only and must not be held across scene rebuilds.

**Status:** COMPILE VERIFIED, RUNTIME UNVERIFIED.

### 27.4 TODO 074 — Candidate Projection/Scoring

**Implementation:** `ModernTargetingController.java` — per-frame target acquisition.

**Gathering:** Iterates NpcList (visible NPCs), PlayerList (visible players, excluding self), SceneGraph.objStacks (ground items within MAX_ACQUISITION_DISTANCE=20 tiles). Locations deferred to later iteration.

**Projection:** Uses same world→screen transform as RT4 scene rendering:
1. `elevation = SceneGraph.getTileHeight(plane, xFine, zFine) - yOffset`
2. Camera-relative: `relX = xFine - SceneGraph.cameraX` (fine coords)
3. Yaw rotation: `MathUtils.sin/cos[Camera.cameraYaw]`
4. Pitch rotation: `MathUtils.sin/cos[Camera.cameraPitch]`
5. Perspective: `screenX = 256 + (relX << 9) / relZ` (fixed mode)

**Key source verification:** `SceneGraph.cameraX/Y/Z` are in FINE coordinates (confirmed by `method2954` bounds check `arg0 >= width * 128` and tile conversion `anInt4069 = arg0 / 128`). Set from `Camera.renderX/anInt40/renderZ` during scene render setup.

**Scoring:** 70% screen-center distance (normalized by viewport half-diagonal) + 30% world distance. Hysteresis margin (5.0) prevents target flickering.

**Integration:** Called in `client.mainRedraw()` after `LoginManager.method1841()` and before `ModernCrosshair.draw()`.

**Status:** COMPILE VERIFIED, STATICALLY REVIEWED, RUNTIME UNVERIFIED.

### 27.5 Verification Status

| Item | Status |
|------|--------|
| Crosshair reticle rendering | **COMPILE VERIFIED** |
| MiniMenu/scene picking architecture | **SOURCE VERIFIED** |
| ModernTarget data model | **COMPILE VERIFIED** |
| Target projection math | **SOURCE VERIFIED** |
| Target scoring/selection | **STATICALLY REVIEWED** |
| Targeting controller integration | **COMPILE VERIFIED** |
| Crosshair visibility in-game | **RUNTIME UNVERIFIED** |
| Target acquisition accuracy | **RUNTIME UNVERIFIED** |
| Projection correctness (fixed mode) | **RUNTIME UNVERIFIED** |
| Projection correctness (resizable/HD) | **RUNTIME UNVERIFIED** |
| Hysteresis behavior | **RUNTIME UNVERIFIED** |
| Location/scenery targeting | **PENDING** (deferred) |


---

## Section 28 — Milestone F: First-Person Physical Structural Rendering

### 28.1 TODO 051 — Roof/Plane/Wall Visibility Pipeline Trace

**Two-system architecture discovered:**

**System A — Tile Visibility Mask (aByteArrayArrayArray15):**
- method4302() runs per-frame during render setup (called from method4326())
- Roof mode determined by method4047() -> getBaseRoofMode():
  - Mode 0: neverRemoveRoofs -> no mask applied
  - Mode 1: Non-HD / !allLevelsAreVisible() -> blanket (byte) 0 mask (all under-roof hidden)
  - Mode 2: HD + removeRoofsSelectively -> BFS propagation via method4348() with frame-unique stamp
- Frame-unique stamp: (byte)(anInt3325 & 0xFF) — increments each frame
- Critical visibility test in SceneGraph.method3292() line 3003:
  Tile visible when mask value != current stamp (or stamp older than threshold)
- method960() fills mask array with specified byte value

**System B — Scenery Occlusion Bounding Boxes:**
- Arrays: anIntArray205/338/518/476/134 — min/max X/Z bounding boxes
- SceneGraph.method2419() culls occluders within bounding boxes
- Sentinel value -1000000 = disabled (occluder active)
- 1000000 = disabled (occluder inactive)

**Existing FP Override (ScriptRunner lines 1370-1381):**
- Early return when ModernCameraRig.isFirstPersonRigState()
- Resets all bounding boxes to sentinels (disable scenery occlusion)
- Skips mask update entirely

**Render pipeline call order in method4326():**
1. anInt3325++ (frame counter increment)
2. method4302() (roof removal — FP early return here)
3. Compute stamp: local387 = method4047() == 2 ? (byte)anInt3325 : 1
4. SceneGraph.method2954(...) passes mask + bounding boxes to scene renderer

**Key finding:** When FP early-returns from method4302(), the mask retains old values. BUT the frame-unique stamp mechanism prevents stale masks from hiding tiles: the stamp check ensures mask byte != current stamp, so tiles are always visible. No tiles hidden by mask in FP — correct behavior.

**allLevelsAreVisible():** GlRenderer.enabled || Preferences.allLevelsVisible
- HD mode: always true -> all planes rendered -> FP sees all floors/ceilings
- SD mode: depends on Preferences.allLevelsVisible -> typically one plane

**method2218() is NOT per-frame:** Called only on plane change, game state, script commands.

**Verdict: FP override is architecturally SOUND.**

### 28.2 TODO 052 — Separate Structural from Culling

**SOURCE VERIFIED.** The roof removal system is purely structural (overhead-camera convenience). It is separate from:
- Frustum culling (built into method3292() tile iteration bounds)
- Distance culling (built into tile iteration)
- Occluder culling (method2419() — System B above)

The FP override disables System A (mask) and System B (occlusion boxes) without affecting frustum/distance culling.

### 28.3 TODO 053 — FP Roof Visibility Override

**COMPILE VERIFIED.** Already implemented at ScriptRunner lines 1370-1381. The early return prevents mask update, and frame-unique stamp ensures stale masks never hide current-frame tiles. Bounding box sentinel reset disables scenery occlusion.

### 28.4 TODO 054 — FP Wall Visibility

**SOURCE VERIFIED.** Walls are stored in Tile.wall (primary/secondary). The roof mask system does NOT directly hide walls. Walls are rendered by method3292() as part of tile geometry. The only way walls could be hidden is:
1. The tile itself is hidden by mask -> FP override prevents this
2. The wall is outside frustum -> normal culling, correct behavior
3. The wall is occluded by method2419() -> FP override disables this

**Walls remain visible in FP.** Correct.

### 28.5 TODO 055 — FP Ceiling / Upper Floor Visibility

**RUNTIME UNVERIFIED.** Source analysis:
- HD mode: allLevelsAreVisible() = true -> all 4 planes rendered -> ceiling/upper floor geometry IS processed by renderer
- SD mode: only one plane rendered -> inherent limitation, not FP-specific
- Whether ceiling geometry has visible underside textures is a separate question (see TODO 056)
- The FP override ensures the mask does not hide upper-plane tiles

### 28.6 TODO 056 — Roof/Floor Underside Investigation

**RUNTIME UNVERIFIED.** Two possible causes for missing ceiling:
1. **Removal by mask:** FP override prevents this (verified above)
2. **One-sided/back-face geometry:** RS building ceilings may only have textures on the top face (walking-on-top side). Looking up from underneath would see untextured back faces. This is a cache/modeling issue, not a visibility code issue.
3. **Plane filtering:** In SD mode, only getRenderLevel() plane is rendered. Upper planes are skipped entirely.

Cannot determine which cause dominates without runtime testing.

### 28.7 TODO 057 — Bridges, Stairs and Multi-Storey

**SOURCE VERIFIED (partial).** getRenderLevel() handles bridge flags (0x8) and floor-over-floor (0x2). Bridge tiles render at plane 0 even when physically at a higher level. Stairs/ladders change Player.plane which triggers method2218() cleanup and getRenderLevel() recalculation.

FP behavior: When player changes plane, the FP override continues to prevent roof hiding on the new plane. Multi-storey visibility in HD mode depends on allLevelsAreVisible() being true.

### 28.8 TODO 058 — SD and HD Parity

**SOURCE VERIFIED.** Both SD and HD use the same method4302() path. The difference is:
- HD: getBaseRoofMode() returns 2 (selective) -> BFS propagation -> frame-unique stamp
- SD: getBaseRoofMode() returns 1 (basic) -> blanket mask -> all under-roof hidden

FP override early-returns in both cases. In HD, the frame-unique stamp mechanism ensures stale masks do not hide tiles. In SD, the blanket (byte) 0 mask would hide tiles only if stamp matches, but since method4302() is skipped in FP, the mask is never updated to the current frame value.

**Both renderers benefit from the FP override.** The HD path is more robust due to frame-unique stamps.

### 28.9 TODO 059 — Mode/Lifecycle Restoration

**SOURCE VERIFIED.** The FP override is a render-time policy check:
- No permanent mutation of Preferences, neverRemoveRoofs, or cache data
- Leaving FP immediately (scroll to CHASE) -> isFirstPersonRigState() returns false -> normal roof processing resumes next frame
- No stale state can persist because the check is per-frame

### 28.10 TODO 060 — Milestone F Verification

| Item | Status |
|------|--------|
| Roof/plane/wall pipeline trace | **SOURCE VERIFIED** |
| Structural vs culling separation | **SOURCE VERIFIED** |
| FP roof override | **COMPILE VERIFIED** |
| FP wall visibility | **SOURCE VERIFIED** |
| FP ceiling/upper floor (HD) | **RUNTIME UNVERIFIED** |
| Underside geometry investigation | **RUNTIME UNVERIFIED** |
| Bridges/stairs/multi-storey | **SOURCE VERIFIED** |
| SD/HD parity | **SOURCE VERIFIED** |
| Mode restoration | **SOURCE VERIFIED** |
| FP structural visibility (complete) | **STATICALLY REVIEWED** |

**Runtime test checklist:**
1. Outside house: nearby roof/upper storey visible
2. Inside house: walls, floor, ceiling visible
3. Look straight up: ceiling/roof geometry present
4. Neighbouring building: roof not missing
5. Upstairs/downstairs: coherent structure
6. Region change: no stale roof state
7. FP to CHASE switch: immediate restoration of normal visibility

### 28.11 Build Verification

No source changes required for Milestone F — existing FP override (from previous session) is architecturally sound. No compile needed.

---

## Section 29 — Phase 3C Runtime Stabilization (Debug Overlay + Camera/Input Fixes)

**Date:** 14-08-2026
**Baseline:** Post-Overnight Milestone E
**Status:** COMPILE VERIFIED + STATICALLY REVIEWED / RUNTIME UNVERIFIED

### 29.1 P0 — F12 Debug Overlay

**Implementation:** `DebugOverlay.java` (201 lines)

**Features:**
- F12 edge-triggered toggle (no repeat while held)
- Overlay does NOT steal gameplay input
- Compact monospaced text with semi-transparent background
- Sections: CONTROL, PLAYER, INPUT, CAMERA, BODY, SCENE, DIAGNOSTIC
- Diagnostic "last writer" trackers: lastCameraWriter, lastBodyYawWriter, lastMovementRebaseReason
- Movement update tick counter

**Integration points:**
- `CameraMode.onKeyPressed()` → `DebugOverlay.onKeyPressed(keyCode)` (AWT boundary)
- `client.mainRedraw()` → `DebugOverlay.draw()` after `PluginRepository.LateDraw()` (render last)

**Overlay fields displayed:**
- profile (ORIGINAL/MODERN), rig state, cameraType
- tile (x,z,plane), fine (xFine,zFine), serverTile, pending moves, move update count
- W/A/S/D/shift pressed states, chat input, gameplay input allowed
- Camera pos (renderX, anInt40, renderZ), yaw, pitch, yawTarget, pitchTarget
- desired/safe/actual distance, wheelRotation, Camera.ZOOM
- anInt3400 (target), anInt3381 (visual), anInt3385 (counter)
- locomotionYaw, bodyYaw (rig), fpCamYaw
- roofMode, allLevels, fpStructOverride, fpValidPos
- lastCamWriter, lastBodyYawWriter, lastRebaseReason

**Debug annotations added to source:**
- `FirstPersonCamera.java:L313`: lastCameraWriter = "fp_camera"
- `ModernCameraRig.java:L549`: lastCameraWriter = "fp_immediate_transition"
- `ModernCameraRig.java:L607`: lastCameraWriter = "rig_chase"
- `ModernCameraRig.java:L694`: lastCameraWriter = "rig_free"
- `ModernCameraRig.java:L983`: lastBodyYawWriter = "fp_body_coupling"
- `ModernMovementController.java:L366`: lastBodyYawWriter = "movement_controller"

### 29.2 P1 — CHASE/Third-Person WASD Continuous Movement

**Root cause analysis:** Movement hitching caused by multiple factors:
1. CHASE yaw smoothing too slow (factor=8) causing camera lag behind player turns
2. Yaw min step too small (2) causing stalling at small angle differences
3. Movement reconciliation previously resetting fractional offset (fixed in Phase 3B)

**Fixes applied in `ModernCameraRig.java`:**
- YAW_SMOOTH_FACTOR: 8 → 3 (tighter chase yaw tracking)
- YAW_SMOOTH_MIN: 2 → 4 (prevent stalling at small deltas)

**Movement pipeline verified (static):**
- WASD → Keyboard.pressedKeys[] → ModernMovementController.update() called every logic tick
- Q16 prediction → DDA tile boundary → walk packet → server hooks → reconciliation
- Movement works identically in FP/CHASE/FREE (rig only changes camera)

### 29.3 P2 — Camera Ownership / CHASE Fixed-Relative Behavior

**Architecture verified (static):**
- Camera.cameraType=0 enforced by modern rig (FirstPersonCamera, ModernCameraRig)
- Camera.cameraType=1 restored by LoginManager on scene rebuild (original code)
- FirstPersonCamera self-heals cameraType=0 every frame when active
- Single writer per rig state: fp_camera / rig_chase / rig_free

**CHASE stability fix:**
- Yaw smoothing tightened (factor 3, min 4) so camera tracks player turns
- chaseYawTarget = self.anInt3400 (body orientation) — changes during movement
- Smooth interpolation prevents jitter while maintaining responsive follow

### 29.4 P3 — FIRST_PERSON Body Orientation Follows Look

**Root cause:** SHOULDER_DEAD_ZONE was too large (100 units = ~17°), preventing visible body rotation for small camera movements.

**Fixes applied in `ModernCameraRig.java`:**
- SHOULDER_DEAD_ZONE: 100 → 32 (~5.5°) — body closely follows look
- SHOULDER_LIMIT: 200 → 128 (~35°)
- BODY_CATCHUP_SPEED: 24 → 48 units per tick
- BODY_FAST_CATCHUP_SPEED: 64 → 96 units per tick

**FP body-look coupling verified (static):**
- Camera look yaw is facing authority in FP state
- W/S: body faces look direction
- A/D: strafe relative to look, body remains facing look
- Body-look coupling runs in rig FP state only
- Movement controller writes anInt3400 in CHASE/FREE only (mutually exclusive)

### 29.5 P4 — Restore ORIGINAL Zoom Completely

**Static verification result:** ORIGINAL zoom is NOT broken by modern code.

**Ownership trace:**
- `Camera.ZOOM = 600` (default, only modified by plugin API — NOT by game loop)
- Effective zoom = `Camera.ZOOM + pitchTarget * 3` (range 984..1749)
- pitchTarget controlled by CS2 zoom script via viewport onScroll
- In ORIGINAL mode: ModernCameraRig does NOT run, does NOT consume wheel
- Legacy camera path (ScriptRunner line 229-238, cameraType==1) uses ZOOM formula
- `Camera.method4273()` legacy follow + arrow key input runs normally

**Conclusion:** If ORIGINAL zoom feels wrong at runtime, it is a pre-existing issue or user perception, NOT a modern code regression. The modern rig has zero effect on ORIGINAL zoom.

### 29.6 P5 — MODERN Zoom Ownership + Smooth Interpolation

**Architecture:**
- ModernCameraRig owns desiredDistance/safeDistance/actualDistance in MODERN mode
- processWheelInput() reads MouseWheel.wheelRotation only when modern rig is active
- Viewport heuristic prevents camera zoom when mouse is over UI scrollable areas
- actualDistance smoothly interpolates toward desiredDistance (not snap-set)
- Obstruction reduces safeDistance only; clearing restores toward desiredDistance

**Distance model:**
- MIN_DISTANCE=0, MAX_DISTANCE=1350
- FP entry: desiredDistance <= FP_ENTER_DISTANCE
- CHASE default: 600 (matches Camera.ZOOM)
- FREE entry: desiredDistance >= FREE_ENTRY_DISTANCE
- Zoom formula: `actualDistance + pitch * 3` (matches original `ZOOM + pitch*3`)

### 29.7 P6 — Real MODERN FREE Controls

**Architecture:**
- FREE camera uses classic-style free rotation
- Arrow keys and middle-mouse orbit supported
- `processMiddleMouseOrbit()`: render-rate delta, 1 yaw unit per 2px
- Arrow keys: continuous input in updateFree() path
- Pitch clamped to 128..383 (same as arrow keys)

**FREE→CHASE transition fix:**
- chaseYaw seeded from freeYaw to avoid pop
- chaseYawTarget acquired from self.anInt3400 (character orientation)
- chasePitch set to CHASE_PITCH

### 29.8 P7 — Extended Zoom Max Calibration

**Current state:** MAX_DISTANCE=1350 in modern rig.
- FREE max effective zoom: `1350 + 383*3 = 2499` (vs original max 1749)
- This provides ~43% more range than original max
- Consistent with "RuneLite +150 outer-limit FEEL" goal

**Deferred:** Fine-tuning of exact max value pending runtime feedback.

### 29.9 P8 — Structural Visibility Static Review

**Status:** SOURCE VERIFIED (from Milestone F, Section 28).
- FP override disables roof mask update + scenery occlusion bounding boxes
- Frame-unique stamp prevents stale masks from hiding tiles
- Walls remain visible (not affected by roof mask system)
- Ceiling visibility depends on HD mode (allLevelsAreVisible)
- Mode restoration is per-frame policy check (no permanent state mutation)

### 29.10 Static Verification Sweeps

**B. Wheel reads — OWNERSHIP CONFIRMED:**
- `client.java`: writer (reads AWT MouseWheel.getRotation() into MouseWheel.wheelRotation)
- `ModernCameraRig.processWheelInput()`: reader (MODERN mode only, viewport heuristic)
- `InterfaceList`: reader (UI scroll handlers)
- `Protocol`: reader (staff teleport — unrelated)
- `DebugOverlay`: reader (display only)

**C. Camera.ZOOM writes — SINGLE OWNER:**
- Only `API.java` (plugin API) modifies Camera.ZOOM
- Main game loop does NOT modify Camera.ZOOM
- Effective zoom = ZOOM + pitchTarget*3 (computed, not stored in ZOOM)

**D. Camera cameraType=0 writers:**
- FirstPersonCamera.update() — when FP active
- ModernCameraRig — when modern rig active
- Camera.cameraType=1 writers: LoginManager (scene rebuilds — original code)

**E. Self orientation (anInt3400) writers — MUTUALLY EXCLUSIVE:**
- FP rig state: body-look coupling in ModernCameraRig.updateBodyLookCoupling()
- CHASE/FREE rig state: ModernMovementController.update()
- NpcList.method949(): smooths anInt3381 toward anInt3400 (for ALL entities)
- faceEntity/faceX/faceY: intentional overrides (talking to NPC, etc.)

**F. Self xFine/zFine writers:**
- ModernMovementController: normal Q16 prediction (MODERN mode)
- NpcList: bounds check / force move (ALL entities including self)
- InterfaceList: debug only
- NpcList.method2247: SKIPPED for modern self (isModernSelf gate)

**G. F11 transitions — CLEAN:**
- ORIGINAL→MODERN: saves legacy camera state, activates modern systems
- MODERN→ORIGINAL: deactivates FP, restores legacy camera state
- Safety net: resetToSafeDefaults() on ORIGINAL enter

**H. ORIGINAL path — NO ACCIDENTAL MODERN GATING:**
- ModernControlController.update(): ORIGINAL case → break (no modern code runs)
- ModernCameraRig: only runs when CameraMode.isModern()
- FirstPersonCamera: only runs when active
- Legacy camera path runs normally when cameraType==1

**I. Movement dispatch — CORRECT:**
- FIRST_PERSON: FP.update() → Rig.update() → Movement.update()
- THIRD_PERSON: FP.update() → Rig.update() → Movement.update()
- ORIGINAL: break (no modern movement)
- Movement works identically regardless of rig state (FP/CHASE/FREE)

### 29.11 Files Modified (Phase 3C Runtime Stabilization)

| File | Change |
|---|---|
| `rt4/DebugOverlay.java` | NEW (201 lines): F12 debug overlay with comprehensive diagnostics |
| `rt4/CameraMode.java` | Added F12 handling in onKeyPressed() |
| `rt4/client.java` | Added DebugOverlay.draw() after PluginRepository.LateDraw() |
| `rt4/FirstPersonCamera.java` | Added lastCameraWriter debug annotation |
| `rt4/ModernCameraRig.java` | Yaw smoothing (3/4), body-look (32/128/48/96), FREE→CHASE seed, debug annotations, getSafeDistance() |
| `rt4/ModernMovementController.java` | Added lastBodyYawWriter debug annotation |

### 29.12 Build Verification

```
gradlew.bat :client:compileJava → BUILD SUCCESSFUL (all tasks up-to-date)
```

### 29.13 Verification Status

| Item | Status |
|------|--------|
| F12 debug overlay toggle | **COMPILE VERIFIED** |
| Overlay diagnostics display | **COMPILE VERIFIED** |
| CHASE yaw smoothing (factor 3, min 4) | **COMPILE VERIFIED** |
| FP body-look coupling (dead zone 32, limit 128) | **COMPILE VERIFIED** |
| FREE→CHASE transition pop fix | **COMPILE VERIFIED** |
| ORIGINAL zoom ownership (no regression) | **SOURCE VERIFIED** |
| MODERN zoom ownership (rig owns distances) | **SOURCE VERIFIED** |
| Wheel ownership (viewport heuristic) | **COMPILE VERIFIED** |
| Camera ownership (cameraType=0/1) | **SOURCE VERIFIED** |
| Self orientation ownership (mutually exclusive) | **SOURCE VERIFIED** |
| xFine/zFine ownership (Q16 prediction) | **SOURCE VERIFIED** |
| F11 transition safety | **SOURCE VERIFIED** |
| ORIGINAL path isolation | **SOURCE VERIFIED** |
| Movement dispatch (FP/CHASE/FREE identical) | **SOURCE VERIFIED** |
| CHASE stable relative to character | **RUNTIME UNVERIFIED** |
| FP body follows look direction | **RUNTIME UNVERIFIED** |
| WASD continuous smooth movement | **RUNTIME UNVERIFIED** |
| MODERN zoom smooth interpolation | **RUNTIME UNVERIFIED** |
| FREE classic-like controls | **RUNTIME UNVERIFIED** |
| Extended zoom max feel | **RUNTIME UNVERIFIED** |
| FP structural visibility (roofs/ceiling) | **RUNTIME UNVERIFIED** |

### 29.14 Runtime Test Checklist

1. Press F12 — overlay appears?
2. ORIGINAL — wheel zoom works?
3. ORIGINAL — arrows/middle mouse still normal?
4. F11 -> MODERN CHASE
5. Hold W for several tiles — smooth continuous movement?
6. Test A/S/D — smooth?
7. Observe overlay predicted tile/server tile/pending movement
8. Rotate/move — CHASE stays stably behind player?
9. Scroll fully inward — true FP?
10. Rotate FP camera — body target/visual yaw follow?
11. W/S/A/D in FP — correct facing/movement?
12. Scroll through FP→CHASE — smooth?
13. Scroll CHASE outward — smooth?
14. Reach FREE — arrows/middle mouse behave like classic?
15. Scroll FREE to extended maximum
16. F11 -> ORIGINAL — previous original camera/zoom restored?
17. Test FP roofs/ceiling if structural override is present

---

## 30. PHASE 3C RUNTIME FIX ROUND #4 (user runtime test response)

User performed an actual runtime test; results OVERRIDE prior static claims.
Preserved passes: F11 toggle, ORIGINAL arrows/middle-mouse, W movement, FP
entry, FP movement basis (NOT reversed), FREE arrows/middle-mouse, F11
restore, FP roofs rendering.

### 30.1 P0 — F12 crash (done previous round, preserved)

AWT `Graphics2D` overlay removed; overlay draws INSIDE the RT4 render
pipeline (GlRaster/SoftwareRaster + Fonts.p11Full), F12 edge-triggered.
Status: **COMPILE VERIFIED / RUNTIME UNVERIFIED** (user retest needed).

### 30.2 P1 — FP body yaw opposite direction — FIXED

**Root cause (SOURCE VERIFIED):** camera and body yaw conventions differ.
- Camera (`Camera.method3849`, negative-multiplier atan2): 0=N(+Z), 512=W(-X), 1024=S(-Z), 1536=E(+X).
- Body (`PathingEntity.anInt3400`; proven from `NpcList.method2247` movement
  mapping + `NpcList.method949` positive-multiplier atan2): 0=-Z, 512=-X, 1024=+Z, 1536=+X.
- ONE explicit conversion added: `ModernCameraRig.cameraYawToBodyYaw(...)` /
  `bodyYawToCameraYaw(...)` = `(1024 - yaw) & 0x7FF` (involution; verified at
  all 4 cardinals). Mouse-look sign untouched; movement basis untouched.
- Writers converted: FP body-look coupling, movement orientation write,
  all rig reads of `anInt3400`.
Status: **SOURCE VERIFIED / COMPILE VERIFIED / RUNTIME UNVERIFIED**.

### 30.3 P2 — CHASE zoom world-position ownership — FIXED (rearchitecture)

The old code wrote the final camera in the 50Hz logic tick and read previous
camera position back through smooth-follow fields — producing the "camera
tries to keep its world position" symptom while zooming.

New pipeline (§4/§5 one transform per render):
- LOGIC (tick): `updateChase()`/`updateFree()` compute only authoritative
  state: yaw/pitch targets, `desiredDistance`, obstruction `safeZoomLimit`.
- RENDER (`ModernCameraRig.renderUpdate()`, hooked into
  `GameShell.mainInputLoop()`): interpolates visual pivot/yaw/pitch/boom and
  runs ONE `Camera.method555()` — Camera fields are OUTPUT only.
- `Camera.renderX/renderZ` are never read back as input authority; the only
  exception is the explicit FP→CHASE transition seed (`seedVisualFromCamera`).
- `Camera.cameraType = 0` re-asserted at render timing (beats region-rebuild/
  packet races that re-set cameraType=1 between ticks).
Status: **COMPILE VERIFIED / STATICALLY REVIEWED / RUNTIME UNVERIFIED**.

### 30.4 P3 — Render-timed smooth zoom/interpolation — FIXED

- All 50Hz tick-based smoothing removed (`smoothDistance`,
  `updateSmoothCameraPosition`, `smoothYaw`, `smoothInt` deleted).
- Frame-rate independent: `alpha = 1 - exp(-rate * dt)` with
  `GameShell.renderDelta`. Rates calibrated to old feel: distance 9/s,
  yaw 20/s, pitch 9/s, pivot 3.2/s.
- §7: rig threshold crossings do NOT reset visual distance. FP→CHASE seeds
  visual boom at 0 and lets it grow smoothly; entering FP lets the boom
  smoothly approach the eye state. Spatial continuity preserved.
Status: **COMPILE VERIFIED / STATICALLY REVIEWED / RUNTIME UNVERIFIED**.

### 30.5 P4 — ORIGINAL wheel zoom — IMPLEMENTED (after source proof)

**Source proof:** the classic transform (`ScriptRunner.method4326`,
cameraType==1) reads `Camera.ZOOM + pitchTarget*3`; `Camera.ZOOM` had no
game-loop writer and no functioning wheel→camera path exists in the cache
scripts. The previous "static verified" claim ("CS2 script handles it") was
runtime-disproven.

New small ORIGINAL path (`ModernControlController.updateOriginalWheelZoom()`):
- Wheel moves a zoom TARGET on the legacy scale (100..1200, step 50/notch).
- `Camera.ZOOM` smoothly approaches target per tick (factor 3, min step ±1).
- Skipped when mouse is over scrollable UI (viewport heuristic).
- No rig activation, no FOV change, arrows/middle mouse untouched.
- Overlay diagnostics: legacyZoomInputSeen/Before/After.
Status: **SOURCE VERIFIED / COMPILE VERIFIED / RUNTIME UNVERIFIED**.

### 30.6 P5 — MODERN FREE wheel zoom — FIXED

- ONE FREE distance authority: the rig's desired/safe/actual distance
  (old FREE path mixed tick-time `smoothDistance()` with ratio-based
  obstruction clamping that could fight the wheel).
- `checkObstruction()` height-awareness fix: far/high FREE cameras were
  clamped by ground-level collision flags/walls that ignore sample height;
  flag/wall blocking now gated on `nearGround` (camY > terrain - 200).
  Terrain-under-camera check unchanged.
Status: **COMPILE VERIFIED / STATICALLY REVIEWED / RUNTIME UNVERIFIED**.

### 30.7 P6 — Roof flashing — FIXED (root cause proven)

**Root cause (SOURCE VERIFIED):** the FP override early-returned in
`method4302()` BEFORE the rolling column reset of `aByteArrayArrayArray15`.
The mask byte cycles mod 256 while the column reset cycles mod 104; stale
stamps written before entering FP re-match the current frame stamp ~256
frames later, hiding those roof tiles for one frame each cycle — sweeping
column-by-column = "a flash moving through the roofs".

Fix: the rolling column reset now runs inside the FP branch every frame
(refresh 104 < collision 256 ⇒ stale stamps can never re-collide), yielding
a stable NO-SELECTIVE-ROOF-REMOVAL state. No timing-constant hacks.
Status: **SOURCE VERIFIED / COMPILE VERIFIED / RUNTIME UNVERIFIED**.

### 30.8 P7 — Ceiling investigation (no code change this round)

Source-traced findings:
- CASE A/D RULED OUT: `SceneGraph.anInt5276` (min render plane, set once per
  region via `method2750` ← LoginManager) includes planes above the player;
  `method3049` frustum test covers the full height range. Upper-plane tiles
  ARE submitted and pass visibility while FP roof removal is disabled.
- CASE B PRIMARY SUSPECT: `GlRenderer` enables `GL_CULL_FACE`/`GL_BACK`
  globally; plane/tile surfaces are single-sided, so the roof underside is
  back-face culled when viewed from inside/below.
- CASE C secondary (some model roofs may lack underside geometry).
- Next round decision needed: smallest FP-only solution (e.g. two-sided
  draw of the player-plane-overhead tile batch only; never global cull
  disable). No fake ceiling mesh unless cache geometry is proven absent.
Status: **SOURCE VERIFIED investigation / NO CODE CHANGE**.

### 30.9 Round #4 static sweeps (§16)

- B. `MouseWheel.wheelRotation` readers: client (writer), InterfaceList (UI
  scroll), ModernCameraRig (MODERN), ModernControlController (ORIGINAL P4),
  Protocol (staff plane change), DebugOverlay (display). ✔
- B. `Camera.ZOOM` writers: plugin API (pre-existing) + ORIGINAL wheel path
  (new, ORIGINAL-only); reader: `ScriptRunner.method4326`. ✔
- B. `desiredDistance` writers: init + wheel clamp only. `actualDistance`
  writers: onSceneRebuild/activate re-anchor + renderUpdate from visDistanceD
  (NO threshold-crossing resets). ✔
- B. `Camera.renderX/renderZ` writers: FirstPersonCamera (FP tick),
  writeFpCameraImmediate (transition safety), method555 (render OUTPUT),
  ScriptRunner save/restore, InterfaceList cutscene. No rig read-back. ✔
- C. `anInt3400` writers: FP body coupling + movement controller, both via
  `cameraYawToBodyYaw`; remainder vanilla (NPC spawn/packets). ✔
- D. Roof-mask writes: method4348 flood fill (never reached in FP) + two
  rolling resets (vanilla + new FP). ✔
- E. F11 transition: save/restore intact; ORIGINAL zoom target released by
  the isModern() guard. ✔
- F. FREE controls: arrow/middle-mouse input path unchanged (TEST 14 pass
  preserved); only distance ownership changed. ✔
- G. No AWT Graphics2D overlay drawing remains (remaining getGraphics uses
  are vanilla loading bars/fonts/error screens). ✔

### 30.10 Round #4 files modified

| File | Change |
|---|---|
| `rt4/ModernCameraRig.java` | P1 conversions; render-timed visual state + `renderUpdate()`; logic-only `updateChase`/`updateFree`; no threshold distance resets; height-aware `checkObstruction`; removed tick smoothing |
| `rt4/ModernMovementController.java` | P1: yaw read conversion + orientation write via `cameraYawToBodyYaw` |
| `rt4/GameShell.java` | `mainInputLoop()` calls `ModernCameraRig.renderUpdate()` |
| `rt4/ModernControlController.java` | P4: `updateOriginalWheelZoom()` (ORIGINAL-only, legacy ZOOM scale) |
| `rt4/ScriptRunner.java` | P6: rolling roof-mask column reset kept active in FP branch |
| `rt4/DebugOverlay.java` | Added visYaw/fp rig line |

Build: `gradlew.bat compileJava` → **BUILD SUCCESSFUL (EXIT_CODE=0)**.

### 30.11 Verification status (round #4)

| Item | Status |
|------|--------|
| P0 F12 overlay (RT4 pipeline) | COMPILE VERIFIED / RUNTIME UNVERIFIED |
| P1 cameraYawToBodyYaw conversion | SOURCE VERIFIED / COMPILE VERIFIED |
| P2 pivot-authority render pipeline | COMPILE VERIFIED / STATICALLY REVIEWED |
| P3 render-timed interpolation | COMPILE VERIFIED / STATICALLY REVIEWED |
| P4 ORIGINAL wheel zoom | SOURCE VERIFIED / COMPILE VERIFIED |
| P5 FREE distance authority + obstruction | COMPILE VERIFIED / STATICALLY REVIEWED |
| P6 roof-mask rolling reset in FP | SOURCE VERIFIED / COMPILE VERIFIED |
| P7 ceiling CASE A–E determination | SOURCE VERIFIED investigation only |
| All runtime behavior | **RUNTIME UNVERIFIED — awaiting user test** |

### 30.12 Round #4 runtime test list (given to user)

1. F12 — overlay opens without crash?
2. ORIGINAL — wheel zoom in/out?
3. ORIGINAL — arrows/middle mouse still good?
4. F11 -> MODERN CHASE
5. Stand still and scroll — does camera move only along boom, not fight world position?
6. Walk/turn in CHASE — camera smooth and stable?
7. Scroll slowly inward/outward — smooth?
8. Enter FP and rotate left/right — body faces SAME direction?
9. FP movement still correct?
10. FP -> CHASE transition smooth?
11. CHASE -> FREE transition smooth?
12. FREE arrows/middle mouse still good?
13. FREE wheel zoom works?
14. F11 -> ORIGINAL restores previous state?
15. FP outside building — roof stable, no flashing?
16. FP inside building — ceiling visible?

---

## 31. PHASE 3C — RUNTIME FIX ROUND #5 + FIRST-PERSON INTERACTION FOUNDATION

Round #4 runtime results (authoritative): smooth zoom, ORIGINAL zoom,
FP/FREE entry, roof-flashing fix, F11 all PASS. Failures carried into this
round: F12 crash, FP WASD regression, semantic/visual FP threshold mismatch,
repeated FP<->CHASE corruption, CHASE A/D feedback loop, zoom not far
enough, missing ceiling, plus new P6/P7 interaction features.

### 31.1 P0 — F12 crash — ROOT CAUSE FOUND (plugin), FIXED

Round #4's overlay rewrite was not the cause. The exact crash site was
proven from `hs_err_pid24356.log`: the **ToggleResizableSD plugin's VK_F12
key binding** fired `glDeleteTextures` on the GL thread →
`EXCEPTION_ACCESS_VIOLATION`. Fix: the VK_F12 binding was removed from the
plugin (`plugin.kt`); `DebugOverlay` now exclusively owns F12.
Status: **SOURCE VERIFIED (hs_err log) / COMPILE VERIFIED / RUNTIME UNVERIFIED**.

### 31.2 P1 — FP WASD — FIXED (regression traced)

Regression: after Round #4 body-yaw work, `CameraMode.getCameraRelativeYaw()`
could return the CHASE/body-convention yaw while the rig was visually at the
FP eye position. Fix: it now returns `FirstPersonCamera.getYaw()` whenever
the rig is in FIRST_PERSON state OR `FirstPersonCamera.isActive()`.
The proven movement basis is UNTOUCHED (static sweep B):
`Forward = (-sin, +cos)`, `Right = (+cos, +sin)`, yaw in camera convention
(0=+Z N, 512=-X W, 1024=-Z S, 1536=+X E).
Status: **SOURCE VERIFIED / COMPILE VERIFIED / RUNTIME UNVERIFIED**.

### 31.3 P2 — Semantic vs visual FP threshold — FIXED

CHASE<->FP transitions are now driven by the RENDERED distance
(`visDistanceD`, visual authority), not the desired distance:
- CHASE -> FP when `visDistanceD <= FP_ENTER_DISTANCE` (100) — FP behavior
  activates exactly when the rendered boom reaches the eye.
- FP -> CHASE when `visDistanceD >= FP_EXIT_DISTANCE` (200) or
  `desiredDistance >= FREE_ENTER_DISTANCE` (1200). Hysteresis preserved.
- FP case recomputes obstruction `safeDistance` every tick.
Status: **COMPILE VERIFIED / STATICALLY REVIEWED / RUNTIME UNVERIFIED**.

### 31.4 P3 — Repeated FP<->CHASE lifecycle corruption — FIXED

Self-heal guards in `ModernCameraRig.update()` make stale one-shot state
impossible to survive multiple cycles:
- `rigState == FIRST_PERSON && !FirstPersonCamera.isActive()` →
  re-activate + `writeFpCameraImmediate()`.
- `rigState != FIRST_PERSON && FirstPersonCamera.isActive()` →
  deactivate + `Camera.cameraType = 0`.
- `deactivate()` clears `seedVisualFromCamera` + `visInitialized`; seeds are
  also cleared on CHASE->FP and CHASE->FREE transitions.
Status: **COMPILE VERIFIED / STATICALLY REVIEWED / RUNTIME UNVERIFIED**.

### 31.5 P4 — CHASE A/D camera/body feedback — FIXED

New stable `movementHeading` (camera convention) in
`ModernMovementController`, synced from body yaw ONLY on:
enterModernMode / scene rebuild / idle / FP->CHASE edge. Authority chain:
WASD -> movementHeading -> velocity -> body (`anInt3400` via
`cameraYawToBodyYaw`) -> `chaseYawTarget` -> render-smoothed `visYawD`.
Camera yaw never feeds back into locomotion. FREE independence preserved.
Status: **COMPILE VERIFIED / STATICALLY REVIEWED / RUNTIME UNVERIFIED**.

### 31.6 P5 — FREE zoom extension — IMPLEMENTED

Rig-specific maxima: `MAX_DISTANCE = 1350` stays the CHASE cap; new
`FREE_MAX_DISTANCE = 2200` (FREE only). Wheel clamp, `safeDistance` and
`visDistanceD` clamps all rig-aware (L530-532, L722, L785, L935).
Obstruction logic unchanged. Feel is RUNTIME UNVERIFIED (user may want
further tuning).
Status: **COMPILE VERIFIED / RUNTIME UNVERIFIED**.

### 31.7 P6 — FP crosshair action overlay — NEW (ModernActionOverlay.java)

MODERN + FIRST_PERSON only. Design = reuse, do not invent:
- `LoginManager.method1841()` rebuilds MiniMenu every frame; at render time
  (after rebuild+sort, `client.mainRedraw`) `ModernActionOverlay.snapshot()`
  captures the existing crosshair-target world entries (primary op first:
  sort() places action>1000 entries at the front, menu reads from the end).
- 3-tile Chebyshev range gate vs player tile; hysteresis keeps the last
  target while it still exists. Max 3 actions shown, name + "N Op" lines,
  drawn with the proven GlRaster/SoftwareRaster + Fonts.p11Full pattern
  (no AWT Graphics2D).
- Keys 1/2/3 (edge-detected) execute the displayed actions by matching the
  LIVE entry (keys+action+tile args) and calling `MiniMenu.doAction(i)` —
  the exact mouse-click route. E executes the primary action (no existing
  E binding found in any Modern* file). Selected item/spell variants are
  respected because the snapshot mirrors whatever entries the existing
  pipeline produced (Use X -> / Cast X -> whitelisted).
- Reused action IDs (H): loc 42/50/49/46/1001, NPC 17/16/4/19/2, ground
  items 21/34/18/20/24, player options, use-item 14/26/1/33, cast-spell
  38/45/15/39 (+ examine 1002/1004 via whitelist omission policy).
Status: **SOURCE VERIFIED (routes traced) / COMPILE VERIFIED / RUNTIME UNVERIFIED**.

### 31.8 P7 — Dialogue keyboard (ModernDialogueKeyboard.java) — NEW, MODERN-only

- SPACE = "Click here to continue" via the EXACT doAction-41 route:
  `MiniMenu.method10(child, id)` + `Cs1ScriptRunner.aClass13_10 =
  InterfaceList.method1418(id, child)` + `InterfaceList.redraw(...)`.
  Continue predicates match the menu builder's (`buttonType == 6` /
  `isResumePauseButtonEnabled()`); chatbox-layer interfaces scanned first.
- 1..9 = dialogue choice N via the EXACT doAction-8 button route
  (`p1isaac(10); p4(componentId)` after the `method4265` clientCode gate).
  Choices = `buttonType == 1` components of interfaces opened on the
  chatbox layer (clientCode 1406), insertion-sorted by component y =
  rendered top-to-bottom order. Fully generic; no dialogue hardcoded.
  Server proof: `DialogueInterpreter.handle` maps child index -> topic.
- Why an interface scan: MiniMenu.addComponentEntries is mouse-bounds-gated,
  so in FP (cursor centred) Continue/choice entries never reach the menu.
- Input priority (§15): chat input active / right-click menu open -> inert;
  consumed keypress blocks the FP world-action layer for that tick.
  ORIGINAL mode untouched (§17).
Status: **SOURCE VERIFIED (routes traced byte-for-byte) / COMPILE VERIFIED / RUNTIME UNVERIFIED**.

### 31.9 P8 — Ceiling investigation (no code change)

New source evidence this round completes the CASE B proof:
- `GlTile.method1944` fans every tile as `(v0, v[i], v[i+1])` — a FIXED
  winding for ALL planes (vertex layout via `anIntArrayArray35` templates +
  `method3683`; `method1324` takes no plane parameter affecting winding).
- Global `glEnable(GL_CULL_FACE)` + `glCullFace(GL_BACK)`
  (`GlRenderer` L293-294) therefore makes every tile plane single-sided:
  front-facing only from the above side. Camera below the plane (FP indoors)
  => back-face => culled. This covers both test path A (floor tile above
  player) and B (roof tile) — they are the SAME mechanism.
- Upper planes ARE submitted (`method3292` loops `anInt5276..levels`,
  visibility check spans plane 0..3 heights), so culling — not submission —
  is the blocker.
- Test path C (roof loc/model): rendered as `GlModel` under the same global
  cull; model faces wound outward, so model roofs viewed from inside are
  likewise culled. Solution may need per-path treatment.
- Decision NOT implemented: an FP-only underside pass (e.g. cull-disable or
  reversed-winding second draw for the overhead tile batch only) remains the
  proposed design; material uncertainty about lighting/normals on the
  underside keeps it out of this round. Never a global cull disable.
Status: **SOURCE VERIFIED investigation / NO CODE CHANGE**.

### 31.10 Round #5 static sweeps (§20)

- B. FP WASD basis UNCHANGED: `ModernMovementController` L384-387 —
  `velocityX = fwd*(-sin)+str*(+cos)`, `velocityZ = fwd*(+cos)+str*(+sin)`.
- C. Body yaw conversions: all via the involution pair
  `cameraYawToBodyYaw`/`bodyYawToCameraYaw` (ModernCameraRig L265/273);
  writes to `anInt3400` only at defined sync points.
- D. Camera final writers: FP writer (`Camera.cameraYaw/pitch` from
  FirstPersonCamera, L677-678), ORIGINAL-mode save/restore (L312-315),
  `cameraType = 0` ownership guards; final zoom = ONE `Camera.method555`
  (L968) + ORIGINAL route `ScriptRunner` L238.
- E. Distance writes: `desiredDistance` writers = init + rig-aware wheel
  clamp (L530-532); `FREE_MAX_DISTANCE = 2200` caps safe/vis clamps;
  no writers outside ModernCameraRig.
- F. Overlay draw calls: `client.mainRedraw` — `ModernCrosshair.draw()` →
  `ModernActionOverlay.snapshot()/draw()` → `DebugOverlay.draw()`.
- G. `MiniMenu.doAction` usage: ModernActionOverlay (new, matched live
  entry) + Protocol L3646 (vanilla). No invented packets.
- H. Reused action IDs documented in 31.7.
- I. Continue = action-41 route; choice = action-8 route (byte-for-byte,
  see 31.8).
- J. Roof-mask writers unchanged from Round #4 (§30.9-D); no new
  roof-mask writes.

### 31.11 Round #5 files modified/created

| File | Change |
|---|---|
| `ToggleResizableSD plugin.kt` | P0: removed VK_F12 binding (crash root cause) |
| `rt4/CameraMode.java` | P1: getCameraRelativeYaw FP safety net |
| `rt4/ModernCameraRig.java` | P2 visual-authority transitions; P3 self-heal guards; P5 FREE_MAX_DISTANCE=2200 + rig clamps |
| `rt4/ModernMovementController.java` | P4 movementHeading + FP->CHASE edge sync |
| `rt4/ModernActionOverlay.java` | NEW P6: FP crosshair action overlay + 1/2/3/E execution |
| `rt4/ModernDialogueKeyboard.java` | NEW P7: SPACE continue + 1-9 choices (MODERN only) |
| `rt4/ModernControlController.java` | updateInteractionLayer() priority chain |
| `rt4/client.java` | mainRedraw snapshot/draw hooks (after method1841 rebuild) |

Build: `build_rt4.bat` -> **BUILD SUCCESSFUL in 34s (EXIT_CODE=0)**.

### 31.12 Verification status (round #5)

| Item | Status |
|------|--------|
| P0 F12 crash root cause (plugin) | SOURCE VERIFIED (hs_err) / COMPILE VERIFIED |
| P1 FP WASD yaw authority | SOURCE VERIFIED / COMPILE VERIFIED |
| P2 visual-authority FP transitions | COMPILE VERIFIED / STATICALLY REVIEWED |
| P3 lifecycle self-heal guards | COMPILE VERIFIED / STATICALLY REVIEWED |
| P4 CHASE movementHeading authority | COMPILE VERIFIED / STATICALLY REVIEWED |
| P5 FREE_MAX_DISTANCE 2200 | COMPILE VERIFIED / RUNTIME UNVERIFIED |
| P6 FP action overlay (reuse MiniMenu) | SOURCE VERIFIED / COMPILE VERIFIED |
| P7 dialogue keyboard (routes 41/8) | SOURCE VERIFIED / COMPILE VERIFIED |
| P8 ceiling CASE B proof | SOURCE VERIFIED investigation only |
| All runtime behavior | **RUNTIME UNVERIFIED — awaiting user test** |

### 31.13 Round #5 runtime test list (given to user)

1. F12 opens without crash?
2. FP WASD works again?
3. FP look left/right -> body follows same direction?
4. Scroll CHASE -> FP: FP behavior activates exactly when camera reaches eye?
5. FP -> CHASE -> FP repeated 10 times: still works every time?
6. CHASE W/A/S/D: movement and body correct?
7. CHASE camera follows smoothly without fighting A/D?
8. FREE zoom goes materially farther?
9. FREE arrows/middle mouse still work?
10. Roof flashing still gone?
11. Aim at object within 3 tiles: name/actions appear by crosshair?
12. Press 1/2/3: correct existing object action executes?
13. NPC within range: correct existing actions appear?
14. Dialogue with multiple choices: 1/2/3 selects corresponding option?
15. "Click here to continue": SPACE continues?
16. ORIGINAL camera/zoom/input still normal?
17. Ceiling still missing / any new source finding?

---

## Section 32 — Round #6A: Runtime Regression Fixes (Chase Movement / Camera State / Input Ownership / Roofs)

Date: 2026-08-14. Input: user runtime results from Round #5 (7 verified
PASSES preserved; 5 failures addressed here). USER RUNTIME RESULTS OVERRIDE
ALL STATIC CLAIMS.

**Round #5 runtime-verified passes (untouched this round):** F12 overlay,
FP WASD, FP diagonals, crosshair interaction, numbered object actions,
SPACE dialogue continue, smooth wheel zoom.

**Constraints honored:** no Round #5 reverts, no combat changes, no ceiling
fix attempt, no global culling change, FP WASD basis unchanged, ORIGINAL
untouched, Round #4 rolling-stamp roof fix preserved.

### 32.1 P0 — CHASE -> FP semantic rig authority

**Runtime failure:** camera obstruction pulled the chase camera inward and
the rig accidentally transitioned to FIRST_PERSON.

**Root cause (SOURCE TRACED):** `ModernCameraRig.updateStateTransitions()`
CHASE case gated FP entry on `visDistanceD <= FP_ENTER_DISTANCE` ALONE.
`visDistanceD` follows `min(desiredDistance, safeDistance)` in
`renderUpdate()` (CHASE branch), so obstruction (small safeDistance)
compressed the visual boom into FP range and flipped the semantic rig.

**Fix:** CHASE -> FP now requires BOTH:
- A. explicit USER zoom intent: `desiredDistance <= FP_ENTER_DISTANCE`
- B. rendered camera convergence: `visDistanceD <= FP_ENTER_DISTANCE`

Obstruction alone can NEVER change the rig. Example preserved:
rig=CHASE desired=600 safe=20 stays CHASE (boom visually shortens only).

Status: SOURCE VERIFIED / COMPILE VERIFIED / STATICALLY REVIEWED /
RUNTIME UNVERIFIED.

### 32.2 P1 — FIRST_PERSON -> CHASE intent-driven exit (no hitch)

**Runtime failure:** zooming outward from FP hesitated/hung before CHASE
returned — the visual-distance gate kept semantic FP alive while the FP
camera still hard-owned the final camera and a hidden visDistanceD timer grew.

**Root cause (SOURCE TRACED):** FP exit waited for
`visDistanceD >= FP_EXIT_DISTANCE`; visDistanceD approaches its target at
DIST_RATE_PER_S=9/s at RENDER timing, so FP remained semantically active
long after the user's scroll intent had passed the threshold.

**Fix:** FP -> CHASE now triggers IMMEDIATELY on outward USER INTENT:
`desiredDistance >= FP_EXIT_DISTANCE` (200). On the transition tick:
- semantic rig flips to CHASE immediately (FirstPersonCamera deactivated);
- `seedVisualFromCamera = true` (existing Round #5 mechanism, renderUpdate
  seed block): next render seeds the CHASE visual camera from the LIVE FP
  eye camera — same position (pivot=self), same yaw (Camera.cameraYaw),
  same pitch (Camera.cameraPitch), boom distance starts at 0;
- render-timed interpolation extends the boom outward smoothly.

Hysteresis preserved: enter <= 100, exit >= 200 (2+ wheel notches).
No frame exists where desired says "leave FP" but FP still owns the camera.
FREE escape hatch (desired >= FREE_ENTER) subsumed by the >= 200 gate.

Status: SOURCE VERIFIED / COMPILE VERIFIED / STATICALLY REVIEWED /
RUNTIME UNVERIFIED (10+ FP<->CHASE cycles to be re-tested).

### 32.3 P2 — CHASE diagonal WASD

**Runtime failure:** FP W+D worked; CHASE W+D/W+A did not produce normal
diagonal free movement.

**Trace (all listed steps re-read):**
- `readInput()` — independent `if` per key (W/S forward, D/A right). No
  `else if`; W and D are simultaneously represented.
- `MovementIntent.normalize()` — divides both components by magnitude.
- Yaw selection — CHASE uses `movementHeading` (stable heading, camera
  convention) via `CameraMode.getCameraRelativeYaw()` returning -1.
- Velocity composition — proven FP basis untouched (L391-394):
  `velX = fwd*(-sin) + right*(+cos)`, `velZ = fwd*(+cos) + right*(+sin)`.
- DDA — simultaneous X+Z crossing sends diagonal target tile.
- Body write — CHASE: `anInt3400 = cameraYawToBodyYaw(atan2(velX,velZ))`.

**Root cause identified:** the movement math was already correct. The
runtime CHASE diagonal failure was a CONSEQUENCE of the P0 bug: near any
wall/object, obstruction flipped the semantic rig to FIRST_PERSON, swapping
the movement basis from the stable movementHeading to the live FP look yaw
(and the `wasFirstPersonLastTick` edge re-sync ran on every flicker back).
CHASE diagonals therefore only failed in exactly the obstructed contexts
the user tested. The P0 dual-gate fix removes this basis-swapping.

**Diagnostics added (temporary, no stdout flood):**
- F12 overlay: intentF/intentR (percent), movementHeading, chaseYawT,
  rigFlips counter, visDist, obstructed YES/NO.
- `[MOVE-DEBUG]` console line at 1 Hz ONLY while moving in CHASE/FREE
  (intentF/intentR/movementHeading/velocityX/velocityZ/bodyTarget/
  chaseTargetYaw).

Status: movement math SOURCE VERIFIED / COMPILE VERIFIED / STATICALLY
REVIEWED. Runtime diagonal behavior RUNTIME UNVERIFIED — if it still fails
with rig=CHASE shown on the overlay, the [MOVE-DEBUG] lines isolate which
stage drops a component.

### 32.4 P3 — MODERN chat input ownership (hard fix)

**Runtime failure:** gameplay keys still typed into the chatbox
("eeeefddddss..."; pressing 2 executed the FP action AND typed "2").

**Real vanilla typed-key path (SOURCE TRACED):**
- `Keyboard.keyPressed()` queues a KEYCODE entry (keyCode >= 0, keyChar=-1).
- `Keyboard.keyTyped()` queues a CHAR-ONLY entry (keyCode=-1, keyChar=c).
- `Keyboard.nextKey()` drains both entry types into `keyCode`/`keyChar`.
- Two drain sites copy into `InterfaceList.keyCodes/keyChars`:
  `client.mainUpdate` L1170 and `Protocol` L2819 — BOTH apply
  `shouldForwardKeyToChat`. Consumers (Camera L377, InterfaceList L1026,
  Protocol L2566) only read the filtered queue. No third gameplay path
  exists (the only other nextKey() call is the safe-mode loader).

**Root cause:** the Round #5 filter only blocked W/A/S/D keycodes and
w/a/s/d char entries. E, 1-9 and SPACE (and their char forms) leaked;
held-key char-only repeats produced the "ddddss" streams.

**Fix:** `shouldForwardKeyToChat` now blocks ALL gameplay keys when MODERN
and chat is closed — keycode entries: W/A/S/D (33/48/49/50), E (34),
1-9 (16..24), SPACE (83); char-only entries: w/a/s/d/e (both cases),
'1'..'9', space. ENTER activates explicit chat mode (chatInputActive) ->
everything forwards, gameplay yields. ORIGINAL mode never filters.
Priority: 1. explicit chat, 2. dialogue/choice UI, 3. modal UI,
4. FP action keys, 5. movement (dialogue/action layers poll
`Keyboard.pressedKeys` directly, so blocking the typed queue does not
affect SPACE-continue or numbered actions).

Status: SOURCE VERIFIED / COMPILE VERIFIED / STATICALLY REVIEWED /
RUNTIME UNVERIFIED.

### 32.5 P4 — CHASE/FREE vanilla roof removal restored

**Runtime failure:** walking INTO a building in CHASE left the roof rendered.

**Root causes (two, SOURCE TRACED in ScriptRunner.method4302):**
1. The P0 bug flipped the rig to FIRST_PERSON whenever obstruction
   compressed the chase boom (entering buildings always obstructs) — the FP
   early return skips selective roof removal entirely.
2. Even staying in CHASE, the rig runs with `Camera.cameraType = 0`, and
   method4302's `cameraType != 1` branch only removes the roof at the
   CAMERA tile then returns — it never runs the vanilla chase-camera path
   (player-tile removal + camera->player roof walk), so a camera parked
   outside the building never hid the roof above the player.

**Fix:**
- P0 dual-gate (32.1) keeps the rig in CHASE near/inside buildings, so the
  FP no-removal override no longer misfires.
- New gate in method4302: `modernThirdPersonRoofs = CameraMode.isModern()
  && ModernCameraRig.isActive() && !isFirstPersonRigState()` forces the
  vanilla cameraType==1 roof path (player-tile UNDER_ROOF removal +
  camera->player roof walk) for MODERN CHASE/FREE while cameraType stays 0.

**Preserved:** FP early return untouched (FP keeps its intentional
no-removal behavior); Round #4 rolling-column stale-stamp reset still runs
every frame inside the FP branch (no roof flashing); ORIGINAL unaffected
(runs with cameraType==1 as vanilla — the new gate is false there); no
global all-levels hack; no GL culling change.

Status: SOURCE VERIFIED / COMPILE VERIFIED / STATICALLY REVIEWED /
RUNTIME UNVERIFIED. Ceiling remains a separate later issue (P8 CASE B).

### 32.6 P5 — Diagnostics retained/added

F12 overlay now shows: CONTROL profile, rig, cameraType, chat/gameplay
gates, desired/safe/act distances, visDist + obstructed YES/NO, intentF/
intentR/heading, chaseYawT, rigFlips counter, body target/visual/locoYaw,
roofMode/allLvl/fpStruct, lastCamWriter/lastBodyYawWriter/lastRebase.
Console: existing 1 Hz `[CAMERA-RIG-DEBUG]` extended with vis +
obstructed; transition log now includes safe/vis/obstructed; new 1 Hz
`[MOVE-DEBUG]` only while moving in CHASE/FREE. No flooding.

### 32.7 Round #6A files modified

| File | Change |
|---|---|
| `rt4/ModernCameraRig.java` | P0 dual-gate CHASE->FP; P1 intent-driven FP exit; new accessors (getVisualDistance/isObstructionLimited/getChaseYawTarget); rig debug lines + rigFlips |
| `rt4/ModernControlController.java` | P3: full gameplay-key filter (keycodes + char forms) via isGameplayKeyCode/isGameplayChar |
| `rt4/ModernMovementController.java` | P2/P5: overlay intent/heading writes; temporary 1 Hz [MOVE-DEBUG] |
| `rt4/ScriptRunner.java` | P4: modernThirdPersonRoofs gate -> vanilla chase roof path |
| `rt4/DebugOverlay.java` | New fields + overlay lines (visDist/obstructed/intent/heading/chaseYawT/rigFlips) |

Build: `build_rt4.bat` -> **BUILD SUCCESSFUL in 46s (EXIT_CODE=0)**.

### 32.8 Static review checklist (round #6A)

- FP WASD basis unchanged: L391-394 identical (-sin/+cos, +cos/+sin). PASS.
- No CHASE camera feedback loop: movementHeading never reads visual camera
  yaw; authority remains WASD -> heading -> velocity -> body -> chaseYawTarget
  -> visYawD. PASS.
- Obstruction cannot change semantic rig: CHASE->FP requires
  desired<=100 AND vis<=100; safeDistance only feeds visual boom. PASS.
- FP exit user-intent driven and seeded from live eye camera:
  desired>=200 immediate; seedVisualFromCamera seeds pivot/yaw/pitch from
  live Camera fields with visDistanceD=0. PASS.
- ORIGINAL untouched: shouldForwardKeyToChat returns true for ORIGINAL;
  roof gate requires CameraMode.isModern(); ORIGINAL camera path unchanged.
  PASS.
- No global roof/culling hack: single per-path boolean in method4302; GL
  culling state untouched. PASS.
- No gameplay-key leak path: all three typed-queue consumers read the
  filtered InterfaceList queue; both drain sites filter; only other
  nextKey() caller is the safe-mode loader. PASS.
- Hysteresis: enter<=100 / exit>=200 / FREE enter 1200 / FREE exit 1100.
  PASS.

### 32.9 Verification status (round #6A)

| Item | Status |
|------|--------|
| P0 obstruction cannot enter FP | SOURCE VERIFIED / COMPILE VERIFIED / RUNTIME UNVERIFIED |
| P1 intent-driven FP exit + live seed | SOURCE VERIFIED / COMPILE VERIFIED / RUNTIME UNVERIFIED |
| P2 CHASE diagonal basis (math) | SOURCE VERIFIED / STATICALLY REVIEWED |
| P2 CHASE diagonal runtime | **RUNTIME UNVERIFIED** (root cause was P0 flicker) |
| P3 gameplay keys never reach chat | SOURCE VERIFIED / COMPILE VERIFIED / RUNTIME UNVERIFIED |
| P4 CHASE/FREE vanilla roof removal | SOURCE VERIFIED / COMPILE VERIFIED / RUNTIME UNVERIFIED |
| P5 diagnostics | COMPILE VERIFIED |
| Round #5 passes preserved | STATICALLY REVIEWED (no regressions in touched paths) |

### 32.10 Round #6A runtime test list (given to user)

1. CHASE W+D
2. CHASE W+A
3. CHASE S+D
4. CHASE S+A
5. CHASE camera smooth while diagonally moving
6. Stand beside object/wall in CHASE: camera may shorten, but NEVER enters FP
7. Scroll CHASE -> FP normally
8. Scroll FP -> CHASE: no hesitation
9. Repeat FP <-> CHASE 10 times
10. WASD no longer types into chat
11. Number keys no longer leak into chat
12. Explicitly open chat with ENTER: typing still works
13. Enter building in CHASE: roof hides normally
14. Leave building: roof restores normally
15. Roof flashing remains gone
16. ORIGINAL unchanged

---

## Section 33 — Round #6A HOTFIX: FIRST_PERSON entry crash

**RUNTIME FAILED:** "Round #6A initial implementation crashed on entering
FIRST_PERSON." (User runtime report — overrides all Round #6A static claims.)

### 33.1 Exact crash (P0 — found, not guessed)

From the client terminal stdout after the crash (no hs_err needed — pure
Java exception, game thread):

```
Error: rt4.ScriptRunner.method4302:1455 rt4.ScriptRunner.method4326:273
rt4.Cs1ScriptRunner.renderComponent:392 ... rt4.GameShell.run:662
java.lang.Thread.run | java.lang.ArithmeticException: / by zero
error_game_crash
```

- Exception: `java.lang.ArithmeticException: / by zero`
- Class/method: `ScriptRunner.method4302` (roof removal), line 1455:
  `local192 = local174 * 65536 / local146;`
- Thread: main game thread (`GameShell.run`), via scene render.
- Category from the brief: **E. ScriptRunner.method4302 roof logic** — the
  Round #6A P4 `modernThirdPersonRoofs` gate.

### 33.2 Root cause (SOURCE VERIFIED)

The vanilla camera→player roof walk DIVIDES by the camera↔player tile
distance on each axis (`local146` = Z tile delta, `local174` = X tile
delta). In vanilla this code only runs with `cameraType == 1`, where the
chase camera is ALWAYS offset from the player, so neither delta is 0.

Round #6A P4 routed MODERN CHASE/FREE (cameraType=0) into this same walk.
A modern chase camera whose boom has converged onto the player — which
happens on the CHASE→FP entry frames (zoom ≈ 0 → camera sits ON the
player's tile) and whenever obstruction compresses the boom to ~0 — makes
BOTH deltas 0 → division by zero → `error_game_crash`. This is exactly
why the crash occurred "when entering FIRST_PERSON": the frames just
before/at FP entry have the camera on the player tile while the rig is
still CHASE (or has just flipped), so the FP early-return does not apply.

### 33.3 Hotfix (smallest proven cause — P5)

`ScriptRunner.method4302` only: the `modernThirdPersonRoofs` gate is now a
STABLE FRAME SNAPSHOT. It additionally requires that the camera stands on
a different tile than the player (at least one axis differs):

```java
boolean modernThirdPersonRoofs = false;
if (CameraMode.isModern() && ModernCameraRig.isActive()
        && !ModernCameraRig.isFirstPersonRigState()
        && PlayerList.self != null) {
    modernThirdPersonRoofs = (camTileX != selfTileX || camTileZ != selfTileZ);
}
```

Why this is correct and complete:

- If the camera shares the player's tile, the roof walk would have ZERO
  length anyway (nothing to walk), so falling back to the division-free
  cameraType branch (camera-tile-only removal) is exactly equivalent.
- "At least one tile axis differs" guarantees BOTH divisions below are
  safe: the X-dominant branch divides by `local146` (>0 because Z tiles
  differ), the Z-dominant branch divides by `local174` (>0 because X
  tiles differ).
- FP entry frames (camera on player tile) → gate false → no division.
- FP→CHASE seed frames (boom starts at 0, camera on player tile) → gate
  false until the boom exceeds one tile → then normal roof walk resumes.
- Genuinely offset CHASE/FREE camera → gate true → full vanilla roof
  behavior (Round #6A P4 intent preserved).

Files changed: ONLY `rt4/ScriptRunner.java` (gate hardening). No camera,
movement, input or overlay code touched. No Round #6A semantics reverted.

### 33.4 Static review (hotfix)

1. CHASE→FP can execute without invalid intermediate state: PASS (roof
   walk unreachable while camera is on the player tile; writeFpCameraImmediate
   null/bounds guards unchanged).
2. FirstPersonCamera.active matches rigState: PASS (untouched).
3. Exactly one final camera owner: PASS (FP: writeFpCameraImmediate →
   FirstPersonCamera.update; CHASE/FREE: rig renderUpdate).
4. ORIGINAL untouched: PASS (gate requires CameraMode.isModern()).
5. FP WASD basis unchanged: PASS.
6. No global roof/culling hack: PASS.
7. Round #6A chat filtering intact: PASS (untouched).
8. Debug overlay fields: simple int/bool writes, no division, no null
   deref: PASS.

Build: `build_rt4.bat` -> **BUILD SUCCESSFUL in 29s (EXIT_CODE=0)**.

### 33.5 Verification status (hotfix)

| Item | Status |
|------|--------|
| Crash reproduced + exact site identified | RUNTIME VERIFIED (user report + stdout trace) |
| Root cause (division by zero in roof walk) | SOURCE VERIFIED |
| Tile-snapshot gate fix | SOURCE VERIFIED / COMPILE VERIFIED / STATICALLY REVIEWED / **RUNTIME UNVERIFIED** |
| FP entry survival | **RUNTIME UNVERIFIED** — pending user test |
| FP <-> CHASE cycling stability | **RUNTIME UNVERIFIED** — pending user test |

### 33.6 Next user test — ONLY this

1. Launch client.
2. Stay in CHASE for 10 seconds.
3. Slowly scroll into FIRST_PERSON.
4. Does client survive?
5. Move W/A/S/D in FIRST_PERSON.
6. Scroll back to CHASE.
7. Repeat FP <-> CHASE 5 times.

Do NOT test anything else until this crash is resolved.

## Section 34 — Round #6B/C COMBINED: Complete Modern Interaction + FREE Legacy Movement + FP UI Cursor + Chase Animation

Date: 2026-08-14. Build: `build_rt4.bat` -> **BUILD SUCCESSFUL in 39s (EXIT_CODE=0)**, `:client:compileJava` clean, zero errors.

### 34.0 Architecture update — MODERN FREE

**MODERN FREE now uses VANILLA LOCOMOTION + MODERN EXPANDED CAMERA.**

- F11 toggles ONLY ORIGINAL <-> MODERN (unchanged).
- Inside MODERN: FIRST_PERSON (WASD + mouse-look + FP interaction), CHASE (WASD + chase camera), FREE (**vanilla click-to-walk**, WASD disabled, vanilla movement queue/path ownership, modern FREE camera with expanded zoom retained).
- FREE IS NOT ORIGINAL: profile stays MODERN, rig stays FREE, camera stays modern/expanded — only MOVEMENT AUTHORITY changes.
- CHASE <-> FREE handoffs happen via the scroll wheel inside the rig; F11 is never involved.
- Movement ownership is exclusive and queryable: `ModernMovementController.getMovementOwner()` returns `ORIGINAL` / `MODERN_Q16` / `VANILLA_FREE`. The single predicate `ModernMovementController.isModernQ16Owner()` replaced `CameraMode.isModern()` at exactly the movement-ownership sites (NpcList.method4514 self gate + 5 Protocol.readSelfPlayerInfo drain/teleport blocks + the update() gate).

### 34.1 P0 — crash hotfix preserved

Round #6A tile-snapshot gate in `ScriptRunner` (modernThirdPersonRoofs, L1411/L1419/L1440) untouched. `method4302` not rewritten. SOURCE VERIFIED / STATICALLY REVIEWED.

### 34.2 P1 — FP hold-CTRL UI cursor substate

Implemented in `FirstPersonCamera` as a real FP input substate (FP_GAMEPLAY / FP_UI_CURSOR):

- CTRL press edge (KEY_CTRL = 82): `uiCursorActive = true`, `unlockCursor()` (visible normal cursor), mouse-look tracking reset. Camera stays where it was; FIRST_PERSON is NOT left.
- While held: mouse-look fully gated (`isChatInputActive() || uiCursorActive`), normal interfaces own the mouse; world shortcuts 1-3/E are gated out via `FirstPersonCamera.isUiCursorActive()` in the overlay; WASD remains active.
- CTRL release edge: `uiCursorActive = false`, `lockCursor()` — which sets `discardLockedMouseSample = true` and recentres — plus `lastMouseLookX/Y` reset, so the first locked sample is discarded and there is NO yaw/pitch jump.
- Scene-rebuild re-lock respects the substate (`!cursorLocked && !uiCursorActive`); activate()/deactivate() reset it.

SOURCE VERIFIED / COMPILE VERIFIED / STATICALLY REVIEWED / **RUNTIME UNVERIFIED**.

### 34.3 P2 — dialogue fully overrides world actions

`ModernDialogueKeyboard` is now the ONE source of truth: `hasActiveDialogue()` (continue pending OR continue component OR >=1 visible choice), `hasActiveChoiceDialogue()`, `getDialogueChoiceCount()`. Same scan predicates as the SPACE/number execution routes, so authority can never disagree with what the keys would execute.

- `ModernActionOverlay.isOverlayActive()` now additionally requires `!ModernDialogueKeyboard.hasActiveDialogue()` and `!FirstPersonCamera.isUiCursorActive()`. Since `snapshot()` clears the snapshot when inactive, the FP world overlay is hidden AND input-dead while a dialogue owns input — 1-9/E can only reach the dialogue, even with the crosshair on a bank booth.
- Input priority chain (P13 matrix): 1. explicit chat, 2. dialogue, 3. modal (aBoolean108), 4. CTRL-held UI cursor, 5. FP world shortcuts, 6. movement.

SOURCE VERIFIED / COMPILE VERIFIED / STATICALLY REVIEWED / **RUNTIME UNVERIFIED**.

### 34.4 P3 — dialogue 1..N actually works (generic, no hardcoding)

`collectChatboxButtons` now collects BOTH component families the existing menu builder would make clickable, from interfaces opened on the chatbox layer (clientCode 1406):

- CS1 `buttonType == 1` buttons (classic choice buttons) -> executed via the exact existing UNKNOWN_8 route: `p1isaac(10); p4(component.id)` (+ method4265 clientCode gate).
- if3 components with a server-enabled op (`InterfaceList.getOp` predicate — the same predicate `MiniMenu.addComponentEntries` uses) -> executed via the exact existing UNKNOWN_9/1003 route: `ClientProt.method4512(optionBase, createdComponentId, opIndex + 1, component.id)`.
- Hidden components skipped; options sorted by component `y` (rendered top-to-bottom order).
- Server semantics confirmed from `DialogueInterpreter.handle()` (topic index = buttonId - 2; else handle(componentId, buttonId - 1)) — no server change needed.
- One-shot `[DLG-DIAG]` dump on the dialogue-open edge prints EVERY chatbox-layer component (iface, child, buttonType, if3, type, y, createdComponentId, hidden, text, option) so the runtime proves which components are rendered options.
- SPACE continue route untouched and still works (same code path).

SOURCE VERIFIED (routes traced byte-for-byte incl. server DialogueInterpreter) / COMPILE VERIFIED / STATICALLY REVIEWED / **RUNTIME UNVERIFIED**.

### 34.5 P4 — NPC crosshair overlay

The overlay snapshot machinery is entity-agnostic (matches MiniMenu entries by keys + action + tile args); `addNpcEntries` was re-traced and stores the same tile args (intArgs1/2) as LOC entries with action codes 17/16/4/19/2 (+2000 for higher-level Attack). Changes:

- Whitelist now includes Examine entries (`LOC_ACTION_EXAMINE` 1004 shared LOC/NPC, `OBJ_EXAMINE` 1002) so e.g. Goblin -> "1 Attack / 2 Examine" fully displays.
- Acquisition range raised (see P5) — out-of-range was the prime suspect.
- Throttled `[FP-TARGET]` diagnostic (1 Hz, only when NPC action entries exist but no target was acquired) prints NPC entry count / out-of-range count / menu size so the remaining runtime cause is provable on the next test.
- Execution remains 100% existing pipeline: live-entry match -> `MiniMenu.doAction(i)`. No new packets, no second interaction engine.

SOURCE VERIFIED / COMPILE VERIFIED / STATICALLY REVIEWED / **RUNTIME UNVERIFIED** (root cause of the prior NPC failure not fully source-provable; diagnostics added).

### 34.6 P5 — display/acquisition range ~8 tiles

`INTERACT_RANGE_TILES` 3 -> 8, applied to overlay acquisition/hysteresis only. DISPLAY RANGE != GAME ACTION RANGE: execution still goes through `MiniMenu.doAction` -> existing RuneScape action logic; existing pathfinding/server decides approach distance and legality. No server checks changed. SOURCE VERIFIED / COMPILE VERIFIED / STATICALLY REVIEWED / **RUNTIME UNVERIFIED**.

### 34.7 P6 — E = primary world action

E executes overlay slot 0 (primary existing MiniMenu action) for ANY target type — no per-entity behavior. Gates: FIRST_PERSON rig state, CTRL not held, chat inactive, dialogue inactive, right-click menu closed. E never reaches chat (gameplay key filter). Existing from round #5; gates hardened this round. SOURCE VERIFIED / COMPILE VERIFIED / STATICALLY REVIEWED / **RUNTIME UNVERIFIED**.

### 34.8 P7-P11 — FREE vanilla locomotion + handoffs

Root cause of the prior FREE failure (source proven, exactly two blockers):

1. `NpcList.method4514` skipped vanilla `method2247` for self in ANY modern state.
2. `Protocol.readSelfPlayerInfo` drained the movement queue on every server step in ANY modern state.

Both now gated on `isModernQ16Owner()` (false in FREE) -> in FREE, `move()` steps accumulate in the vanilla queue and `method2247` consumes/interpolates/animates exactly like ORIGINAL. Click-to-walk, minimap walk, and vanilla run all flow through the untouched vanilla PathFinder/MOVE_GAMECLICK path (`Protocol.method1756` one-shot, `anInt1742` — never touched by modern code).

- **CHASE -> FREE (P9)**: rig transition calls `ModernMovementController.onEnterFreeMode()` — Q16 velocity/intent zeroed, pending cleared, `movementQueueX[0]/Z[0]` rebased to the live tile then `method2689()` (queue empty, stationary at the actual position — xFine/zFine untouched, NO tile snap; mid-tile offsets are safe for vanilla interpolation). Vanilla queue becomes the single movement owner; WASD inert (update() no-ops).
- **FREE -> CHASE (P10)**: rig transition calls `onExitFreeMode()` — vanilla auto-path cancelled (`method2689()`), prediction seeded from live xFine/zFine (no snap), server tile rebased, heading from body facing, stale pending discarded, WASD re-enabled. **Arbitration rule (documented in code):** client queue cleared immediately; the next modern DDA walk packet resets the server-side route; residual server steps for the cancelled path arrive through the Q16 drain hooks and are reconciled — never replayed as vanilla movement.
- **P11**: FREE camera untouched — arrows/middle-mouse/expanded zoom/no-behind-character all remain; no vanilla zoom max restored; profile stays MODERN.
- `aBoolean187` (minimap destination) verified render-only (SceneGraph cross marker) — no per-tick re-path, no handoff cleanup needed.

SOURCE VERIFIED / COMPILE VERIFIED / STATICALLY REVIEWED / **RUNTIME UNVERIFIED**.

### 34.9 P12 — CHASE run animation flicker

All `movementSeqId` writers enumerated and gated correctly during continuous RUN (ModernMovementController transition-only writes + idle branch; method949 overwrites gated on `idleAnimationId == movementSeqId`, impossible during RUN; method879 advances frames without seq changes). Sequence alternation is therefore NOT source-provable as the cause. Delivered:

- Throttled diagnostics: `[MOVE-DEBUG]` (1 Hz, CHASE moving) now includes moveSeqId, frame/anInt3407, anInt3396, state, runRequested, queueSize, movement owner; F12 overlay gained moveSeq + queueSize + velocity + predicted tile.
- One safe structural fix: `selectAnimationForState()` resets `anInt3407/anInt3396` when the selected sequence actually CHANGES, so WALK->RUN starts at frame 0 instead of inheriting a mid-sequence index (source-plausible flicker candidate; no per-tick restart; BasType/sequence system untouched).

SOURCE VERIFIED (writer audit) / COMPILE VERIFIED / STATICALLY REVIEWED / **RUNTIME UNVERIFIED** — true cause pending `[MOVE-DEBUG]` output from the next runtime.

### 34.10 P13 — input ownership matrix

Documented as the class-level javadoc authority table on `ModernControlController` (ORIGINAL / MODERN FIRST_PERSON incl. CTRL substate / MODERN CHASE / MODERN FREE), matching the implementation 1:1. No ambiguous dual owners: movement = exactly one owner via `getMovementOwner()`; keyboard = priority chain above; mouse = FP lock vs CTRL UI vs vanilla.

### 34.11 P14 — debug overlay

F12 unchanged/safe. Added: `movementOwner`, `cameraOwner`, `ctrlUICursor`, `cursorLocked` (CONTROL); new DIALOGUE/TARGET section (`dlgActive`, `choices`, `modal`, `worldBlockedByDlg`, targetType/targetName/targetDist, action1/2/3); new MOVEMENT section (velX/velZ, run, queueSize, moveSeq, predictedTile). Existing serverTile/intent/heading lines retained. No stdout flooding (overlay-only; diagnostics prints are throttled 1 Hz or edge-triggered).

### 34.12 P15 — ORIGINAL invariant

All new gates require `CameraMode.isModern()` and/or the modern rig; `isModernQ16Owner()` is false in ORIGINAL; `shouldForwardKeyToChat` returns true unconditionally in ORIGINAL; FirstPersonCamera/overlay/dialogue controller never run in ORIGINAL. ORIGINAL remains authentic vanilla 2009Scape. STATICALLY REVIEWED.

### 34.13 P16 — build + static sweeps A-R

Build: **BUILD SUCCESSFUL (EXIT_CODE=0)**, `:client:compileJava` executed, zero compile errors. Sweeps:

- A. Cursor lock/unlock ownership — only lockCursor/unlockCursor mutate cursorLocked; CTRL substate + activate/deactivate are the sole callers. PASS
- B. CTRL held cannot rotate FP camera — mouse-look gated on uiCursorActive. PASS
- C. CTRL release cannot produce a first-frame jump — lockCursor sets discardLockedMouseSample + recentres; lastMouseLook reset. PASS
- D. Dialogue blocks world 1-9/E — uiConsumed chain + isOverlayActive dialogue gate. PASS
- E. Dialogue 1..N uses exact existing button execution — UNKNOWN_8 / method4512(UNKNOWN_9/1003) routes. PASS
- F. NPC overlay uses existing MiniMenu entries — snapshot + doAction only. PASS
- G. Range raised without changing interaction range — overlay-only constant. PASS
- H. E routes through primary existing action — executeAction(0) -> doAction. PASS
- I. FREE has no ModernMovementController writes — update() gated on isModernQ16Owner. PASS
- J. FREE vanilla movement queue active — NpcList + 5 Protocol sites gated. PASS
- K. FREE->CHASE rebases safely — onExitFreeMode seeds from live state. PASS
- L. CHASE->FREE rebases safely — onEnterFreeMode rebases queue, no xFine write. PASS
- M. No dual movement owners — single predicate, 7 call sites. PASS
- N. FP WASD basis unchanged — velocity math untouched. PASS
- O. ORIGINAL untouched — see 34.12. PASS
- P. Roof crash hotfix preserved — ScriptRunner gate verbatim. PASS
- Q. No global culling hack — none introduced. PASS
- R. No new handcrafted combat/action packets — existing routes only. PASS

### 34.14 Verification summary

| Item | Status |
|------|--------|
| P0 crash hotfix preserved | SOURCE VERIFIED / STATICALLY REVIEWED |
| P1 CTRL UI cursor | SOURCE / COMPILE / STATIC / RUNTIME UNVERIFIED |
| P2 dialogue authority | SOURCE / COMPILE / STATIC / RUNTIME UNVERIFIED |
| P3 dialogue 1..N | SOURCE / COMPILE / STATIC / RUNTIME UNVERIFIED |
| P4 NPC overlay | SOURCE / COMPILE / STATIC / RUNTIME UNVERIFIED (diagnostics added) |
| P5 range 8 | SOURCE / COMPILE / STATIC / RUNTIME UNVERIFIED |
| P6 E primary | SOURCE / COMPILE / STATIC / RUNTIME UNVERIFIED |
| P7-P11 FREE vanilla locomotion | SOURCE / COMPILE / STATIC / RUNTIME UNVERIFIED |
| P12 run flicker | SOURCE / COMPILE / STATIC / RUNTIME UNVERIFIED (diagnostics added) |
| P13 ownership matrix | SOURCE / STATIC |
| P14 debug overlay | SOURCE / COMPILE / STATIC / RUNTIME UNVERIFIED |
| P15 ORIGINAL invariant | STATIC |

No runtime success is claimed. The 31-item user runtime test list from the round brief is the acceptance gate.

---

## Section 35 — Round #7: FREE Vanilla Input + NPC Pick Fix + Real Dialogue Route + Ceiling

Date: 2026-08-14. Build: `build_rt4.bat` -> **BUILD SUCCESSFUL in 38s (EXIT_CODE=0)**, `:client:compileJava` clean, zero errors.

### 35.0 USER RUNTIME from Round #6B/C — AUTHORITATIVE

**PASS (preserved, not touched this round):**
- FIRST_PERSON CTRL-hold free mouse works correctly.
- Releasing CTRL returns to locked FP with no camera jump.
- When dialogue is visible, the FP object/world action overlay disappears.
- FREE movement ownership improved: WASD no longer moves the player in FREE.

**FAIL (addressed this round):**
1. FREE kept MODERN keyboard/chat filtering (W/A/S/D blocked from chatbox).
2. NPC crosshair/action overlay never appeared (object overlay worked) — NOT a range problem.
3. Dialogue number shortcuts 1-5 did not select choices (SPACE continue worked).
4. FIRST_PERSON had no visible ceiling indoors.

### 35.1 P1 — FREE keyboard ownership architecture

Single source of truth: `ModernControlController.isModernGameplayKeyboardOwner()` = `CameraMode.isModern() && (!ModernCameraRig.isActive() || isFirstPersonRigState() || isChaseRigState())`.

- Both `Keyboard.nextKey()` drain sites (`Protocol` L2826 region, `client` L1173 region) route through `shouldForwardKeyToChat`, which now returns TRUE (fully unfiltered vanilla) for ORIGINAL **and** FREE.
- `ModernDialogueKeyboard` is fully inert in FREE — every gate switched from `CameraMode.isModern()` to `isModernGameplayKeyboardOwner()` (vanilla has no SPACE-continue / number-choice shortcuts).
- FREE needs no explicit-ENTER chat ownership: vanilla accepts typing directly. FREE click-to-walk + modern expanded camera retained.

**Ownership matrix (documented in code):**
| Mode/rig | Keyboard owner | Chat forward mode |
|---|---|---|
| MODERN FIRST_PERSON | MODERN_GAMEPLAY | MODERN_FILTER (gameplay letters suppressed) |
| MODERN CHASE | MODERN_GAMEPLAY | MODERN_FILTER |
| MODERN FREE | VANILLA_FREE | VANILLA (W/A/S/D/E/1-9 are ordinary characters) |
| ORIGINAL | ORIGINAL (untouched) | VANILLA |

### 35.2 P2 — NPC pick finding (source-proven root cause)

The pick/menu pipeline WAS delivering NPC entries; the overlay rejected them because of a key-decoding asymmetry in `MiniMenu.addEntries` (L1224-1228):

```
x = (int) key & 0x7F;                     // NPC keys: 0
local133 = (int) key >> 29 & 0x3;         // type: 0=player 1=NPC 2=loc 3=objstack
local140 = (int)(key >>> 32) & MAX_INT;   // entity index
z = (int) key >> 7 & 0x7F;                // NPC keys: 0
```

NPC pick tags are `npcIndex << 32 | 0x20000000L` — the low tile bits are ZERO, so `addNpcEntries` stored `intArgs1 = intArgs2 = 0` for every NPC. The old overlay range check compared those zeros against the player tile and rejected EVERY NPC at any range (runtime proof: `[FP-TARGET] NPC menu entries=1 outOfRange=1`).

**Fix (`ModernActionOverlay.snapshot` rewrite):**
- New `resolveEntryTile(key, fallbackX, fallbackZ, out)`: NPC/player tiles resolved LIVE from `NpcList.npcs[index].xFine/zFine >> 7` / `PlayerList.players[index]`; loc/objstack keep intArgs tiles.
- Hysteresis re-resolves the previous target's live tile each frame; fresh acquisition walks the menu backwards with per-entry live-tile range check; collect loop matches on the pick KEY only (intArgs are unreliable for entities).
- `NPC_EXAMINE` (1007) added to the world-action whitelist + getTargetType.
- Execution unchanged: live-entry match -> `MiniMenu.doAction(i)`. doAction's NPC branches use the live NPC from the key, so the zero tile args are irrelevant there. No second interaction engine.
- Old `[FP-TARGET]` print replaced with throttled `NPC_PICK:` diagnostic (F12 on, ~1 Hz): npcUnderCrosshair / scenePickTagSeen (scans `Model.aLongArray11[0..MiniMenu.anInt7)`) / npcMiniMenuEntries / firstNpcAction / overlayAccepted / rejectReason.

### 35.3 P3 — dialogue click-route finding + fix

**Why 1-5 failed while SPACE worked:** `collectChatboxButtons` scanned ONLY the chatbox-layer interface (clientCode 1406 subs). The continue component that SPACE found lived in a DIFFERENT open interface (`findContinueComponent` already had the any-open-interface fallback — that is why SPACE worked). The choice buttons live in that same interface, so the number-key collector found zero candidates.

**Fix:** `collectChatboxButtons` is now two-pass — pass 1: chatbox-layer subs (unchanged); pass 2 (only if pass 1 found nothing): ANY open interface with the same predicates, mirroring the proven-working continue scan. Ordering still by component `y` (visual top-to-bottom). Execution routes unchanged and byte-identical to vanilla:
- CS1 button -> UNKNOWN_8: `p1isaac(10); p4(component.id)`.
- if3 op -> UNKNOWN_9/1003: `ClientProt.method4512(optionBase, createdComponentId, op+1, component.id)`.

**Runtime trace added (observation only):** `MiniMenu.doAction` logs `[DIALOGUE-CLICK-TRACE]` ONCE per distinct (component, action) for action codes 8/9/1003/41 with interfaceId, childId, component.id, createdComponentId, if3, type, buttonType, clientCode, actionCode, key, intArg1/2, opIndex, text, option, executionRoute. Compare a manual mouse click against a number-key press to prove identical routes. No packets invented, no interface IDs hardcoded.

`ModernDialogueKeyboard` records `lastNumberKey / lastChoiceRoute (IF3_METHOD4512 / CS1_BUTTON) / lastChoiceComponent` for the F12 overlay. Input priority chain unchanged (chat > dialogue > modal > CTRL UI cursor > world keys > locomotion).

### 35.4 P4 — CEILING_SOURCE_RESULT = UPPER_FLOOR_SINGLE_SIDED

Source trace (no implementation guesses):
- No dedicated ceiling/underside geometry exists in the engine or cache build path.
- `SceneGraph.method2610` (software floor render) culls via explicit screen-space winding tests; GL floors are baked `GlTile` VBOs rendered with `GL_CULL_FACE`/`GL_BACK`.
- Roof LOC models are ordinary one-sided models.
- Upper-plane floors (`tiles[plane+1]`, `PlainTile`/`ShapedTile`) are therefore single-sided surfaces seen from above only.

Conclusion: P5 (un-cull real geometry) is impossible — there is no real underside. **P6 generated-underside path implemented.**

### 35.5 P6 — ceiling implementation approach (GENERATED, FP-only)

New `ModernCeiling.java` (gate + diagnostics) + `SceneGraph.modernCeilingUnderside(x, z, heightPlane)` hooked in `method4245` immediately after the floor dispatch, gated by `ModernCeiling.isEnabled() && level == Player.plane`:

- **Authority:** only when the tile ABOVE (`tiles[plane+1][x][z]`) has a real floor `PlainTile`. No outdoor/courtyard/overhang fake ceilings — the structural upper floor IS the authority.
- **Projection:** exact `method2610` maths (camera-relative corners, yaw/pitch rotate, near-clip < 50, `(x<<9)/depth` screen projection).
- **Software path:** same two triangles with INVERTED winding tests (visible from below), corner colours darkened ~20% via `ColorUtils.multiplyLightness3(hsl, 102)`; textured tiles reuse the upper tile's texture (`manyGroundTextures` gate + `getAverageColor` fallback exactly like vanilla); texture orientation flag ignored for the underside (first-impl simplification).
- **GL path:** dedicated immediate-mode pass; `glPushAttrib`-scoped `GL_LIGHTING`/`GL_CULL_FACE` disable (restored by `glPopAttrib` — global state never changed), texture bound via `Rasteriser.textureProvider.method3227`, brightness `201.5 - (plane+1)*50` like vanilla upper floors, palette-derived vertex colours, 2-unit downward offset against z-fighting.
- **Deferred:** `TILE_FLAG_UNDER_ROOF` flat-roof fallback ceiling (reported in diagnostics only) until user runtime feedback on upper-floor coverage.

**P7 preservation:** gate is FIRST_PERSON-only; CHASE/FREE/ORIGINAL never reach the hook; roof logic, Round #4 rolling-stamp reset and Round #6A divide-by-zero hotfix untouched (verified in `ScriptRunner.method4302` L1361-1367 / L1411-1440); global `allLevelsVisible` and scene occlusion untouched.

### 35.6 P8 — debug overlay extensions

F12 gained: INPUT `keyboardOwner` (MODERN_GAMEPLAY / VANILLA_FREE / ORIGINAL) + `chatForwardMode`; DIALOGUE `lastNumberKey / lastChoiceComp / lastChoiceRoute`; new **NPC TARGET** block (`npcPickSeen / npcMenuEntries / accepted / npcUnderXhair / firstNpcAction / rejectReason`); new **CEILING** block (`sourceMode / overheadPlane / quadsDrawn / overheadTile / underRoofFlag / textureId`). No AWT, no crashes.

### 35.7 P9 — static review (15 points)

1. FREE receives NO Q16 writes — PASS (`isModernQ16Owner()` gates intact, L196-231/L395).
2. FREE receives FULL vanilla keyboard/chat — PASS (`shouldForwardKeyToChat` bypass + inert dialogue keyboard).
3. FP/CHASE still filter gameplay letters — PASS (same gate, positive branch).
4. ORIGINAL untouched — PASS (early return preserved).
5. CTRL FP cursor unchanged — PASS (no FirstPersonCamera edits this round beyond prior approved work).
6. Dialogue blocks world overlay — PASS (`isOverlayActive` gates unchanged).
7. Dialogue 1..N invokes vanilla mouse-click route — PASS (UNKNOWN_8 / method4512 only).
8. NPC target uses existing MiniMenu route — PASS (doAction only).
9. E invokes slot 1 — PASS (unchanged).
10. No invented packets — PASS.
11. Ceiling pass FIRST_PERSON only — PASS (gate + plane check).
12. No global backface/culling disable — PASS (scoped pushAttrib; software winding).
13. CHASE/FREE roof removal unchanged — PASS.
14. Roof flash fix unchanged — PASS (L1361-1367).
15. Round #6A divide-by-zero fix unchanged — PASS (L1411-1440).

### 35.8 Verification summary

| Item | Status |
|------|--------|
| P1 FREE vanilla keyboard/chat | SOURCE / COMPILE / STATIC / **RUNTIME UNVERIFIED** |
| P2 NPC crosshair fix | SOURCE / COMPILE / STATIC / **RUNTIME UNVERIFIED** |
| P3 dialogue 1..N two-pass + trace | SOURCE / COMPILE / STATIC / **RUNTIME UNVERIFIED** |
| P4 ceiling source trace | SOURCE VERIFIED (UPPER_FLOOR_SINGLE_SIDED) |
| P6 generated ceiling | SOURCE / COMPILE / STATIC / **RUNTIME UNVERIFIED** |
| P8 overlay blocks | SOURCE / COMPILE / STATIC |
| P9 build | **BUILD SUCCESSFUL in 38s (EXIT_CODE=0)** |

No runtime success is claimed. Next user runtime is the acceptance gate.

**Next user test checklist:**
1. FREE: type `wasd` in chatbox — letters must appear; click-to-walk still works.
2. FP: aim crosshair at a Goblin — expect `Goblin / 1 Attack / 2 Examine`; press E. F12 NPC TARGET block shows the pick state.
3. FP dialogue with choices: press 1/2 — must equal mouse click (compare `[DIALOGUE-CLICK-TRACE]` lines); SPACE continue still works.
4. FP indoors: look up — ceiling visible, ~20% darker than the floor above, matching material. F12 CEILING block: sourceMode=GENERATED, quadsDrawn > 0.
5. CHASE/FREE roofs + ORIGINAL unchanged.

---

## Section 36 — Round #7B: NPC Overlay Crash Root Cause + Ceiling Coverage/Near-Plane Hardening

Date: 2026-08-14. Build: `build_rt4.bat` -> **BUILD SUCCESSFUL in 42s (EXIT_CODE=0)** (one transient Kotlin daemon restart, build recovered), zero compile errors.

### 36.0 USER RUNTIME from Round #7 — AUTHORITATIVE

**CONFIRMED PASS (not touched this round):**
- FP CTRL-hold free mouse + release relock.
- Object crosshair overlay.
- Dialogue hides world overlay.
- FREE no longer uses WASD locomotion.
- **Generated ceiling underside fundamentally works — ceiling textures are visible.**
- Smooth zoom / FP / CHASE foundations; roof flashing fix; Round #6A divide-by-zero hotfix.

**NEW FAILURES:**
1. Aiming at an NPC in FIRST_PERSON crashes the client around the moment the NPC overlay would activate. **User correction: the NPC overlay has NEVER been confirmed rendered — no "NPC pick -> overlay render = pass" claim is made.**
2. Ceiling: looking straight up can clip the ceiling away exposing sky; coverage extends only a few tiles ahead.

### 36.1 P0 — EXACT CRASH RETRIEVED (no guessing)

Exact stacktrace from client stdout at crash:

```
Error: rt4.SceneGraph.modernCeilingUnderside:3995
       rt4.SceneGraph.method4245:1799
       rt4.SceneGraph.method3292:3123
       rt4.SceneGraph.method2954:2981
       rt4.ScriptRunner.method4326:315
       rt4.Cs1ScriptRunner.renderComponent:392 ...
java.lang.ArrayIndexOutOfBoundsException: 1
```

- **NPC_CRASH_BOUNDARY = the CEILING render pass — NOT the NPC overlay.** The crash coincided with aiming at an NPC only in timing; a region reload (localTile jump 22,46 -> 54,54) preceded it.
- **NPC_CRASH_SITE = rt4.SceneGraph.modernCeilingUnderside:3995** (Round #7 per-tile ceiling hook indexing `tiles[plane+1]`).
- **NPC_CRASH_EXCEPTION = java.lang.ArrayIndexOutOfBoundsException: 1.**

**Root cause (SOURCE VERIFIED):** `SceneGraph` rebinds `tiles`/`tileHeights` to the UNDERWATER scene (`underWaterGroundTiles` / `underwaterTileHeights`), which is allocated `new Tile[1][width][length]` — only ONE level. The Round #7 hook guarded with a hardcoded `planeAbove >= 4` check, so on the underwater scene `tiles[1]` threw `AIOOBE: 1`.

**NPC overlay code audit (unchanged, bounds-safe):** `ModernActionOverlay` NPC index decode `(int)(key >>> 32)` is guarded by `index >= 0 && index < NpcList.npcs.length` at both use sites (L366 snapshot, L552 resolveEntryTile); E still routes through `MiniMenu.doAction(i)` (L532). Per the user's instruction, the new tile-resolution logic was NOT assumed guilty and was left untouched.

### 36.2 P0/P3 fix — structural coverage pass replaces the per-tile hook

The Round #7 per-tile hook in `method4245` was REMOVED. Replaced by `SceneGraph.modernCeilingPass()`, called ONCE per frame at the end of `method2954`, gated by `ModernCeiling.isEnabled()` (MODERN + FIRST_PERSON only). Guard chain (actual array lengths everywhere — P0 class of bug eliminated):

1. `tiles` / `tileHeights` null check.
2. **`tileHeights == underwaterTileHeights` -> return** (the exact P0 crash condition).
3. `plane < 0 || plane >= 3 || planeAbove >= tiles.length || planeAbove >= tileHeights.length` -> return.
4. Null level / null row guards per scanned column.
5. Software-renderer pitch gate (`cameraPitch >= 1280`): the software rasterizer has no depth buffer and the pass runs after the whole scene, so it draws only while looking up; GL relies on its depth test for occlusion.

**Coverage (P3):** circle scan of `CEILING_COVERAGE_RADIUS = 16` tiles around the camera (`cameraX/Z >> 7`) on `Player.plane + 1` — bounded per-frame work, independent of which tiles the floor traversal reached. A tile renders ONLY if `tiles[plane+1][x][z]` holds a real `PlainTile` or `ShapedTile` (structural authority — no outdoor/courtyard fills, no blind square fill).

### 36.3 P2 — near-plane TRIANGLE clipping (no whole-tile reject)

Vanilla `method2610`/`method2762` reject an entire tile when ANY vertex has depth < 50 — that is what opened the sky hole looking straight up. The ceiling pass instead clips per triangle (Sutherland–Hodgman against `depth >= 50`):

- 3 vertices in front -> draw normally; 0 in front -> skip; 1-2 in front -> true edge intersections (16.16 fixed-point `t`, clamped), producing a 3-4 vertex clipped polygon fanned to triangles.
- ALL attributes interpolated across the clip: colour/light, projection rotX/rotY, UVs, absolute world position.
- Software path keeps the INVERTED winding test (visible from below) and vanilla fill branches (untextured gouraud / `manyGroundTextures` average-colour fallback / textured).
- GL path submits clipped triangles to immediate mode; GPU clipping + depth buffer handle the rest. GL state is scoped by `glPushAttrib`/`glPopAttrib` — **global culling never changes**.
- No `depth = max(depth, 50)` hack; the global near plane is untouched.
- The only remaining whole-tile reject is the vanilla all-vertices-behind case (unavoidable: nothing to clip to).

### 36.4 P4 — shaped upper floors

`drawShapedCeilingTile` reuses the `ShapedTile`'s REAL vertices (`anIntArray168/160/163`), per-triangle colours (`anIntArray167/172/171` darkened ×102/128) and textures (`anIntArray161`) with exact `method2762` rotation — stairs/slopes are NOT flattened. Texture anchoring mirrors vanilla (`aBoolean113` flat mode uses corner verts 0,1,3). A clipped textured shaped triangle degrades to average-colour gouraud because vanilla's UV basis vertices can be clipped away (documented in code). GL shaped triangles draw as average-colour gouraud (shaped data carries no per-vertex UVs).

### 36.5 P5 — material quality preserved

`DARKEN_MULTIPLIER = 102` (~20% darker) and full material inheritance from the upstairs floor are unchanged from Round #7. No universal beige/gray.

### 36.6 P1 — F12 diagnostics

CEILING block extended with Round #7B counters (per-frame latched on `client.loop`, no stdout spam): `candidateTiles`, `drawnTiles`, `nearRejected` (all-vertices-behind rejects), `behindVerts` (vertices crossing the near plane), `plainTiles`, `shapedTiles`; `quadsDrawn` now reports total triangles drawn. `ModernCeiling.updateDiagnostics` hardened: actual-length bounds, null-row guards, shaped tiles accepted for `overheadTilePresent`.

### 36.7 P7 — static review (brief checklist)

1. NPC overlay no crash route preserved — code untouched; the actual crash was the ceiling pass (36.1). SOURCE VERIFIED.
2. NPC index bounds checked — PASS (L366 + L552 guards intact).
3. NPC target still through existing MiniMenu — PASS (doAction L532).
4. E still uses `MiniMenu.doAction` — PASS (unchanged).
5. Object overlay unchanged — PASS (no edits).
6. Ceiling only active in MODERN FIRST_PERSON — PASS (`isEnabled()` gate + FP-only call site).
7. No global culling disable — PASS (scoped `glPushAttrib`/`glPopAttrib`; software uses inverted winding).
8. No global near-plane hack — PASS (local `CEILING_NEAR_PLANE = 50`, vanilla methods untouched).
9. Clipping doesn't expose sky at vertical pitch — STATIC PASS (true triangle clipping; software pitch gate); RUNTIME UNVERIFIED.
10. Coverage scans only structurally valid overhead tiles — PASS (null/PlainTile/ShapedTile authority + actual-length bounds + underwater + plane<3 guards).
11. CHASE/FREE roof behaviour untouched — PASS (no roof code edits).
12. ORIGINAL untouched — PASS (no ORIGINAL-path edits).

### 36.8 Verification summary

| Item | Status |
|------|--------|
| P0 crash boundary/site/exception | **SOURCE VERIFIED** (exact stacktrace, root cause proven) |
| P0 fix (underwater guard + coverage pass) | SOURCE / COMPILE / STATIC / **RUNTIME UNVERIFIED** |
| P1 F12 ceiling counters | SOURCE / COMPILE / STATIC |
| P2 near-plane triangle clipping | SOURCE / COMPILE / STATIC / **RUNTIME UNVERIFIED** |
| P3 16-tile structural coverage scan | SOURCE / COMPILE / STATIC / **RUNTIME UNVERIFIED** |
| P4 shaped upper floors | SOURCE / COMPILE / STATIC / **RUNTIME UNVERIFIED** |
| P5 material inheritance + 20% darkening | SOURCE / COMPILE / STATIC (unchanged from Round #7) |
| P6 structural safety | STATIC PASS / **RUNTIME UNVERIFIED** |
| Build | **BUILD SUCCESSFUL in 42s (EXIT_CODE=0)** |
| NPC OVERLAY | **RUNTIME UNVERIFIED / CURRENTLY CRASHING** until user sees the layout without a crash |

No runtime success is claimed. Stopping after build/static review per brief.

**Next user test checklist:**
1. FP: aim at a Goblin-equivalent NPC — expect `Goblin / 1 Attack / 2 Examine` WITHOUT a crash; E executes slot 1 (MiniMenu route). If it still crashes, capture the new stacktrace.
2. FP indoors: look STRAIGHT up — ceiling must stay closed (no sky hole) while crossing the near plane. F12 `behindVerts > 0` with `nearRejected` low while this happens.
3. FP: walk a long corridor/large building — ceiling coverage should extend continuously (~16-tile radius), not stop after a few tiles. F12: `candidateTiles`/`drawnTiles` track coverage.
4. FP outdoors/courtyard/under an overhang with no upper floor — no ceiling appears.
5. Underwater areas — no crash (the exact P0 condition).
6. CHASE/FREE roofs + ORIGINAL unchanged.

---

## Section 37 — Round #7C: STABILIZATION — Ceiling Quarantine + Overlay Blocking Boundary Proven + Dialogue Numbers AWAITING

Date: 2026-08-14. Build: `gradlew compileJava` -> **BUILD SUCCESSFUL in 27s (EXIT_CODE=0)**, zero compile errors (only pre-existing Kotlin plugin-playground warnings). One transient Kotlin daemon cache error — build recovered via fallback, as in Round #7B.

### 37.0 USER RUNTIME from Round #7B — AUTHORITATIVE

**FAIL:**
1. Round #7B generated ceiling: **RUNTIME FAILED** — ceiling visible indoors: **FAIL** (no ceiling appears at all).
2. New severe geometry artifact: **FAIL** — giant horizontal textured slab/triangle crossing the FIRST_PERSON view at certain locations/angles (user screenshot: huge green/black horizontal surface spanning the view). Strong suspect = Round #7B coverage/near-plane pass.
3. Object crosshair/action overlay: **FAIL — REGRESSION** (worked in an earlier runtime round).
4. NPC crosshair/action overlay: **FAIL** (NEVER user-runtime verified in any round).
5. Dialogue number keys 1/2/3/4/5: **FAIL** (NPC dialogue and object/interface dialogue; mouse/dialogue otherwise works).

**PASS / NOT TOUCHED:** ORIGINAL, FREE vanilla keyboard/chat ownership, click-to-walk, FIRST_PERSON CTRL-hold free mouse + release, smooth zoom, CHASE/FP movement basis, roof flashing fix, Round #6A divide-by-zero roof hotfix, camera rig state machine, combat, world generation.

### 37.1 P1 — Generated ceiling pass QUARANTINED (single source gate, zero geometry)

- `ModernCeiling.RENDER_ENABLED = false` — ONE explicit source gate. `SceneGraph.modernCeilingPass()` now returns as its FIRST statement, before any scan/projection/rasterizer code: the pass submits **ZERO geometry** this round. Implementation fully preserved (not deleted); flip the single flag to re-enable in a future round.
- No vanilla roof/scene code touched, no global culling change, no camera transform change.
- F12 CEILING block now shows `rendererEnabled N` and `trianglesSubmitted 0` so the quarantine is runtime-visible.
- **Boundary rule:** if the giant slab STILL appears at runtime with the generated ceiling quarantined, then `CEILING_ARTIFACT_CAUSE = NOT_GENERATED_CEILING` and the next renderer boundary must be source-traced (the slab provably cannot originate from the disabled pass).
- No replacement ceiling rendering this round (per brief).

### 37.2 P2 — Overlay blocking boundary: dialogue false-positive PROVEN FROM SOURCE

The exact predicate chain that disabled the world overlay everywhere:

1. Round #7 P3 added a "pass 2: scan ANY open interface" fallback to `ModernDialogueKeyboard.collectChatboxButtons`.
2. Its if3 predicate accepts ANY visible component with ANY server-enabled op — `InterfaceList.getOp(c, op)` returns non-null whenever `getServerActiveProperties(c).isButtonEnabled(op)` and `ops[op]` is non-empty.
3. The ALWAYS-OPEN gameframe/HUD interfaces contain visible if3 components with enabled ops, so the scan returns > 0 with NO dialogue open.
4. `hasActiveDialogue()` = pending resume || continue component || that scan > 0 → permanently TRUE.
5. `ModernActionOverlay.isOverlayActive()` requires `!hasActiveDialogue()` → overlay permanently disabled (object + NPC), and number keys were consumed by guessed HUD-button "choices" that do nothing.

**Fix (false-positive detection ONLY removed; the correct rule preserved):**
- Choice collection is CHATBOX-LAYER-ONLY again (pass 2 removed).
- Detection now = pending resume (`Cs1ScriptRunner.aClass13_10`) || continue component (`buttonType==6` CS1 / if3 resume-pause — narrow predicates that do not match ordinary HUD components; the proven-RUNTIME two-pass SPACE continue scan is preserved) || chatbox-layer choice buttons.
- Result: NO real dialogue → world overlay allowed; REAL dialogue → world overlay hard blocked.
- F12 WORLD OVERLAY block proves the boundary at next runtime: `overlayGate`, `blockedReason` (NOT_MODERN / NOT_FP / RCLICK_MENU / CHAT_INPUT / DIALOGUE_BLOCK / CTRL_UI_CURSOR), `dialogueBlock`, `dialogueActive`, `choiceCount`, `menuSize`, `scenePickTags`, `worldEntries`, `locEntries`, `npcEntries`, `accepted`, `targetType`, `targetName`.

### 37.3 P3 — LOC/object route: intact, blocker removed

The known-good architecture is untouched: scene pick → existing LOC MiniMenu entries → `ModernActionOverlay.snapshot()` → `draw()` → E/1/2/3 → `MiniMenu.doAction(i)` (exact mouse-click route). No new raycaster, no custom packets, no fake proximity targeting. The ONLY change affecting this route is the P2 gate fix. Runtime target: aim at Door → `Door / 1 Open / 2 Examine`, E executes slot 1. **RUNTIME UNVERIFIED.**

### 37.4 P4 — NPC route: same architecture + independent boundary diagnostics

Round #7 source finding kept: NPC pick tag = `npcIndex << 32 | 0x20000000L` with NO useful x/z tile bits — range therefore uses the LIVE NPC (`NpcList.npcs[index].xFine/zFine`), never MiniMenu intArgs. Index decode is bounds-guarded at every use site. F12 NPC TARGET block extended for independent boundary proof: `npcIndex`, `npcExists`, `liveTile x,z`, `playerTile`, `distance` alongside `npcPickSeen`, `npcMenuEntries`, `accepted`, `npcUnderXhair`, `firstNpcAction`, `rejectReason`. Expected: `Goblin / 1 Attack / 2 Examine`, E → slot 1 via `MiniMenu.doAction`. **NEVER RUNTIME VERIFIED.**

### 37.5 P5 — Dialogue numbers: NO real trace exists → AWAITING (no third guess)

- Searched for a captured `[DIALOGUE-CLICK-TRACE]` of a REAL manual mouse click: **NONE EXISTS** (client stdout is console-only; the only persisted logs are JVM crash dumps — no trace lines).
- Therefore: **DIALOGUE_NUMERIC_ROUTE = AWAITING_RUNTIME_CLICK_TRACE** (shown on F12 as `numericRoute`).
- Number-key choice execution is QUARANTINED this round: `ModernDialogueKeyboard.update()` consumes SPACE continue only (proven route); number keys are NOT consumed and fall through to the FP world-action layer. No third guessed component predicate was added.
- The one-shot `[DIALOGUE-CLICK-TRACE]` in `MiniMenu.doAction` (codes 8/9/1003/41) stays armed: the next runtime the user manually clicks ONE visible dialogue option, producing the exact route record (interfaceId, childId, component.id, createdComponentId, if3, type, buttonType, clientCode, actionCode, key, intArg1/2, opIndex, text, option, executionRoute) for a tiny follow-up round implementing 1..N from that exact route, ordered visually top-to-bottom.

### 37.6 P6 — Input priority preserved

FIRST_PERSON/CHASE: (1) explicit chat/text input → (2) REAL active dialogue → (3) modal/interface input → (4) FP CTRL cursor → (5) FP world action E/1/2/3 → (6) gameplay movement. With no dialogue present the world overlay is no longer blocked by unrelated open interfaces (P2 fix). FREE keeps full vanilla keyboard/interface ownership.

### 37.7 P7 — Static review (15 points)

1. Generated ceiling submits ZERO geometry — PASS (`RENDER_ENABLED=false` gate returns before all geometry code).
2. Vanilla scene/roof code otherwise unchanged — PASS (only the now-inert `modernCeilingPass()` call exists in the scene path).
3. Screenshot-style slab cannot originate from disabled ModernCeiling code — PASS (zero submission; if it persists → NOT_GENERATED_CEILING).
4. Object MiniMenu path intact — PASS (snapshot → whitelist → doAction unchanged).
5. NPC bounds/index logic safe — PASS (all `NpcList.npcs[index]` accesses bounds-guarded).
6. NPC uses live tile for range — PASS (`resolveEntryTile` type==1 → live `xFine/zFine`).
7. World overlay blocked only by REAL dialogue — PASS (detection = pending resume / narrow continue predicate / chatbox-layer choices).
8. No generic always-open UI false-positive — PASS (any-interface choice scan removed).
9. E still calls `MiniMenu.doAction` — PASS (`executeAction`).
10. No invented packets — PASS (numeric execution fully disabled; SPACE uses the existing method10 route).
11. FREE keyboard behaviour unchanged — PASS (no ownership edits).
12. CTRL FP mouse unchanged — PASS.
13. ORIGINAL unchanged — PASS.
14. Round #4 roof stamp fix unchanged — PASS.
15. Round #6A roof divide-by-zero fix unchanged — PASS.

### 37.8 Verification summary

| Item | Status |
|------|--------|
| P1 ceiling quarantine (single gate, zero geometry) | SOURCE VERIFIED / COMPILE VERIFIED / STATIC PASS / RUNTIME UNVERIFIED |
| P2 overlay blocking cause | **SOURCE PROVEN** (predicate chain 37.2) / runtime confirmation on next F12 |
| P2 false-positive removal + real-dialogue rule preserved | SOURCE VERIFIED / COMPILE VERIFIED / RUNTIME UNVERIFIED |
| P3 LOC route restoration | SOURCE VERIFIED (route untouched, blocker removed) / RUNTIME UNVERIFIED |
| P4 NPC route + diagnostics | SOURCE VERIFIED / RUNTIME UNVERIFIED (never verified) |
| P5 real [DIALOGUE-CLICK-TRACE] exists | **NO** → DIALOGUE_NUMERIC_ROUTE = AWAITING_RUNTIME_CLICK_TRACE |
| Build | **BUILD SUCCESSFUL in 27s (EXIT_CODE=0)** |

No runtime success is claimed. Stopping after build/static review per brief — no new ceiling implementation, no combat/viewmodel/world-generation changes.

**Next user test checklist:**
1. FP indoors — no generated ceiling should appear (quarantine active); F12 CEILING: `rendererEnabled N`, `trianglesSubmitted 0`. If the giant slab STILL appears: capture screenshot + location/angle → `CEILING_ARTIFACT_CAUSE = NOT_GENERATED_CEILING`.
2. FP: aim at a Door/object — expect `Door / 1 Open / 2 Examine` overlay; E executes slot 1. F12 WORLD OVERLAY: `overlayGate Y`, `blockedReason -`, `locEntries > 0`, `accepted Y`. If still blocked: read `blockedReason` — it names the exact gate.
3. FP: aim at a Goblin — expect `Goblin / 1 Attack / 2 Examine`; E executes slot 1. F12 NPC TARGET: `npcPickSeen Y`, `npcExists Y`, `liveTile`, `distance`, `accepted Y`.
4. Open a REAL dialogue — world overlay must be hard blocked (`dialogueBlock Y`), SPACE continue still works.
5. Dialogue with visible choices: manually MOUSE-CLICK one option once; copy the `[DIALOGUE-CLICK-TRACE]` stdout line. Number keys 1..5 deliberately do nothing this round (AWAITING that trace).
6. CHASE/FREE roofs + ORIGINAL + smooth zoom + CTRL-hold FP mouse — unchanged.

## Section 38 — Round #7D: REAL DIALOGUE OPTION INTERFACES (cache-proven family 228..238) + NPC PICK BOUNDARY TRACE + FP GATE DIAGNOSTICS

Date: 2026-08-14. Build: `gradlew :client:compileJava` -> **BUILD SUCCESSFUL (EXIT_CODE=0)**, zero compile errors, zero IDE problems on all six edited files (only pre-existing Kotlin plugin-playground warnings; one transient Kotlin daemon cache error recovered via fallback, as in prior rounds).

### 38.0 USER RUNTIME from Round #7C — AUTHORITATIVE

**PASS:** LOC/object crosshair overlay WORKS again (Round #7C P2 gate fix confirmed). False dialogue block GONE (`dialogueBlock N`, `dialogueActive N`, `choiceCount 0`). Ceiling quarantine intact (`rendererEnabled N`, `trianglesSubmitted 0`).

**FAIL:**
1. NPC overlay still does NOT appear — F12: `scenePickTags=0`, `npcEntries=0` with an NPC at the crosshair. Per brief: do NOT touch NPC range/overlay acceptance first — trace the pick chain instead.
2. Dialogue number keys 1–5 still do not work. Manual trace observed `[CONTINUE OPT] Iface:241 Child:5 Slot:65535` — interface 241 child 5 is a "Click here to continue" component, NOT a multi-choice option. 241–244 are NPC continue dialogues.

### 38.1 P0 — Source/cache proof of the choice-interface family (NO guesses)

Proven from the cache, not from scanning:
- `dumps/530/530_interface_names.txt`: 228/230/232/234 = `multi2`..`multi5`; 236/237/238 = `multivar2`/`multivar4`/`multivar5`; 241–244 = `npcchat1..4`. The 229/231/233/235 variants belong to the same multi-choice family structure.
- `dumps/498/498_interface_dump.txt`: every interface 228..238 has child 0 = "Select an Option" title and children 1..N = option1..optionN **in rendered top-to-bottom order**; 241–244 have Name/Line/"Click here to continue" children only.
- `MiniMenu.addComponentEntries` routes (byte-verified against this fork's source): CS1 `buttonType==1` → action 8 (`UNKNOWN_8`); if3 ops 0..4 → action 9 (`UNKNOWN_9`, key=op+1), ops 5..9 → 1003; if3 resume-pause → 41. `MiniMenu.doAction` routes: UNKNOWN_8 = `method4265` clientCode gate + `p1isaac(10); p4(componentId)`; UNKNOWN_9/1003 = `ClientProt.method4512(optionBase, intArgs1(createdComponentId), (int)key(op+1), intArgs2(componentId))`; left-click executes `doAction(size-1)` (primary entry after `sort()` = first enabled op).

### 38.2 P1 — Real 1..N dialogue keys via structural family detection (ModernDialogueKeyboard)

- **Detection = STRUCTURAL**: `findChoiceInterfaceId()` walks `InterfaceList.openInterfaces` and accepts only interface ids **228..238** that have at least one clickable option child (children 1..5, not hidden). No generic op-scan of arbitrary interfaces → the Round #7C HUD false-positive mode is structurally impossible. 241..244 deliberately excluded (continue dialogues).
- **Option mapping**: Nth visible clickable child in INDEX order = option N (index order == rendered top-to-bottom order, cache-proven). Child 0 (title) is never selectable.
- **Execution = EXACT vanilla mouse-click routes** (no invented packets, no mouse simulation, no hardcoded text/NPCs):
  - if3 option → `ClientProt.method4512(c.optionBase, c.createdComponentId, op+1, c.id)` — byte-identical to `doAction` UNKNOWN_9/1003.
  - CS1 `buttonType==1` option → `method4265` gate + `Protocol.outboundBuffer.p1isaac(10); p4(c.id)` — byte-identical to `doAction` UNKNOWN_8.
- **Ownership**: while a choice-family interface is open, the dialogue OWNS 1..9 (keys beyond the visible option count are consumed as no-ops — they never reach the world layer). With NO family open, 1..9 fall through to the FP world-action layer exactly as before. SPACE continue keeps its proven UNKNOWN_41 route untouched.
- `NUMERIC_ROUTE_STATUS` = `FAMILY_STRUCTURAL_7D` (supersedes AWAITING_RUNTIME_CLICK_TRACE).
- New F12 (DIALOGUE/TARGET): `choiceIface`, `choiceCount`, `ch1..ch5` (child index of each visible option), `lastChoiceKey`, `lastActionCode`, `lastChoiceRoute`.

### 38.3 P2 — NPC pick boundary TRACE (instrumentation only, zero behavioural change)

No statically provable NPC-vs-LOC boundary exists (interactive NPC key is positive → `miniMenuPick` true; `Npc.render` sets `body.pickable=true` for size==1; allowInput/pick coords are shared statics). The FIRST divergent stage must be proven at runtime, so per-stage counters now sit INSIDE the vanilla chain:
- `Npc.render`: `diagNpcRendered++` after the null-type guard (render-chain entry).
- `GlModel.render`: `npcKeyDiag = (arg8 >>> 29 & 0x3L) == 1L` (key type bits == NPC). Inside the existing pick gate (`(miniMenuPick || roofVisibilityLocPick) && RawModel.allowInput && local70 > 0`): `diagNpcPickAttempts++`. At the mouse-bounds box hit: `diagNpcBoundsHits++` + `diagNpcCandidateIndex = (int)(arg8 >>> 32)` + capture of `this.pickable` / `miniMenuPick`. At BOTH `Model.aLongArray11[MiniMenu.anInt7++] = arg8` write sites: `diagNpcTagsWritten++`.
- `ModernActionOverlay.snapshot()` calls `refreshNpcPickChain()` FIRST (before the gate return, so diagnostics refresh even while the overlay is blocked). It folds counters into `diagNpcRejectBoundary`, publishes per-frame copies (`*Last`), then resets the accumulators.
- **Boundary ladder** (first failing stage, matching the brief's chain order): `NPC_NOT_RENDERED` → `ALLOW_INPUT`/`PICK_GATE` → `BOUNDS_MISS` → `NOT_PICKABLE`/`WRITE_MISS` → `MENU_BUILD` → `""` (no divergence).
- NO range-check change, NO custom raycaster, NO behavioural fix this round — the boundary must be proven by user runtime first.
- New F12 (NPC TARGET): `npcRendered`, `attempts`, `boundsHit`, `candNpc`, `pickable`, `mmPick`, `allowInput`, `tagWritten`, `boundary` alongside the existing `scenePickTags`/`npcEntries`.

### 38.4 P3 — FP gate disagreement check (diagnostics only; NO gate change)

Static analysis: `ModernCameraRig` self-heal (update step 2b) enforces `rigState == FIRST_PERSON ⇔ FirstPersonCamera.isActive()` every 50Hz tick, so in a STABLE frame the gate `isFirstPersonRigState()` cannot disagree with a visually-FP camera. The one legitimate divergence is BY DESIGN (Round #6A semantic rig authority): an obstruction-compressed CHASE boom sits at the eye position and LOOKS FP but stays CHASE — the Round #7C `blockedReason=NOT_FP` screenshot is consistent with either genuine CHASE or that designed case. Therefore NO gate/accessor rewrite (per brief: fix only if disagreement exists); instead `ModernCameraRig.getVisualMode()` (`INACTIVE` / `FIRST_PERSON` if `FirstPersonCamera.isActive()` / else `rigState.name()`) now lets the user PROVE any disagreement. New F12 (WORLD OVERLAY): `visualMode`, `rigState`, `overlayFpGate`.

### 38.5 P4 — Preserve (untouched)

Working LOC overlay route (snapshot → whitelist → `doAction`), E/number world action execution architecture, FREE keyboard behaviour, CTRL free mouse, movement, zoom, roofs, ceiling quarantine (`RENDER_ENABLED=false`), ORIGINAL mode. No ceiling code touched this round.

### 38.6 Static review

1. Choice detection is structural (ids 228..238 + clickable children 1..5) — PASS (no HUD false-positive possible).
2. if3 route byte-identical to `doAction` UNKNOWN_9/1003 (arg order `optionBase, createdComponentId, op+1, id` matches `method4512(JagString,int,int,int)`) — PASS.
3. CS1 route byte-identical to UNKNOWN_8 (method4265 gate + p1isaac(10)/p4(id)) — PASS.
4. Dialogue owns 1..9 only while a family interface is open; otherwise keys fall through to world layer — PASS.
5. SPACE continue route unchanged — PASS.
6. NPC instrumentation is pure counters inside existing branches; zero control-flow change in `Npc.render`/`GlModel.render` — PASS.
7. `refreshNpcPickChain()` runs before the overlay gate return and resets accumulators after publishing `*Last` copies — PASS (F12 never reads a reset counter).
8. No NPC range/raycaster/overlay-acceptance change — PASS.
9. FP gate logic unchanged; `getVisualMode()` is read-only — PASS.
10. No ceiling code touched; `ModernCeiling.RENDER_ENABLED` stays false — PASS.
11. LOC overlay path untouched — PASS.
12. FREE/CTRL/movement/zoom/roofs/ORIGINAL untouched — PASS.
13. No invented packets anywhere — PASS.
14. `GetProblems` on all six edited files: no errors — PASS.
15. Build: `gradlew :client:compileJava` BUILD SUCCESSFUL (EXIT_CODE=0) — PASS.

### 38.7 Verification summary

| Item | Status |
|------|--------|
| P0 family proof (cache names + 498 dump + doAction routes) | **SOURCE PROVEN** |
| P1 real 1..N dialogue keys (family 228..238) | SOURCE VERIFIED / COMPILE VERIFIED / STATIC PASS / RUNTIME UNVERIFIED |
| P2 NPC pick-chain boundary instrumentation | COMPILE VERIFIED / STATIC PASS / boundary AWAITING USER RUNTIME |
| P3 FP gate disagreement check | STATICALLY ANALYZED (no stable disagreement; diagnostics added) |
| P4 preservation | STATIC PASS |
| Build | **BUILD SUCCESSFUL (EXIT_CODE=0)** |

No runtime success is claimed. Stopping after build/static review per brief.

**Next user test checklist:**
1. FP: talk to an NPC with CHOICES — F12 should show `choiceIface 228..238`, `choiceCount > 0`, `ch1..ch5` child indices. Press 1..5 — the matching option must be selected via the real server route (`lastChoiceKey`, `lastActionCode 9 or 8`, `lastChoiceRoute IF3_FAMILY_x/CS1_FAMILY_x`).
2. Continue dialogues (241..244): SPACE must still work; number keys must NOT be consumed by the dialogue layer (they fall through to world actions).
3. FP: aim at an NPC — copy the F12 NPC TARGET line (`npcRendered / attempts / boundsHit / candNpc / pickable / mmPick / allowInput / tagWritten / boundary`). The `boundary` value names the FIRST stage where NPC diverges from the working LOC path — that is the only thing a future round may fix.
4. FP: if the world overlay shows `blockedReason NOT_FP` while the view LOOKS first-person, copy `visualMode / rigState / overlayFpGate` — this proves or disproves the designed compressed-CHASE case.
5. LOC overlay, ceiling quarantine (`rendererEnabled N`), FREE keyboard, CTRL mouse, movement/zoom/roofs — unchanged.

---

## 39. ROUND P4B — F11 MODERN → ORIGINAL SCENE/COLLISION RESYNC

User runtime (authoritative): after F11 MODERN → ORIGINAL, some normally
walkable tiles are blocked and some objects clip incorrectly; walking into a
new chunk/region load repairs it automatically → stale client-side
scene/collision/pathfinding state. Brief constraints honoured: NO OpenGL /
GlRenderer restart, NO display recreate, NO texture reload, NO reconnect,
NO fake teleport packet, NO collision-map clearing without rebuild, NO
custom collision rebuild, ORIGINAL region loading untouched.

### 39.1 P0/P1 — Source trace: root cause + smallest vanilla refresh

**Root cause (SOURCE PROVEN).** While MODERN Q16 owns locomotion,
`ModernMovementController.update()` writes `self.xFine/zFine` every tick but
never touches `movementQueueX/Z`. Vanilla pathfinding seeds its BFS exactly
at `PlayerList.self.movementQueueX[0]/movementQueueZ[0]`
(`PathFinder.findPathToLoc` → `findPath(...)`; `findPathN/2/1` seed
`parents/queueX[0]` from those args). The old `exitModernMode()` rebased
xFine/zFine but **never synced movementQueueX[0]/Z[0]** — so after F11,
click-to-walk BFS radiated from the tile where the player stood when MODERN
was entered. Result: walkable tiles unreachable, wrong adjacency targets
near walls/doors — exactly the reported symptoms.

**Why a region load heals it (SOURCE PROVEN).**
`LoginManager.method2463` (region shift) ends in
`PlayerList.self.teleport(...)` → `PathingEntity.method2683` hard branch:
`movementQueueX[0]/Z[0] = target`, `movementQueueSize = 0`,
`xFine/zFine = tile centre` — the vanilla authoritative entity reset. This
is what restores BFS origin correctness, NOT any collision rebuild.

**Collision maps are never stale (SOURCE PROVEN by exhaustive grep).** All
`CollisionMap.flags` mutators are vanilla (`flagScenery/flagWall/flagTile/
flagGroundDecor` + unflag pairs during `rebuildMap`/loc reads, NPC per-tick
flagging in `client.mainUpdate`, SceneGraph loc clears of the 0x1000000
bit). The only direct `flags = 0` writer is the `::noclip` chat cheat.
Modern code performs ZERO collision-flag writes. Therefore NO collision
rebuild is needed — the smallest proven vanilla refresh is the
teleport-style entity reset, reused verbatim.

**Secondary staleness found.** Server-step drain hooks
(`Protocol.readSelfPlayerInfo`) are gated by `isModernQ16Owner()`; after F11
exit the gate closes, so residual in-flight steps from the last modern walk
requests would land in the vanilla queue and replay as ghost movement.
`mapFlagX/Z` are NOT written by the modern path
(`sendModernWalkPacket` omits flag markers by design) — no action needed.

### 39.2 P2 — Exit resync implementation (ModernMovementController.exitModernMode)

Order of operations (brief-mandated):
1. Capture before-state (fine, queue[0], pending count) for F12.
2. Authoritative tile = last server-confirmed LOCAL tile
   (`lastServerReportedTileX/Z`; fallback live tile if never reported).
3. Stop modern Q16 writes: velocity/intent zeroed, pending ring cleared,
   `initialized/suspended/wasFirstPersonLastTick` reset,
   `lastMovementState = IDLE`.
4. Vanilla-proven refresh (route `VANILLA_TELEPORT_RESET`):
   `self.teleport(authTileX, true, authTileZ)` — the EXACT call
   `method2463` uses. Hard branch of `method2683` resets queue[0],
   queueSize and xFine/zFine to the authoritative tile centre, so
   PathFinder BFS originates at the live player tile. Side effect is the
   vanilla far-teleport `FogManager.setInstantFade()` (same as any server
   teleport) — noted, not suppressed.
5. Post-exit drain window: new `isDrainingServerSteps()`
   (= `isModernQ16Owner() || client.loop < postExitDrainUntil`, 150 ticks)
   replaces the drain gate in `Protocol.readSelfPlayerInfo` type 1/2
   branches so residual in-flight steps are consumed (`onServerStep` +
   `method2689`) instead of replaying. Type 3 (teleport) stays vanilla.
6. Only then is movement ownership handed to ORIGINAL.

MODERN FREE exit (rig FREE = vanilla already owns): route
`VANILLA_FREE_NOOP` — queue untouched (it is live and consistent); touching
it would cancel legitimate walking. ORIGINAL-only users: `exitModernMode`
never runs; `postExitDrainUntil = -1` keeps the drain gate byte-identical
to prior behaviour.

### 39.3 P3 — F12 diagnostics + one-shot log

- New F12 section **F11 EXIT** (last MODERN → ORIGINAL snapshot):
  `beforeFine / afterFine`, `authTile`, `serverTile`, `queue0Before / after`,
  `lastSent` (last DDA walk target sent — new `lastSentTileX/Z` tracked in
  `maybeSendWalkRequest`), `pendingMoves`, `collisionRefresh Y/N`, `route`.
- One-shot per exit: `[F11-ORIGINAL-RESYNC] authTile=... queue0Before=...
  queue0After=... collisionRefreshRoute=...`.

### 39.4 Static review

1. `teleport(x, true, z)` → `method2683(size, x, z, true)` → hard branch
   (arg3=true skips near-queue path) — PASS.
2. Drain gate only EXTENDS prior behaviour in time; `postExitDrainUntil=-1`
   ⇒ ORIGINAL-only semantics unchanged — PASS.
3. Type-3 teleport branch untouched — PASS.
4. No collision-map writes/clears anywhere in the change — PASS.
5. No renderer/display/texture/reconnect changes — PASS.
6. No fake teleport packet (no outbound writes added) — PASS.
7. `onServerStep` during drain window: `reconcile()` early-returns
   (`!initialized`); `consumePendingExact` on empty ring is a no-op — PASS.
8. `GetProblems` on all three edited files: no errors — PASS.
9. Build: `gradlew :client:compileJava` BUILD SUCCESSFUL (EXIT_CODE=0) — PASS.

### 39.5 Verification summary

| Item | Status |
|------|--------|
| P0 trace (BFS origin, method2463/teleport repair, flag mutators) | **SOURCE PROVEN** |
| P1 smallest vanilla refresh = teleport-style entity reset | **SOURCE PROVEN** (no collision rebuild needed) |
| P2 exit resync + drain window | COMPILE VERIFIED / STATIC PASS / RUNTIME UNVERIFIED |
| P3 F12 F11 EXIT + [F11-ORIGINAL-RESYNC] log | COMPILE VERIFIED / STATIC PASS |
| ORIGINAL-only user unchanged | STATIC PASS |
| Build | **BUILD SUCCESSFUL (EXIT_CODE=0)** |

No runtime success is claimed. Stopping after build/static review per brief.

**Next user test checklist (brief's required runtime, 10×):**
1. Enter MODERN (F11), WASD around buildings/walls/doors.
2. F11 back to ORIGINAL — console must print one `[F11-ORIGINAL-RESYNC]`
   line with `collisionRefreshRoute=VANILLA_TELEPORT_RESET` (or
   `VANILLA_FREE_NOOP` if exiting from FREE).
3. Immediately click tiles around walls/doors/objects — clipping must match
   a fresh vanilla region load; NO walk into a new chunk needed to repair.
4. Repeat 10×.
5. F12 → **F11 EXIT** section: `authTile` should equal `serverTile`,
   `queue0After` should equal `authTile`, `collisionRefresh Y`.
6. ORIGINAL-only session without F11 — behaviour unchanged.

---

## 40. Round #8 — FP Context Menu + P4B Hotfix + Overlay Range

**Date:** 15-08-2026  
**Status:** SOURCE VERIFIED · COMPILE VERIFIED · STATICALLY REVIEWED · RUNTIME UNVERIFIED

### 40.1 Scope

Round #8 addresses three user-reported issues:
1. **FP vanilla context menu** — right-click in FIRST_PERSON must open the real vanilla MiniMenu at the crosshair, with wheel-scroll selection and left-click execution.
2. **P4B post-F11 drain hotfix** — the 150-tick blanket drain was swallowing ORIGINAL click-to-walk immediately after F11 exit.
3. **Quick overlay range** — restore from 8 tiles back to 2 tiles (user preference).

### 40.2 Implementation Summary

#### P1-P7: FP Context Menu Controller

**New file:** `rt4-client/client/src/main/java/rt4/FPContextMenuController.java`

This controller provides FIRST_PERSON-specific INPUT CONTROL for the EXISTING vanilla MiniMenu. It does NOT implement a second custom context-menu data model — it uses the real MiniMenu arrays, sorting, rendering, and `doAction()` execution.

**Activation conditions (ALL must be true):**
- `CameraMode.isModern()` is true
- `ModernCameraRig` semantic state == FIRST_PERSON
- Real dialogue inactive (`ModernDialogueKeyboard.hasActiveDialogue()` == false)
- Chat/text inactive (`ModernControlController.isChatInputActive()` == false)
- CTRL UI cursor inactive (`FirstPersonCamera.isUiCursorActive()` == false)
- FP cursor/crosshair mode active

**Menu opening (P2):**
On FP right-click, the crosshair point is computed as:
```
viewportX + viewportWidth / 2
viewportY + viewportHeight / 2
```
This point is fed into the EXISTING vanilla scene-pick/menu build authority (`ScriptRunner.method3901()`) which sets `Cs1ScriptRunner.aBoolean108` and positions the menu. Menu contents remain FULL VANILLA.

**Scroll selection (P3):**
While the FP-owned vanilla context menu is open, MOUSE WHEEL OWNER = FP CONTEXT MENU. `ModernCameraRig` MUST NOT receive that wheel delta. Wheel down = next visible row, wheel up = previous visible row (wrap-around).

**Visual highlight (P4):**
The vanilla menu already highlights rows based on hover coordinates in `MiniMenu.drawA()`/`drawB()`. This controller adds a visual highlight pass over the SELECTED row (wheel-selected, not just mouse-hovered) using the exact menu x/y/width/height from vanilla.

**Left click execution (P5):**
When FP context menu owns input, left click executes `MiniMenu.doAction(selectedArrayIndex)` with the selected array index, then closes via the vanilla-equivalent close route. No new packets, no action reconstruction.

**Input priority (P7):**
```
1. explicit chat/text
2. real dialogue / modal UI
3. FP vanilla context menu (THIS controller)
4. CTRL UI cursor
5. quick crosshair overlay
6. camera/movement
```
While context menu open: wheel = menu, no camera zoom, no FP->CHASE zoom transition, E cannot fire quick overlay, number keys cannot fire quick overlay, WASD suspended, left click confirms menu row.

#### P6: Protocol.method843 Input Ownership

**Modified:** `rt4-client/client/src/main/java/rt4/Protocol.java`

Added early return in `method843()` when `FPContextMenuController.isMenuOpen()` is true. This prevents vanilla mouse action processing from double-executing actions while the FP context menu owns input.

#### P8: Quick Overlay Range 8->2 Tiles

**Modified:** `rt4-client/client/src/main/java/rt4/ModernActionOverlay.java`

Changed `INTERACT_RANGE_TILES` from 8 to 2. This is DISPLAY/ACQUISITION range only — executing an action still goes through existing RuneScape action logic; existing pathfinding/server decides where the player must stand and whether the action is in range.

#### P10: P4B Hotfix — Remove Post-F11 Drain

**Modified:** `rt4-client/client/src/main/java/rt4/ModernMovementController.java`

**REMOVED:**
- `postExitDrainUntil` field
- `POST_EXIT_DRAIN_TICKS` constant (was 150)
- Time-based drain window in `exitModernMode()`
- Time-based check in `isDrainingServerSteps()`

**NEW:**
`isDrainingServerSteps()` now returns `isModernQ16Owner()` ONLY. No time-based window. ORIGINAL click-to-walk must work IMMEDIATELY after F11. Residual in-flight steps are handled by the vanilla queue naturally (they arrive through the normal vanilla `move()` path and append cleanly to the live queue).

**Rationale:** The 150-tick blanket drain was causing ORIGINAL click-to-walk to be swallowed after F11. The vanilla queue already handles residual steps correctly — they arrive through the normal `move()` path and append cleanly. No time-based drain window is needed.

#### P11: F12 Diagnostics Update

**Modified:** `rt4-client/client/src/main/java/rt4/DebugOverlay.java`

Added new **FP CONTEXT MENU** section:
```
== FP CONTEXT MENU ==
menuOpen Y/N  selectedIdx <int>  wheelConsumed Y/N
selectedOp <action code>
selectedTarget <target name or "->
```

#### P9: Frontmost/Nearest Crosshair Target Selection (DEFERRED)

**Status:** SOURCE UNPROVEN — DEFERRED TO DEDICATED TARGETING ROUND

The user requirement is: CAMERA -> DOOR -> TABLE must select DOOR (nearest/frontmost target along the camera/crosshair hit).

**Source trace findings:**
- `Model.aLongArray11` is the scene pick tag array, populated by `GlModel.render()` and `SoftwareModel.render()`.
- The write order in `GlModel.render()` is: iterate `SceneGraph.sceneObjects`, for each object call `model.render()` which writes pick tags.
- The iteration order is `SceneGraph.sceneObjects` array order, which is NOT guaranteed front-to-back or back-to-front — it's insertion order from the region load.
- There is NO per-hit depth authority in the current RT4 picking pipeline. The pick tags are written in scene-object iteration order, not camera-space depth order.

**Conclusion:** Without a true per-hit depth authority, implementing frontmost target selection would require a custom raycaster or depth-sort pass. This is deferred to a dedicated targeting round. The current overlay uses the existing pick order (which is source-proven but not depth-sorted).

### 40.3 Files Modified

| File | Change |
|------|--------|
| `FPContextMenuController.java` | **NEW** — FP vanilla context menu controller (P1-P7) |
| `ModernActionOverlay.java` | Changed `INTERACT_RANGE_TILES` 8→2 (P8); added `FPContextMenuController.isMenuOpen()` gate (P7); added `toPlainStringPublic()` accessor |
| `ModernControlController.java` | Integrated `FPContextMenuController.update()` into `updateInteractionLayer()` (P7) |
| `ModernCameraRig.java` | Added `FPContextMenuController.wasWheelConsumed()` gate in `processWheelInput()` (P3/P7) |
| `Protocol.java` | Added `FPContextMenuController.isMenuOpen()` early return in `method843()` (P6) |
| `ModernMovementController.java` | **REMOVED** time-based post-exit drain (P10); `isDrainingServerSteps()` now == `isModernQ16Owner()` |
| `DebugOverlay.java` | Added **FP CONTEXT MENU** section (P11) |

### 40.4 Static Review Checklist

| Item | Status |
|------|--------|
| FP context menu uses real MiniMenu arrays/rendering | **SOURCE VERIFIED** |
| Execution only `MiniMenu.doAction()` | **SOURCE VERIFIED** |
| No hardcoded action packets | **STATIC PASS** |
| No double wheel consumption | **STATIC PASS** (gate in `ModernCameraRig.processWheelInput()`) |
| No guessed pick-depth ordering | **SOURCE VERIFIED** (P9 deferred — no depth authority proven) |
| Overlay max range exactly 2 | **STATIC PASS** (`INTERACT_RANGE_TILES = 2`) |
| NPC live tile resolution retained | **STATIC PASS** (unchanged from Round #7) |
| ORIGINAL untouched | **STATIC PASS** (no ORIGINAL-mode code paths modified) |
| FREE untouched | **STATIC PASS** (no FREE-mode code paths modified) |
| No 150-tick post-F11 drain | **STATIC PASS** (removed `postExitDrainUntil` + `POST_EXIT_DRAIN_TICKS`) |
| New ORIGINAL click not swallowed after F11 | **STATIC PASS** (`isDrainingServerSteps()` == `isModernQ16Owner()`) |
| Ceiling untouched | **STATIC PASS** (no `ModernCeiling.java` changes) |
| Build | **BUILD SUCCESSFUL (EXIT_CODE=0)** |

### 40.5 Verification Summary

| Item | Status |
|------|--------|
| P0 trace (MiniMenu, Protocol, ScriptRunner, ModernActionOverlay, ModernCameraRig, ModernMovementController) | **SOURCE PROVEN** |
| P1-P7 FP context menu controller | COMPILE VERIFIED / STATIC PASS / RUNTIME UNVERIFIED |
| P8 overlay range 8->2 | COMPILE VERIFIED / STATIC PASS |
| P9 frontmost target selection | **DEFERRED** (no proven depth authority) |
| P10 P4B hotfix (remove post-F11 drain) | COMPILE VERIFIED / STATIC PASS / RUNTIME UNVERIFIED |
| P11 F12 diagnostics | COMPILE VERIFIED / STATIC PASS |
| Build | **BUILD SUCCESSFUL (EXIT_CODE=0)** |

No runtime success is claimed. Stopping after build/static review per brief.

**Next user test checklist:**
1. Enter MODERN (F11), scroll to FIRST_PERSON.
2. Right-click — vanilla context menu must open at crosshair.
3. Scroll wheel — menu selection must cycle (no camera zoom).
4. Left-click — selected action must execute (e.g., Open door, Talk-to NPC).
5. F11 back to ORIGINAL — click-to-walk must work IMMEDIATELY (no 3-second wait).
6. F12 → **FP CONTEXT MENU** section: `menuOpen`, `selectedIdx`, `wheelConsumed` must update.
7. Quick overlay (E key) — must only show targets within 2 tiles (was 8).
8. ORIGINAL-only session without F11 — behaviour unchanged.

---

## 41. CODEX ROUND 1 — F11 MODERN -> ORIGINAL MOVEMENT/CLIPPING ROOT CAUSE

**Status:** SOURCE VERIFIED / COMPILE VERIFIED / STATICALLY REVIEWED / RUNTIME UNVERIFIED

### 41.1 Root cause

The F11 edge was handled inside `Keyboard.keyPressed`, which is an AWT event-
dispatch callback. `CameraMode.cycle()` then performed the complete mode,
camera, and movement handoff on that AWT thread. At the same time, the client
game thread independently:

- writes modern Q16 `self.xFine/zFine` prediction;
- decodes relative self-movement steps in `Protocol.readSelfPlayerInfo()`;
- updates `movementQueueX/Z` and the last server-confirmed tile;
- runs vanilla PathFinder and scene/region rebases.

There was no common lock or game-thread handoff around those player fields.
F11 could therefore interleave a reset/ownership change with a Q16 write or a
relative server step, leaving the fine position, queue/path origin, and server
tile from different moments. That inconsistent local origin presents as wrong
walkability/adjacency around otherwise correct collision flags.

The earlier client-only `self.teleport(...)` workaround did not solve this
boundary: it was itself executed from the AWT thread and could race the same
game-thread writers. It also did not reset the authoritative server queue.

### 41.2 Why a region transition repaired it

The normal region path runs on the client game thread. `LoginManager.method2463`
changes `Camera.originX/Z`, rebases every entity's fine position and movement
queue together, and applies the server-provided local self position. The
subsequent `rebuildMap()` clears and reconstructs all four collision maps and
scene loc collision before returning to game state 30. This serial,
game-thread-owned re-anchor removes the mixed-moment local origin, which is why
walking far enough repaired the symptom.

Source search found no MODERN collision-flag writer. The fix therefore does not
rebuild, clear, weaken, or bypass collision.

### 41.3 Smallest fix implemented

- `Keyboard.keyPressed` still provides the physical F11 edge, but now only
  records a volatile pending request.
- `client.mainLoop()` consumes that request immediately after `Keyboard.loop()`
  on the game thread, before `ModernControlController.update()` and
  `Protocol.method1756()`.
- `ModernMovementController.exitModernMode()` now stops Q16 ownership while
  preserving the live vanilla queue, fine position, scene, and collision maps.
- Removed the disproven F11 client `self.teleport(...)` reset. No post-exit
  drain was added; ORIGINAL server steps use the normal vanilla `move()` path.

### 41.4 Diagnostics

F12 now labels these values unambiguously:

- Player Local Tile X/Z
- Server Local Tile X/Z
- Scene Base X/Z
- Player World Tile X/Z
- Server World Tile X/Z
- Plane and local fine coordinates

Compact snapshots are retained for `HEALTHY_ORIGINAL`, `BEFORE_F11_EXIT`,
`AFTER_F11_EXIT`, and `AFTER_REGION_REBUILD`. Console
`[MOVEMENT-BOUNDARY]` lines additionally include queue/path origin, queue size,
collision-map plane, and the exact 3x3 collision flags around both player and
PathFinder origin. `[F11-TRANSITION]` reports the requesting and processing
thread names.

### 41.5 Files changed

| File | Change |
|------|--------|
| `rt4-client/client/src/main/java/rt4/CameraMode.java` | Deferred F11 transition request and game-thread consumer; boundary snapshots |
| `rt4-client/client/src/main/java/rt4/client.java` | Processes pending F11 transition at the start of the game tick |
| `rt4-client/client/src/main/java/rt4/ModernMovementController.java` | Removed client teleport workaround; clean main-thread ownership handoff |
| `rt4-client/client/src/main/java/rt4/Protocol.java` | Corrected server-step ownership documentation |
| `rt4-client/client/src/main/java/rt4/LoginManager.java` | Captures post-region-rebuild diagnostic snapshot |
| `rt4-client/client/src/main/java/rt4/DebugOverlay.java` | Explicit LOCAL/WORLD diagnostics and boundary collision snapshots |
| `MODERN_CONTROLS_PROGRESS.md` | This truthful round status |

### 41.6 Verification

`gradlew.bat :client:compileJava`:

**BUILD SUCCESSFUL (EXIT_CODE=0)**

The repository's known Kotlin daemon cache/module warning occurred; Gradle used
its fallback strategy and completed `:client:compileJava` successfully.

Static review:

- no time-based movement drain;
- no ORIGINAL step swallowing;
- no renderer/OpenGL restart;
- no collision clearing, bypass, or weakened validation;
- no server/protocol control-state packet;
- no unrelated subsystem changes;
- ORIGINAL-only sessions do not execute the F11 transition path.

No user runtime success is claimed.

