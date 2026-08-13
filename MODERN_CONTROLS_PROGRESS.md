# Modern Controls — Phase 0 Analysis & Implementation Plan

**Status:** Phase 0 (Analysis) — COMPLETE · Phase 1 (Camera Mode Framework) — COMPLETE
**Date:** 13-08-2026

This document captures the Phase 0 inspection of both the current RT4-client
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
  (`setCursor(Point, int, Component, int, int[])`, `setPosition(int x,int y)`),
  versus the sandbox's `GameShell.signLink.setCursor(...)`/`setCursorPosition(...)`.
  The correct current API is `CursorManager.setCursor`/`setPosition` (see §10).
- **FOV** is fixed in `GlRenderer.method4171 → method4175` (perspective). The
  sandbox added a `FirstPersonCamera.getProjectionScale()` multiplier inside
  `method4171`; we port that pattern.

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

### 2.2 RS-Sandbox reference (source for FPS)
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
| `ProceduralSceneApplier.java` | Sandbox-only procedural chunk system (region rebuild path; **not present in RT4 base**). |
| `ModernHud.java`, `SandboxLoginOverlay.java`, `GlobalJsonConfig.java` | Sandbox-only UI/config extras (FOV/sensitivity/auto-enable). Not part of RT4 base; optional later. |

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

## 4. Exact camera pipeline (RT4 current)

### 4.1 Fields (Camera.java)
- `renderX`, `renderZ`: camera position in fine/world coords.
- `anInt40`: camera height (terrain-relative; note: in game this is `terrainHeight - eyeOffset`; `updateLockedCamera` does `getTileHeight - anInt5203`).
- `cameraYaw`, `cameraPitch`: 0..2047.
- `cameraType`: 0 = free/login, 1 = follow (`method4273()` + `clampCameraAngle`), 2 = locked (`updateLockedCamera`).
- `yawTarget`, `pitchTarget`: used by `method4273()` arrow-key control (only when `Preferences.aBoolean63`).

### 4.2 Update sites (dual!)
1. `client.java:1203-1207` (`mainUpdate()`): `if (Camera.cameraType == 2) updateLockedCamera() else updateLoginScreenCamera()`. Runs only when `LoginManager.step==0 && CreateManager.step==0` (login/loading).
2. `Protocol.java:2883-2889` (in-game tick): `if(cameraType==1) method4273() else if(cameraType==2) updateLockedCamera() else updateLoginScreenCamera()`.

The sandbox's FPS mode sets `Camera.cameraType = 0` so **both** sites skip their own update, then writes camera values directly. It also guards `Protocol.java:2894` with `if (!FirstPersonCamera.active)`. We must replicate this dual-skip.

