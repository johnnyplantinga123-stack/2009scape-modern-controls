The ContentAPI is a consolidated central location that provides every method you'll need to implement ***almost*** anything.

It is located at `Server/src/main/kotlin/api/ContentAPI.kt` ([View In Browser](https://gitlab.com/2009scape/2009scape/-/blob/master/Server/src/main/kotlin/api/ContentAPI.kt))

To use methods in the ContentAPI, you must add the import to whatever kotlin file you are working with if it doesn't already have it:

```
import api.*
```

All of the methods are documented and globally accessible. You can use the methods by simply typing their name, e.g. `sendMessage(player, "Bababooey")`. 

It's very important that you read the documentation for the methods you use and attempt to understand them.

Here's an example of a [listener](writing-listeners) that uses the ContentAPI:

```kt
    override fun defineListeners() {
        onUseWith(IntType.ITEM, evilTurnip, knife) { player, used, with ->
            if(removeItem(player, used.asItem())) {
                sendMessage(player, "You carve a scary face into the evil turnip.")
                sendMessage(player, "Wooo! It's enough to give you nightmares.")
                return@onUseWith addItem(player, carvedEvilTurnip)
            }
            return@onUseWith false
        }
    }
```

In this example, `sendMessage`, `removeItem` are both ContentAPI methods.