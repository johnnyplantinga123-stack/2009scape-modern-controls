package cacheops.cache.definition.decoder

import Rs2MapEditor
import cacheops.cache.definition.data.ItemDefinition
import const.cache
import ext.unsignedShort
import java.nio.ByteBuffer

val itemDecoder = ItemDecoder(cache)

class GroundItemDecoder {

    fun decode(data: ByteArray): ArrayList<Item> {

        val buffer = ByteBuffer.wrap(data)
        val list = ArrayList<Item>()

        while (buffer.hasRemaining()) {
            val coords = buffer.unsignedShort()

            val lz = coords shr 14
            val lx = (coords shr 7) and 0x3f
            val ly = coords and 0x3f

            val itemID = buffer.unsignedShort()

            val respawnTime = buffer.unsignedShort()

            val amount = buffer.int

            list.add(Item(itemID, lx, ly, lz, respawnTime, amount))
        }
        return list
    }
}

object GroundItemSpawnParser {

    fun parseItemSpawns(regionId: Int): ArrayList<Item> {
        val x = (regionId shr 8) and 0xFF
        val y = regionId and 0xFF
        val groundItemData = Rs2MapEditor.library.data(5, "i${x}_${y}")

        if (groundItemData != null && groundItemData.isNotEmpty()) {
            return GroundItemDecoder().decode(groundItemData)
        }
        return ArrayList()
    }
}

class Item(val id: Int, val x: Int, val y: Int, val plane: Int, val respawnTime: Int, val amount: Int) {
    val definition: ItemDefinition = itemDecoder.forId(id)
}