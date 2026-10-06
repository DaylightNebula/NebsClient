package com.nebs.core.message

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** Leaves the current server and returns to the title screen. */
@Serializable
@SerialName("disconnect")
data object Disconnect : Message

/** Respawns after death (the "Respawn" button on the death screen). */
@Serializable
@SerialName("respawn")
data object Respawn : Message

/**
 * Waits until the client reaches [state]: one of [STATES]. `alive`/`dead` refer to the player.
 * Fails after [timeout] seconds.
 */
@Serializable
@SerialName("wait-for")
data class WaitFor(val state: String, val timeout: Double = 30.0) : Message {
    init {
        require(state in STATES) { "state must be one of ${STATES.joinToString()}, was '$state'" }
        require(timeout > 0) { "timeout must be positive" }
    }

    companion object {
        val STATES = listOf("loading", "menu", "connecting", "in-world", "alive", "dead")
    }
}
