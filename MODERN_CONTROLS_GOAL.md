Je werkt aan mijn lokale 2009Scape-project.

## Projectlocaties

Hoofdproject:

`E:\Dev\RSPS Project\2009scape`

De relevante client staat in:

`E:\Dev\RSPS Project\2009scape\RT4-client`

Controleer de daadwerkelijke directorystructuur eerst; ga niet blind uit van classnamen of paden.

Mijn oudere werkende first-person implementatie staat hier:

`E:\Dev\RS-Sandbox`

Gebruik `E:\Dev\RS-Sandbox` als referentie voor de reeds werkende first-person camera.

De camera werkte daar al goed. Analyseer en port zoveel mogelijk van die implementatie naar de huidige RT4-client in plaats van opnieuw een FPS-camera vanaf nul te ontwerpen.

---

# EINDDOEL

Ik wil uiteindelijk drie speelmodi:

1. **Original RuneScape**
2. **First Person**
3. **Third Person**

`F11` moet cyclisch toggelen:

```text
Original
   ↓ F11
First Person
   ↓ F11
Third Person
   ↓ F11
Original
```

Gebruik edge-triggering zodat F11 slechts één keer reageert per keypress.

Gebruik bij voorkeur:

```java
public enum CameraMode {
    ORIGINAL,
    FIRST_PERSON,
    THIRD_PERSON
}
```

Vermijd tegenstrijdige losse booleans.

---

# ARCHITECTUUR

First Person en Third Person moeten dezelfde moderne movement- en interaction-code gebruiken.

Bijvoorbeeld:

```text
ModernControlController
├── ModernMovementController
├── ModernInteractionController
├── ModernTargetingController
├── FirstPersonCamera
└── ThirdPersonCamera
```

Conceptueel:

```java
switch (cameraMode) {
    case ORIGINAL:
        runOriginalRuneScapeControls();
        break;

    case FIRST_PERSON:
        ModernControlController.update();
        FirstPersonCamera.update();
        break;

    case THIRD_PERSON:
        ModernControlController.update();
        ThirdPersonCamera.update();
        break;
}
```

First Person en Third Person verschillen voornamelijk qua camera/rendering.

Movement, targeting, interactions en combat-input moeten zoveel mogelijk gedeeld worden.

---

# ORIGINAL MODE MAG NIET KAPOT

Wanneer:

```text
CameraMode.ORIGINAL
```

actief is, moet zoveel mogelijk exact de originele RT4-code draaien.

Dan moeten blijven werken:

- originele camera
- originele mouse controls
- originele click-to-move
- originele `PathFinder`
- originele interactions
- originele contextmenus
- originele movement queues
- originele combat
- originele spell/item interactions

Modern controls mogen original mode niet onnodig beïnvloeden.

---

# FIRST-PERSON CAMERA

Gebruik eerst de bestaande camera uit:

`E:\Dev\RS-Sandbox`

Zoek daar naar:

- first-person flags
- camera position
- camera yaw
- camera pitch
- FOV
- mouse-look
- mouse capture
- cursor hiding
- terrain height
- player camera positioning
- eye/head height
- camera clipping
- key input
- oude toggle-code

Port deze code zorgvuldig naar de huidige RT4-client.

Niet blind complete classes overschrijven.

Vergelijk oude en nieuwe code eerst.

---

# THIRD-PERSON CAMERA

Wanneer first-person stabiel is, implementeer Third Person.

Third Person gebruikt dezelfde:

- WASD
- collision
- movement sync
- targeting
- interactions
- combat controls

De camera:

- volgt de speler
- draait met mouse-look
- gebruikt dezelfde kijkrichting/yaw
- staat achter en boven de speler
- houdt rekening met terrain
- mag niet door muren/objecten gaan
- schuift dichter naar de speler als iets achter hem de camera blokkeert

Implementeer camera collision.

Conceptueel:

```text
desired camera position
        ↓
collision test
        ↓
blocked?
 ┌──────┴──────┐
yes            no
↓               ↓
camera closer   desired position
```

---

# MODERN WASD MOVEMENT

First-person en third-person moeten soepel met WASD bewegen:

```text
W = forward
S = backward
A = strafe left
D = strafe right
```

Movement is relatief aan camera yaw.

Conceptueel:

```java
forwardX = sin(yaw);
forwardZ = cos(yaw);

rightX = cos(yaw);
rightZ = -sin(yaw);
```

Normalizeer gecombineerde input.

Dus:

```text
W+D
```

mag niet sneller zijn dan alleen:

```text
W
```

---

# FINE COORDINATES

Gebruik bestaande:

```java
xFine
zFine
```

voor vloeiende lokale movement.

Onderzoek exact hoe deze RT4-revision fine coordinates implementeert.

RuneScape gebruikt normaliter ongeveer:

```text
128 fine units = 1 tile
```

maar de bestaande source is leidend.

Movement moet visueel continuous zijn:

```text
3200.10
3200.17
3200.26
3200.35
...
```

en niet:

```text
tile center
→ tile center
→ tile center
```

---

# BESTAANDE MOVEMENT PIPELINE ONDERZOEKEN

Onderzoek minimaal:

- `PathingEntity`
- `Player`
- `PlayerList`
- `NpcList`
- `PathFinder`
- `CollisionMap`
- movement queues
- `xFine`
- `zFine`
- movement interpolation
- orientation
- walk animations
- run animations
- `ClientProt`
- movement packets

Zoek exact uit hoe:

```text
movementQueueX/Z
        ↓
movement target
        ↓
xFine/zFine
        ↓
orientation
        ↓
walk/run animation
```

werkt.

---

# COLLISION

Gebruik bestaande RuneScape collision-data.

Bouw geen los volledig physics-systeem.

Concept:

```text
desired movement
      ↓
RuneScape collision
      ↓
valid movement
      ↓
xFine/zFine
```

Ondersteun:

- blocked tiles
- walls
- solid objects
- diagonal clipping
- corners
- doorways
- map boundaries

---

# WALL SLIDING

Wanneer de speler schuin tegen een muur loopt moet hij, indien mogelijk, langs de muur kunnen bewegen.

Bijvoorbeeld:

```java
tryMoveX(dx);
tryMoveZ(dz);
```

of een betere oplossing passend bij deze engine.

Als X geblokkeerd is maar Z vrij:

```text
X = blokkeren
Z = toestaan
```

en omgekeerd.

---

# PLAYER COLLISION RADIUS

Gebruik niet puur één mathematisch punt voor collision.

Voorkom dat camera/speler half in:

- muren
- deuren
- objecten

terechtkomt.

Gebruik een kleine footprint/radius passend bij RuneScape.

Maak hiervoor geen onnodig complex physics-systeem.

---

# WALK/RUN SPEED

Behoud RuneScape movement speed.

Modern movement mag geen gameplay speedhack zijn.

Gebruik bestaande:

- walking
- running
- run toggle
- run energy indien relevant
- movement speed

en vertaal dit naar fine-coordinate displacement.

---

# ANIMATIONS

Gebruik originele animations:

```text
velocity = 0
→ idle

walking velocity
→ walk

running velocity
→ run
```

Reset dezelfde animation niet iedere frame.

Gebruik bestaande animation/state systemen.

---

# PLAYER ORIENTATION

Onderzoek bestaande:

- orientation
- angle
- yaw
- targetAngle

First Person:

de player orientation moet logisch overeenkomen met de kijk-/movementrichting waar gameplay dat nodig heeft.

Third Person:

de speler moet zichtbaar in de goede richting draaien.

Dit is ook belangrijk voor:

- melee
- ranged
- magic
- projectiles
- andere spelers die mijn character zien

---

# SERVER AUTHORITY BLIJFT BESTAAN

Maak movement niet volledig client-authoritative.

Doel:

```text
continuous client-side xFine/zFine
+
geldige tile transitions
+
bestaande server authority
```

Verander niet meteen het hele movementprotocol.

Onderzoek eerst bestaande movement packets.

---

# TILE TRANSITIONS

Detecteer wanneer fine movement een andere tile binnengaat.

Conceptueel:

```java
tileX = xFine >> 7;
tileZ = zFine >> 7;
```

maar gebruik bestaande helpers indien aanwezig.

Wanneer:

```text
oldTile != newTile
```

moet de transition:

1. collision-valid zijn
2. geregistreerd worden
3. correct richting server worden gesynchroniseerd

---

# SERVER RECONCILIATION

Voorkom constante snapping naar tile centers.

Doel:

```text
smooth local movement
+
server authoritative state
+
reconciliation
```

Kleine afwijkingen:

```text
smooth correction
```

Grote/ongeldige afwijkingen:

```text
authoritative correction
```

Geen permanente desync toestaan.

---

# MODERN TARGETING & INTERACTION SYSTEM

First-person en third-person moeten volledig speelbaar blijven zonder continu terug te moeten naar klassieke world-clicking.

Gebruik bijvoorbeeld:

```java
ModernTargetingController
ModernInteractionController
```

Deze systemen moeten uitsluitend bestaande RuneScape actions triggeren.

Geen nieuwe gameplaylogica verzinnen.

---

# CROSSHAIR / CENTER TARGETING

First Person en Third Person moeten een klein crosshair/reticle rond het midden van het viewport hebben.

Gebruik camera-/scene-data om te bepalen naar welke entity/world-object de speler kijkt.

Ondersteun:

- NPC's
- objects
- ground items
- eventueel players indien relevante bestaande actions bestaan

Gebruik zoveel mogelijk bestaande scene picking/menu construction.

Onderzoek hoe de RT4-client momenteel bepaalt wat zich onder de muiscursor bevindt en probeer die bestaande infrastructuur te hergebruiken.

---

# BELANGRIJK: GEEN VASTE 2-TILE TARGET CAP VOOR ALLES

Gebruik **NIET één universele maximale afstand van 2 tiles**.

De ongeveer 2-tile afstand is alleen bedoeld voor nearby/context interactions zoals:

- deur openen
- object gebruiken
- dichtbij een NPC praten
- ground item herkennen
- Search
- Climb
- Pick-up

Combat targeting moet andere regels hebben.

Maak onderscheid tussen:

```text
TARGET ACQUISITION
```

en:

```text
ACTION VALIDITY / RANGE
```

Deze twee mogen NIET hetzelfde zijn.

---

# NEARBY INTERACTION TARGETING

