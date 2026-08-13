package tools

object Util {
    fun getRegionId(location: TableData.Location) : Int {
        return ((location.x shr 6) shl 8) or (location.y shr 6)
    }

    fun getRegionCoordinates(location: TableData.Location) : IntArray {
        return intArrayOf(location.x and 63, location.y and 63, location.z)
    }

    fun getAbsoluteCoordinates(regionId: Int, regionX: Int, regionY: Int, plane: Int) : TableData.Location {
        val x = regionId shr 8
        val y = regionId and 0xFF
        return TableData.Location((x shl 6) or regionX, (y shl 6) or regionY, plane)
    }

    fun getRegion (currentRegionId: Int, changeX: Int, changeY: Int) : Int {
	var x = ((currentRegionId shr 8) + changeX) shl 8
	var y = ((currentRegionId and 0xFF) + changeY)
	return (x or y)
    }
}
