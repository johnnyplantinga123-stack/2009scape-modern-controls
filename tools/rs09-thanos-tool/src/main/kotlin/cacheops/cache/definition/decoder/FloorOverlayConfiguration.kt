package cacheops.cache.definition.decoder

import Rs2MapEditor
import cacheops.cache.definition.data.OverlayDefinition
import const.Archives
import const.Indices
import java.nio.ByteBuffer

object FloorOverlayConfiguration {

    val cache = Rs2MapEditor.library.index(Indices.CONFIGURATION).archive(Archives.FLOOR_OVERLAYS)

    var floorOverlays = hashMapOf<Int, OverlayDefinition>()

    @JvmStatic
    fun main(args: Array<String>) {
        cache!!.files().forEach {
            val buffer = ByteBuffer.wrap(it.data)
            val definition = FloorOverlayDecoder().decode(buffer)
            floorOverlays[it.id] = definition
        }
    }

    fun init() {
        cache!!.files().forEach {
            val buffer = ByteBuffer.wrap(it.data)
            val definition = FloorOverlayDecoder().decode(buffer)
            floorOverlays[it.id] = definition
        }
    }

}