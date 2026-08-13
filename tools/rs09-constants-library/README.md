# RS09 Constants Library

A library containing constants for animations, interfaces, graphics, items, networking, npcs, scenery, and sounds.

Note: Interfaces, Items, NPCs, and Scenery are already named. If you have a suggestion, or want to be more specific with the naming for one of those, post an issue.

<br>

# Naming Conventions 

<h3>Animations:</h3>

```
const val Entity/Scenery_Name_Describe_Animation_AnimationId = AnimationId
```

Example:
```kotlin
    const val HUMAN_FLAP_4280 = 4280
    const val TRAIBORN_SUMMON_CABINET_4602 = 4602
```

<h3>Graphics/Sounds:</h3>

```
const val Describe_Sound/GFX_Sound/GFXId = Sound/GFXId
```

Example:
```kotlin
    // GFX:
    const val RECEIVE_SILVERLIGHT_778 = 778
    const val ZOMBIE_HAND_1244 = 1244

    // Sound:
    const val OPEN_CABINET_44 = 44
    const val OPEN_HEAVY_54 = 54
    const val WATER_BEING_POURED_2982 = 2982

```

<h3>Varps</h3>

```
const val VARP_CATEGORY_DESCRIPTION = id
```

Example:
```kotlin
    const val VARP_IFACE_BANK_INSERT_MODE = 304
```

<h3>Varbits</h3>

``` 
const val VARBIT_CATEGORY_DESCRIPTION = id
```

Example:
```kotlin
    const val VARBIT_IFACE_BANK_INSERT_MODE = 7000
```
