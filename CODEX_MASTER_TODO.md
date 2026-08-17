HARD ARCHITECTURE RULE:

Client and server must remain independently runnable.

The RT4 client must NEVER depend on server Java classes, server files,
server memory/state, or direct server method calls.

All client/server communication must occur through the network protocol.

Client-side prediction/collision uses CLIENT-owned scene/collision data.
Server-side validation uses SERVER-owned authoritative world/collision data.

They may implement equivalent rules, but must not share runtime implementation.# ============================================================
# CODEX MASTER TODO — 2009SCAPE MODERN CONTROLS
# Runtime recovery + FP/TP interaction + combat + UI + hotbars
# ============================================================

PROJECT ROOT:
E:\Dev\RSPS Project\2009scape

CLIENT:
E:\Dev\RSPS Project\2009scape\rt4-client

CURRENT KNOWN SAFE GIT CHECKPOINT:
main
around commit 4e78f45

IMPORTANT:
USER RUNTIME RESULTS ARE AUTHORITATIVE.

THIS IS A MASTER ROADMAP, NOT A SINGLE IMPLEMENTATION TASK.

Read it for project context and constraints.

Do NOT attempt to implement all TODOs in one run.

Only implement the specific subsystem/task explicitly requested in the
current Codex session.

Everything else is context and must remain untouched unless required by the
current bounded task.

This TODO describes:

- what the project is supposed to become;
- what currently works;
- what currently fails;
- what previous models attempted but did not finish;
- what still needs to be designed;
- what still needs to be implemented.

This TODO intentionally focuses on WHAT is required.

Codex must inspect the CURRENT repository/source before deciding HOW to
implement any item.

Do NOT blindly follow old AI implementations, comments, progress documents,
or assumptions if current source/runtime proves otherwise.

A successful compile is NOT runtime verification.

Do not broadly rewrite working subsystems merely to make them cleaner.

Preserve unrelated uncommitted work and Git history.

============================================================
0 — PRODUCT VISION
============================================================

The goal is NOT:

"put an FPS camera into RuneScape."

The goal is:

Create an OPTIONAL modern first-person / third-person control and UI layer
for 2009Scape while preserving the real RuneScape game underneath.

RuneScape remains authoritative for:

- inventory;
- equipment;
- NPCs;
- objects;
- skills;
- items;
- prayers;
- magic;
- animations;
- movement restrictions;
- pathfinding;
- combat rules;
- attack speed;
- damage;
- accuracy;
- XP;
- runes;
- ammunition;
- dialogue;
- interaction actions;
- server state.

Modern mode changes how the player CONTROLS and EXPERIENCES RuneScape.

It should NOT become a separate FPS game with fake combat, fake inventory,
fake item copies, or custom replacement packets.

============================================================
1 — CONTROL PROFILE ARCHITECTURE
============================================================

There are two top-level profiles:

ORIGINAL
MODERN

F11 toggles ONLY:

ORIGINAL <-> MODERN

Within MODERN there are camera/control rigs:

FIRST_PERSON
CHASE
FREE

Scrolling must NEVER switch MODERN back to ORIGINAL.

ORIGINAL must always remain authentic vanilla 2009Scape.

============================================================
2 — ORIGINAL MODE GUARANTEE
============================================================

ORIGINAL must preserve:

[ ] vanilla click-to-walk.

[ ] vanilla PathFinder.

[ ] vanilla movement queue.

[ ] vanilla object interaction.

[ ] vanilla NPC interaction.

[ ] vanilla player interaction.

[ ] vanilla minimap movement.

[ ] vanilla camera.

[ ] vanilla mouse behaviour.

[ ] vanilla keyboard/chat behaviour.

[ ] vanilla interfaces.

[ ] vanilla roof behaviour.

[ ] vanilla combat.

[ ] vanilla spell/item selection.

[ ] vanilla context menus.

A player who never presses F11 should effectively be playing normal
2009Scape.

============================================================
3 — MODERN CAMERA STATES
============================================================

FIRST_PERSON:

[ ] modern WASD movement.

[ ] mouse-look.

[ ] cursor locked during normal gameplay.

[ ] true head/eye camera.

[ ] CTRL temporary free mouse.

[ ] crosshair interactions.

[ ] modern UI.

CHASE:

[ ] modern WASD movement.

[ ] third-person chase camera.

[ ] camera follows player smoothly.

[ ] modern UI.

FREE:

[ ] remains inside MODERN profile.

[ ] uses classic RuneScape camera freedom.

[ ] vanilla click-to-walk movement.

[ ] modern WASD disabled.

[ ] vanilla keyboard/chat ownership.

[ ] extended modern zoom remains available.

============================================================
4 — CURRENT USER-RUNTIME PASSES — PRESERVE
============================================================

Preserve these unless a new user runtime test proves regression:

[ ] F11 ORIGINAL <-> MODERN concept.

[ ] FIRST_PERSON camera exists and is usable.

[ ] CHASE camera exists and is usable.

[ ] FREE mode exists.

[ ] FREE disables modern WASD.

[ ] FREE uses vanilla click-to-walk.

[ ] smooth MODERN zoom.

[ ] smooth FP <-> CHASE transition.

[ ] CTRL-held free mouse works in FIRST_PERSON.

[ ] F12 overlay works without renderer/plugin crash.

[ ] roof/object flashing bug is fixed.

[ ] LOC/object quick interaction overlay works.

[ ] SPACE continues dialogue.

[ ] current Q16 movement basis/orientation generally works.

============================================================
5 — CURRENT FAILED / INCOMPLETE FEATURES
============================================================

These must NOT be documented as completed runtime features:

[ ] FP context-menu wheel selection does NOT work.

[ ] FP context-menu selected row is NOT visibly highlighted.

[ ] dialogue number keys 1/2/3/4/5 do NOT work.

[ ] NPC quick overlay does NOT work reliably.

[ ] true frontmost crosshair target selection is NOT implemented.

[ ] F11 MODERN -> ORIGINAL clipping/pathfinding issue still exists.

[ ] FIRST_PERSON ceilings currently do NOT render.

[ ] complete FP combat interaction is NOT finished.

[ ] quickbar does NOT yet exist as final feature.

[ ] action bar does NOT yet exist as final feature.

[ ] modern FP/TP HUD is NOT yet finalised.

============================================================
6 — QUICK WORLD INTERACTION OVERLAY
============================================================

Purpose:

Provide fast nearby RuneScape actions while looking at a target.

Desired behaviour:

look at nearby object/NPC

-> target detected
-> small action overlay appears
-> E = primary action
-> additional actions shown clearly.

Examples:

Door
E Open
2 Examine

Goblin
E Attack
2 Examine

Requirements:

[ ] max acquisition/display distance approximately 2 tiles.

[ ] LOC/object support.

[ ] NPC support.

[ ] use real vanilla MiniMenu actions.

[ ] use real MiniMenu.doAction execution.

[ ] no invented packets.

[ ] no fake custom world actions.

[ ] E executes primary displayed action.

[ ] secondary actions remain accessible.

[ ] do not combine actions from two separate overlapping targets.

============================================================
7 — TRUE FRONTMOST CROSSHAIR TARGET
============================================================

Current targeting is not reliable enough when several targets overlap.

Required examples:

CAMERA -> DOOR -> TABLE

must choose:

DOOR

not the TABLE behind it.

CAMERA -> NPC A -> NPC B

must choose:

NPC A

not NPC B.

Must work for:

[ ] LOC -> LOC.

[ ] NPC -> NPC.

[ ] NPC -> LOC.

[ ] LOC -> NPC.

Targeting should correspond to what the player visually believes the
crosshair is pointing at.

Do NOT simply choose:

- last MiniMenu entry;
- highest-priority action;
- nearest entity to PLAYER.

Primary intention is nearest/frontmost target along the VIEW/CROSSHAIR.

============================================================
8 — BETTER FIRST-PERSON OBJECT TARGETING
============================================================

Vanilla object click areas can be difficult in FIRST_PERSON.

Examples:

- staircases;
- ladders;
- trees;
- doors;
- booths;
- narrow objects;
- unusual scenery meshes.

In vanilla, the mouse can precisely target a small clickable region.

In FIRST_PERSON this can feel frustrating because the player may clearly aim
at a visible staircase/tree/etc. while the interaction system fails to find it.

TODO:

[ ] make FP object acquisition more forgiving.

[ ] preserve actual object identity.

[ ] preserve real vanilla object actions.

[ ] improve aim tolerance/hit usability.

[ ] do not allow clicking unrelated objects through walls.

[ ] do not create huge invisible interaction zones.

[ ] visible target and detected target should match intuitively.

============================================================
9 — AUTO-APPROACH WHILE REMAINING FIRST_PERSON
============================================================

Extremely important interaction requirement.

If player chooses an action but is too far away:

DO NOT leave FIRST_PERSON.

Example:

Look at staircase several tiles away
-> choose Climb-up
-> player automatically walks toward staircase using normal RuneScape
   pathfinding
-> FIRST_PERSON camera remains active
-> when vanilla interaction conditions are satisfied, Climb-up executes.

