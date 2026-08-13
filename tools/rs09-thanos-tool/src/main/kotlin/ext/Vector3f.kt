package ext

class Vector3f(var x: Float, var y: Float, var z: Float) {

    override fun hashCode(): Int {
        var result = x.hashCode()
        result = 31 * result + y.hashCode()
        result = 31 * result + z.hashCode()
        return result
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as Vector3f

        if (x != other.x) return false
        if (y != other.y) return false
        if (z != other.z) return false

        return true
    }
}