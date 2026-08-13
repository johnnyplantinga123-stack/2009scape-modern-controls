package cacheops.cache.definition.encoder

import Rs2MapEditor
import cacheops.cache.definition.decoder.Item
import const.Indices
import java.nio.ByteBuffer
import javax.swing.JOptionPane

object GroundItemEncoder {

    fun write(item: ArrayList<Item>) {

        // Setup buffer | Determine capacity (ArrayList size * (short2 + short2 + short2 + int4))
        val buffer = ByteBuffer.allocate(item.size * 10)

        // Put data to buffer
        item.forEach {
            val formattedCoord = (it.plane shl 14) or (it.x shl 7) or it.y
            buffer.putShort(formattedCoord.toShort())
            buffer.putShort(it.id.toShort())
            buffer.putShort(it.respawnTime.toShort())
            buffer.putInt(it.amount)
        }

        // Convert buffer to byte array
        val dataArray = buffer.array()

        // Get region information
        val region = Rs2MapEditor.region
        val x = (region shr 8) and 0xFF
        val y = region and 0xFF

        // Put region data
        if (dataArray != null) {
            Rs2MapEditor.library.put(Indices.LANDSCAPES, "i${x}_${y}", dataArray, intArrayOf(0,0,0,0))
            Rs2MapEditor.library.update()
            JOptionPane.showMessageDialog(Rs2MapEditor.mapPanel, "Items written to cache [✓]")
            Rs2MapEditor.itemsUpdated = false
        } else {
            System.err.println("Panic! Something went VERY wrong!")
        }
    }
}

fun main() {
    val itemList = arrayListOf<Item>(Item(946, 24, 2,0, 6, 1))
    GroundItemEncoder.write(itemList)
}