Likewise:

NPC -> Talk-to
NPC -> Attack
Booth -> Bank
Door -> Open
Ground item -> Take
etc.

TODO:

[ ] keep FIRST_PERSON active.

[ ] use normal RuneScape approach/pathfinding behaviour.

[ ] use normal collision.

[ ] use real reach requirements.

[ ] execute action when vanilla requirements are satisfied.

[ ] manual WASD can cancel/override automatic approach.

[ ] do not teleport player.

[ ] do not artificially increase interaction distance.

============================================================
10 — REAL VANILLA RIGHT-CLICK MENU IN FIRST_PERSON
============================================================

Current runtime:

RIGHT CLICK OPENS MENU.

This is a real partial pass.

Current failures:

- wheel does not navigate options;
- no visible selected-row highlight.

Final UX:

Aim at target
-> right click
-> real vanilla RuneScape MiniMenu
-> wheel selects entries
-> current entry visibly highlighted
-> left click executes selected entry.

Menu must retain full vanilla options for:

[ ] players.

[ ] NPCs.

[ ] LOCs.

[ ] doors.

[ ] ladders/stairs.

[ ] booths.

[ ] ground items.

[ ] item-on-target.

[ ] spell-on-target.

[ ] Examine.

[ ] Follow.

[ ] Trade.

[ ] Attack.

[ ] Talk-to.

[ ] Bank.

etc.

TODO:

[ ] fix wheel navigation.

[ ] make selected row visibly highlighted.

[ ] verify visible row matches executed MiniMenu entry.

[ ] prevent camera zoom while menu owns wheel.

[ ] prevent FP -> CHASE transition while menu owns wheel.

[ ] immediately restore camera wheel when menu closes.

[ ] prevent double click processing.

[ ] verify opening/closing repeatedly does not create stale state.

============================================================
11 — DIALOGUE KEYBOARD INPUT
============================================================

Current runtime:

SPACE continue:
WORKS.

Numeric option selection:
DOES NOT WORK.

Required behaviour:

Select an Option

Option A
Option B
Option C

1 -> Option A
2 -> Option B
3 -> Option C.

TODO:

[ ] 2-option dialogues.

[ ] 3-option dialogues.

[ ] 4-option dialogues.

[ ] 5-option dialogues.

[ ] potentially larger dialogue families if real source supports them.

[ ] NPC dialogue.

[ ] object dialogue.

[ ] Grand Exchange-style option dialogue.

[ ] preserve SPACE continue.

[ ] no mouse required.

Dialogue input must override quickbar/action-bar numeric hotkeys.

============================================================
12 — INPUT OWNERSHIP PRIORITY
============================================================

Final modern input ownership should conceptually be:

1. text/chat input
2. dialogue/modal interface
3. open FP vanilla context menu
4. CTRL-held UI cursor mode
5. quick world interaction
6. quickbar/action-bar hotkeys
7. movement/camera gameplay

Exactly one subsystem should consume a given input event when ownership is
exclusive.

Examples:

Dialogue open:
1 = dialogue option
NOT food.

Context menu open:
wheel = menu navigation
NOT camera zoom.

Normal gameplay:
1 = item quickslot 1.

Action-bar modifier/hotkey:
activates assigned prayer/spell.

============================================================
13 — MOUSE WHEEL OWNERSHIP
============================================================

Do NOT use mouse wheel as primary quickbar/action-bar activation.

Mouse wheel already has important responsibilities:

NORMAL FP/CHASE:
camera zoom / FP-CHASE camera transition.

CONTEXT MENU OPEN:
context-menu selection.

Therefore:

[ ] quickbar should use keyboard hotkeys.

[ ] action bar should use keyboard hotkeys.

[ ] wheel remains camera/menu-specific.

[ ] no triple-purpose quickslot scrolling.

============================================================
14 — QUICKBAR — ITEMS ONLY
============================================================

Final design decision:

QUICKBAR = ITEMS ONLY.

Suggested initial size:

6 slots.

Default activation:

1
2
3
4
5
6

All bindings must later be remappable in settings.

Quickbar can contain actual inventory/equipment-compatible items such as:

[ ] food.

[ ] potions.

[ ] weapons.

[ ] armour.

[ ] teleport items.

[ ] consumables.

[ ] other safe inventory items where a meaningful vanilla action exists.

The quickbar must show:

[ ] actual RuneScape item sprite.

[ ] slot number/hotkey.

[ ] unavailable/empty state.

[ ] optional cooldown/action state only where RuneScape actually has one.

Quickbar must NOT contain prayers or magic spells.

============================================================
15 — ADDING INVENTORY ITEMS TO QUICKBAR
============================================================

Required UX:

Right click a compatible inventory item.

If not assigned:

Add to quickbar

Selecting this places the item in the FIRST AVAILABLE quickbar slot.

If already assigned:

Remove from quickbar

instead of Add to quickbar.

TODO:

[ ] Add to quickbar menu action.

[ ] Remove from quickbar menu action.

[ ] first free quickbar slot selection.

[ ] graceful feedback if all slots are full.

[ ] no fake item copies.

[ ] keep using the real inventory item.

============================================================
16 — QUICKBAR DRAG-AND-DROP
============================================================

After an item has been added to the first free quickbar slot:

Player can use mouse to drag it to another quickbar position.

Examples:

slot 1 -> slot 4.

slot 4 -> slot 2.

TODO:

[ ] drag quickbar slot.

[ ] swap occupied slots naturally.

[ ] move into empty slot.

[ ] visual drag feedback.

[ ] preserve assignment after inventory rearrangement.

[ ] optionally support dragging item directly from inventory to bar later
    if desirable.

============================================================
17 — INVENTORY QUICKBAR HIGHLIGHT
============================================================

Items assigned to quickbar should be visually identifiable in inventory.

TODO:

[ ] subtle highlight/border for quickbar-associated items.

[ ] do not obscure amount/count text.

[ ] do not make inventory visually noisy.

[ ] RuneScape-style presentation.

============================================================
18 — QUICKBAR ITEM REFERENCE MODEL
============================================================

A quickbar assignment must NOT permanently reference one inventory cell.

Example:

Quickslot 1 = Shark.

Current Shark is in inventory slot 12.

Player eats it.

Another Shark exists in slot 20.

Quickslot 1 should automatically continue using the next Shark.

TODO:

[ ] quickslot represents item identity/family.

[ ] locate current matching inventory entry on activation.

[ ] inventory rearrangement does not break quickslot.

[ ] when no item remains, mark unavailable.

[ ] when matching item returns, slot becomes usable again where appropriate.

============================================================
19 — POTION DOSE FAMILIES
============================================================

Potion IDs change after drinking.

Example:

Prayer potion(4)
-> Prayer potion(3)
-> Prayer potion(2)
-> Prayer potion(1)

Quickbar assignment must survive the dose transition.

TODO:

[ ] treat dose variants as one logical quickbar family.

[ ] after drinking, continue referencing remaining-dose item.

[ ] after potion is empty, find another compatible potion in inventory.

[ ] support similar state-changing consumable families where appropriate.

============================================================
20 — QUICKBAR ITEM ACTIONS
============================================================

Quickbar activation must invoke existing real item actions.

Examples:

Food:
Eat.

Potion:
Drink.

Weapon:
Wield.

Armour:
Wear.

Teleport item:
appropriate existing action.

TODO:

[ ] never invent custom consumption/equipment packets.

[ ] preserve level/equipment requirements.

[ ] preserve server authority.

[ ] respect inventory state.

============================================================
21 — ACTION BAR — PRAYERS + MAGIC ONLY
============================================================

Separate from item quickbar.

ACTION BAR =

PRAYERS
+
MAGIC SPELLS

Suggested maximum:

10 slots.

Visual layout:

small/subtle bar positioned ABOVE the item quickbar.

It should use actual:

[ ] prayer sprites.

[ ] magic spell sprites.

Action bar should NOT contain inventory items.

============================================================
22 — ADD PRAYER TO ACTION BAR
============================================================

Required UX:

Open Prayer interface.

Right click prayer icon.

Menu option:

Add to action bar

Selecting it places prayer into FIRST AVAILABLE action-bar slot.

If already assigned:

Remove from action bar.

TODO:

[ ] Add to action bar.

[ ] Remove from action bar.

[ ] detect first free slot.

[ ] clear feedback if full.

[ ] show actual prayer icon.

============================================================
23 — ADD MAGIC SPELL TO ACTION BAR
============================================================

Required UX:

Open Magic interface.

Right click spell icon.

Menu option:

Add to action bar

Selecting it places spell into FIRST AVAILABLE action-bar slot.

If already assigned:

Remove from action bar.

TODO:

[ ] Add to action bar.

[ ] Remove from action bar.

[ ] first free action-bar slot.

[ ] actual spell sprite.

[ ] preserve spell availability state.

============================================================
24 — ACTION BAR DRAG-AND-DROP
============================================================

After prayer/spell is assigned:

[ ] drag icon to another slot.

