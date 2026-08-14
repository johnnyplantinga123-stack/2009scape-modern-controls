# 2009SCAPE MODERN CONTROLS — MASTER GOAL & ROADMAP

Dit document is de architecturale source-of-truth voor het modern-controls
project bovenop 2009Scape / RT4.

Doel:

Een volledig speelbare moderne First-Person / Third-Person ervaring bouwen
BOVENOP de bestaande RuneScape gameplay, zonder de originele RuneScape-client,
server authority, combatregels of legacy speelstijl kapot te maken.

De bestaande RuneScape gameplay blijft de gameplay-engine.

Modern Controls veranderen hoofdzakelijk:

- camera
- locomotion input
- lokale movement prediction
- targeting
- action selection
- presentation

Niet:

- combatregels
- damage
- attack speed
- spellregels
- itemregels
- server authority
- RuneScape collision semantics

---

# 1. PROJECTLOCATIES

Hoofdrepository:

E:\Dev\RSPS Project\2009scape

Verwachte client:

E:\Dev\RSPS Project\2009scape\rt4-client

Server:

E:\Dev\RSPS Project\2009scape\2009scape

Client Gradle wrapper:

E:\Dev\RSPS Project\2009scape\rt4-client\gradlew.bat

Server launcher:

E:\Dev\RSPS Project\2009scape\2009scape\run-server.bat

Oud werkend FPS-prototype:

E:\Dev\RS-Sandbox

BELANGRIJK:

Controleer altijd de werkelijke directorystructuur voordat classes/paden
worden aangenomen.

Gebruik E:\Dev\RS-Sandbox als historische referentie voor:

- first-person camera
- camera position
- mouse look
- FOV
- cursor capture
- eye height
- terrain handling
- scene rebuilds

Maar PORT NOOIT blind de oude movementarchitectuur.

Het oude prototype combineerde camera, prediction en movement networking
te sterk in één class.

De nieuwe architectuur houdt die verantwoordelijkheden gescheiden.

---

# 2. GIT / WORKFLOW

Repository:

https://github.com/johnnyplantinga123-stack/2009scape-modern-controls

Hoofdbranch:

main

Werkwijze per fase:

1. Source inspecteren.
2. Architectuur/plan maken.
3. Plan reviewen.
4. Kleine gerichte implementatie.
5. Compile/build.
6. Runtime testen.
7. Diff/code review.
8. Pas daarna commit/push.
9. Volgende fase begint alleen vanaf bewezen stabiele baseline.

Geen gigantische multi-phase implementaties in één commit.

Geen “while we are here” refactors.

ORIGINAL regressions blokkeren voortgang.

---

# 3. FUNDAMENTELE PRODUCTARCHITECTUUR

Er zijn uiteindelijk TWEE control profiles:

## ORIGINAL

Pure klassieke 2009Scape gameplay.

## MODERN

WASD + moderne camera/targeting/presentation bovenop bestaande gameplay.

Conceptueel:

CONTROL PROFILE

ORIGINAL
MODERN

Dit staat LOS van de camera-rig.

Binnen MODERN bestaat:

MODERN CAMERA RIG

FIRST_PERSON
CHASE
FREE

Dus NIET meer:

Original
→ F11
First Person
→ F11
Third Person
→ F11
Original

De uiteindelijke user-facing werking wordt:

F11:

ORIGINAL <-> MODERN

En uitsluitend BINNEN MODERN bepaalt het scrollwiel de camera:

FIRST_PERSON
↕
CHASE
↕
FREE

Dit onderscheid is fundamenteel.

---

# 4. ORIGINAL IS HEILIG

Een speler die nooit F11 indrukt moet praktisch de normale 2009Scape-client
kunnen spelen.

ORIGINAL behoudt:

- click-to-move
- PathFinder
- movementQueue
- legacy movement interpolation
- minimap clicking
- minimap flags
- legacy run
- legacy run energy
- klassieke camera
- klassieke camera freedom
- klassieke scroll zoom
- klassieke zoomlimieten
- middle-mouse camera
- legacy context menus
- legacy combat
- legacy spells
- legacy item interactions
- legacy animations
- normale player rendering

MODERN code mag ORIGINAL niet onnodig beïnvloeden.

Scroll in ORIGINAL mag NOOIT:

- FIRST_PERSON activeren
- CHASE activeren
- MODERN activeren

F11 is de expliciete keuze van de gebruiker.

---

# 5. MODERN CAMERA CONTINUUM

Wanneer MODERN actief is:

FULLY ZOOMED IN

FIRST_PERSON

    ↕ scroll

CLOSE CHASE

    ↕ scroll

NORMAL CHASE

    ↕ scroll

FAR CHASE

    ↕ scroll

MODERN FREE / CLASSIC-STYLE

FULLY ZOOMED OUT

Scrolling wisselt uitsluitend CAMERA RIG.

Het verandert NIET het control profile.

Dus:

MODERN FREE != ORIGINAL

In MODERN FREE blijven actief:

- WASD
- Shift run
- ModernMovementController
- moderne server sync
- modern targeting
- modern interactions

Alleen de camera wordt classic/free-like.

---

# 6. F11

Finale gebruikersfunctie:

F11:

ORIGINAL -> MODERN

F11 opnieuw:

MODERN -> ORIGINAL

Gebruik edge-triggering.

Geen herhaling zolang F11 ingedrukt blijft.

Bij MODERN -> ORIGINAL:

- modern camera ownership stopt
- modern movement writes stoppen
- bestaande veilige locomotion rebase toepassen
- ORIGINAL movement queue niet herschrijven
- oorspronkelijke camera-instellingen logisch herstellen

Bij ORIGINAL -> MODERN:

- legacy playerpositie als start gebruiken
- geen teleport
- geen queue corruption
- modern prediction correct initialiseren
- modern camera veilig activeren

---

