package cacheops.cache.definition.decoder

import Rs2MapEditor
import cacheops.cache.definition.data.ObjectDefinition
import const.cache
import ext.getBigSmart
import ext.getSmart
import java.nio.ByteBuffer


object TileSceneryParser  {
    fun parseRegion(id: Int): ArrayList<Scenery>{
        if(XteaKeys.XTEAS.isEmpty()){
            XteaKeys.load()
        }

        val regionX = (id shr 8) and 0xFF;
        val regionY = id and 0xFF;
        val data = Rs2MapEditor.library.data(5, "l${regionX}_${regionY}",XteaKeys.get(id))

        return if(data != null) decode(ByteBuffer.wrap(data))
        else ArrayList()
    }

    private fun decode(data: ByteBuffer): ArrayList<Scenery>{
        val list = ArrayList<Scenery>()
        var objectId = -1
        while (true) {
            var offset: Int = data.getBigSmart()
            if (offset == 0) {
                break
            }
            objectId += offset
            var location = 0
            while (true) {
                offset = data.getSmart()
                if (offset == 0) {
                    break
                }
                location += offset - 1
                val y = location and 0x3f
                val x = (location shr 6) and 0x3f
                val configuration: Int = data.get().toInt() and 0xFF
                val rotation = configuration and 0x3
                val type = configuration shr 2
                val z = location shr 12

                list.add(Scenery(objectId,x,y,z,rotation,type))
            }
        }
        return list
    }
}

var objectDecoder: ObjectDecoder? =  null
class Scenery(val id: Int, val x: Int, val y: Int, val plane: Int, val rotation: Int, val type: Int){
    val definition = objectDecoder?.forId(id) ?: if (cache != null) ObjectDecoder(cache!!, true, false).also { objectDecoder = it }.forId(id) else null
}