[ ] swap occupied slots.

[ ] move to empty slots.

[ ] preserve prayer/spell identity.

[ ] show visual drag state.

[ ] allow customised ordering.

============================================================
25 — ACTION BAR HOTKEYS
============================================================

Action bar needs up to 10 hotkeys.

IMPORTANT ERGONOMIC CONCERN:

Shift + 1 ... Shift + 0 is easy to understand,
BUT may be awkward while actively holding WASD.

Especially far number keys are difficult during movement.

Therefore:

DO NOT permanently lock default hotkeys to Shift+1..0 yet.

TODO:

[ ] design an ergonomic default action-bar hotkey scheme.

[ ] ensure it can be comfortably used while holding WASD.

[ ] avoid conflicts with:
    W/A/S/D
    E interaction
    dialogue numbers
    quickbar numbers
    context menu
    CTRL free mouse.

Possible design candidates to evaluate:

- Shift + numbers;
- Alt + numbers;
- modifier + nearby keys;
- Q/R/F/C/X/etc. style bindings;
- mixed nearby combat keys;
- configurable modifier scheme.

Final requirement:

ALL action-bar hotkeys must be fully remappable.

============================================================
26 — QUICKBAR HOTKEY SETTINGS
============================================================

Default:

Quickbar slots 1–6:

1
2
3
4
5
6

TODO:

[ ] fully remappable.

[ ] display assigned hotkey on slot.

[ ] detect conflicts in settings.

[ ] allow resetting defaults.

[ ] dialogue still overrides numbers while dialogue is open.

============================================================
27 — ACTION BAR HOTKEY SETTINGS
============================================================

Up to 10 separate action-bar slots.

TODO:

[ ] independently remappable.

[ ] show assigned hotkey.

[ ] allow modifiers.

[ ] detect duplicate/conflicting binds.

[ ] allow reset to defaults.

[ ] action bar must be usable during WASD combat.

============================================================
28 — PRAYER ACTION-BAR BEHAVIOUR
============================================================

Prayer slot:

key press toggles the REAL prayer.

TODO:

[ ] active prayer state visible on bar.

[ ] inactive state.

[ ] unavailable state.

[ ] insufficient Prayer level state.

[ ] preserve Prayer drain.

[ ] preserve normal mutual-exclusion rules.

[ ] preserve server authority.

Possible later feature:

[ ] bind Quick Prayer preset toggle as special action.

============================================================
29 — MAGIC ACTION-BAR BEHAVIOUR
============================================================

Magic spell assignment must use real RuneScape spell behaviour.

Possible categories:

instant/non-target spell.

target-required spell.

combat spell.

utility spell.

TODO:

[ ] preserve Magic level requirements.

[ ] preserve rune requirements.

[ ] preserve selected spell state.

[ ] preserve target restrictions.

[ ] preserve server casting.

[ ] target-required spell should integrate with FP crosshair targeting.

[ ] unavailable spells visually dimmed appropriately.

============================================================
30 — QUICKBAR + ACTION BAR VISUAL DESIGN
============================================================

Visual hierarchy:

ACTION BAR
small/subtle
prayers + magic

above

QUICKBAR
slightly more prominent
items/consumables/equipment

Example conceptual layout:

     [prayer][spell][spell][prayer][...]
         small Action Bar

       [food][pot][weapon][...]
          item Quickbar

Do NOT turn this into a giant generic MMO hotbar.

Style should feel like modernised RuneScape.

============================================================
31 — FIRST-PERSON COMBAT — CORE GOAL
============================================================

Current RuneScape combat feels wrong under FIRST_PERSON controls.

Current issue:

small movement can interrupt auto attack.

FIRST_PERSON should remain RuneScape combat,
but feel direct and natural.

Primary combat UX:

crosshair over attackable target
+
left click
=
real vanilla Attack action.

No overlay should be REQUIRED merely to attack.

============================================================
32 — COMBAT TARGET LOCK
============================================================

Desired:

crosshair + left click enemy

-> target selected/locked
-> normal RuneScape attack starts
-> player may continue moving/looking
-> attack continues while RuneScape combat rules remain valid.

TODO:

[ ] active combat target.

[ ] target indication.

[ ] freely move camera.

[ ] allow WASD repositioning.

[ ] small movement does not arbitrarily cancel target.

[ ] click another enemy to switch target.

[ ] target dies -> clear appropriately.

[ ] target despawns -> clear.

[ ] invalid combat state -> clear.

============================================================
33 — DO NOT GIVE FP ARTIFICIAL DPS ADVANTAGE
============================================================

FIRST_PERSON should NOT receive:

[ ] faster attack ticks.

[ ] increased damage.

[ ] increased accuracy.

[ ] extra attacks.

[ ] shorter server weapon cooldown.

Reason:

Otherwise FP becomes mandatory for best DPS instead of being an alternative
control mode.

FIRST_PERSON MAY have skill/control advantages:

[ ] easier positioning.

[ ] strafing.

[ ] better target acquisition.

[ ] faster UI access.

[ ] action bar.

[ ] quickbar.

[ ] prayer hotkeys.

[ ] manual dodging.

[ ] easier line-of-sight control.

============================================================
34 — MELEE AUTO APPROACH
============================================================

If player attacks melee target outside valid melee distance:

[ ] retain FIRST_PERSON.

[ ] normal RuneScape pathfinding approaches enemy.

[ ] stop at appropriate interaction range.

[ ] existing combat begins.

[ ] manual WASD can interrupt/reposition.

============================================================
35 — RANGED COMBAT
============================================================

[ ] crosshair selects ranged target.

[ ] preserve real ranged attack distance.

[ ] preserve line-of-sight.

[ ] preserve ammunition.

[ ] preserve projectile behaviour.

[ ] movement should not unnecessarily clear attack.

============================================================
36 — MAGIC COMBAT
============================================================

[ ] action-bar spell may select a spell.

[ ] crosshair may select target.

[ ] preserve rune cost.

[ ] preserve Magic level.

[ ] preserve spell restrictions.

[ ] preserve autocast.

[ ] preserve RuneScape attack timing.

============================================================
37 — TARGET HUD
============================================================

When an active target exists:

Potential compact information:

[ ] target name.

[ ] combat level.

[ ] HP bar.

[ ] subtle target marker.

[ ] possibly selected-action/spell state.

Keep compact.

Avoid generic giant MMO target frames.

============================================================
38 — CROSSHAIR FEEDBACK
============================================================

Potential states:

[ ] neutral.

[ ] interactable object.

[ ] NPC interaction.

[ ] attackable target.

[ ] out of range.

[ ] active combat target.

[ ] selected target spell.

Keep subtle.

Do not make it look like a shooter hitmarker system.

============================================================
39 — FIRST-PERSON MINIMAP VIEW CONE
============================================================

Important navigation feature.

FIRST_PERSON minimap should display:

player position/orientation

PLUS

a camera view/FOV cone.

Similar to many modern first/third-person games.

TODO:

[ ] cone follows camera yaw.

[ ] cone begins at player marker.

[ ] approximate cone width from actual FOV.

[ ] FOV setting affects cone width.

[ ] keep RuneScape minimap visual identity.

[ ] do not interfere with minimap clicking.

Consider whether CHASE should also use it.

============================================================
40 — CAMERA / MODE INDICATOR
============================================================

Need clear but subtle indication of current mode.

Potential states:

FP
CHASE
FREE
ORIGINAL

TODO:

[ ] design small mode indicator.

[ ] likely near minimap or lower HUD.

[ ] RuneScape visual style.

[ ] no large intrusive labels.

[ ] optionally configurable.

============================================================
41 — FIRST-PERSON HUD
============================================================

Still requires dedicated brainstorming/design.

Potential elements:

[ ] crosshair.

[ ] interaction prompt.

[ ] quick action overlay.

[ ] target HP/name.

[ ] HP.

[ ] Prayer.

[ ] Run energy.

[ ] minimap.

[ ] minimap view cone.

[ ] action bar.

[ ] item quickbar.

[ ] mode indicator.

[ ] XP drops.

Goals:

compact.

clean.

RuneScape-like.

modern.

not generic FPS.

not cluttered MMO UI.

============================================================
42 — THIRD-PERSON / CHASE HUD
============================================================

Needs its own design pass.

Likely shares:

[ ] quickbar.

[ ] action bar.

[ ] HP/prayer/run.

[ ] minimap.

[ ] target panel.

[ ] mode indicator.

But may display more information than FIRST_PERSON.

TODO:

[ ] brainstorm FP/TP shared UI.

[ ] brainstorm TP-specific elements.

[ ] decide how vanilla side panels remain available.

============================================================
43 — UI ACCESS IN FIRST_PERSON
============================================================

CTRL free mouse already works and must remain.

Need convenient access to:

[ ] inventory.

[ ] equipment.

[ ] prayer interface.

[ ] magic interface.

[ ] skills.

[ ] quests.

[ ] other vanilla interfaces.

TODO:

[ ] decide preferred hotkeys.

[ ] preserve normal interface behaviour.

[ ] action bar creation through existing prayer/magic icons.