# 7. CAMERA STATE SEPARATION

ORIGINAL camera state en MODERN camera state moeten apart bestaan.

Conceptueel:

legacyCameraState

modernDesiredCameraDistance
modernActualCameraDistance
modernCameraRig
modernFreeYaw
modernFreePitch
modernChaseYaw

Een speler kan dus:

ORIGINAL
→ favoriete vanilla zoom instellen

F11
→ MODERN

F11
→ ORIGINAL

en zijn klassieke camera moet logisch terugkomen.

MODERN camera distance mag vanilla zoomvelden niet destructief overschrijven.

---

# 8. SCROLL ZOOM — ORIGINAL

ORIGINAL gebruikt exact de bestaande RT4/RuneScape zoompipeline.

Geen extended MODERN thresholds.

Geen automatische chase.

Geen first person.

Geen verandering voor regular players.

---

# 9. SCROLL ZOOM — MODERN

MODERN gebruikt één doorlopende gewenste camera-afstand.

Conceptueel:

desiredCameraDistance

Dit is de USER preference.

Daarnaast:

safeCameraDistance

Dit is de maximale momenteel toegestane afstand vanwege camera obstruction.

En:

actualCameraDistance

Dit is de werkelijk vloeiend weergegeven camera-afstand.

Dus:

targetActualDistance =
min(desiredCameraDistance, safeCameraDistance)

Nooit:

obstruction
→ desiredDistance permanent veranderen

---

# 10. MODERN ZOOMRANGES

FIRST_PERSON bevindt zich volledig aan de ingezoomde kant.

CHASE loopt vanaf close third-person tot ongeveer de NORMALE maximale
klassieke 2009Scape-uitzoomafstand.

Daarna begint FREE.

Conceptueel:

0
↓
FIRST_PERSON
↓
close chase
↓
normal chase
↓
far chase
↓
ongeveer vanilla 2009Scape max zoom
↓
FREE
↓
extended FREE
↓
MODERN maximum

---

# 11. RUNELITE-ACHTIGE EXTENDED FREE ZOOM

Doel voor MODERN FREE maximum:

ongeveer het gevoel van RuneLite Camera-plugin:

Expand outer zoom limit = +150

BELANGRIJK:

150 is GEEN:

- fine coordinate afstand
- aantal tiles
- world units

Het is een uitbreiding van RuneLite's bestaande zoom-limit scale.

Daarom moet deze waarde niet blind als:

MAX_DISTANCE += 150

worden vertaald.

Eerst moeten lokaal worden vastgesteld:

- vanilla RT4 zoomvelden
- vanilla minimale zoom
- vanilla maximale uitzoomwaarde
- relatie tussen zoomwaarde en Camera.method555 distance
- equivalent van een RuneLite-achtige +150 uitbreiding

CHASE/FREE boundary moet ongeveer overeenkomen met de normale vanilla max.

FREE krijgt de extra extended range.

ORIGINAL blijft volledig standaard.

---

# 12. CAMERA HYSTERESIS

Camera-rigs mogen niet flikkeren rond thresholds.

Bijvoorbeeld conceptueel:

CHASE -> FP bij <= A

FP -> CHASE pas bij >= B

waar B > A.

En:

CHASE -> FREE bij >= C

FREE -> CHASE pas bij <= D

waar D < C.

Thresholds moeten uit runtime tuning voortkomen.

Geen arbitrary constants als definitieve waarheid behandelen.

---

# 13. SMOOTH CAMERA ZOOM

Scroll verandert:

desiredCameraDistance

Niet instant:

actualCameraDistance

Camera zoom moet vloeiend interpoleren.

RT4 heeft logic/render timing gescheiden.

Visual camera smoothing hoort waar mogelijk bij de bestaande render-timed
cameraarchitectuur.

Movement prediction blijft op de client logic cadence.

Camera interpolation hoeft daar niet kunstmatig aan vast te zitten.

---

# 14. RT4 CAMERA TRANSFORM

Gebruik bestaande RT4 camera-geometrie waar mogelijk.

Camera.method555() is een belangrijke bestaande transform voor:

pivot/target
+ distance
+ yaw
+ pitch
→ camera world position

CHASE en FREE mogen niet zonder noodzaak een tweede bijna-identieke camera
trig-engine bouwen.

Trace lokale callsites en argumenten voordat method555 wordt hergebruikt.

Respecteer:

- yaw
- pitch
- horizontal distance reduction
- viewport/scaling gedrag
- render camera fields

---

# 15. FIRST-PERSON CAMERA

Gebruik de bewezen FPS-camera uit:

E:\Dev\RS-Sandbox

als historische referentie.

Nieuwe FirstPersonCamera is CAMERA ONLY.

Hij volgt:

PlayerList.self.xFine
PlayerList.self.zFine

of de moderne predicted position van de local player.

Hij mag niet zelfstandig locomotion authority worden.

Ondersteun:

- mouse-look
- yaw
- pitch
- FOV
- cursor locking
- recentering
- terrain-relative eye height
- scene rebuild
- teleport/reconnect lifecycle
- no head bob by default

Head bob blijft voorlopig UIT.

---

# 16. FIRST-PERSON MOVEMENT

FIRST_PERSON locomotion is camera-relative.

W:

forward naar waar camera horizontaal kijkt.

S:

exact backward.

A:

camera-relative left strafe.

D:

camera-relative right strafe.

Movement vector wordt iedere logic update opnieuw afgeleid van de ACTUELE
FP look yaw.

Dus:

W ingedrukt houden
+
camera draaien
=
bewegingspad draait live mee.

Geen W-release/repress nodig.

---

# 17. BEWEZEN RT4 CAMERA HANDEDNESS

De actuele RT4 camera yaw convention is clockwise.

Cardinals:

0 = NORTH (+Z)
512 = WEST (-X)
1024 = SOUTH (-Z)
1536 = EAST (+X)

