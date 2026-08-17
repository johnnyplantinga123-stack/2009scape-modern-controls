package core.game.node.entity.combat

import core.game.node.entity.Entity
import core.game.node.entity.player.Player
import core.game.world.GameWorld
import kotlin.math.atan2

/**
 * Small server-authoritative bridge for the RT4 MODERN controls profile.
 *
 * The capability is negotiated per connection through the otherwise-unused
 * top bit of PLAYER_PREFS_UPDATE. It is never stored on the account and does
 * not alter the behaviour of an unmodified/original client.
 */
const val MODERN_CONTROLS_ATTRIBUTE = "modern-controls"
private const val MODERN_MANUAL_MOVE_UNTIL_ATTRIBUTE = "modern-combat-manual-move-until"
const val MODERN_CONTROL_PREFS_BIT = Int.MIN_VALUE
const val MODERN_HIT_DIRECTION_VARP = 3499

fun setModernControls(player: Player, prefs: Int) {
    player.setAttribute(MODERN_CONTROLS_ATTRIBUTE, prefs and MODERN_CONTROL_PREFS_BIT != 0)
}

fun hasModernControls(player: Player): Boolean = player.getAttribute(MODERN_CONTROLS_ATTRIBUTE, false)

/** Keeps a manual one-tile WASD request ahead of combat auto-chasing briefly. */
fun rememberModernManualMove(player: Player) {
    // Server ticks are 600 ms. Two ticks cover the next single-tile request
    // without turning an abandoned target into an infinite combat lock.
    player.setAttribute(MODERN_MANUAL_MOVE_UNTIL_ATTRIBUTE, GameWorld.ticks + 2)
}

fun hasRecentModernManualMove(entity: Entity): Boolean {
    if (entity !is Player || !hasModernControls(entity)) {
        return false
    }
    return entity.getAttribute(MODERN_MANUAL_MOVE_UNTIL_ATTRIBUTE, -1) >= GameWorld.ticks
}

/**
 * Sends a direction-only cue to the player who was hit. No attacker id,
 * name, health, or off-screen entity state is disclosed to the client.
 */
fun sendModernHitDirection(attacker: Entity, victim: Entity) {
    if (victim !is Player || !hasModernControls(victim)) {
        return
    }
    val dx = attacker.location.x - victim.location.x
    val dz = attacker.location.y - victim.location.y
    if (dx == 0 && dz == 0) {
        return
    }
    // RT4 heading convention: 0 = +Z; positive X is 1536. Quantize to eight
    // directions so several simultaneous attackers remain readable.
    val heading = ((atan2(-dx.toDouble(), dz.toDouble()) * 2048.0 / (Math.PI * 2.0)).toInt() and 2047)
    val octant = ((heading + 128) shr 8) and 7
    victim.packetDispatch.sendVarp(MODERN_HIT_DIRECTION_VARP, octant)
}