[ ] quickbar creation through existing inventory icons.

============================================================
44 — F11 MODERN -> ORIGINAL CLIPPING BUG
============================================================

STILL FAILED IN USER RUNTIME.

Symptom:

After FIRST_PERSON/MODERN -> F11 -> ORIGINAL:

- some normally walkable tiles act blocked;
- some scenery/object clipping behaves wrong;
- walking far enough to load another chunk/region repairs the issue.

Previous AI attempts included:

- movementQueue reset;
- authoritative tile sync;
- teleport reset;
- temporary server-step drain;
- later removal of broad drain.

None has produced a user runtime pass.

TODO:

[ ] investigate actual root cause.

[ ] compare healthy ORIGINAL before MODERN.

[ ] compare broken ORIGINAL immediately after F11.

[ ] compare repaired state after region/chunk reload.

Compare:

[ ] player local tile.

[ ] player world tile.

[ ] fine coordinates.

[ ] server-confirmed tile.

[ ] movement queue.

[ ] pathfinding origin.

[ ] collision flags.

[ ] collision plane.

[ ] dynamic object collision state.

[ ] scene/region base.

[ ] whatever region rebuild resets that F11 currently does not.

Do not stack another blind reset without proving why.

============================================================
45 — COORDINATE DIAGNOSTICS
============================================================

F12 currently shows server tile values sometimes below 100.

That likely represents LOCAL scene coordinates, but labelling is confusing.

Display separately:

[ ] Player Local Tile.

[ ] Server Local Tile.

[ ] Scene Base X/Z.

[ ] Player World Tile.

[ ] Server World Tile.

[ ] Plane.

Clearly differentiate LOCAL from WORLD.

============================================================
46 — FIRST-PERSON CEILINGS
============================================================

CURRENT:

no ceilings because experimental renderer is disabled.

Important runtime history:

Earlier ceiling implementation partially WORKED.

It rendered:

- textured underside surfaces;
- floor texture on ceiling.

Problems:

- looking steeply/straight upward caused clipping/disappearance;
- sky became visible;
- only several nearby/ahead tiles rendered.

Later rewrite caused:

- giant slabs/triangles;
- severe geometry artifacts;
- instability.

Renderer was disabled.

TODO:

[ ] restore textured ceilings.

[ ] preserve known-working concept where possible.

[ ] fix near-plane/upward clipping.

[ ] increase safe coverage.

[ ] prevent sky holes indoors.

[ ] shaped floor support if needed.

[ ] no giant triangles.

[ ] no crash.

[ ] no global culling hacks.

[ ] CHASE/FREE/ORIGINAL unaffected.

============================================================
47 — MODERN WASD COLLISION — FUTURE MAJOR ROUND
============================================================

Separate from F11 ORIGINAL bug.

Eventually continuous modern movement needs robust collision:

[ ] walls.

[ ] directional wall flags.

[ ] corners.

[ ] diagonals.

[ ] scenery footprints.

[ ] sliding.

[ ] narrow passageways.

[ ] force movement.

[ ] server corrections.

[ ] rebase.

Do not turn vanilla PathFinder into the continuous WASD movement driver.

Dedicated large implementation round later.

============================================================
48 — FIRST-PERSON VIEWMODEL
============================================================

Future presentation:

[ ] real equipped sword.

[ ] shield.

[ ] defender.

[ ] bow.

[ ] crossbow.

[ ] staff.

[ ] axe.

[ ] pickaxe.

[ ] 2H weapon.

[ ] hands/gloves.

Use real RuneScape equipment/models/animations.

No fake FPS weapon system.

============================================================
49 — MODERN SETTINGS MENU
============================================================

Eventually include:

[ ] mouse sensitivity.

[ ] FOV.

[ ] head bob.

[ ] chase distance.

[ ] camera smoothing.

[ ] UI scale.

[ ] crosshair scale.

[ ] crosshair opacity.

[ ] quickbar bindings 1–6.

[ ] action-bar bindings 1–10.

[ ] key conflict detection.

[ ] reset defaults.

[ ] quickbar visibility.

[ ] action-bar visibility.

[ ] minimap view cone toggle.

[ ] mode indicator toggle.

============================================================
50 — HOTKEY REMAPPING SYSTEM
============================================================

Important future foundation.

Need remappable controls for:

[ ] movement.

[ ] interaction.

[ ] quickbar 1–6.

[ ] action bar 1–10.

[ ] UI access.

[ ] combat/cancel where applicable.

Requirements:

[ ] modifier support.

[ ] conflict warning.

[ ] prevent impossible duplicate ownership unless intentionally allowed.

[ ] current binding visible in HUD/settings.

[ ] sensible defaults.

============================================================
51 — CONTROLLER SUPPORT — LATER
============================================================

Do not implement until keyboard/mouse ownership is stable.

Potential future:

left stick = movement.

right stick = camera.

A = primary interaction.

buttons/D-pad = quick/action slots.

triggers/bumpers = modifiers.

UI navigation.

Design bars with future controller support in mind.

============================================================
52 — EXTENDED RENDER DISTANCE — LATER
============================================================

Do not treat draw distance as a single constant.

Future work:

[ ] understand scene load distance.

[ ] region/chunk availability.

[ ] object loading.

[ ] NPC loading.

[ ] render distance.

[ ] performance.

============================================================
53 — UI ART DIRECTION
============================================================

Modern UI must look like:

"RuneScape evolved into a modern FP/TP game."

NOT:

- Call of Duty HUD;
- neon sci-fi;
- generic MMO addon bars;
- huge opaque panels.

Use:

[ ] RuneScape-inspired borders.

[ ] RuneScape textures.

[ ] real sprites.

[ ] compact layouts.

[ ] readable text.

[ ] restrained animation.

[ ] scalable UI.

============================================================
54 — AUDIO / IMMERSION — OPTIONAL FUTURE
============================================================

Possible later:

[ ] directional combat sound.

[ ] positional environment sound.

[ ] footsteps.

[ ] interaction audio feedback.

[ ] configurable head bob.

============================================================
55 — RUNTIME STATUS CLEANUP
============================================================

Audit MODERN_CONTROLS_PROGRESS.md.

Use clear statuses only:

SOURCE VERIFIED
COMPILE VERIFIED
STATICALLY REVIEWED
RUNTIME UNVERIFIED
USER RUNTIME VERIFIED
FAILED USER RUNTIME
BLOCKED

Current important truth:

[ ] context menu opens = USER RUNTIME VERIFIED.

[ ] context menu wheel = FAILED USER RUNTIME.

[ ] context menu highlight = FAILED USER RUNTIME.

[ ] LOC quick overlay = USER RUNTIME VERIFIED.

[ ] NPC overlay = FAILED / RUNTIME UNVERIFIED.

[ ] dialogue SPACE = USER RUNTIME VERIFIED.

[ ] dialogue numbers = FAILED USER RUNTIME.

[ ] F11 clipping fix = FAILED USER RUNTIME.

[ ] ceiling earlier textured underside = HISTORICAL PARTIAL RUNTIME SUCCESS.

[ ] current ceiling = DISABLED / NO CURRENT RUNTIME CEILING.

============================================================
56 — RECOMMENDED DEVELOPMENT ORDER
============================================================

PHASE A — FIX CURRENT BROKEN SYSTEMS

[x] FP context menu wheel. USER RUNTIME VERIFIED.

[x] FP context menu highlight. USER RUNTIME VERIFIED.

[ ] NPC overlay.

[ ] dialogue 1–5.

[x] F11 ORIGINAL clipping/pathfinding regression. USER RUNTIME VERIFIED.

[ ] frontmost targeting.

PHASE B — COMPLETE CORE INTERACTION

[ ] improved object clickability.

[ ] auto approach while staying FP.

[ ] E / quick interaction finalisation.

[x] direct LMB combat targeting. USER RUNTIME VERIFIED.

[x] combat target lock and server-authoritative MODERN WASD persistence.
    USER RUNTIME VERIFIED.

PHASE C — MODERN UI FOUNDATION

[ ] item quickbar.

[ ] quickbar Add/Remove menu options.

[ ] quickbar drag-and-drop.

[ ] item auto-reference.

[ ] potion families.

[ ] prayer/magic action bar.

[ ] Add/Remove prayer/spell menu options.

[ ] action-bar drag-and-drop.

[ ] hotkey mapping system.

PHASE D — HUD

[ ] minimap view cone.

[ ] mode indicator.

[ ] target HUD.

[ ] FP HUD.

[ ] TP HUD.

[ ] settings.

PHASE E — RENDERING / POLISH

[ ] ceilings.

[ ] FP equipment/viewmodel.

[ ] advanced collision.

[ ] audio.

[ ] extended render distance.

============================================================
57 — CODEX WORKING RULES
============================================================

[ ] Inspect current Git state first.

[ ] Inspect actual current source before assumptions.

[ ] Preserve unrelated changes.

[ ] User runtime is final authority.

[ ] Compile success != runtime success.

[ ] Static review != runtime success.