Voor FP camera-relative velocity is de bewezen basis:

Forward:

(-sin(yaw), +cos(yaw))

Right:

(+cos(yaw), +sin(yaw))

Deze convention is runtime bewezen.

NIET opnieuw omdraaien zonder concrete runtime regression.

---

# 18. INPUT NORMALIZATION

Gecombineerde WASD input wordt genormaliseerd.

W+D mag niet sneller zijn dan W.

Geen diagonal speed exploit.

---

# 19. FINE COORDINATES

128 fine units = 1 tile in deze RT4 movementarchitectuur.

Tile center voor size 1:

tile * 128 + 64

Modern locomotion gebruikt continuous/fixed-point prediction.

Bij voorkeur Q16/sub-fine accumulation.

Voorbeeld:

fine position:

409664.0
409668.0
409672.0
...

Geen lokale tile-center hopping.

---

# 20. CLIENT CADENCE VS SERVER TICK

RT4 client logic:

20 ms
≈ 50 Hz

Server major world tick:

ongeveer 600 ms

Deze zijn NIET hetzelfde.

Client smooth movement:

50Hz lokaal

Server:

tile-based authoritative movement.

Modern movement moet daar bewust tussen vertalen.

---

# 21. WALK/RUN SPEED

Bewezen legacy referentie:

walk ≈ 4 fine units / logic tick

run ≈ 8 fine units / logic tick

Dat geeft ongeveer:

walk:
128 / 4 = 32 ticks ≈ 640 ms/tile

run:
128 / 8 = 16 ticks ≈ 320 ms/tile

Gebruik bestaande RuneScape movement semantics als source of truth.

Geen gameplay speedhack.

---

# 22. RUN INPUT

MODERN:

LEFT SHIFT = run.

Niet Ctrl.

Actual RT4 internal mapping is reeds getraceerd.

ORIGINAL behoudt eigen legacy gedrag.

Modern run flag richting server volgt moderne Shift-state.

---

# 23. SERVER MOVEMENT PROTOCOL

Server protocol blijft tile-based.

Geen arbitrary fine-coordinate network packets toevoegen.

Modern locomotion doet:

continuous local prediction
↓
tile boundary crossing
↓
geldige adjacent tile request
↓
bestaand server movement protocol

ClientProt heeft/kan een gerichte helper gebruiken voor moderne one-tile
movement packets.

Geen packet iedere 20 ms.

---

# 24. DDA TILE SYNCHRONISATIE

Server tile requests volgen de werkelijke continuous trajectory.

Gebruik boundary-aware traversal / DDA.

Niet:

sign(vx), sign(vz)

want dat maakt shallow movement ten onrechte altijd diagonal.

Bij gelijktijdige X/Z boundary crossing:

diagonal tile request correct afhandelen.

Gebruik fixed-point / cross multiplication waar nuttig.

---

# 25. LOCAL VS WORLD COORDINATES

Modern movement intern:

LOCAL scene tiles.

Alleen bij packet send:

worldX = Camera.originX + localX
worldZ = Camera.originZ + localZ

Niet half local / half world door de controller verspreiden.

---

# 26. PENDING SERVER REQUESTS

Run kan meerdere tile requests outstanding hebben.

Gebruik bounded pending buffer/ring.

Geen simpele singlePendingTile.

Pending entries vertegenwoordigen gevraagde tile progression.

Server updates hebben geen explicit request ID.

Matching is daarom gebaseerd op server-reported tiles.

---

# 27. LAST SERVER REPORTED TILE

Gebruik de term:

lastServerReportedTile

Niet:

acknowledgedTile

want het serverprotocol bevat geen request-ID ACK.

Exact server tile match:

consume pending entries tot en met die tile.

Geen vage:

"reached or passed"

2D logica.

Unexpected authoritative server tile:

route divergence
→ pending route passend resetten/superseden.

---

# 28. SERVER RECONCILIATION

PlayerList.self.xFine/zFine zijn in MODERN voorspelde lokale posities.

Ze mogen niet als servertruth worden gebruikt.

Normale correction source:

lastServerReportedTile
→ authoritative tile-center fine position

Geen snap puur omdat een timer afloopt.

Open terrain mag geen spontane multi-tile rebase geven.

Blocked geometry kan voorlopig server correction veroorzaken zolang Phase 4
local collision nog ontbreekt.

---

# 29. SERVER SNAPBACK DIAGNOSIS

Belangrijk onderscheid:

OPEN TERRAIN snapback:

waarschijnlijk movement/reconciliation/packet bug.

WALL-THROUGH snapback:

waarschijnlijk server collision authority die client prediction corrigeert.

Niet automatisch aannemen.

Diagnostiek moet kunnen tonen:

- predicted fine
- predicted tile
- lastServerReportedTile
- packets
- pending ring
- server self movement
- reconciliation reason

---

# 30. FORCE MOVEMENT PRIORITEIT

Force movement is authoritative.

Tijdens force movement schrijft ModernMovementController NIET:

- xFine/zFine
- normal locomotion velocity
- modern movement packets
- normal orientation
- normal movement animation

Na force move:

prediction rebasen.

Ook ondersteunen:

- agility
- scripted movement
- knockbacks
- cutscenes
- special movement

---

# 31. TELEPORTS / REGION REBUILDS

Bij:

- teleport
- login
- logout
- death
- respawn
- region change
- plane change
- scene rebuild

reset/rebase waar nodig:

- modern prediction
- pending requests
- camera interpolation
- target lock
- stale interaction state

Geen camera interpolation vanaf oude-region coordinates.

Geen momentum meenemen door teleport.

---

# 32. MODERN -> ORIGINAL MOVEMENT HANDOFF

ORIGINAL movement queue niet herschrijven.

Bij verlaten MODERN:

Als current predicted tile != lastServerReportedTile:

local player xFine/zFine rebasen naar authoritative server tile center.

Als dezelfde tile:

fine position indien veilig behouden om onnodige snap te voorkomen.

Legacy queue blijft server-owned.

---

# 33. PHASE 4 — LOCAL PLAYER COLLISION

Modern continuous locomotion mag NIET PathFinder gebruiken als directe WASD
locomotion driver.

Phase 4 wordt:

WASD
↓
desired Q16 velocity
↓
fine-coordinate RuneScape collision resolver
↓
allowed dx/dz
↓
predicted position
↓
DDA server sync

Gebruik bestaande RT4 CollisionMap.

Geen externe physics engine.

---

# 34. COLLISION SEMANTICS

Collision moet onderscheid maken tussen:

1. tile occupancy
2. directional wall/edge collision
3. diagonal/corner rules
4. map boundaries
5. object blocking
6. doorways
7. player footprint

Een wall edge kan crossing blokkeren terwijl beide aangrenzende tiles zelf
standable zijn.

Dat mag niet verloren gaan.

---

# 35. WALL SLIDING

Als volledige gewenste velocity niet mogelijk is:

dx blocked, dz free
→ dz toestaan

dz blocked, dx free
→ dx toestaan

Dus schuin tegen muur:

player glijdt langs muur waar mogelijk.

Geen simpele:

blocked
→ stop alles

---

# 36. DIAGONAL CORNER CUTTING

Diagonal movement mag niet door gesloten hoek/corner snijden.

Gebruik RuneScape collision flags.

Geen eigen los bedacht collision model.

---

# 37. PLAYER FOOTPRINT

Begin met RuneScape-semantic footprint.

Niet direct een grote capsule/radius-engine bouwen.

Player is ongeveer tile-scale maar local fine movement vraagt mogelijk een
kleine collision margin.

Tune later op runtime.

---

# 38. SERVER CONSISTENCY

Lokale collision moet zoveel mogelijk dezelfde flags/semantics gebruiken als
server movement.

Doel:

client voorkomt voorspelde beweging die server toch zou weigeren.

Resultaat:

minder rubberbanding.

---

# 39. ANIMATIONS

Gebruik originele BasType animation data.

States:

IDLE
WALK
RUN

Do not restart sequences every logic tick.

Animation frames blijven door bestaande RT4 method879 lopen.

Houd rekening met:

- idleAnimationId
- walkAnimation
- runAnimationId
- CW/CCW variants
- full-turn variants
- standing turns
- fallbacks

Modern movement mag method2247 positional interpolation niet hergebruiken als
locomotion driver.

Animaties mogen wel legacy semantics hergebruiken.

---

# 40. FIRST-PERSON VISUAL BODY ORIENTATION

In FP moeten movement direction en visual facing uit elkaar.

Voorbeeld:

W:
forward bewegen
body kijkt look direction

S:
achteruit bewegen
body blijft naar look direction kijken

A/D:
strafen
body blijft naar look direction kijken

Dus:

LOCOMOTION VELOCITY != VISUAL BODY YAW

ModernMovementController mag FP body orientation niet na camera/body-look
code opnieuw overschrijven vanuit velocity.

Eén normale owner voor visual body yaw.

---

# 41. BODY LOOK / SHOULDER BEHAVIOR

Idealiter:

camera/look kan beperkte yaw hebben ten opzichte van torso.

Bij kleine delta:

body relatief stabiel.

Bij grotere delta:

body draait soepel mee.

Conceptueel shoulder region:

ongeveer 55–75° als feel target.

Maar:

geen fake skeletal hacks.

Eerst RT4 player model/orientation mogelijkheden traceren.

Als echte independent head yaw niet bestaat:

body-yaw follow implementeren.

Independent head yaw later/deferred.

Body blijft upright.

Camera pitch roteert niet hele character voorover/achterover.

---

# 42. THIRD-PERSON CHASE — DEFINITIEVE PRINCIPES

CHASE is character-centric.

Niet:

camera bepaalt character movement.

Wel:

locomotion
↓
character/body heading
↓
camera volgt

Camera is een FOLLOWER.

CHASE camera mag nooit zijn yaw terugvoeren als movement authority.

---

# 43. THIRD-PERSON MOVEMENT

Third Person gebruikt WASD en dezelfde:

- Q16 prediction
- collision
- server sync
- run
- interaction
- targeting

Maar niet hetzelfde "camera controls locomotion" principe als FP.

Een stabiele locomotion reference moet onafhankelijk zijn van de chase-camera.

Vermijd feedback loops zoals:

D
→ velocity changes body
→ body changes camera
→ camera changes movement
→ endless turning

Final locomotion heading/state moet expliciet en stabiel ontworpen zijn.

---

# 44. CHASE CAMERA

CHASE camera:

- pivot rond upper torso/head height
- achter speler
- iets verhoogd
- smoothly following
- shortest-angle yaw smoothing
- smooth distance
- smooth position
- character turn causes camera to follow
- no instant snap

Bij 180° character turn:

camera moet vloeiend via de kortste hoek naar achter de nieuwe heading bewegen.

---

# 45. CHASE CAMERA COLLISION

Camera collision is LOS van Phase 4 player collision.

Camera mag niet door:

- walls
- buildings
- scenery
- terrain
- corners

gaan.

Primary response:

camera boom inkorten.

Niet:

camera standaard omhoog laten schieten.

Kleine bounded vertical correction mag eventueel als fallback.

---

# 46. CAMERA OBSTRUCTION DATA

Gebruik RT4 world/collision data.

Geen magic masks zonder trace.

Camera obstruction moet onderscheid maken tussen:

- occupied tile
- directional wall edge

Een dunne wall edge mag niet worden gemist doordat één sample per tile wordt
genomen.

Een tile/edge DDA of equivalent is waarschijnlijk beter.

