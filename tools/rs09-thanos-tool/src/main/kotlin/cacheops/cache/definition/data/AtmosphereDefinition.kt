package cacheops.cache.definition.data

import cacheops.cache.Definition
import ext.Vector3f

//TODO: Maybe finish and come back who knows.
data class AtmosphereDefinition(
    override var id: Int = -1,
    val DEFAULT_SKY_COLOR: Int = 0x17c439a8,
    val DEFAULT_SUN_COLOR: Int = 0x36dac459,
    val DEFAULT_SUN_X_POSITION: Float = 0.69921875F,
    val DEFAULT_SUN_Y_POSITION: Float = 1.2F,
    val DEFAULT_SUN_X_ANGLE: Int = -50,
    val DEFAULT_SUN_Y_ANGLE: Int = -60,
    val DEFAULT_SUN_Z_ANGLE: Int = -50,
    val DEFAULT_SUN_ANGLE: Vector3f = Vector3f(DEFAULT_SUN_X_ANGLE.toFloat(), DEFAULT_SUN_Y_ANGLE.toFloat(), DEFAULT_SUN_Z_ANGLE.toFloat()),
    val DEFAULT_SUN_SHININESS: Float = 1.1523438F,

) : Definition