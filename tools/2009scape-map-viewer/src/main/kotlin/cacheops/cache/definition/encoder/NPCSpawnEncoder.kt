package cacheops.cache.definition.encoder

import Rs2MapEditor
import cacheops.cache.definition.decoder.NPC
import const.Indices
import java.nio.ByteBuffer
import javax.swing.JOptionPane

object NPCSpawnEncoder {

    fun write(npc: ArrayList<NPC>) {
        val buffer = ByteBuffer.allocate(npc.size * 4)

        npc.forEach {
            val formattedCoord = (it.plane shl 14) or (it.x shl 7) or it.y
            buffer.putShort(formattedCoord.toShort())
            buffer.putShort(it.id.toShort())
        }

        val dataArray = buffer.array()

        val region = Rs2MapEditor.region
        val x = (region shr 8) and 0xFF
        val y = region and 0xFF

        if (dataArray != null) {
            Rs2MapEditor.library.put(Indices.LANDSCAPES, "n${x}_${y}", dataArray, intArrayOf(0,0,0,0))
            Rs2MapEditor.library.update()
            JOptionPane.showMessageDialog(Rs2MapEditor.mapPanel, "NPCs written to cache [✓]")
            Rs2MapEditor.npcsUpdated = false
        } else {
            System.err.println("Panic! Something went VERY wrong!")
        }
    }
}