Camera krijgt kleine safety margin zodat near plane niet half in muur zit.

---

# 47. DESIRED / SAFE / ACTUAL CAMERA DISTANCE

Verplicht model:

desiredDistance:
user zoom preference

safeDistance:
geometry limit

actualDistance:
smooth rendered camera distance

Voorbeeld:

desired = 500
wall allows = 210

actual:
500 -> 400 -> 300 -> 210

wall weg:

210 -> 270 -> 340 -> 420 -> 500

desired blijft 500.

---

# 48. FIRST_PERSON <-> CHASE TRANSITION

Geen:

CHASE
→ threshold
→ instant FP teleport

Wel:

far chase
→ close chase
→ vlak achter hoofd
→ eye position
→ FP

En reverse.

Hysteresis bepaalt ownership/state.

Spatial blend zorgt voor visuele overgang.

Slechts één camera owner schrijft de final render camera per frame.

---

# 49. FREE CAMERA

MODERN FREE:

classic/default RuneScape-achtig overzicht.

Maar:

- modern WASD blijft actief
- ORIGINAL niet geactiveerd
- chase lock wordt losgelaten
- camera freedom komt terug

Reuse waar mogelijk bestaande:

- Camera.yawTarget
- Camera.pitchTarget
- render-timed camera input
- middle mouse controls
- classic camera transform

Geen tweede primitieve arrow-key camera bouwen als RT4 dit al heeft.

---

# 50. FREE MOVEMENT IS NIET CAMERA-AUTHORITATIVE

Free camera mag los rondkijken.

De movement heading mag niet iedere keer veranderen alleen omdat de gebruiker
de FREE camera roteert.

Dat onderscheid is cruciaal:

FIRST_PERSON:
look camera mag locomotion bepalen

CHASE:
character locomotion bepaalt chase camera

FREE:
camera en locomotion zijn onafhankelijk

---

# 51. WHEEL INPUT

Gebruik bestaande RT4 wheel pipeline.

Geen tweede MouseWheelListener toevoegen als JavaMouseWheel al bestaat.

Trace:

JavaMouseWheel
→ getRotation()
→ MouseWheel.wheelRotation
→ UI
→ modern camera

UI wheel ownership moet echt worden geregeld.

Geen situatie:

bank/chat/interface scrollt
EN
camera zoomt tegelijk.

Priority:

1. modal/text input
2. scrollable RuneScape UI
3. modern camera/action input
4. movement

afhankelijk van context.

---

# 52. FIRST-PERSON SELF MODEL

Local player blijft in FP zichtbaar waar bruikbaar.

Doel:

- body zichtbaar
- equipment zichtbaar
- arms/weapons zichtbaar
- naar beneden kijken geeft body presence

Als hoofd/helmet camera clipt:

hide alleen minimaal lokale FP head/head-equipment indien technisch mogelijk.

Niet hele player cullen.

Remote players blijven volledig zichtbaar.

CHASE/FREE tonen volledige local player.

---

# 53. FIRST-PERSON VIEWMODEL — LATERE FASE

Dedicated FP arms/weapons zijn niet de eerste camera requirement.

Later:

- echte equipment IDs
- echte item models
- echte player appearance
- bestaande attack animation state

Opties:

A. bestaand player model geschikt renderen

B. dedicated local FP renderpass

Geen fake generic weapon.

---

# 54. TARGETING ARCHITECTUUR

Modern targeting verandert NIET gameplay validity.

Conceptueel:

ModernTargetingController

bepaalt:

"waar kijkt de speler naar?"

ModernInteractionController

bepaalt:

"welke bestaande RuneScape action wil hij uitvoeren?"

Server / bestaande gameplay bepaalt:

"mag die action daadwerkelijk?"

---

# 55. CROSSHAIR

FP/CHASE krijgen centraal reticle/crosshair.

FREE kan afhankelijk van final UX andere targetingpresentation krijgen.

Crosshair target acquisition moet zoveel mogelijk bestaande:

- scene picking
- entity data
- menu building
- action definitions

hergebruiken.

Ondersteun:

- NPC
- object
- ground item
- player indien bestaande actions relevant zijn

---

# 56. TARGET ACQUISITION != ACTION RANGE

Fundamentele regel.

Targeting mag zeggen:

"ik kijk naar Goblin"

maar niet:

"ik mag Goblin raken"

Acquisition en action validity zijn verschillende lagen.

---

# 57. NEARBY INTERACTIONS

Nearby acquisition voor:

- Open
- Close
- Search
- Talk-to
- Trade
- Take
- Climb
- Mine
- Chop
- Fish
- Use
- Bank
- etc.

Praktische nearby afstand ongeveer 2 tiles kan als startpunt gebruikt worden.

Centraliseer configuratie.

Niet overal magic number `2`.

---

# 58. LONG-RANGE COMBAT TARGET ACQUISITION

Combat acquisition moet aanzienlijk verder kunnen.

NPC op bijvoorbeeld:

8
10
of meer tiles

kan nog target zijn wanneer duidelijk onder crosshair.

Maar dit verhoogt NIET attack range.

Bestaande RuneScape combat bepaalt dat.

---

# 59. TARGET SCORING

Priority voornamelijk op aim.

Score kan bestaan uit:

1. in viewport
2. distance to crosshair
3. angular deviation
4. same plane
5. visibility
6. target hysteresis
7. world distance als secundaire factor

NPC op 10 tiles exact gecentreerd mag winnen van NPC op 3 tiles ver naast
reticle.

---

# 60. RAY / TARGETING CONE

Gebruik scene-space ray/cone indien technisch haalbaar.

Niet puur screen pixel distance als betere 3D informatie beschikbaar is.

Maar:

dit is TARGETING.

Geen hitscan combat.

---

# 61. GEEN HITSCAN

NOOIT:

crosshair
→ damage

Wel:

crosshair
→ RuneScape entity target
→ bestaande Attack/Cast action
→ server RuneScape combat

Projectiles blijven RuneScape projectiles.

---

# 62. CONTEXT ACTION UI

Bij target:

Goblin - level 5

> Attack
  Talk-to
  Examine

Object:

Door

> Open
  Examine

Ground:

Coins

> Take
  Examine

Selected spell:

Goblin

> Cast Fire Strike
  Attack
  Talk-to
  Examine

Gebruik bestaande action arrays/menu construction waar mogelijk.

---

# 63. ACTION SELECTIE

Later modern interaction UX:

scroll:
selecteer action

E:
execute selected action

left click:
execute selected action

Geen standaard double-E systeem.

BELANGRIJK:

Camera zoom en action scrolling mogen niet tegelijk hetzelfde wheel-event
consumeren.

Context bepaalt ownership.

---

# 64. MAGIC

Selected spell state uit bestaande client hergebruiken.

Cast spell -> NPC/object moet dezelfde legacy action triggeren.

Server/bestaande game bepaalt:

- runes
- magic level
- cooldown
- LOS
- range
- damage
- projectile
- splash
- XP

Autocast niet opnieuw implementeren.

---

# 65. RANGED

Crosshair kan NPC op afstand targeten.

Bestaande Attack action.

Geen hardcoded eigen bow/crossbow range tabel als existing game/server dit al
kent.

Server behoudt:

- actual range
- LOS
- accuracy
- attack speed
- projectile
- damage

---

# 66. MELEE

Verder target mag geselecteerd worden.

Bestaande gameplay mag automatisch dichterbij pathen indien dat normaal
gebeurt.

Modern targeting maakt melee geen ranged attack.

---

# 67. ITEM-ON-TARGET

Behoud:

Use item -> NPC
Use item -> object

Voorbeeld:

Rope selected
→ crosshair object
→ bestaande Use Rope -> object action

Geen eigen item mechanics.

---

# 68. MOVEMENT / INTERACTION ARBITRATION

Bestaande interactions kunnen legacy pathing starten.

Dat kan botsen met manual WASD.

Daarom expliciet arbitreren.

Doel:

geen:

- movement tug-of-war
- movementQueue corruption
- permanent rubberband

Een mogelijke policy:

manual WASD actief
→ manual locomotion priority

geen manual input
→ interaction auto-path kan tijdelijk authority krijgen

Maar definitieve implementatie moet na source trace worden gekozen.

---

# 69. COMBAT TIJDENS MOVEMENT

Waar serverregels het toestaan:

Attack NPC
+
WASD

moet mogelijk blijven.

Server bepaalt:

- attack continuation
- cooldown
- range
- movement validity
- target loss
- auto-path requirement

Geen client-authoritative strafing combat engine bouwen.

---

# 70. INPUT VS UI

Centraliseer:

isGameplayInputAllowed()

Modern input mag geen world action uitvoeren wanneer:

- chat input actief
- bank search actief
- amount dialog
- login fields
- text fields
- modal interfaces
- cutscenes
- locked interactions

---

# 71. CURSOR CAPTURE

FP en mogelijk CHASE gameplay gebruiken mouse capture.

UI moet bruikbaar blijven.

Concept:

ESC
→ cursor vrij / modal close afhankelijk van context

click viewport
→ recapture

UI:

- inventory
- bank
- spellbook
- prayer
- equipment
- shops
- dialogs

blijft functioneel.

---

# 72. CAMERA / UI PRIORITY

Geen camera mouse-look terwijl gebruiker bijvoorbeeld actief een modal
interface bedient indien dat ongewenst is.

Targeting pauzeert indien cursor/UI context world interaction blokkeert.

---

# 73. DEBUG OVERLAY

Optionele debug overlay, geen console spam.

Voor movement:

Mode/Profile
Rig
Fine X/Z
Tile X/Z
Velocity
Movement yaw
Body yaw
Camera yaw

Server:

lastServerReportedTile
pending count
prediction divergence

Collision:

dx allowed/blocked
dz allowed/blocked

Targeting:

target id/name
distance
screen/angular score
acquisition type
selected action
selected spell

Camera:

desiredDistance
safeDistance
actualDistance
obstruction result

---

# 74. GEEN GAMEPLAY CHEATS

Modern controls mogen NIET:

- sneller bewegen
- collision omzeilen
- attack range verhogen
- spell range verhogen
- LOS omzeilen
- attack cooldown omzeilen
- damage veranderen
- items van illegale afstand oppakken
- server validation omzeilen

Modernization is input/presentation.

Niet gameplay advantage.

---

# 75. RENDER DISTANCE — APARTE GRAPHICS ROADMAP

Camera zoom en world render distance zijn NIET hetzelfde.

We onderscheiden:

CAMERA DISTANCE

vs

DRAW / RENDER DISTANCE

vs

LOADED MAP / SCENE DISTANCE

Ver uitzoomen zonder extra scene loading kan alleen een lege wereldgrens
tonen.

---

# 76. BESTAANDE RT4 DRAW DISTANCE

RT4 bevat al HD/view-distance uitbreidingen.

Historisch/default ongeveer:

28 tiles

Projectcode bevat/kan bijvoorbeeld:

TILE_DISTANCE = 56

ondersteunen.

Dat betekent:

extended drawing bestaat al gedeeltelijk.

Maar extreem RuneLite/117HD-achtig zicht vraagt mogelijk grotere scene/map
loading.

---

# 77. TOEKOMSTIGE CONFIGURABLE RENDER DISTANCE

Later toevoegen:

Graphics:
Render Distance

bijvoorbeeld in stappen zoals:

Vanilla
40
50
60
70
80
90
100
120
...

Exact bereik pas na performance/source onderzoek.

Standaard/default moet veilig blijven.

Geen moderne camera verplicht afhankelijk maken van extreme render distance.

