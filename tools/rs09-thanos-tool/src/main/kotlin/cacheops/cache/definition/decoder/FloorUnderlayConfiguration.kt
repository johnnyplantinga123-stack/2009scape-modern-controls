package cacheops.cache.definition.decoder

import Rs2MapEditor
import cacheops.cache.definition.data.UnderlayDefinition
import const.Archives
import const.Indices
import java.nio.ByteBuffer

object FloorUnderlayConfiguration {

    val cache = Rs2MapEditor.library.index(Indices.CONFIGURATION).archive(Archives.FLOOR_UNDERLAYS)

    var floorUnderlays = hashMapOf<Int, UnderlayDefinition>()

    @JvmStatic
    fun main(args: Array<String>) {
        cache!!.files().forEach {
            val buffer = ByteBuffer.wrap(it.data)
            val definition = FloorUnderlayDecoder().decode(buffer)
            floorUnderlays[it.id] = definition
        }
    }

    fun init() {
        cache!!.files().forEach {
            val buffer = ByteBuffer.wrap(it.data)
            val definition = FloorUnderlayDecoder().decode(buffer)
            floorUnderlays[it.id] = definition
            //println(definition.toString())
        }
    }

}