Voor gewone world interactions mag ongeveer 2 tiles als praktische acquisition distance worden gebruikt.

Voorbeelden:

```text
Door
> Open
  Examine
```

```text
Coins
> Take
  Examine
```

```text
Banker
> Bank
  Talk-to
  Examine
```

Maak de afstand centraal/configureerbaar.

Bijvoorbeeld conceptueel:

```java
MODERN_NEARBY_INTERACT_DISTANCE
```

Hardcode dezelfde afstand niet overal.

---

# DISTANCE TARGETING VOOR RANGED EN MAGIC

First Person en Third Person moeten NPC's op grotere afstand via het crosshair kunnen targeten.

Dit is essentieel voor:

- bows
- crossbows
- thrown weapons
- ranged weapons
- combat spells
- andere long-range combat actions

Een NPC op bijvoorbeeld 8 of 10 tiles afstand moet nog steeds als target geselecteerd kunnen worden wanneer hij duidelijk onder/in de buurt van het crosshair staat.

Dus:

```text
NPC op afstand
      ↓
crosshair target acquisition
      ↓
NPC wordt geselecteerd
      ↓
Attack / Cast action
      ↓
bestaande RuneScape gameplaycode
```

---

# TARGET ACQUISITION IS NIET HETZELFDE ALS ATTACK RANGE

Dit is een harde eis.

De nieuwe targeting-controller mag bepalen:

```text
"dit is de NPC waar de speler naar kijkt"
```

maar mag NIET zelf zomaar bepalen:

```text
"deze NPC mag geraakt worden"
```

Laat bestaande RuneScape code/server bepalen:

- attack range
- spell range
- line-of-sight
- projectile validity
- collision
- attack cooldown
- required movement
- target validity
- weapon rules
- spell rules

---

# GEEN HARDCODED BOW/SPELL RANGES ALS HET SPEL ZE AL KENT

Hardcode niet zomaar:

```text
bow = 7 tiles
magic = 10 tiles
crossbow = 8 tiles
```

als bestaande client/server/gamecode de relevante range al kent.

Onderzoek:

- combat range
- weapon properties
- spell definitions
- selected combat style
- projectile logic
- interaction routing
- LOS
- NPC attack handling

Gebruik bestaande mechanics als source of truth.

---

# MODERN COMBAT TARGET DISTANCE

Maak crosshair acquisition voor combat ruimer dan normale world interactions.

Bijvoorbeeld conceptueel:

```java
MODERN_NEARBY_INTERACT_DISTANCE
MODERN_COMBAT_TARGET_DISTANCE
```

Maar de acquisition distance mag alleen bepalen welke entity de speler kan selecteren.

Hij mag de daadwerkelijke combat range niet vergroten.

---

# TARGET PRIORITY OP AFSTAND

Targetselectie moet voornamelijk kijken naar waar de speler daadwerkelijk op mikt.

Gebruik bijvoorbeeld een score gebaseerd op:

1. binnen viewport
2. afstand tot center/crosshair
3. correcte plane
4. zichtbaarheid indien detecteerbaar
5. angular deviation vanaf camera-forward
6. huidige target hysteresis
7. world distance als secundaire factor

Belangrijk:

Een NPC op 10 tiles die vrijwel exact onder het crosshair staat mag winnen van een NPC op 3 tiles die duidelijk naast het crosshair staat.

Anders voelt ranged targeting slecht.

---

# CROSSHAIR TARGET CONE / RAY

Gebruik indien technisch haalbaar een:

```text
camera ray
```

of kleine:

```text
targeting cone
```

vanaf de camera.

Niet alleen 2D screen distance als een betere scene-space oplossing mogelijk is.

Voorbeeld:

```text
camera -------> Goblin

                  X
               crosshair
```

Het doel is targetselectie.

Dit is GEEN hitscan systeem.

---

# GEEN HITSCAN COMBAT

Crosshair aiming mag absoluut niet betekenen:

```text
crosshair op NPC
→ directe damage
```

Het moet zijn:

```text
crosshair op NPC
→ RuneScape entity target geselecteerd
→ bestaande Attack/Cast action
→ bestaande server combat
```

Projectiles blijven echte RuneScape projectiles.

Accuracy/damage/timing blijven origineel.

---

# RANGED ATTACK

Voorbeeld:

```text
bow equipped
↓
Goblin op 8 tiles onder crosshair
↓
Goblin wordt target
↓
Attack geselecteerd
↓
E / left click
↓
bestaande NPC Attack action
↓
server/client combatcode bepaalt range en verdere afhandeling
```

Als target buiten daadwerkelijke range is, behoud bestaand gedrag.

Indien RuneScape normaal dichterbij loopt:

behoud dat waar mogelijk.

Geen teleport of ranged-range bypass toevoegen.

---

# MAGIC TARGETING

Magic moet expliciet goed ondersteund worden.

Voorbeeld:

```text
Fire Strike geselecteerd
↓
crosshair op Goblin op afstand
↓
UI toont:

Goblin
> Cast Fire Strike
  Attack
  Talk-to
  Examine
```

E of left click op:

```text
Cast Fire Strike
```

moet exact dezelfde bestaande spell-on-NPC action triggeren als in classic RuneScape.