---

# 78. EXTENDED MAP LOADING

RuneLite GPU / 117HD-achtige afstanden vereisen meer dan alleen renderer
far-distance.

Ook nodig:

meer wereld/regions rondom speler geladen.

Future architecture onderzoekt:

- scene arrays
- region rebuild
- map squares
- terrain loading
- loc loading
- collision maps
- occlusion
- server rebuild packets
- cache data availability

Doel:

extended scene/map loading vergelijkbaar in concept met RuneLite expanded map
loading.

---

# 79. RENDER DISTANCE UX

Gebruiker hoeft idealiter niet zelf te begrijpen:

region rings
map squares
scene radius

Eén Render Distance setting kan intern automatisch voldoende map loading
selecteren.

Concept:

render 50
→ standaard scene

render 90
→ extra region ring(s)

render 120
→ meer extended loading

Exact mapping later onderzoeken.

---

# 80. ORIGINAL EN GRAPHICS

ORIGINAL control profile blijft vanilla qua CONTROLS/CAMERA.

Future extended graphics mogen optioneel configurabel zijn.

Default/stock settings moeten de normale 2009Scape ervaring behouden.

Een speler hoeft MODERN controls niet te gebruiken om eventueel hogere
graphics/view distance te kiezen.

Controls en graphics blijven gescheiden.

---

# 81. ROADMAP STATUS / FASES

## COMPLETED FOUNDATION

### Phase 1
Camera mode/framework foundation.

### Phase 2
First-person camera.

### Phase 3 / 3B
Modern continuous movement foundation.

Bewezen onderdelen:

- continuous Q16/fine prediction
- DDA server tile sync
- Shift run
- live FP camera-relative steering
- corrected RT4 yaw handedness
- pending server tile ring
- authoritative server tracking
- force-move priority
- mode lifecycle hooks
- self rendering foundation

---

# 82. PHASE 3C — MODERN CAMERA RIG

CURRENT / ACTIVE CAMERA PHASE.

Bouw:

ControlProfile:
ORIGINAL / MODERN

ModernCameraRig:
FIRST_PERSON / CHASE / FREE

Taken:

- F11 ORIGINAL <-> MODERN
- scroll continuum
- vanilla ORIGINAL isolation
- FP/CHASE transition
- CHASE follow camera
- camera obstruction
- FREE classic-style camera
- desired/safe/actual camera distance
- body look coupling
- ORIGINAL camera state preservation
- vanilla-max chase boundary
- extended FREE max ≈ RuneLite +150 feel

Niet in deze fase:

- player collision Phase 4
- targeting
- viewmodel
- extended map loading

---

# 83. PHASE 4 — FINE COORDINATE PLAYER COLLISION

Bouw local collision resolver.

Taken:

- CollisionMap trace
- occupied tiles
- directional wall edges
- diagonal corners
- no corner cutting
- wall sliding
- small player footprint
- terrain/map bounds
- doors/objects
- server consistency

Output:

allowed dx/dz vóór prediction write.

---

# 84. PHASE 5 — EXTENDED RENDERING / MAP LOADING

Grafische/view-distance fase.

Taken:

- inspect GlobalConfig.TILE_DISTANCE
- configurable draw distance
- fog/far plane integration
- scene/render bounds
- performance profiling
- extended scene/map loading
- region rings/map squares
- loc/terrain loading
- collision scene expansion
- RuneLite GPU expanded map loading als architecturale referentie
- 117HD-style extended world feel als UX referentie

Niet simpelweg TILE_DISTANCE absurd hoog zetten.

---

# 85. PHASE 6 — TARGET ACQUISITION

ModernTargetingController.

Vanaf start onderscheid:

NEARBY_INTERACTION

vs

COMBAT_LONG_RANGE

Taken:

- NPC
- object
- ground item
- target scoring
- viewport
- center/angular score
- ray/cone
- target hysteresis
- scene rebuild clearing

---

# 86. PHASE 7 — MODERN CONTEXT ACTION UI

Implement:

crosshair context menu

scroll:
select action

E:
execute

left-click:
execute selected action

Reuse bestaande actions/menu system.

---

# 87. PHASE 8 — MELEE INTEGRATION

Crosshair target:

Attack

→ bestaande melee action.

Test:

- pathing
- range
- combat state
- movement arbitration
- server authority

Geen nieuwe combat mechanics.

---

# 88. PHASE 9 — RANGED TARGETING

Test:

- bow
- crossbow
- multiple distances
- multiple NPCs
- obstruction
- out-of-range targets
- projectile
- server validation

Long-range acquisition zonder range cheat.

---

# 89. PHASE 10 — MAGIC TARGETING

Test:

- selected combat spell
- spell-on-NPC
- LOS
- range
- insufficient runes
- insufficient level
- projectile
- splash/hit
- autocast compatibility

---

# 90. PHASE 11 — MOVEMENT / INTERACTION ARBITRATION

Los conflict op tussen:

manual WASD

en

legacy action/pathing movement.

Test:

- melee
- ranged
- magic
- NPC interactions
- objects
- ground items

---

# 91. PHASE 12 — TARGETING / SERVER SYNC HARDENING

Niet opnieuw movementprotocol ontwerpen.

Wel:

- interaction sync
- target lifecycle
- unexpected server movement
- death/despawn
- region changes
- combat movement edge cases

---

# 92. PHASE 13 — FIRST-PERSON EQUIPMENT / VIEWMODEL

Doel:

echte equipment zichtbaar.

Ondersteun onder meer:

- sword
- shield
- defender
- staff
- wand
- bow
- crossbow
- axe
- pickaxe
- 2H weapons
- gloves/hands

Reuse:

- item IDs
- item models
- player appearance
- animation state

Geen fake generic FPS sword.

---

# 93. PHASE 14 — CAMERA / CHARACTER POLISH

