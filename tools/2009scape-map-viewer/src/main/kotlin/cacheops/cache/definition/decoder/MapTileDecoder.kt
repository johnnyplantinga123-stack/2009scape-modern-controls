package cacheops.cache.definition.decoder

import cacheops.cache.definition.data.MapDefinition
import cacheops.cache.definition.data.MapTile
import ext.unsignedByte
import java.nio.ByteBuffer

class MapTileDecoder {

    fun readLoop(definition: MapDefinition, buffer: ByteBuffer): ByteArray {

        for (plane in 0 until 4) {
            for (localX in 0 until 64) {
                for (localY in 0 until 64) {
                    var height = 0
                    var attrOpcode = 0
                    var overlayPath = 0
                    var overlayRotation = 0
                    var overlayId = 0
                    var settings = 0
                    var underlayId = 0
                    loop@ while (true) {
                        val config = buffer.unsignedByte()
                        if (config == 0) {
                            break@loop
                        } else if (config == 1) {
                            height = buffer.unsignedByte()
                            break@loop
                        } else if (config <= 49) {
                            attrOpcode = config
                            overlayId = buffer.unsignedByte()
                            overlayPath = (config - 2) / 4
                            overlayRotation = 3 and (config - 2)
                        } else if (config <= 81) {
                            settings = config - 49
                        } else {
                            underlayId = (config - 81) and 0xff
                        }
                    }
                    if (height != 0 || attrOpcode != 0 || overlayPath != 0 || overlayRotation != 0 || overlayId != 0 || settings != 0 || underlayId != 0) {
                        definition.setTile(localX, localY, plane, MapTile(height, attrOpcode, if(overlayId == 42) 0 else overlayId, overlayPath, overlayRotation, settings, underlayId))
                    }
                }
            }
        }
        return buffer.array().copyOfRange(buffer.position(), buffer.capacity())
    }
}