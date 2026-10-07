package dsh.nebsclient.core.message

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

// Movement. A new movement command (move, goto, follow, stop) replaces the one in progress.

/** Sets the view direction: [yaw] in degrees (0 = south, 90 = west), [pitch] from -90 (up) to 90 (down). */
@Serializable
@SerialName("look")
data class Look(val yaw: Float, val pitch: Float) : Message {
    init {
        require(pitch in -90f..90f) { "pitch must be between -90 and 90" }
    }
}

/** Turns to look at a point. */
@Serializable
@SerialName("look-at")
data class LookAt(val x: Double, val y: Double, val z: Double) : Message

/**
 * Holds a movement key ([direction]: forward, back, left, right). With [ticks] the reply comes
 * once the key is released; without, the key stays held until `stop`.
 */
@Serializable
@SerialName("move")
data class Move(val direction: String, val ticks: Int? = null) : Message {
    init {
        require(direction in DIRECTIONS) { "direction must be one of ${DIRECTIONS.joinToString()}" }
        require(ticks == null || ticks > 0) { "ticks must be positive" }
    }

    companion object {
        val DIRECTIONS = listOf("forward", "back", "left", "right")
    }
}

/** Jumps once. */
@Serializable
@SerialName("jump")
data object Jump : Message

/** Holds or releases sneak. */
@Serializable
@SerialName("sneak")
data class Sneak(val on: Boolean) : Message

/** Holds or releases sprint. */
@Serializable
@SerialName("sprint")
data class Sprint(val on: Boolean) : Message

/** Stops any movement command and releases every key nebs is holding. */
@Serializable
@SerialName("stop")
data object StopMoving : Message

/**
 * Walks in a straight line to [x], [z] (jumping over one-block steps), replying when it arrives.
 * Not a pathfinder: it gives up when stuck or after [timeout] seconds.
 */
@Serializable
@SerialName("goto")
data class Goto(val x: Double, val z: Double, val timeout: Double = 60.0) : Message

/** Keeps walking toward a player (by name) or entity (by id) until within [distance]; runs until `stop`. */
@Serializable
@SerialName("follow")
data class Follow(val target: String, val distance: Double = 3.0) : Message
