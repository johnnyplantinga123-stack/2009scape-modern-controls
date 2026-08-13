## What Is A Listener?
Listeners are the way 2009Scape *listens* for, and then handles an interaction. 

Interactions are described as either:
- (For Standard Interactions) A combination of the option clicked, the type of thing you clicked on (IntType.SCENERY, IntType.NPC, IntType.ITEM, etc), and the ID of the thing you clicked on. 
- (For Use-With Interactions) A combination of the item id used, the type of thing it's being used on (IntType.SCENERY, etc), and the ID of the thing it's being used on.

## Defining Standard Listeners
These are the listeners you would write for interactions that consist of clicking an option on a Node in the game.

In the below example, we define a simple listener for the "drink" option of the Blamish Oil (ID 1582) item:

```kt
class DrinkBlamishOilListener : InteractionListener {

    override fun defineListeners() {
        on(Items.BLAMISH_OIL_1582, IntType.ITEM, "drink"){player, _ ->
            sendPlayerDialogue(player, "You know... I'd really rather not.")
            return@on true
        }
    }
}
```

Our class, `DrinkBlamishOilListener`, implements the `InteractionListener` interface. 
This interface provides the defineListeners() function, which you should write your listener definitions inside of.

We use the `on` method provided by InteractionListener to define the interaction. The first argument is the ID of the item we are listening for, which is accessible with the `Items` constants file. The next argument is the type of interaction this is. This is an item, so the interaction is `IntType.ITEM`. Next, we define the option we are listening for. This is the lowercased option that you would see in the right click menu for the item. In this case, it is `drink`. 

In the curly braces, we accept the `player, node` arguments, but we don't need `node` for this particular interaction, so we replace it with an `_`. In some cases you will need a reference to the actual thing the player clicked on, which is what `node` is for. 

After the arrow (`->`), we then define the code that will actually be ran for this interaction, which in this case is opening a dialogue box where the player says "You know... I'd really rather not."

## Defining Use-With Listeners
These are listeners that listen for the player to *use* an item on something else, whether it be another item, an npc, an object, whatever. 

In the below example, we define a simple listener that listens for a player to use a knife on an evil turnip, which replaces the evil turnip with a different item and then sends some chat messages to the player.

```kt
class CarvedEvilTurnipListener : InteractionListener {
    val knife = Items.KNIFE_946
    val evilTurnip = Items.EVIL_TURNIP_12134
    val carvedEvilTurnip = Items.CARVED_EVIL_TURNIP_12153

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
}
```

The definition is much the same as the above, excluding the actual definition method used: `onUseWith`. 

`onUseWith` accepts three arguments, the Type of thing we are using the item on, the ID of the item that we are using, and the ID of the thing it is being used with.

In this case, we are using the item with another item, so the type is `IntType.ITEM`. If we were using the item with an NPC, it would be `IntType.NPC`. 

Item -> Item listeners work both ways, meaning you can use the evil turnip on the knife, or the knife on the evil turnip, and this listener will trigger. 