[ ] Do not claim USER RUNTIME VERIFIED until user tests it.

[ ] Do not broadly revert working camera/movement systems.

[ ] Do not replace vanilla actions with fake modern packets.

[ ] Keep RuneScape server authority.

[ ] Prefer small coherent implementation rounds.

[ ] Build after each bounded round.

[ ] Update progress documentation truthfully.

[ ] Use Git commits as recovery checkpoints after useful milestones.

[ ] Stop for user runtime testing before declaring a subsystem complete.

============================================================
58 — FIRST-PERSON PLAYER BODY / EQUIPMENT VISIBILITY
============================================================

FIRST_PERSON must NOT feel like a floating camera.

The player should be able to see their own character naturally.

WHEN LOOKING FORWARD:

[ ] visible hands/arms where appropriate.

[ ] currently wielded weapon visible.

[ ] shield / defender visible in off-hand where applicable.

[ ] staff visible.

[ ] bow visible.

[ ] crossbow visible.

[ ] axe/pickaxe visible.

[ ] 2H weapon support.

[ ] unarmed hands/gloves support.

Use the REAL RuneScape player appearance, equipment models and animations
where practical.

Do NOT create fake generic FPS weapon models.

WHEN LOOKING DOWN:

[ ] player torso/body should be visible.

[ ] legs should be visible.

[ ] equipped armour/clothing should be visible.

[ ] boots should be visible.

[ ] body should correspond to the real player appearance/equipment.

IMPORTANT:

[ ] player's own HEAD must NOT be visible in FIRST_PERSON.

[ ] head equipment must not clip through the camera.

[ ] hair/helmets/capes/etc. must not obstruct the camera.

[ ] hide only the minimum local-player geometry required for a clean view.

[ ] do NOT move the camera backwards merely to avoid head clipping.

[ ] body animations must remain synchronized with real RuneScape animation.

[ ] walking/running animations.

[ ] attack animations.

[ ] weapon animations.

[ ] skilling animations where visible.

[ ] emotes where appropriate.

[ ] equipment switching should update visible FP equipment immediately.

============================================================
59 — FIRST-PERSON DAMAGE DIRECTION INDICATOR
============================================================

STATUS: USER RUNTIME VERIFIED (17-08-2026).

The current implementation is server-authoritative: a finalized combat impact
sends only a quantized direction to the affected MODERN-session player. The
client rotates that cue into camera space and fades simultaneous cues without
replacing normal hit splats or exposing attacker identity.

When the player receives damage in FIRST_PERSON, they need spatial feedback
showing approximately where the attack came from.

Similar in principle to directional damage indicators used by many modern
first/third-person games.

Examples:

attacked from behind
-> rear indicator.

attacked from left
-> left-side indicator.

attacked from front-right
-> front-right indicator.

TODO:

[ ] determine attack/source direction relative to current camera/player.

[ ] display subtle directional damage indicator around screen/crosshair.

[ ] support melee attacks.

[ ] support ranged attacks.

[ ] support magic attacks.

[ ] support multiple attackers without overwhelming the HUD.

[ ] indicator fades naturally.

[ ] repeated hits may refresh direction.

[ ] do NOT replace RuneScape hit splats.

[ ] RuneScape hit splats remain authoritative/visible.

[ ] indicator communicates DIRECTION only, not a replacement damage system.

[ ] do not reveal attacker information the player should not legitimately know.

[ ] style must match modernised RuneScape HUD, not a military FPS overlay.

Potential optional later setting:

[ ] damage-direction indicator ON/OFF.

[ ] indicator opacity/intensity.

============================================================
60 — COMPLETE FIRST-PERSON CONTENT COMPATIBILITY PASS
============================================================

IMPORTANT:

Even when:

- cameras work;
- movement works;
- combat works;
- interaction works;
- HUD is finished;
- quickbars/action bars work;
- ceilings work;
- visuals are polished;

THE PROJECT IS NOT FINISHED.

At that point begin a dedicated FULL-GAME FIRST_PERSON COMPATIBILITY QA PHASE.

Goal:

Virtually all playable 2009Scape content must be tested in MODERN
FIRST_PERSON mode.

The purpose is to discover content-specific assumptions that were originally
designed only for vanilla click-to-walk/camera controls.

Do NOT assume generic systems automatically make every piece of content work.

============================================================
61 — ALL SKILLS MUST BE PLAYABLE IN FIRST_PERSON
============================================================

Every skill available in the game must receive actual gameplay testing.

Test the complete gameplay loop, not merely opening the interface.

Examples include, according to content present in the current 2009Scape
version:

[ ] Attack.

[ ] Strength.

[ ] Defence.

[ ] Hitpoints.

[ ] Ranged.

[ ] Prayer.

[ ] Magic.

[ ] Cooking.

[ ] Woodcutting.

[ ] Fletching.

[ ] Fishing.

[ ] Firemaking.

[ ] Crafting.

[ ] Smithing.

[ ] Mining.

[ ] Herblore.

[ ] Agility.

[ ] Thieving.

[ ] Slayer.

[ ] Farming.

[ ] Runecrafting.

[ ] Construction if supported by current content.

[ ] Hunter if supported by current content.

[ ] Summoning if supported by this revision/server content.

Use actual current server/content availability as authority.

For EACH skill test:

[ ] target acquisition.

[ ] object/NPC clickability.

[ ] action selection.

[ ] automatic approach.

[ ] animation.

[ ] movement interruption behaviour.

[ ] interfaces/dialogues.

[ ] inventory interaction.

[ ] equipment interaction.

[ ] XP/progression.

[ ] repeated actions.

[ ] camera behaviour.

[ ] FIRST_PERSON body/viewmodel behaviour.

[ ] completion/cancel behaviour.

============================================================
62 — SKILL-SPECIFIC FIRST-PERSON PROBLEM LIST
============================================================

During full content testing, create a living compatibility list.

Examples of issues that may appear:

Woodcutting:
- tree difficult to target;
- animation/camera clips;
- automatic approach wrong.

Mining:
- rock interaction target too small;
- ore depletion target state incorrect.

Fishing:
- fishing spot moving/NPC-style target difficult to acquire.

Agility:
- obstacle target difficult to identify;
- automatic approach/path transitions;
- forced movement/camera problems.

Thieving:
- NPC targeting;
- stalls;
- repeated interaction.

Farming:
- patch interaction;
- tool/item-on-object actions.

Runecrafting:
- altar/ruin interaction;
- talisman/item-on-object flows.

Cooking/Smithing/Crafting:
- interface ownership;
- Make-X interfaces;
- keyboard/dialogue interaction.

TODO:

[ ] record every content-specific issue separately.

[ ] do not fix several unrelated content bugs with one dangerous global hack.

[ ] group common root causes where genuinely shared.

============================================================
63 — QUEST COMPATIBILITY PASS
============================================================

Quests often contain unusual scripts, interfaces and object interactions.

Eventually test quests in FIRST_PERSON.

For each tested quest verify:

[ ] NPC conversations.

[ ] dialogue choices.

[ ] cutscenes.

[ ] forced camera sequences.

[ ] forced player movement.

[ ] doors.

[ ] ladders/stairs.

[ ] item-on-object.

[ ] item-on-NPC.

[ ] spell interactions.

[ ] puzzle interfaces.

[ ] teleport transitions.

[ ] instanced/special areas.

[ ] quest combat.

[ ] scripted animations.

[ ] completion flow.

IMPORTANT:

Vanilla scripted/cutscene camera ownership may temporarily override MODERN
camera when content genuinely requires it.

After scripted sequence ends, MODERN camera/control state must recover cleanly.

============================================================
64 — DUNGEONS / BUILDINGS / MULTI-LEVEL AREAS
============================================================

FIRST_PERSON is especially sensitive to indoor/multi-plane content.

Test:

[ ] houses.

[ ] castles.

[ ] banks.

[ ] caves.

[ ] dungeons.

[ ] towers.

[ ] upstairs/downstairs transitions.

[ ] ladders.

[ ] staircases.

[ ] trapdoors.

[ ] underground areas.

[ ] multi-plane structures.

Verify:

[ ] ceilings.

[ ] roofs.

[ ] camera.

[ ] clipping.

[ ] plane changes.

[ ] target selection.

[ ] interaction reach.

[ ] minimap.

[ ] auto approach.

============================================================
65 — MINIGAME / ACTIVITY COMPATIBILITY
============================================================

Test available minigames and activities individually.

Examples depending on server content:

[ ] Barrows.

[ ] Pest Control.

[ ] Castle Wars.

[ ] Fight Caves.

[ ] Duel Arena / similar activities if present.

[ ] Grand Exchange.

[ ] Slayer activities.

[ ] minigames using custom interfaces.

[ ] transportation systems.

For each:

[ ] movement.

[ ] combat.

[ ] interfaces.

[ ] dialogues.

[ ] targeting.

[ ] camera.

[ ] teleport/region changes.

[ ] rewards.

============================================================
66 — TRANSPORT / TELEPORT COMPATIBILITY
============================================================

Test:

[ ] stairs.