Na core systemen:

- shoulder tuning
- body yaw smoothing
- camera follow tuning
- FP/CHASE blend tuning
- obstruction recovery tuning
- optional subtle camera bob
- optional weapon sway

Bob/sway standaard makkelijk uit te zetten.

---

# 94. PHASE 15 — MODERN UI / HOTBAR

Future UI:

- Enter-to-chat behouden
- modern hotbar
- scroll action/slot ownership afhankelijk van context
- inventory/equipment grouping
- skills/quests
- combat/prayer/magic
- social/settings
- cursor unlock bij interface
- Q/E subtab cycling indien gewenst

Hotbar refereert bestaande inventory/actions.

Geen duplicate game state.

---

# 95. ACCEPTANCE — ORIGINAL

ORIGINAL:

- click-to-move
- minimap movement
- minimap flag
- legacy run
- vanilla scroll zoom
- middle mouse camera
- classic free camera
- normal interactions
- combat
- spells/items
- idle/walk/run
- player model

alles blijft werken.

---

# 96. ACCEPTANCE — FIRST PERSON

- W/S/A/D correct
- live camera-relative steering
- Shift run
- no diagonal speed bonus
- collision
- wall sliding
- correct server sync
- idle/walk/run
- body faces look direction
- backward/strafe zonder body velocity-facing bug
- cursor/mouse look
- FOV
- region rebuild
- visible body/equipment waar mogelijk

---

# 97. ACCEPTANCE — CHASE

- WASD stabiel
- geen movement/camera feedback loop
- camera achter character
- smooth yaw
- smooth sudden direction changes
- no wall clipping
- boom compression
- smooth obstruction recovery
- scroll chase distance
- chase max ≈ vanilla maximum camera distance
- full player visible

---

# 98. ACCEPTANCE — MODERN FREE

- camera freedom vergelijkbaar met classic
- modern WASD blijft actief
- camera yaw verandert locomotion niet automatisch
- middle-mouse/default camera input hergebruikt waar mogelijk
- scroll verder uit tot extended MODERN max
- max ongeveer RuneLite Camera +150 feel
- scroll terug naar CHASE werkt
- nooit automatisch ORIGINAL

---

# 99. ACCEPTANCE — COLLISION

Test:

- straight wall
- diagonal wall
- corners
- doors
- objects
- narrow rooms
- map edge
- slopes
- bridges
- caves
- upstairs/downstairs

Geen walk-through prediction die server telkens terugcorrigeert.

---

# 100. ACCEPTANCE — SERVER

Open terrein:

geen periodieke multi-tile snapback.

Blocked terrain:

client voorkomt invalid prediction lokaal.

Server blijft uiteindelijk authoritative.

Teleport/force move:

modern controller geeft authority correct terug.

---

# 101. ACCEPTANCE — TARGETING

Nearby:

Door
> Open

Coins
> Take

Banker
> Bank
  Talk-to

Long range:

Goblin exact onder crosshair op afstand
→ target acquisition

Maar:

bestaande combat range/LOS blijft leidend.

---

# 102. ACCEPTANCE — MAGIC

Selected spell:

Fire Strike

Crosshair Goblin:

> Cast Fire Strike
  Attack
  Talk-to
  Examine

E/click:

bestaande spell-on-NPC action.

Geen nieuwe magic engine.

---

# 103. ACCEPTANCE — RANGED

Crosshair NPC op afstand:

Attack

→ existing ranged attack.

Geen hitscan.

Geen custom damage.

Geen custom range.

---

# 104. ACCEPTANCE — UI

Tijdens tekstinput:

WASD/E/world click voert geen modern gameplay action uit.

Interfaces blijven bruikbaar.

Wheel ownership veroorzaakt geen:

UI scroll
+
camera zoom

tegelijk.

---

# 105. DEVELOPMENT RULES

Na iedere fase:

1. compile
2. runtime test
3. ORIGINAL regression test
4. MODERN relevante tests
5. inspect git diff
6. document root cause / changes
7. commit
8. push
9. verify HEAD == origin/main

Geen volgende fase vóór huidige runtime stabiel is.

---

# 106. GEEN MASSALE REFACTOR

Nieuwe modern-control code zoveel mogelijk isoleren.

Voorbeelden:

ModernControlController
ModernMovementController
ModernCameraRig
FirstPersonCamera
ModernTargetingController
ModernInteractionController

Legacy RT4 alleen wijzigen waar duidelijke hook nodig is.

Geen formatting sweep.

Geen honderden irrelevante changes.

---

# 107. SOURCE-OF-TRUTH RULE

Wanneer documentatie, theorie en runtime elkaar tegenspreken:

1. actuele source trace
2. runtime gedrag
3. protocol/server trace

winnen.

Niet blijven redeneren vanuit een oude aanname.

Voorbeeld reeds geleerd:

RT4 yaw handedness moest vanuit echte camera transform worden vastgesteld,
niet uit generieke sin/cos aannames.

---

# 108. BELANGRIJKSTE REGEL VAN HET PROJECT

Bouw GEEN nieuwe RuneScape gameplay-engine.

Modern Controls bepalen:

waar kijkt de speler naar?
hoe beweegt hij lokaal vloeiend?
welke bestaande action selecteert hij?
hoe wordt de wereld gepresenteerd?

RuneScape bepaalt:

mag de beweging?
mag de actie?
is er collision?
is er line-of-sight?
is target binnen range?
moet player dichterbij lopen?
wanneer valt weapon aan?
raakt de aanval?
hoeveel damage?
welk projectile?
welke animation?
welke XP?
welke server state?

Het einddoel is:

een moderne First-Person / Third-Person / Free-camera speelervaring bovenop
authentieke 2009Scape mechanics,

terwijl ORIGINAL volledig bruikbaar blijft voor spelers die de klassieke
RuneScape ervaring willen.