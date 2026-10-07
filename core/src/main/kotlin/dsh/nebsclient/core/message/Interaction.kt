package dsh.nebsclient.core.message

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** Attacks the entity with id [entity], or whatever is under the crosshair. */
@Serializable
@SerialName("attack")
data class Attack(val entity: Int? = null) : Message

/**
 * Right-clicks with the held item, like the use key: on the targeted block or entity, or in the air.
 * With [ticks], keeps holding use (eat, drink, draw a bow) for that long.
 */
@Serializable
@SerialName("use")
data class UseItem(val ticks: Int = 0) : Message {
    init {
        require(ticks >= 0) { "ticks must not be negative" }
    }
}

/** Right-clicks a specific block face with the held item (open a door, press a button, use a bed). */
@Serializable
@SerialName("use-on")
data class UseOn(val x: Int, val y: Int, val z: Int, val face: String = "up") : Message {
    init {
        require(face in FACES) { "face must be one of ${FACES.joinToString()}" }
    }

    companion object {
        val FACES = listOf("down", "up", "north", "south", "west", "east")
    }
}

/** Breaks a block (holding attack on it until it breaks), giving up after [timeout] seconds. */
@Serializable
@SerialName("mine")
data class Mine(val x: Int, val y: Int, val z: Int, val timeout: Double = 30.0) : Message

/** Places the held block at a position, against any solid neighbour. */
@Serializable
@SerialName("place")
data class Place(val x: Int, val y: Int, val z: Int) : Message

/** Selects a hotbar slot (0-8). */
@Serializable
@SerialName("select-slot")
data class SelectSlot(val slot: Int) : Message {
    init {
        require(slot in 0..8) { "slot must be between 0 and 8" }
    }
}

/** Puts an item (e.g. `diamond_sword`) in the main hand, moving it from the inventory if needed. */
@Serializable
@SerialName("hold")
data class HoldItem(val item: String) : Message

/** Drops one of the held item, or the whole stack. */
@Serializable
@SerialName("drop")
data class DropItem(val all: Boolean = false) : Message

/** Swaps the main-hand and off-hand items. */
@Serializable
@SerialName("swap-hands")
data object SwapHands : Message

/** Right-clicks an entity by id (trade, mount, name tag). */
@Serializable
@SerialName("interact")
data class InteractEntity(val entity: Int) : Message