### 4.3 Port target (from FirstPersonCamera)
- `fpCamX/Z` → should derive from `ModernMovementController`'s prediction, not separate free-fly vars (per goal doc).
- `fpCamYaw` (0..2047), `fpCamPitch` (signed; wrapped to 0..2047 for `cameraPitch`).
- Eye height: `EYE_HEIGHT = 200`. `Camera.anInt40 = getTileHeight(plane,fpCamX,fpCamZ) - EYE_HEIGHT - bobOffset`.
- `Camera.cameraX/Z` set too (so `method4273` won't snap).
- Head bob (`bobPhase`, `MathUtils.sin`) — optional polish (Phase 15), but keep the hook.

---

## 5. Movement & collision (RT4 current)

### 5.1 Collision map
- `PathFinder.collisionMaps[plane].flags[104][104]`, integer bitmask.
- PathFinder cardinal-check masks (current `findPathN`):
  - West `0x12C0108`, East `0x12C0180`
  - North `0x12C0102`, South `0x12C0120`
  - Diag NE `0x12C01E0`, SE `0x12C0183`, NW `0x12C0138`, SW `0x12C010E`
- Multi-tile (`findPath1`, size>1): extra masks `0x12C013E/0x12C018F/0x12C01E3/0x12C01F8` and rect checks `isInsideOrOutsideRect`.
- Walls/doors use `CollisionMap.isAtWall/isAtWallDecor` with shape/angle for pathing to locs — modern WASD should primarily use the cardinal mask approach in `canMoveTile` for simple adjacent steps, but **must reuse** the same masks as PathFinder (verified they match).

### 5.2 Sandbox `canMoveTile` (reference)
```
E (0x12C0180), W (0x12C0108), S (0x12C0120), N (0x12C0102), diag (0x12C01E0)
cardinal: (flags[dst][dst] & mask) == 0
diagonal: card1 && card2 && (flags[dst][dst] & 0x12C01E0) == 0
```
These match current RT4 `findPathN` exactly → **safe to port** (still verify against `findPath1` for size>1).

### 5.3 Wall sliding & footprint
- Goal requires `tryMoveX(dx)` + `tryMoveZ(dz)` sliding and a small footprint (player size from `getSize()`).
- New controller should evaluate candidate fine-destination tile(s) and try axis-separated movement: attempt X first, then Z (or both at once with hit-stop). Reuse masks + `getSize()`.

---

## 6. Scene picking & menu construction (RT4 current)

### 6.1 Menu build
- `MiniMenu.size` reset (client.java:651/721, LoginManager).
- `MiniMenu.add(cursor, key, opName, arg3, action, op, arg6)` pushes a row: `ops[]/opBases[]/cursors[]/actions[]/keys[]/intArgs1[]/intArgs2[]`.
- `Protocol.java:3489+` / `ScriptRunner` handle menu sizing/redraw; `Protocol.method843` handles click→`MiniMenu.doAction`.
- `MiniMenu.doAction(index)` (line 446) dispatches by `actions[]` to the real RS action (walk, NPC attack, object use, examine, spell, etc.).

### 6.2 Key action constants (MiniMenu.java:148-204)
- NPC: `NPC_ACTION_1..5` (17/16/4/19/2), `NPC_EXAMINE=1007`.
- Player: `PLAYER_ACTION_1=30`, `PLAYER_ACTION_BLOCK=34`, `PLAYER_ACTION_TRADE=29`, `PLAYER_FOLLOW_ACTION=31`, `PLAYER_ACTION_5=57`.
- Object: `OBJ_ACTION_1=47`, `OBJ_EQUIP_ACTION=5`, `OBJ_ACTION_4=35`, `OBJ_OPERATE_ACTION=23`, `OBJ_ACTION_5=58`, `OBJ_EXAMINE=1002`, plus use-target combos.
- Object stacks: `OBJSTACK_ACTION_1=18`, `2=20`.
- Locs: `LOC_ACTION_1..5` (42/50/49/46/1001), `LOC_ACTION_EXAMINE=1004`.
- World: `WALK_HERE=60`.

This is the reusable action system — **modern targeting interacts by constructing `MiniMenu` rows (or directly invoking the equivalent packet send) for the current target**, rather than inventing new gameplay.

### 6.3 Crosshair → target selection
- Current picking is **mouse-cursor based** only (menu built around `Mouse.clickX/Y` + world-space via `API.*` projection when camera is classic).
- FPS/TPP unique camera means we need a **center-screen ray/or cone**:
  `cameraForward → convert to tiles → candidate entities (PlayerList.self excluded for TPP body, NPCs, Locs, ObjStacks)`.
- Reuse existing `SceneGraph` render collections (`NpcList.npcs`, `SceneGraph.tiles[][][].scenery/objStacks`, `LocEntity`) for candidate enumeration.
- Score by: center distance, angular deviation, plane match, hysteresis (keep current target while still reasonable), world distance as tiebreak.
- **Two acquisition distances** (configurable centrally):
  - `MODERN_NEARBY_INTERACT_DISTANCE` (~2 tiles) for objects/doors/ground/NPC talk/trade.
  - `MODERN_COMBAT_TARGET_DISTANCE` (wider, e.g. 8–10+) for ranged/magic **acquisition only**.
- **Action validity stays with RS code**: the controller only selects a target and presents existing actions (`Attack`, `Cast X`, `Talk-to`, `Open`, ...). The existing `MiniMenu.doAction` path (or direct equivalent packet) triggers real RS logic; we do NOT fake ranges/LOS.

---

## 7. Combat / ranged / magic integration (RT4 current)

- Combat targeting flows through:
  - NPC entity: `NpcList.npcs[slot]`; `NpcType` has combat-level/options; `Npc.options[]`.
  - Player options: `Player.options[8]` (attack/follow/trade/...).
  - Spell/Use state: `MiniMenu.aBoolean302` (use-target mode), `MiniMenu.aClass100_545` (selected item/spell text), and `Cs1ScriptRunner.aClass13_14`/`aClass13_10`. The spell/item-on-target actions are `OBJ_NPC_ACTION(26)`, `OBJ_LOC_ACTION(14)`, `OBJ_OBJ_ACTION(40)`, etc.
- To support magic targeting: read the **selected spell state** (from the magic interface / `MiniMenu.aClass100_466` "Use" state or `Cs1ScriptRunner`), and when a combat spell is armed, the context menu for an NPC must offer `Cast <spell>` → which maps to the same action route as classic spell-on-NPC.
- Autocast: do NOT touch; remain on existing combat state.
- Projectiles/LOS/range: server/client existing code (`SceneGraph.projectiles`, `Projectile`, npc pathing) — untouched.

---

## 8. Rendering / equipment / first-person body

### 8.1 Local-player body culling
- `ScriptRunner.method964(arg0)` renders:
  - `arg0=true` → the local player model pass.
  - `arg0=false` → all other players.
- Sandbox hooks `method964(true)` to skip the local model when `FirstPersonCamera.active` (§2.2). **Port this exact hook** so the FPS camera doesn't see the player's head/torso/cape. Other players still render normally.
- Third Person: do NOT skip; full player renders normally.

### 8.2 Equipment / viewmodel
- Equipment is in `Player.appearance` (`PlayerAppearance`, `Equipment.objIds`), and rendered via `Player.render()` → `appearance.method1954(...)` + `PlayerAppearance.getModelCacheSize()`.
- First-person equipment (Phase 13): either (A) render relevant equipment parts of the existing player model from the FPS eye, or (B) a separate viewmodel render pass using the same `appearance`/equipment IDs. **(A) is preferred** — less fragile than a second full render pipeline, reuses `PlayerAppearance` and existing animations.
- `Player.render()` (line 408) currently sets `model.pickable=true` and renders locally. In FPS we skip `method964(true)`; to keep the viewmodel we'd add a dedicated pass.

### 8.3 FOV
- Projection built in `GlRenderer.method4171` (line 562) → `method4175` (line 630). Sandbox multiplies `fovScale` in `method4175` call when active+in-game+large viewport. **Port** that; add `FirstPersonCamera.getProjectionScale()` (or move to a shared `ModernCameraConfig`).

---

## 9. Input / cursor / UI

### 9.1 Key mapping (current RT4 CODE_MAP values)
- F11=11, F12=12, Esc=13, W=33, A=48, S=49, D=50, E=34, Space=83, Shift=81, Ctrl=82,
  arrows Up=98 Down=99 Left=96 Right=97.

### 9.2 Input routing & guard rails
- `Keyboard.keyPressed` (line 300) — add F11/Esc/WASD routing here (port sandbox `onKeyPressed`/`isMovementKey`/`consumesTypedCharacter`).
- Prevent WASD/E from becoming chat/input text: filter in `keyPressed`/`keyTyped` (already done in sandbox).
- UI priority (goal doc): modal/text > RS UI > modern interaction > camera > movement. Central `isGameplayInputAllowed()`.

### 9.3 Cursor lock (current RT4)
- Available via `SignLink`/`CursorManager`:
  - `setCursor(Point hotSpot, int width, Component c, int height, int[] pixels)` (null → hide? no — null restores default; the sandbox used 1×1 empty pixel).
  - `setPosition(int x, int y)` (Robot-based mouse move).
- Sandbox used `GameShell.signLink.setCursor(new int[]{0},1,canvas,new Point(0,0),1)` + `setCursorPosition(...)`. Note the signature mismatch: sandbox had `setCursorPosition(x,y)`; current has `CursorManager.setPosition(x,y)`. **Adapt**.
- Esc = release/cancel; click viewport = recapture. Keep UI (inventory/bank/spellbook/dialogs) usable.

---

## 10. Networking / server sync

- Movement stays **server-authoritative**. Send valid tile transitions via existing packets.
- `ClientProt.method3502` sends walk routes (`MOVE_GAMECLICK`). For continuous WASD we want short single-step routes (like sandbox `sendPredictedTile`) but must:
  - Only send when a **valid** adjacent collision-passing tile per `canMoveTile`/PathFinder.
  - Keep within `Camera.originX/Z` local tile space (clamp 0..102).
  - Respect run modifier (`p1add(ctrl/pressed ? 1 : 0)`).
  - Throttle (`SEND_THROTTLE_MS`) + reconciliation: snap to server on large mismatch (sandbox uses 256 fine units), small drift eased.
- No permanent desync; no movement through walls; no speed increases.
- Do NOT change the server protocol or introduce client-sided damage.

---

## 11. Identified gotchas / differences vs sandbox

1. **Cursor API** differs: sandbox `GameShell.signLink.setCursorPosition(x,y)` vs current `CursorManager.setPosition(x,y)` (and `setCursor` takes `(hotSpot,w,comp,h,pixels)` — the empty-pixel trick for hiding).
2. **Two camera update sites** must both be gated (client.java:1203 for login, Protocol.java:2883 for in-game). Sandbox gated both (client + Protocol).
3. **Mouse`click` camera**: RT4 `Camera.method4273` only reacts to arrows when `Preferences.aBoolean63`; irrelevant for FPS but must not interfere.
4. **`method964` body-skip** is already present in current RT4 (line 668) — same method name; easy hook.
5. **Scene rebuild**: RT4 uses standard RS region rebuild (`LoginManager.setupLoadingScreenRegion()`, `SceneGraph.clear()`), no procedural applier. FPS `onSceneRebuild` should reset camera interp/state on region rebuild (port sandbox logic into a hook on `LoginManager`'s rebuild).
6. **FOV scaling**: only in-game, large viewport (port the sandbox `arg2>=256 && arg3>=256` guard).
7. **`Preferences`/`GlobalJsonConfig`**: config persistence lives in `Preferences.java` + `GlobalJsonConfig.java`; add FOV/sensitivity/auto-enable there (or a new `ModernConfig`), not sandbox `SandboxLoginOverlay`.
8. **No GPU sandbox extras**: don't port `GpuPipeline/Jogl/ProceduralSceneApplier/ModernHud`.

---

## 12. Proposed new classes (all under `rt4`)

All new classes are **additions**; no mass refactor.

### `rt4/CameraMode.java` (new enum)
- `ORIGINAL`, `FIRST_PERSON`, `THIRD_PERSON`.
- Static `current`, `cycle()` (Original→First→Third→Original with edge-triggered F11).

### `rt4/ModernControlController.java` (new)
- Central dispatcher. Per frame:
  - `if mode == FIRST_PERSON`: `ModernMovementController.update()`, `FirstPersonCamera.update()`, `ModernTargetingController.update()`, `ModernInteractionController.update()`.
  - `if mode == THIRD_PERSON`: same + `ThirdPersonCamera.update()` (later phase).
  - `if mode == ORIGINAL`: run untouched original code (no-op here; original paths keep running natively).
- Owns `MODERN_NEARBY_INTERACT_DISTANCE`, `MODERN_COMBAT_TARGET_DISTANCE`.
- Owns `isGameplayInputAllowed()`.

### `rt4/ModernMovementController.java` (new)
- Owns the **predicted smooth player position** (dpX/dpZ fine) — NOT the free camera.
- Reads WASD held state from `Keyboard.pressedKeys[]`; builds camera-relative direction (forward = -sin(yaw), cos(yaw); right = cos, sin) normalizing combined input.
- Applies `xFine/zFine`-style displacement at RS walk/run speed (tile/sec based on `Player.runEnergy`, run toggle, `BasType`/SeqType speeds).
- Collision via `tryMoveX/tryMoveZ` using `PathFinder.collisionMaps[plane].flags` + `getSize()` footprint + the verified `canMoveTile` masks (port from sandbox, verify against `findPath1` for size>1).
- Detects tile transitions (`oldTile != newTile`), validates collision, and (via controller) enqueues a short server step through the existing movement queue + sends `MOVE_GAMECLICK` (single step, throttled).
- Reconciliation: snap to `PlayerList.self.xFine/zFine` on large mismatch (>256 fine), ease small drift.
- Resets on teleport/death/respawn/region rebuild; never overrides forced movement / locks.
- Drives walk/run animation via setting `movementQueue*[0]` + `movementQueueSize` so existing `NpcList.method2247` plays idle/walk/run — or sets `movementSeqId` directly; pick the least-racy path.

### `rt4/ModernTargetingController.java` (new)
- Center-screen ray/cone → candidate entities (NPCs, Locs, ObjStacks; optionally players).
- Score/hysteresis (keep current until clearly worse or removed).
- **Separates** `NEARBY_INTERACTION` vs `LONG_RANGE_COMBAT` acquisition at controller level; does NOT decide validity/range/LOS.
- Exposes current `Target` (type, id, distance, best action list).
- Integrates with `MiniMenu` action constants for the crosshair context menu (via `ModernInteractionController`).

### `rt4/ModernInteractionController.java` (new)
- Builds the small crosshair context menu for the current target using existing RS action rows (Attack/Cast/Talk-to/Trade/Examine/Open/Take...) and `MiniMenu.doAction`-style dispatch (or direct opcode sends).
- Scroll-wheel selects row; `E` or left-click executes selected row (edge-triggered; no double-E).
- Handles `Use item -> X` and `Cast spell -> X` states by reading selected item/spell state and offering `OBJ_NPC_ACTION/OBJ_LOC_ACTION/...`.
- Respects `isGameplayInputAllowed()` and UI priority.

### `rt4/FirstPersonCamera.java` (new — port, then slim)
- Camera-only: owns `cameraYaw/pitch`, eye height, writes `Camera.*`, `getProjectionScale()`.
- **Follows** `ModernMovementController` position; does NOT free-fly / send movement.
- Mouse-look + cursor lock via `CursorManager`/`GameShell` (adapted).
- `onSceneRebuild()` guard.
- Toggle/cycle wiring: uses `CameraMode` + F11 edge detection.

### `rt4/ThirdPersonCamera.java` (new — later Phase 14)
- Stub in Phase 0/1; implements follow-behind, terrain handling, camera collision in later phase.

### `rt4/ModernConfig.java` (recommended, new)
- Central FOV/mouse-sensitivity/auto-enable + `MODERN_*` distances. Persist via `GlobalJsonConfig` (existing).

---

## 13. Concrete file modifications (file-level plan, Phase 1+)

| File | Change | Phase |
|---|---|---|
| `rt4/CameraMode.java` (new) | enum + cycling, F11 edge-trigger | 1 |
| `rt4/ModernControlController.java` (new) | dispatcher, distances, `isGameplayInputAllowed` | 1/3/6 |
| `rt4/ModernMovementController.java` (new) | smooth WASD, collision, server sync | 3/4/12 |
| `rt4/ModernTargetingController.java` (new) | crosshair multi-distance targeting, hysteresis | 6 |
| `rt4/ModernInteractionController.java` (new) | context menu + E/click/scroll + spell/item-on-target | 7 |
| `rt4/FirstPersonCamera.java` (new, ported+slimmed) | camera only, mouse-look, cursor lock, FOV | 2 |
| `rt4/ThirdPersonCamera.java` (new) | third-person camera + collision | 14 |
| `rt4/ModernConfig.java` (new) | config | 1 |
| `rt4/Camera.java` | (optional) add helper accessors only; do NOT change legacy behavior | — |
| `rt4/Keyboard.java` | add F11/WASD/E routing + text-consumption guards in `keyPressed`/`keyTyped` | 1/3/7 |
| `rt4/client.java` | gate camera update site 1 (`mainUpdate`, line 1203); call `ModernControlController.update()`; keep original untouched in ORIGINAL | 1/2 |
| `rt4/Protocol.java` | gate camera update site 2 (line 2883); (later) ensure modern movement sends are flushed; guard `method843` click handling for modern mode | 1/2/12 |
| `rt4/ScriptRunner.java` | `method964(true)` skip local body when FIRST_PERSON (body culling) | 2/13 |
| `rt4/GlRenderer.java` | FOV scale in `method4171` when modern+in-game+large viewport | 2 |
| `rt4/LoginManager.java` | hook `onSceneRebuild()` on region rebuild for camera reset | 2/4/13 |
| `rt4/GameShell.java` | guard arrow-key scroll when modern active; cursor lock helpers | 2/9 |
| `rt4/MiniMenu.java` | (reuse) expose action builders for modern context menu; no behavior change | 7 |
| `rt4/PathFinder.java` / `CollisionMap.java` | **read-only** references; no changes expected (verify masks only) | 4 |
| `rt4/Player.java` / `PathingEntity.java` / `NpcList.java` | **read-only**; movement interpolation stays in `NpcList.method2247`; modern controller feeds queue | 3/5 |
| `rt4/GlobalJsonConfig.java` / `Preferences.java` | persist FOV/sens/auto-enable/distances | 1 |

---

## 14. Phase 0 acceptance notes

- No feature code was written; this document is the analysis + plan only.
- Verified the current RT4 client is the clean base (no first-person/modern files).
- Verified the sandbox `FirstPersonCamera` and its integration sites are portable.
- Verified collision masks match; verified dual camera-update sites; verified cursor API differs and needs adaptation.
- Confirmed `MiniMenu` is the reusable action/interaction system for targeting.

Next step: user switches to ACT MODE to begin **Phase 1 (camera mode framework)**.

---

## 15. Phase 1 — Camera Mode Framework (COMPLETE)

Implemented and built successfully. **No first-person camera, WASD movement,
targeting, networking, or third-person camera was added** — this phase only
establishes the mode state machine and F11 cycling, preserving original
behaviour in `ORIGINAL` mode.

### 15.1 Changes

| File | Change |
|---|---|
| `rt4/CameraMode.java` (new) | `Mode` enum { ORIGINAL, FIRST_PERSON, THIRD_PERSON }, `getCurrent()`, `isModern()`, `isFirstPerson()`, `isThirdPerson()`, `cycle()` (Original→First→Third→Original), `onKeyPressed(int)`. |
| `rt4/ModernControlController.java` (new) | Central dispatcher `update()` + `isGameplayInputAllowed()` + `MODERN_NEARBY_INTERACT_DISTANCE` (2) + `MODERN_COMBAT_TARGET_DISTANCE` (10). Phase 1 no-ops in all modes; comment placeholders marked for later phases. |
| `rt4/Keyboard.java` | Added `CameraMode.onKeyPressed(code)` call in `keyPressed()` (AWT boundary) — F11 edge-triggered once per physical press. |
| `rt4/client.java` | Added `ModernControlController.update()` call in the in-game (`gameState == 30`) branch of `mainLoop()`. |

### 15.2 How it works

- F11 is handled at the AWT boundary (`Keyboard.keyPressed`), which fires once
  per physical key press — giving natural edge-triggering. This mirrors the
  sandbox `FirstPersonCamera.onKeyPressed` pattern (F11=11).
- `CameraMode.cycle()` changes `ORIGINAL → FIRST_PERSON → THIRD_PERSON →
  ORIGINAL`. Mode switching does **not** touch player/camera position.
- `ModernControlController.update()` is invoked every in-game tick. In Phase 1
  it does nothing for all modes, so **original RuneScape controls run untouched**
  in `ORIGINAL` mode, and even in the modern modes nothing is overridden yet.
- `ORIGINAL` remains the default; the client boots in original mode.

### 15.3 Build result

- `gradlew.bat :client:compileJava` → **BUILD SUCCESSFUL** (only pre-existing
  `java.applet` deprecation warnings; the Kotlin compile-daemon `IllegalAccessError`
  is a pre-existing JDK/Kotlin-plugin environment issue that fell back to
  in-process compilation and did not block the Java compile).
- No compile errors from the new classes or edits.

### 15.4 Next steps (not done yet)

- **Phase 3+** — WASD movement (`ModernMovementController`), collision, targeting,
  interactions, combat, third-person.

---

## 16. Phase 2 — First Person Camera (COMPLETE)

Implemented and built successfully. **No WASD movement, networking, collision,
targeting, or combat was added** — this phase only implements the first-person
camera that follows the player's position with mouse-look control.

### 16.1 Changes

| File | Change |
|---|---|
| `rt4/FirstPersonCamera.java` (new) | Camera-only controller: follows `PlayerList.self.xFine/zFine`, mouse-look with cursor lock, FOV scaling, head bob, pitch limits (-384 to 512), yaw wrapping (0-2047). |
| `rt4/CameraMode.java` | Added `onModeChanged()` hook to activate/deactivate `FirstPersonCamera` on mode transitions. |
| `rt4/ModernControlController.java` | Added `FirstPersonCamera.update()` call in `FIRST_PERSON` mode. |
| `rt4/client.java` | Gated camera update site 1 (`mainUpdate`, line 1203): skip `updateLockedCamera`/`updateLoginScreenCamera` when `FirstPersonCamera.isActive()`. |
| `rt4/Protocol.java` | Gated camera update site 2 (line 2883): skip `method4273`/`updateLockedCamera`/`updateLoginScreenCamera` when `FirstPersonCamera.isActive()`. |
| `rt4/ScriptRunner.java` | Body culling in `method964(true)`: skip local player rendering when `FirstPersonCamera.isActive()` to prevent head/torso clipping. |
| `rt4/GlRenderer.java` | FOV scaling in `method4171`: apply `FirstPersonCamera.getProjectionScale()` to projection matrix when active. |
| `rt4/LoginManager.java` | Scene rebuild hook: call `FirstPersonCamera.onSceneRebuild()` at end of `setupLoadingScreenRegion()` to restore camera state after region changes. |

### 16.2 How FIRST_PERSON camera works

- **Position**: Camera follows `PlayerList.self.xFine/zFine` directly (no independent movement).
- **Eye height**: `SceneGraph.getTileHeight(plane, x, z) - 200 - bobOffset` (200 units above terrain).
- **Mouse-look**: Cursor-locked mode using `SignLink.setCursor()` with 1x1 transparent pixel. Mouse delta from screen center updates yaw/pitch. Cursor recentered via `java.awt.Robot.mouseMove()`.
- **Yaw**: 0-2047 range (wraps at 2048), mouse right decreases yaw (turn right).
- **Pitch**: Signed -384 (looking up) to 512 (looking down), wrapped to 0-2047 for renderer via `& 0x7FF`.
- **Head bob**: Subtle vertical offset based on `MathUtils.sin[bobPhase]` when `movementQueueSize > 0`, decays when idle.
- **FOV**: Configurable 60-110 degrees (default 75), applied as projection scale multiplier in `GlRenderer.method4171`.

### 16.3 Legacy camera updates gated

Both camera update sites are gated to prevent legacy camera from overwriting first-person values:

1. **`client.java:1203`** (`mainUpdate`): `if (!FirstPersonCamera.isActive())` before `updateLockedCamera()`/`updateLoginScreenCamera()`.
2. **`Protocol.java:2883`** (in-game tick): `if (!FirstPersonCamera.isActive())` before `method4273()`/`updateLockedCamera()`/`updateLoginScreenCamera()`.

### 16.4 Cursor lock implementation

- **Lock**: `GameShell.signLink.setCursor(new int[]{0}, 1, canvas, new Point(0,0), 1)` sets 1x1 transparent cursor.
- **Unlock**: `GameShell.signLink.setCursor(null, -1, canvas, new Point(), -1)` restores default cursor.
- **Recenter**: `java.awt.Robot.mouseMove(canvasOnScreen.x + canvasWidth/2, canvasOnScreen.y + canvasHeight/2)` after each mouse-look sample.
- **Discard first sample**: `discardLockedMouseSample` flag prevents initial mouse delta spike after lock.

### 16.5 FOV scaling

- `FirstPersonCamera.getProjectionScale()` returns `tan(configuredFOV/2) / tan(75/2)`.
- Applied in `GlRenderer.method4171` to projection matrix bounds: `local7 * aFloat34 * fovScale`, etc.
- Only active when `FirstPersonCamera.isActive()` returns true.

### 16.6 Local player body culling

- `ScriptRunner.method964(true)` renders local player model.
- Added early return: `if (arg0 && FirstPersonCamera.isActive()) return;`
- Prevents head/torso/cape from clipping into camera view.
- Other players still render normally (`method964(false)` unaffected).

### 16.7 Scene rebuild handling

- `LoginManager.setupLoadingScreenRegion()` calls `FirstPersonCamera.onSceneRebuild()` at end.
- `onSceneRebuild()` restores `Camera.cameraType = 0` and re-locks cursor if needed.
- Prevents legacy camera from taking over after region/teleport transitions.

### 16.8 Known limitations

- **No WASD movement**: Camera follows player position only. Movement added in Phase 3.
- **No collision**: Camera can clip through walls/objects if player is near them. Collision added in Phase 4.
- **No targeting/interaction**: Crosshair targeting added in Phase 6.
- **No third-person camera**: Placeholder mode only, added in Phase 14.
- **Cursor lock uses Robot**: `CursorManager.setPosition` is private, so `java.awt.Robot` is used directly. May have slight latency vs native cursor manager.
- **No equipment viewmodel**: First-person weapon/hand rendering added in Phase 13.

### 16.9 Build result

- `gradlew.bat :client:compileJava` → **BUILD SUCCESSFUL** (only pre-existing
  `java.applet` deprecation warnings; the Kotlin compile-daemon `IllegalAccessError`
  is a pre-existing JDK/Kotlin-plugin environment issue that fell back to
  in-process compilation and did not block the Java compile).
- No compile errors from the new class or edits.

### 16.10 Explicit confirmation

**Phase 2 contains NO:**
- WASD movement code
- `MOVE_GAMECLICK` or other movement packets
- `sendPlayerStep` or `sendPredictedTile`
- Collision checks or `PathFinder` usage
- Movement queue manipulation
- Targeting or interaction code
- Combat modifications
- Third-person camera implementation

The camera is purely a view controller that follows the player's existing position.

### 16.11 Next steps (not done yet)

- **Phase 3** — WASD movement (`ModernMovementController`): smooth local fine-coordinate movement, collision via `tryMoveX/tryMoveZ`, server sync via tile transitions.
- **Phase 4+** — Collision, animation/orientation, targeting, interactions, combat, third-person.
