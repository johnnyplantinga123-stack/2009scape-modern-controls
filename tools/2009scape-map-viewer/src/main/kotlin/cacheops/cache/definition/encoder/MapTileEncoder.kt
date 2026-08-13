package cacheops.cache.definition.encoder

import Rs2MapEditor
import cacheops.cache.definition.data.MapDefinition
import cacheops.cache.definition.decoder.MapTileParser
import const.Indices
import java.nio.ByteBuffer

object MapTileEncoder {

    fun write(definition: MapDefinition) {
        val buffer = ByteBuffer.allocate(80000)
        for (plane in 0 until 4) {
            for (localX in 0 until 64) {
                for (localY in 0 until 64) {
                    val tile = definition.getTile(localX, localY, plane)
                    if (tile.underlayId != 0) {
                        buffer.put(((tile.underlayId + 81) and 0xFF).toByte())
                    }
                    if (tile.settings != 0) {
                        buffer.put(((tile.settings + 49) and 0xFF).toByte())
                    }
                    if (tile.attrOpcode != 0) {
                        buffer.put((tile.attrOpcode and 0xFF).toByte())
                        buffer.put((tile.overlayId and 0xFF).toByte())
                    }
                    if (tile.height == 0) {
                        buffer.put((0).toByte())
                    } else {
                        buffer.put((1).toByte())
                        buffer.put((tile.height and 0xFF).toByte())
                    }
                }
            }
        }
        val data = buffer.array().copyOf(buffer.position())
        val combinedData = data.plus(MapTileParser.atmosphereDataTail)

        val x = (Rs2MapEditor.region shr 8) and 0xFF
        val y = Rs2MapEditor.region and 0xFF

        if (combinedData != null) {
            Rs2MapEditor.library.put(Indices.LANDSCAPES, "m${x}_${y}", combinedData)
            Rs2MapEditor.library.update()
        } else {
            System.err.println("Something went VERY wrong!")
        }
    }
}