De bestaande game/server bepaalt:

- spell range
- runes
- required magic level
- cooldown
- autocast/state
- line-of-sight
- target validity
- splash/hit
- damage
- projectile
- XP

---

# SPELL SELECTED STATE

Onderzoek bestaande state voor:

```text
selected spell
```

Wanneer een combat spell geselecteerd is, moet `ModernInteractionController` de bestaande spell interaction kunnen aanbieden.

Voorbeeld:

```text
selected spell = Fire Bolt
target = Guard
```

dan kan het menu worden:

```text
Guard

> Cast Fire Bolt
  Attack
  Talk-to
  Examine
```

Gebruik bestaande action-opbouw waar mogelijk.

---

# AUTOCAST

Breek bestaande autocast-functionaliteit niet.

Als een staff/combat setup bestaande autocast gebruikt, moet dit via normale RuneScape combat blijven functioneren.

Modern targeting verandert alleen hoe een target gekozen/attacked wordt.

Autocast zelf niet opnieuw implementeren tenzij strikt noodzakelijk.

---

# LINE OF SIGHT

Een target dat zichtbaar op het scherm staat is niet automatisch geldig om aan te vallen.

Gebruik bestaande RuneScape LOS/collision checks.

Bijvoorbeeld:

```text
player
████ wall ████
             NPC
```

mag niet via de nieuwe targetingcode een bestaande serverregel omzeilen.

Je mag de NPC eventueel als scene-target herkennen als dat technisch logisch is, maar de daadwerkelijke action validity blijft bestaand gedrag.

Indien gemakkelijk detecteerbaar kan de UI ook aangeven dat target niet direct bereikbaar/attackable is, maar dit is polish en geen vereiste voor de eerste versie.

---

# TARGET LOCK / HYSTERESIS

Voorkom dat het target iedere frame wisselt.

Gebruik een kleine hysteresis.

Bijvoorbeeld:

- huidig target behouden zolang het nog redelijk dichtbij crosshair staat
- alleen wisselen wanneer een ander target duidelijk beter gecentreerd is
- verwijderen wanneer target uit viewport/range/scene verdwijnt
- verwijderen bij death/despawn/region change

Dit is extra belangrijk bij enemies die vlak naast elkaar staan.

---

# CONTEXT ACTION MENU

Wanneer er een target is, toon klein menu vlak bij het crosshair.

NPC:

```text
Goblin - level 5

> Attack
  Talk-to
  Examine
```

Object:

```text
Door

> Open
  Examine
```

Ground item:

```text
Rune arrow

> Take
  Examine
```

Magic:

```text
Goblin - level 5

> Cast Fire Strike
  Attack
  Talk-to
  Examine
```

Gebruik bestaande entity/object/item actions.

Niet hardcoden per NPC/object tenzij absoluut noodzakelijk.

---

# SCROLL WHEEL

Gebruik scroll wheel om action te selecteren:

```text
> Attack
  Talk-to
  Examine
```

scroll:

```text
  Attack
> Talk-to
  Examine
```

---

# E = UITVOEREN

`E` voert de geselecteerde action uit.

Gebruik GEEN standaard double-E systeem voor action 2.

Dus liever:

```text
scroll = selecteer
E = execute
```

dan:

```text
E = option 1
double E = option 2
```

---

# LEFT CLICK

In First/Third Person mag left click eveneens de geselecteerde crosshair action uitvoeren.

Voorbeeld:

```text
Goblin target
Attack geselecteerd
left click
→ bestaande Attack action
```

Geen nieuwe combatlogica toevoegen.

---

# COMBAT BLIJFT ORIGINEEL

Verander NIET:

- hit chance
- damage
- attack speed
- weapon speed
- combat tick rate
- XP
- projectiles
- magic formulas
- ranged formulas
- special attack mechanics
- NPC combat AI
- drops
- death
- protection prayers
- defence calculations

Modern controls veranderen alleen hoe bestaande actions worden geselecteerd.

---

# BEWEGEN TIJDENS COMBAT

Tijdens combat moet ik in First Person en Third Person WASD kunnen blijven gebruiken zolang normale RuneScape/serverregels movement toestaan.

Bijvoorbeeld:

```text
Attack NPC
+
WASD strafe/backward/forward
```

moet mogelijk zijn.

Dit mag combat niet client-side maken.

De server blijft bepalen of:

- attack doorgaat
- target binnen range is
- player opnieuw moet pathen
- cooldown actief is
- target verloren gaat
- movement geldig is

---

# MELEE VS RANGED/MAGIC

Targeting mag voor alle drie werken, maar gameplay-range blijft verschillend door bestaande RuneScape-logica.

### Melee

Ik mag een verder gelegen NPC eventueel selecteren, maar bestaande combat/pathing moet hem dichterbij brengen wanneer nodig.

### Ranged

Ik kan targets op afstand crosshair-targeten.

### Magic

Ik kan targets op afstand crosshair-targeten en selected spells gebruiken.

De controller hoeft zelf geen eigen combat-range tabel te onderhouden als het bestaande systeem dit al doet.

---

# INTERACTION PATHING / MOVEMENT ARBITRATION

Onderzoek hoe bestaande interactions automatisch pathing starten.

Een probleem kan zijn:

```text
Attack target
→ bestaande RuneScape path movement

tegelijk:

WASD
→ ModernMovementController
```

Ontwerp hiervoor expliciete arbitration.

Bijvoorbeeld:

```text
manual WASD input
→ manual movement priority

geen manual input
→ interaction auto-pathing mag doorlopen
```

of een oplossing die beter bij de engine past.

Doel:

- geen movement tug-of-war
- geen queue corruption
- geen rubberband-loop

---

# GROUND ITEMS

Wanneer ik naar een ground item dichtbij kijk:

```text
Coins

> Take
  Examine
```

E op `Take` moet dezelfde bestaande pickup action triggeren.

Geen client-side inventory update.

Server blijft authoritative.

Ground items hoeven niet van tientallen tiles afstand selecteerbaar te zijn.

Gebruik hiervoor nearby interaction distance.

---

# NPC INTERACTIONS

Ondersteun bestaande NPC options:

- Talk-to
- Attack
- Trade
- Pickpocket
- Bank
- Exchange
- quest actions
- Examine

Attack mag long-range target acquisition gebruiken.

Andere opties mogen dichterbij/contextueel blijven.

Gebruik bestaande action arrays.

---

# OBJECT INTERACTIONS

Ondersteun bestaande object options:

- Open
- Close
- Climb
- Search
- Mine
- Chop-down
- Fish
- Use
- Enter
- Exit
- Bank
- Examine

Gebruik normaliter nearby acquisition.

Geen deuren vanaf 15 tiles afstand laten bedienen omdat ze toevallig onder het crosshair staan.

---

# ITEM-ON-TARGET EN SPELL-ON-TARGET

Behoud states zoals:

```text
Use item -> NPC
Use item -> object
Cast spell -> NPC
Cast spell -> object
```

Voorbeeld:

```text
Rope selected
↓
crosshair op object
↓
Use Rope -> object
```

Magic combat spells moeten wel long-range NPC targeting ondersteunen waar bestaande mechanics dat toelaten.

---

# FIRST-PERSON EQUIPMENT / VIEWMODEL

In First Person wil ik zichtbare equipment.

Voorbeelden:

- sword
- shield
- defender
- staff
- wand
- bow
- crossbow
- axe
- pickaxe
- two-handed weapons
- hands/gloves

De first-person presentatie moet laten zien wat het character doet.

---

# GEBRUIK ECHTE EQUIPMENT

Gebruik indien technisch haalbaar echte:

- equipment IDs
- item models
- player appearance
- animation state

Geen generieke fake sword gebruiken.

Voorbeeld:

```text
Dragon scimitar equipped
+
Rune defender
```

moet zoveel mogelijk overeenkomen met echte equipment.

---

# VIEWMODEL RENDERING

Onderzoek twee opties:

### Optie A

Render relevante delen van bestaand player model vanuit first person.

### Optie B

Aparte first-person viewmodel renderpass met dezelfde:

- equipment IDs
- item models
- animation state

Kies wat technisch het minst fragiel is.

---

# BODY CLIPPING

Voorkom dat ik vanuit First Person de binnenkant zie van:

- hoofd
- helm
- torso
- cape
- face

Verberg/exclude alleen de onderdelen die voor de lokale first-person camera problemen geven.

Andere spelers moeten mijn volledige character normaal blijven zien.

Third Person toont mijn volledige player model.

---

# FIRST-PERSON COMBAT ANIMATIONS

Weapon/equipment moet bestaande attack animations reflecteren.

Voorbeelden:

```text
melee attack
→ sword swing
```

```text
bow attack
→ bow draw/fire
```

```text
crossbow
→ fire animation
```

```text
magic
→ staff/hand casting animation
```

Gebruik bestaande RuneScape animation state.

Gameplay bepaalt animation timing, niet andersom.

---

# PROJECTILES

Bij ranged/magic blijven projectiles volledig onderdeel van bestaande RuneScape gameplay.

Crosshair is alleen target-selection.

Dus:

```text
aim
→ select target
→ Attack/Cast
→ bestaande projectile
```

Geen FPS projectile physics implementeren.

---

# CAMERA BOB / WEAPON SWAY

Pas toevoegen nadat alles stabiel is.

Camera bob:

- subtiel
- alleen bij movement
- makkelijk uit te zetten

Weapon sway:

- subtiel
- alleen visuele polish

Lage prioriteit.

---

# CAMERA COLLISION THIRD PERSON

Camera mag niet door normale walls/objects bewegen.

Test expliciet:

- smalle kamers
- gebouwen
- doorways
- caves
- upstairs
- downstairs
- bridges
- terrain slopes

Wanneer obstruction verdwijnt moet camera soepel terug naar normale afstand.

---

# PLANE / HEIGHT

First en Third Person moeten omgaan met:

- hills
- slopes
- stairs
- ladders
- plane changes
- bridges
- underground
- region changes

Gebruik bestaande terrain height systemen.

---

# TELEPORTS / REGION LOADING

Bij:

- teleport
- death
- respawn
- login
- logout
- region rebuild
- plane change

reset indien nodig:

- movement velocity
- target lock
- interaction menu
- camera interpolation
- stale movement state

Geen momentum meenemen door teleport.

---

# FORCED MOVEMENT