[ ] ladders.

[ ] doors.

[ ] portals.

[ ] boats.

[ ] ships.

[ ] magic teleports.

[ ] jewellery teleports.

[ ] home teleport.

[ ] NPC transportation.

[ ] lever/portal transitions.

[ ] dungeon entrances/exits.

After every transition verify:

[ ] MODERN mode remains coherent.

[ ] player local/world coordinates rebase correctly.

[ ] camera does not become detached.

[ ] collision remains correct.

[ ] interaction still works.

[ ] minimap/view cone remains correct.

============================================================
67 — BANK / SHOP / GE / INTERFACE COMPATIBILITY
============================================================

Test major vanilla UI workflows while MODERN is active:

[ ] Bank.

[ ] Deposit Box.

[ ] Shops.

[ ] Grand Exchange.

[ ] Trading.

[ ] Equipment interface.

[ ] Prayer interface.

[ ] Magic interface.

[ ] skill interfaces.

[ ] Make-X / quantity interfaces.

[ ] dialogue interfaces.

[ ] inventory context menus.

Verify:

[ ] CTRL/free-mouse route.

[ ] keyboard ownership.

[ ] quickbar/action-bar integration.

[ ] no WASD keys leaking into text fields.

[ ] no quickslot activation while typing.

[ ] clean return to FIRST_PERSON controls after closing UI.

============================================================
68 — COMBAT CONTENT COMPATIBILITY
============================================================

After core FP combat works, test across many enemy/content types.

Test:

[ ] basic melee NPC.

[ ] ranged NPC.

[ ] magic NPC.

[ ] aggressive NPC.

[ ] multiple attackers.

[ ] large NPC models.

[ ] small NPC models.

[ ] moving NPCs.

[ ] bosses where available.

[ ] enemies behind obstacles.

[ ] enemies on edges/corners.

[ ] melee.

[ ] ranged.

[ ] magic.

[ ] autocast.

[ ] prayer switching.

[ ] food/potions through quickbar.

[ ] target switching.

[ ] auto approach.

[ ] retreat/re-engage.

[ ] death.

[ ] respawn.

[ ] loot pickup.

============================================================
69 — CONTENT QA ISSUE DATABASE / CHECKLIST
============================================================

During the final compatibility phase maintain a structured list such as:

CONTENT:
SKILL / QUEST / AREA / NPC / OBJECT / INTERFACE

LOCATION:

REPRODUCTION:

EXPECTED:

ACTUAL:

MODE:
FIRST_PERSON / CHASE / FREE / ORIGINAL

SEVERITY:
BLOCKER
MAJOR
MINOR
POLISH

ROOT CAUSE:
UNKNOWN until proven.

STATUS:
OPEN
SOURCE VERIFIED
FIX IMPLEMENTED
COMPILE VERIFIED
RUNTIME UNVERIFIED
USER RUNTIME VERIFIED

TODO:

[ ] use this list to drive late-stage compatibility work.

[ ] do not mark an entire skill/quest "FP compatible" after testing only one
    object.

============================================================
70 — DEFINITION OF "FIRST_PERSON CONTENT COMPATIBLE"
============================================================

A piece of content is considered FIRST_PERSON compatible only when the user
can reasonably perform its full normal gameplay flow without switching back
to ORIGINAL merely to work around the modern controls.

Acceptable:

A scripted cutscene temporarily owns camera/input where vanilla requires it.

Not acceptable:

"switch to ORIGINAL because this staircase/NPC/interface doesn't work."

Goal:

FIRST_PERSON should be a genuinely playable control mode across the game,
not just something usable while walking around the overworld.

============================================================
71 — FINAL PROJECT COMPLETION GATES
============================================================

Do NOT consider MODERN CONTROLS finished merely because the framework is
feature complete.

Final completion requires approximately:

GATE 1:
Core camera/movement stable.

GATE 2:
Interaction stable.

GATE 3:
Combat stable.

GATE 4:
Quickbar/action bar stable.

GATE 5:
HUD/settings polished.

GATE 6:
FP body/equipment presentation polished.

GATE 7:
Ceilings/indoor presentation stable.

GATE 8:
Major skills tested and playable.

GATE 9:
Representative quests tested.

GATE 10:
Major interfaces/minigames/transport tested.

GATE 11:
Content-specific compatibility bug list substantially resolved.

GATE 12:
Repeated ORIGINAL <-> MODERN switching does not damage vanilla gameplay.

Only after these phases should the project be considered broadly ready for
normal player use/community testing.

============================================================
72 — MODERN UI PROFILE / F11 COMPLETE UI SWITCH
============================================================

IMPLEMENTED IN SOURCE (17-08-2026) — runtime verification pending:
the modern HUD is present in FIRST_PERSON and CHASE. FREE deliberately restores
the normal vanilla layout; returning to ORIGINAL does the same. Interface state
remains untouched across every transition.

F11 must switch more than camera/control behaviour.

It also switches the COMPLETE active HUD/UI presentation.

ORIGINAL:

[ ] restore authentic/default 2009Scape HUD.

[ ] restore original side tabs.

[ ] restore original tab placement.

[ ] restore original chat/interface presentation.

[ ] restore original minimap presentation.

[ ] remove MODERN quickbar/action bar.

[ ] remove MODERN compass.

[ ] remove MODERN mode indicator.

[ ] remove MODERN target HUD.

[ ] remove MODERN crosshair interaction HUD.

[ ] remove MODERN minimap view cone.

[ ] remove other MODERN-only overlays.

MODERN:

[ ] replace permanently visible old side-tab layout with modern HUD.

[ ] show modern minimap.

[ ] show modern compass where appropriate.

[ ] show quick item bar.

[ ] show prayer/magic action bar.

[ ] show modern chat presentation.

[ ] show modern interaction/crosshair elements.

[ ] show modern mode indicator.

[ ] show target/combat information only when relevant.

IMPORTANT:

F11 should feel like switching between TWO COMPLETE PRESENTATION PROFILES.

F11 -> ORIGINAL:
"BAM" — original RuneScape UI/control presentation returns.

F11 -> MODERN:
"BAM" — modern FP/TP UI/control presentation returns.

No mixture of half-original/half-modern permanent HUD elements unless
intentionally required by an opened vanilla interface.

Switching profiles must NOT destroy interface state, inventory state,
chat state, settings, quickbar assignments, or action-bar assignments.

============================================================
73 — REMOVE PERMANENT VANILLA SIDE-TAB STRIP IN MODERN HUD
============================================================

The old permanent RuneScape tab strip should not remain constantly visible
in MODERN FIRST_PERSON / CHASE HUD.

Reason:

The MODERN HUD already has:

- item quickbar;
- prayer/magic action bar;
- modern minimap;
- modern status information;
- modern interaction controls.

Keeping the entire old side-tab bar permanently visible would make the HUD
cluttered and defeat the purpose of the redesign.

However:

NO vanilla tab functionality may be lost.

Every important original tab/interface must remain accessible through
modern UI entry points and configurable hotkeys.

============================================================
74 — MODERN DETACHED TAB / PANEL SYSTEM
============================================================

In MODERN mode, original RuneScape tabs should be able to open as individual
modern overlay panels rather than requiring the permanent old side-tab strip.

Examples:

Inventory
Equipment
Prayer
Magic
Skills
Quest list
Friends
Ignore
Clan/chat-related interfaces
Music
Settings
Emotes
other supported tabs.

Desired presentation:

[ ] open as standalone overlay/panel.

[ ] semi-transparent/translucent background where appropriate.

[ ] retain RuneScape visual identity.

[ ] preserve real interface content and functionality.

[ ] modern positioning suitable for FP/TP gameplay.

[ ] close without leaving MODERN mode.

[ ] opening a panel must not permanently unlock the cursor unless necessary.

[ ] interface ownership must cooperate with CTRL/free-mouse behaviour.

[ ] panels must remain functional at different resolutions/UI scales.

DO NOT replace real interfaces with fake simplified copies unless there is a
specific approved redesign.

============================================================
75 — INVENTORY HOTKEY / MODERN INVENTORY PANEL
============================================================

Suggested default:

I = Inventory.

Press I in MODERN mode:

-> open inventory as a standalone modern overlay panel.

Press I again:

-> close inventory.

Desired:

[ ] transparent/semi-transparent background.

[ ] real inventory slots.

[ ] real item sprites.

[ ] real item amounts.

[ ] real vanilla right-click actions.

[ ] Add to Quickbar / Remove from Quickbar integration.

[ ] assigned quickbar items visually marked subtly.

[ ] drag/drop behaviour remains functional.

[ ] item-on-item remains functional.

[ ] item selection remains functional.

[ ] closing inventory returns immediately to gameplay controls.

I is a suggested default only.

All UI hotkeys must eventually be remappable.

============================================================
76 — EQUIPMENT PANEL
============================================================

Equipment must remain conveniently accessible in MODERN mode.

TODO:

[ ] standalone modern equipment overlay.

[ ] real equipped items.

[ ] real equipment stats/interface behaviour.

[ ] configurable hotkey.

