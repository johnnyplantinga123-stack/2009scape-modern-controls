package cacheops.cache.definition.decoder

import Rs2MapEditor
import cacheops.cache.definition.data.MapDefinition
import java.nio.ByteBuffer

object MapTileParser {

    val definition = MapDefinition()

    lateinit var atmosphereDataTail: ByteArray

    @JvmStatic
    fun main(args: Array<String>) {

        val region = 12850
        val baseX = ((region shr 8) shl 6)
        val baseY = ((region and 0xFF) shl 6)
        val x = (region shr 8) and 0xFF
        val y = region and 0xFF
        val mapData = Rs2MapEditor.library.data(5,"m${x}_${y}")

        MapTileDecoder().readLoop(definition, ByteBuffer.wrap(mapData))

        for (lclX in 0 until 64) {
            for (lclY in 0 until 64) {
                println(definition.getTile(lclX,lclY,0).underlayId)
            }
        }

    }

    fun init() {
        definition.objects.clear()
        definition.tiles.clear()
        val region = Rs2MapEditor.region
        val baseX = ((region shr 8) shl 6)
        val baseY = ((region and 0xFF) shl 6)
        val x = (region shr 8) and 0xFF
        val y = region and 0xFF
        val mapData = Rs2MapEditor.library.data(5,"m${x}_${y}")
        println(mapData!!.size)
        atmosphereDataTail = MapTileDecoder().readLoop(definition, ByteBuffer.wrap(mapData))
    }

    fun coordinateX(x: Int): Int {
        val baseX = ((Rs2MapEditor.region shr 8) shl 6)
        return x + baseX
    }

    fun coordinateY(y: Int): Int {
        val baseY = ((Rs2MapEditor.region and 0xFF) shl 6)
        return y + baseY
    }
}