Onderzoek bestaande forced movement.

Bijvoorbeeld:

- agility
- scripted movement
- cutscene
- knockback
- special event

Modern WASD mag forced movement niet overschrijven.

---

# GAMEPLAY LOCK STATES

Respecteer bestaande locks:

- dialog
- cutscene
- stun
- teleport
- scripted sequence
- movement lock

Modern movement mag server/game restrictions niet omzeilen.

---

# INPUT VS UI

WASD/E/click mogen geen world actions uitvoeren wanneer speler bijvoorbeeld:

- chat typt
- bank search gebruikt
- login typt
- amount dialog gebruikt
- tekstveld gebruikt

Maak een centrale:

```java
isGameplayInputAllowed()
```

of vergelijkbare check.

---

# CURSOR CAPTURE

First/Third Person gebruiken mouse-look.

Zorg dat UI bruikbaar blijft.

Bijvoorbeeld:

```text
ESC
→ release cursor
```

en eventueel:

```text
click viewport
→ capture cursor
```

maar integreer dit goed met bestaande UI.

Interfaces zoals:

- inventory
- bank
- spellbook
- prayer
- equipment
- shops
- dialogs

moeten bruikbaar blijven.

---

# TARGETING TIJDENS UI GEBRUIK

Wanneer cursor vrij is voor UI:

- geen onbedoelde crosshair attack
- geen E world-interaction wanneer tekstinput actief is
- camera movement waar nodig tijdelijk stoppen

Zodra terug in captured gameplay:

- targeting hervatten

---

# DEBUG OVERLAY

Voeg optionele debug info toe:

```text
Mode: FIRST_PERSON
Fine: 409664, 410112
Tile: 3200, 3204
Velocity: 1.8, -0.4
Yaw: 1352

Target:
Goblin [id]
Distance: 8.3 tiles
Crosshair score: ...
Selected action: Attack

Acquisition type:
COMBAT_LONG_RANGE

Collision X: OK
Collision Z: BLOCKED

Server tile: ...
Client tile: ...
```

Voor magic eventueel:

```text
Selected spell: Fire Strike
```

Geen console spam.

---

# INPUT CONFIG CENTRALISEREN

Bijvoorbeeld:

```text
F11 = Original → First → Third
WASD = movement
E = selected interaction
Mouse wheel = action selection
Left click = execute selected world action
Esc = release/cancel afhankelijk van context
```

Geen keycodes verspreiden over tientallen classes.

---

# INPUT PRIORITY

Gebruik duidelijke prioriteit:

```text
1. Modal/text input
2. RuneScape UI
3. Modern interaction
4. Camera input
5. Movement
```

of technisch passend equivalent.

Eén toets/klik mag niet onbedoeld meerdere systemen tegelijk triggeren.

---

# GEEN GAMEPLAY CHEATS

Modern controls mogen NIET:

- collision negeren
- sneller lopen
- attack range verhogen
- spell range verhogen
- door muren aanvallen
- items van ongeldige afstand oppakken
- attack cooldown negeren
- server validation omzeilen
- damage beïnvloeden
- line-of-sight omzeilen

Het doel is moderne besturing, niet veranderde gameplayregels.

---

# IMPLEMENTATIEVOLGORDE

## Phase 0 — Analyse

Inspecteer:

`E:\Dev\RSPS Project\2009scape`

en:

`E:\Dev\RS-Sandbox`

Vind eerst:

- huidige camera
- oude FPS camera
- keyboard/mouse input
- movement pipeline
- collision
- player rendering
- equipment rendering
- scene picking
- menu construction
- action execution
- NPC actions
- object actions
- ground item actions
- selected spell/item state
- combat targeting
- ranged combat
- magic combat
- LOS
- projectiles
- movement networking

Geef daarna kort aan welke concrete classes/methodes gewijzigd moeten worden.

Begin daarna met implementatie.

---

## Phase 1 — Camera mode framework

Maak:

```text
ORIGINAL
FIRST_PERSON
THIRD_PERSON
```

met F11 cycling.

Original mode intact houden.

---

## Phase 2 — FPS camera port

Port bestaande FPS camera uit:

`E:\Dev\RS-Sandbox`

Build/test.

---

## Phase 3 — WASD movement

Implementeer smooth local fine-coordinate WASD movement.

Build/test.

---

## Phase 4 — Collision

Koppel CollisionMap.

Test:

- walls
- corners
- diagonals
- objects
- doors

---

## Phase 5 — Animation/orientation

Koppel:

- idle
- walk
- run
- player orientation

---

## Phase 6 — Scene/crosshair targeting

Implementeer target acquisition.

Maak vanaf het begin onderscheid tussen:

```text
NEARBY INTERACTION TARGET
```

en:

```text
LONG-RANGE COMBAT TARGET
```

Geen 2-tile cap toepassen op ranged/magic combat.

---

## Phase 7 — Context action UI

Implementeer:

```text
scroll → action selecteren
E → action
left click → action
```

via bestaande RuneScape menu/action handlers.

---

## Phase 8 — Melee combat

Crosshair `Attack` moet bestaande melee combat triggeren.

Geen combat mechanics wijzigen.

---

## Phase 9 — Ranged combat targeting

Test:

- bow
- crossbow
- verschillende afstanden
- NPC dicht bij crosshair
- meerdere NPC's
- target achter obstruction
- target buiten daadwerkelijke attack range

Crosshair acquisition moet verder reiken dan nearby interactions.

Bestaande RuneScape range blijft leidend.

---

## Phase 10 — Magic targeting

Test:

- selected combat spell
- spell-on-NPC
- NPC op afstand
- LOS
- insufficient runes
- target buiten range
- projectile
- autocast indien relevant

Gebruik originele spell actions.

---

## Phase 11 — Movement/combat arbitration

Zorg dat WASD en bestaande interaction/pathing elkaar niet constant bestrijden.

Test zowel:

- melee
- ranged
- magic

tijdens manual movement.

---

## Phase 12 — Server sync

Implementeer veilige tile-transition/network-sync en reconciliation.

---

## Phase 13 — First-person equipment

Render correcte equipment/viewmodel.

Test:

- sword
- shield/defender
- bow
- crossbow
- staff
- magic cast
- melee swing
- ranged animation

---

## Phase 14 — Third Person

Voeg third-person camera toe boven dezelfde controllers.

Inclusief camera collision.

---

## Phase 15 — Polish

Pas daarna:

- smoothing
- head bob
- weapon sway
- shoulder offset
- interaction UI polish

---

# BUILD NA ELKE FASE

Na iedere grote fase:

1. build/compile
2. fix compiler errors
3. test Original mode
4. test First Person indien relevant
5. test Third Person indien relevant
6. laat geen half geïmplementeerde protocol hacks achter

---

# GEEN MASSALE REFACTOR

Geen volledige client herschrijven.

Geen enorme formatting sweep.

Geen honderden ongerelateerde wijzigingen.

Isoleer nieuwe modern-control code zoveel mogelijk.

---

# ACCEPTANCE TESTS

## Mode switching

```text
Original
F11
First Person
F11
Third Person
F11
Original
```

werkt zonder teleport/position reset.

## Movement

- WASD smooth
- collision werkt
- diagonal speed correct
- run/walk correct
- animations correct
- server state coherent

## Nearby interaction

Kijk naar deur dichtbij:

```text
> Open
  Examine
```

E werkt.

Kijk naar item dichtbij:

```text
> Take
  Examine
```

E werkt.

## Melee

Kijk naar NPC:

```text
> Attack
```

Attack gebruikt bestaande combat.

## Ranged

NPC op meerdere tiles afstand:

```text
crosshair
→ target acquisition
→ Attack
→ bestaande ranged combat
```

werkt.

Geen 2-tile acquisition beperking.

Range wordt niet door modern controller gefaket.

## Magic

Combat spell geselecteerd.

NPC op afstand targeten:

```text
Goblin

> Cast Fire Strike
  Attack
  Talk-to
  Examine
```

E/click gebruikt originele spell-on-NPC action.

## LOS

NPC achter wall mag niet geraakt worden doordat modern targeting server/game checks omzeilt.

## Movement during combat

Tijdens melee/ranged/magic kan manual WASD blijven functioneren voor zover bestaande game/serverregels dit toelaten.

## Equipment

In First Person zijn relevante weapon/shield/staff/bow animations zichtbaar.

## Third Person

Full player zichtbaar.

Camera volgt speler en clip niet simpelweg door walls.

## UI

Chatten met letter E mag geen interaction uitvoeren.

Bank/inventory/spellbook/dialogs blijven bruikbaar.

## Classic fallback

Terug naar Original geeft weer:

- originele camera
- click-to-move
- originele menu's
- originele combat
- originele spell/item interactions

---

# BELANGRIJKSTE REGEL

Bouw geen nieuwe combat-engine.

Modern controls moeten uitsluitend bepalen:

```text
waar kijk ik naar?
welke bestaande action wil ik uitvoeren?
hoe beweeg ik lokaal vloeiend?
```

RuneScape blijft bepalen:

```text
kan de actie?
ben ik binnen range?
is er line-of-sight?
moet ik dichterbij lopen?
wanneer valt mijn weapon aan?
raakt de aanval?
hoeveel damage?
welk projectile?
welke animation?
```

Dus:

**First Person / Third Person leveren moderne camera, movement en targeting bovenop de bestaande 2009Scape gameplay — zonder ranged, magic, melee of andere serverregels opnieuw uit te vinden.**

# IMPORTANT: LEGACY FIRSTPERSONCAMERA REFERENCE

The older implementation in:

E:\Dev\RS-Sandbox

contains a working FirstPersonCamera implementation.

Use its camera implementation as an important reference, especially for:

- Camera.renderX / renderZ
- Camera.anInt40
- cameraYaw / cameraPitch
- mouse locking and cursor recentering
- FOV handling
- pitch handling
- terrain-relative eye height
- scene rebuild handling
- F11 input handling

HOWEVER:

Do NOT blindly port the old movement architecture.

The old FirstPersonCamera mixes camera, WASD movement, client prediction and
network movement inside one class.

In particular, the old implementation moves fpCamX/fpCamZ independently and
then sends MOVE_GAMECLICK packets when the camera crosses tile boundaries.

This is reference/prototype code, NOT the desired final movement architecture.

The new implementation must separate:

FirstPersonCamera
ModernMovementController
ModernInteractionController
ModernTargetingController

The ModernMovementController should own the smooth/predicted player movement
position.

The FirstPersonCamera should FOLLOW that position instead of acting as a
free-flying movement authority.

Also inspect the old canMoveTile() implementation, but do not blindly trust its
hardcoded collision masks. Verify all collision masks against the current
RT4 PathFinder/CollisionMap implementation.

The old sendPlayerStep() and sendPredictedTile() functions represent previous
movement experiments. Study them, but first inspect the current client/server
movement pipeline and choose the safest integration with existing movement
queues and packets.

---

# PHASE 3C — MODERN CAMERA CONTINUUM (implemented)

## Control Profile vs Camera Rig

```
CONTROL PROFILE (F11 toggle):
    ORIGINAL — pure vanilla 2009Scape (click-to-move, legacy camera, scroll zoom)
    MODERN   — WASD + modern camera rig

CAMERA RIG inside MODERN only:
    FIRST_PERSON  ← scroll →  CHASE  ← scroll →  FREE
```

F11 toggles ORIGINAL ↔ MODERN. Scrolling only changes the camera rig INSIDE
MODERN. Scrolling NEVER switches control profile.

CameraMode enum: ORIGINAL and THIRD_PERSON are the active profiles.
FIRST_PERSON is a legacy enum value; the rig manages FP state internally.
ModernCameraRig (FP / CHASE / FREE) controls CAMERA inside MODERN.

## ORIGINAL Must Remain Vanilla

A player who never presses F11 gets the complete vanilla 2009Scape experience:
- Original click-to-move, PathFinder, minimap movement
- Original camera, scroll-wheel zoom, middle-mouse pan
- Original character orientation and animations
- No modern camera rig behavior, no FP/CHASE/FREE transitions

## MODERN FREE ≠ ORIGINAL

When MODERN is active and the user scrolls fully outward, the camera becomes
classic/free-camera-like. BUT:
- WASD remains active
- Shift run remains active
- Modern locomotion and packets remain active
- ORIGINAL is NOT activated by scrolling

MODERN FREE is a CAMERA RIG state. ORIGINAL is a CONTROL PROFILE.

## Scroll Zoom Continuum (MODERN only)

```
SCROLL IN:
    FREE → far chase → normal chase → close chase → FIRST_PERSON → clamp

SCROLL OUT:
    FIRST_PERSON → close chase → normal chase → far chase → FREE → max clamp
```

No F11 needed for camera rig transitions. F11 only toggles control profile.

## Desired vs Actual Distance

- `desiredDistance`: user's scroll wheel intent (never destroyed by walls)
- `actualDistance`: smoothly approaches desired; compressed by obstruction
- Wall compression: actual < desired when geometry blocks
- Wall removal: actual smoothly returns to desired

## Hysteresis Thresholds

- FP_ENTER_DISTANCE = 120 (CHASE → FP at ≤ 120)
- FP_EXIT_DISTANCE = 200 (FP → CHASE at ≥ 200, > FP_ENTER)
- FREE_ENTER_DISTANCE = 4200 (CHASE → FREE at ≥ 4200)
- FREE_EXIT_DISTANCE = 3800 (FREE → CHASE at ≤ 3800, < FREE_ENTER)

## Chase Camera

- Camera follows character body orientation (anInt3400)
- Camera yaw smoothly interpolates toward body yaw (shortest-angle path)
- Camera position: pivot above player + offset behind at actualDistance
- Camera pitch: ~45° downward (256 units)
- NO feedback loop: camera does NOT drive movement direction

## Camera Obstruction

- Multi-sample line probe from pivot to desired camera position
- Checks collision flags (PathFinder.collisionMaps) and terrain height
- Compresses actual distance when blocked
- desiredDistance preserved throughout

## Body-Look Coupling (FP mode)

- Character body follows camera look direction with shoulder dead-zone
- SHOULDER_DEAD_ZONE = 100 units (~17°): body stable, head/look only
- SHOULDER_LIMIT = 200 units (~34°): beyond this, faster catch-up
- RT4 has NO separate head yaw; body-yaw follow only
- Head-look coupling deferred (not supported by RT4 model system)

## Wheel Input Path

```
JavaMouseWheel.mouseWheelMoved() → currentRotation accumulated
client.java:1725-1726 → MouseWheel.wheelRotation = getRotation()
InterfaceList → UI scroll (reads but does NOT reset wheelRotation)
ModernCameraRig → camera zoom (reads wheelRotation after UI consumers)
```

Why wheel did nothing before: the default follow camera (method4273) has NO
zoom/distance parameter. Camera.ZOOM exists but is only consumed through CS2
scripts (cutscenes), never from mouse wheel for the default camera.

## ORIGINAL Isolation

ORIGINAL mode: zero changes. Legacy camera, click-to-move, scroll zoom via
Ctrl+Shift+wheel, middle-mouse pan, legacy animations all work unchanged.
The modern camera rig is completely inactive in ORIGINAL.

## Camera State Preservation

When entering MODERN (F11): full legacy camera state is saved
(pitch, yaw, position, cameraType). When returning to ORIGINAL (F11):
saved state is restored so the vanilla camera returns exactly where
it was before MODERN was entered.