[ ] maintain RuneScape-style presentation.

[ ] work naturally with quickbar equipment switching.

============================================================
77 — PRAYER PANEL
============================================================

Prayer interface must remain directly accessible even though prayers may
also be assigned to Action Bar.

Action Bar is a shortcut.

It does NOT replace the Prayer interface.

TODO:

[ ] standalone Prayer panel.

[ ] real prayer icons.

[ ] real active/inactive states.

[ ] real requirements.

[ ] right-click prayer:
    Add to Action Bar
    or
    Remove from Action Bar.

[ ] configurable Prayer-panel hotkey.

============================================================
78 — MAGIC PANEL
============================================================

Magic interface remains available as full interface.

Action Bar is only a shortcut.

TODO:

[ ] standalone Magic panel.

[ ] real spell sprites.

[ ] real spell requirement states.

[ ] rune/level availability remains visible.

[ ] right-click spell:
    Add to Action Bar
    or
    Remove from Action Bar.

[ ] configurable Magic-panel hotkey.

============================================================
79 — ALL ORIGINAL TABS MUST REMAIN ACCESSIBLE
============================================================

Even if tabs are no longer permanently visible in MODERN HUD, ALL important
original functionality must remain reachable.

Audit current client tabs and make a complete list.

For each supported original tab:

[ ] provide a MODERN UI access route.

[ ] provide configurable hotkey where appropriate.

[ ] preserve original functionality.

[ ] preserve server/interface authority.

[ ] preserve relevant context-menu behaviour.

No tab should become inaccessible merely because the old tab strip is hidden.

============================================================
80 — FRIENDS / IGNORE / SOCIAL UI
============================================================

Friends and Ignore interfaces should be accessible without occupying a
permanent large HUD area.

Possible MODERN placement:

small social controls integrated around the chat area.

For example:

[Friends] [Ignore] [Clan/Social]

near the modern chat panel.

Selecting one:

-> opens corresponding standalone panel.

TODO:

[ ] Friends panel.

[ ] Ignore panel.

[ ] Clan/social panel where appropriate.

[ ] real names/status/actions.

[ ] right-click player/social actions preserved.

[ ] configurable hotkeys.

[ ] optional small unread/social indicator later.

Exact visual placement should be decided during HUD design.

============================================================
81 — MUSIC / AUDIO TAB MODERN ACCESS
============================================================

Music/audio interface does not need permanent prime HUD space.

Possible placement:

small music/audio icon near minimap or another low-priority HUD utility area.

Clicking it:

-> opens standalone Music/Audio panel.

TODO:

[ ] retain real music functionality.

[ ] configurable hotkey.

[ ] RuneScape-style icon/sprite.

[ ] unobtrusive placement.

Exact location may be refined during HUD design.

============================================================
82 — SKILLS / QUESTS / EMOTES / OTHER PANELS
============================================================

Create clean MODERN access to other original tab functionality.

Examples:

[ ] Skills.

[ ] Quest list.

[ ] Emotes.

[ ] Achievement/content panels if present.

[ ] other revision-specific interfaces.

Each:

[ ] accessible by UI icon and/or hotkey.

[ ] opens standalone panel.

[ ] closes cleanly back into MODERN gameplay.

[ ] retains authentic functionality.

============================================================
83 — FULL UI HOTKEY SYSTEM
============================================================

Every major MODERN panel should optionally have its own configurable hotkey.

Examples:

Inventory
Equipment
Prayer
Magic
Skills
Quests
Friends
Ignore
Clan
Music
Settings
Emotes
other tabs.

Suggested defaults may exist, but NONE should be permanently hard-coded.

Settings must support:

[ ] assign hotkey.

[ ] clear hotkey.

[ ] modifier keys.

[ ] detect conflicts.

[ ] warn about conflicting bindings.

[ ] restore defaults.

[ ] show current binding in relevant tooltip/UI where useful.

============================================================
84 — MODERN CHATBOX
============================================================

MODERN mode should use a cleaner semi-transparent chat presentation.

Desired:

[ ] transparent/translucent background.

[ ] readable RuneScape chat colours.

[ ] retain public/private/clan/system messages.

[ ] retain existing chat commands/functionality.

[ ] retain typing input.

[ ] clear indication when typing mode is active.

Possible idle hint:

"Press Enter to chat"

or equivalent.

TODO:

[ ] chat visible without dominating FP view.

[ ] typing state clearly visible.

[ ] chat keyboard ownership overrides gameplay hotkeys.

[ ] messages remain readable against bright/dark environments.

[ ] opacity configurable later.

============================================================
85 — RESIZABLE CHATBOX DURING GAMEPLAY
============================================================

The player should be able to resize the MODERN chatbox in-game.

TODO:

[ ] resize width.

[ ] resize height.

[ ] drag/reposition if approved during final HUD design.

[ ] minimum sensible size.

[ ] maximum sensible size.

[ ] preserve text clipping/wrapping correctly.

[ ] preserve scroll history.

[ ] save user's chosen size/position.

[ ] optionally allow collapsed/minimal chat state.

Potential future controls:

chat opacity
font scaling
message fade time
always-visible vs fade behaviour.

============================================================
86 — MODERN COMPASS
============================================================

The modern compass shown in the HUD concept is approved as a desirable
FIRST_PERSON feature.

Desired:

[ ] horizontal modern compass.

[ ] N / E / S / W.

[ ] intermediate directions where useful.

[ ] follows actual camera yaw.

[ ] smooth movement.

[ ] compact.

[ ] semi-transparent.

[ ] RuneScape-inspired visual style.

[ ] optional toggle in MODERN settings.

Potential modes:

FIRST_PERSON:
full compass.

CHASE:
full or compact compass.

FREE:
possibly hidden or adapted depending on final UX.

============================================================
87 — MINIMAP + VIEW CONE + COMPASS COHERENCE
============================================================

Minimap and compass must describe the same camera orientation correctly.

TODO:

[ ] camera yaw matches compass.

[ ] camera yaw matches minimap cone.

[ ] FOV matches cone width approximately.

[ ] transitions FP/CHASE/FREE update indicators cleanly.

[ ] no stale orientation after F11.

[ ] no mismatch after teleports/region changes.

============================================================
88 — MODERN TARGET / NPC HEALTH DISPLAY
============================================================

When the player actively attacks or targets an NPC, provide modern readable
target health feedback.

Approved visual direction:

a compact health/name display associated with the attacked NPC.

Potential presentation:

[ ] HP bar above/near NPC in world.

and/or

[ ] compact target information near centre HUD.

NPC world indicator may show:

NPC name
combat level where appropriate
health bar.

TODO:

[ ] only show when relevant.

[ ] attacked/actively targeted NPC.

[ ] follow moving NPC correctly.

[ ] scale/project correctly with distance.

[ ] disappear when target lost/dead.

[ ] preserve RuneScape hit splats.

[ ] do not replace server combat information.

[ ] do not clutter every NPC with permanent HP bars.

[ ] optionally support player targets later if appropriate.

Visual style:

modernised RuneScape,
not giant MMO floating nameplates.

============================================================
89 — MODERN SETTINGS ENTRY INSIDE ORIGINAL SETTINGS UI
============================================================

Add a proper MODERN SETTINGS entry/button to the existing RuneScape
Settings/Options interface.

IMPORTANT ART DIRECTION:

DO NOT make this a developer-looking text button.

DO NOT create generic rectangular hover boxes with plain text.

It must look like it genuinely belongs in the 2009Scape client.

Use:

[ ] proper sprites.

[ ] RuneScape-style button frame.

[ ] matching hover/pressed states.

[ ] matching fonts.

[ ] matching interface spacing.

[ ] real visual assets consistent with existing interface style.

Goal:

A player should believe "Modern Settings" was intentionally added to the
original client UI rather than injected as a debug plugin.

============================================================
90 — MODERN SETTINGS PANEL
============================================================

Opening Modern Settings provides configuration for the MODERN control/UI
system.

Eventually include categories such as:

CAMERA

[ ] FOV.

[ ] sensitivity.

[ ] invert Y if desired.

[ ] chase distance.

[ ] smoothing.

[ ] head bob.

[ ] zoom behaviour.

CONTROLS

[ ] movement bindings.

[ ] interaction key.

[ ] quickbar bindings.

[ ] Action Bar bindings.

[ ] UI panel bindings.

[ ] context-menu controls.

[ ] controller bindings later.

HUD

[ ] HUD scale.

[ ] crosshair scale.

[ ] crosshair opacity.

[ ] minimap view cone.

[ ] compass.

[ ] mode indicator.

[ ] quickbar visibility.

[ ] action-bar visibility.

[ ] target HUD.

[ ] damage-direction indicators.

CHAT

[ ] opacity.

[ ] size.

[ ] position if supported.

[ ] text scale if supported.

ACCESSIBILITY / OTHER

[ ] appropriate toggles discovered during testing.

============================================================
91 — MODERN SETTINGS VISUAL QUALITY
============================================================

Modern Settings itself must be a polished in-game interface.

