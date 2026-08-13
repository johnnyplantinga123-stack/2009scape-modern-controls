package cacheops.cache.definition.decoder

import Rs2MapEditor
import cacheops.cache.definition.data.NPCDefinition
import const.cache
import ext.unsignedShort
import java.nio.ByteBuffer

val decoder = NPCDecoder(cache, true)

class NPCSpawnDecoder {

    fun decode(data: ByteArray): ArrayList<NPC> {

        val buffer = ByteBuffer.wrap(data)
        val list = ArrayList<NPC>()

        while (buffer.hasRemaining()) {
            val coords = buffer.unsignedShort()

            val lz = coords shr 14
            val lx = (coords shr 7) and 0x3f
            val ly = coords and 0x3f

            val npcID = buffer.unsignedShort()
            list.add(NPC(npcID, lx, ly, lz))
        }
        return list
    }
}

object NPCSpawnParser {

    fun parseNPCSpawns(regionId: Int): ArrayList<NPC> {
        val x = (regionId shr 8) and 0xFF
        val y = regionId and 0xFF
        val npcSpawnData = Rs2MapEditor.library.data(5, "n${x}_${y}")

        if (npcSpawnData != null && npcSpawnData.isNotEmpty()) {
            return NPCSpawnDecoder().decode(npcSpawnData)
        }
        return ArrayList()
    }
}

class NPC(val id: Int, val x: Int, val y: Int, val plane: Int) {
    val definition: NPCDefinition = decoder.forId(id)
}