DO NOT make a temporary debug/settings window the final feature.

Requirements:

[ ] RuneScape-style sprites.

[ ] proper buttons.

[ ] proper sliders.

[ ] proper checkboxes/toggles.

[ ] hover sprites.

[ ] pressed states.

[ ] tooltips.

[ ] category icons where appropriate.

[ ] consistent spacing.

[ ] scalable where practical.

The setting system may use modern functionality but should visually feel
native to the game.

============================================================
92 — UI LAYOUT EDIT / POSITIONING
============================================================

Consider allowing selected MODERN HUD elements to be repositioned.

Possible movable elements:

[ ] chat.

[ ] quickbar/action bar group.

[ ] target HUD.

[ ] mode indicator.

Potential later option:

"Edit HUD Layout"

TODO:

[ ] decide whether full HUD editing is worthwhile.

[ ] prevent elements from being moved off-screen permanently.

[ ] save layout.

[ ] reset layout to default.

This is optional polish, not required before core functionality.

============================================================
93 — HUD SCALE / RESOLUTION SUPPORT
============================================================

MODERN HUD must work beyond one exact 1920x1080 screenshot.

Test:

[ ] different resizable window sizes.

[ ] 1080p.

[ ] higher resolutions.

[ ] different aspect ratios where practical.

[ ] UI scaling.

[ ] minimap placement.

[ ] quickbar centering.

[ ] chat bounds.

[ ] compass centering.

[ ] detached panels.

Reference concept art is a VISUAL TARGET, not fixed pixel coordinates.

============================================================
94 — MODERN UI VISUAL REFERENCE
============================================================

The approved generated FP HUD concept should be stored in the repository as
visual design reference.

Suggested location:

design/
or
docs/design/

Example:

design/fp_hud_reference.png

IMPORTANT:

Reference image defines:

- visual hierarchy;
- general placement;
- intended cleanliness;
- overall modern RuneScape feeling.

It is NOT intended to be drawn as one giant bitmap over the game.

Final HUD must be composed from native components/assets.

Reuse REAL RuneScape sprites for:

[ ] inventory items.

[ ] prayers.

[ ] spells.

[ ] appropriate existing interface graphics.

Generate/create NEW assets only where genuinely required:

[ ] quickslot frames.

[ ] Action Bar frames.

[ ] mode icons.

[ ] crosshair assets.

[ ] damage-direction indicators.

[ ] target frames.

[ ] modern settings sprites.

[ ] other new HUD decorations.

============================================================
95 — F11 UI STATE TRANSITION TEST MATRIX
============================================================

Test repeatedly:

MODERN FP -> F11 -> ORIGINAL.

ORIGINAL -> F11 -> MODERN FP.

MODERN CHASE -> F11 -> ORIGINAL.

ORIGINAL -> F11 -> MODERN CHASE/restored modern state where appropriate.

Verify every time:

[ ] correct HUD appears.

[ ] incorrect HUD disappears.

[ ] cursor state correct.

[ ] keyboard ownership correct.

[ ] chat remains intact.

[ ] open interfaces resolve safely.

[ ] minimap correct.

[ ] camera correct.

[ ] quickbar assignments preserved.

[ ] action-bar assignments preserved.

[ ] settings preserved.

[ ] no duplicate overlays.

[ ] no original tab strip accidentally remaining under modern HUD.

[ ] no modern widgets remaining in ORIGINAL.

============================================================
96 — UI STATE PERSISTENCE
============================================================

MODERN personal configuration should persist appropriately.

Potential persistent state:

[ ] quickbar assignments.

[ ] Action Bar assignments.

[ ] hotkeys.

[ ] FOV.

[ ] sensitivity.

[ ] HUD scale.

[ ] chat size.

[ ] chat position if movable.

[ ] HUD element positions if supported.

[ ] visibility toggles.

[ ] controller mappings later.

Do NOT accidentally encode transient state such as current target as a
persistent setting.

============================================================
97 — ORIGINAL UI REMAINS THE FALLBACK / SAFETY BASELINE
============================================================

Even after MODERN UI becomes heavily redesigned:

ORIGINAL UI remains untouched/authentic.

This provides:

- compatibility;
- fallback;
- debugging comparison;
- nostalgia;
- safe access to every original feature.

Any MODERN tab/panel redesign must therefore remain isolated from ORIGINAL
presentation wherever possible.

============================================================
98 — FINAL MODERN HUD DESIGN PASS
============================================================

After functionality exists, do a dedicated POLISH pass.

Review:

[ ] spacing.

[ ] sprites.

[ ] fonts.

[ ] opacity.

[ ] borders.

[ ] alignment.

[ ] scale.

[ ] transitions.

[ ] hover feedback.

[ ] drag/drop feedback.

[ ] target feedback.

[ ] action-bar active states.

[ ] quickbar unavailable states.

[ ] combat readability.

[ ] indoor readability.

[ ] outdoor readability.

[ ] bright-area readability.

[ ] dark-area readability.

Goal:

The finished HUD should look intentional and coherent, not like several
independent features added by different development rounds.

============================================================
99 — FINAL KEYBOARD / MOUSE UX PASS
============================================================

Before controller work begins, keyboard/mouse MODERN controls must be
considered complete.

Verify:

[ ] WASD movement.

[ ] mouse look.

[ ] FP interaction.

[ ] context menu.

[ ] quickbar.

[ ] Action Bar.

[ ] inventory hotkey.

[ ] all major panel hotkeys.

[ ] dialogue numbers.

[ ] chat typing.

[ ] CTRL UI cursor.

[ ] camera wheel.

[ ] menu wheel.

[ ] combat targeting.

[ ] spell targeting.

[ ] prayer activation.

[ ] F11 profile switch.

[ ] no major input conflicts.

Only after keyboard/mouse passes this phase should controller work begin.

============================================================
100 — CONTROLLER SUPPORT — ABSOLUTE FINAL MAJOR FEATURE
============================================================

CONTROLLER SUPPORT IS THE LAST MAJOR FEATURE PHASE.

Do NOT begin controller implementation while keyboard/mouse interaction,
combat, HUD, interfaces or content compatibility are still unstable.

Reason:

Controller support should map onto a FINISHED interaction/UI architecture,
not force repeated rewrites as systems continue changing.

Goals:

A controller user should be able to play MODERN FIRST_PERSON / CHASE without
requiring mouse/keyboard for normal gameplay.

Potential defaults:

Left Stick:
movement.

Right Stick:
camera/look.

Face button:
primary interaction.

Other face buttons:
context/action functions.

Bumpers/triggers:
modifiers / combat / Action Bar access.

D-pad:
quickslots/action selection/interface navigation where appropriate.

Start/Menu:
settings/main interface access.

Exact layout must be designed later based on the completed MODERN control
system.

============================================================
101 — CONTROLLER FULL REMAPPING
============================================================

Controller inputs must be configurable in Modern Settings.

TODO:

[ ] remap movement where meaningful.

[ ] remap primary interaction.

[ ] remap context menu.

[ ] remap quickbar actions.

[ ] remap Action Bar actions.

[ ] remap combat actions.

[ ] remap interface shortcuts.

[ ] remap modifiers.

[ ] sensitivity.

[ ] invert look.

[ ] dead zones.

[ ] vibration settings if implemented.

[ ] restore controller defaults.

[ ] conflict handling.

Controller UI should display controller glyphs instead of keyboard prompts
when controller is active.

============================================================
102 — AUTOMATIC INPUT METHOD PRESENTATION
============================================================

Potential final polish:

When keyboard/mouse is being used:

E Open
1 Eat
etc.

When controller is being used:

[A] Open
appropriate controller glyphs.

TODO:

[ ] detect current active input method.

[ ] update HUD prompts.

[ ] update quickbar/action-bar binding labels.

[ ] do not rapidly flicker between modes from tiny incidental input.

============================================================
103 — CONTROLLER INTERFACE NAVIGATION
============================================================

Controller must eventually support:

[ ] inventory navigation.

[ ] equipment.

[ ] prayer.

[ ] magic.

[ ] skills.

[ ] quests.

[ ] friends.

[ ] ignore.

[ ] music.

[ ] settings.

[ ] context menus.

[ ] bank.

[ ] shops.

[ ] Grand Exchange.

[ ] dialogues.

[ ] Make-X interfaces.

[ ] other important content interfaces.

This is why controller support comes LAST:
the complete MODERN interface system must already exist first.

============================================================
104 — FINAL PROJECT END CONDITION
============================================================

Controller support is not the beginning of another redesign.

It is the LAST major compatibility layer added after:

- camera;
- movement;
- collision;
- interactions;
- targeting;
- combat;
- quickbar;
- Action Bar;
- HUD;
- modern tabs;
- settings;
- body/viewmodel;
- ceilings;
- content compatibility testing;
- keyboard/mouse polish;

are substantially complete.

After controller support:

[ ] controller content QA.

[ ] final regression testing.

[ ] final visual polish.

[ ] performance profiling.

[ ] community/beta testing preparation.

At that point the MODERN CONTROLS project approaches release-candidate state.
