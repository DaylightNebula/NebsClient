package com.nebs.core.message

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** Opens the player inventory screen. */
@Serializable
@SerialName("open-inventory")
data object OpenInventory : Message

/** Closes the open screen (and container). */
@Serializable
@SerialName("close-screen")
data object CloseScreen : Message

/**
 * Clicks a slot of the open container (slot numbers as listed by `screen`; the player inventory
 * when no container is open). [mode] is one of [MODES]; [button] is the mouse button, or the
 * hotbar slot for `swap`.
 */
@Serializable
@SerialName("click-slot")
data class ClickSlot(val slot: Int, val button: Int = 0, val mode: String = "pickup") : Message {
    init {
        require(mode in MODES) { "mode must be one of ${MODES.joinToString()}" }
    }

    companion object {
        val MODES = listOf("pickup", "quick_move", "swap", "clone", "throw", "quick_craft", "pickup_all")
    }
}

/** Clicks a button on the open screen, by its label or by `#index` from `screen`. */
@Serializable
@SerialName("click-button")
data class ClickButton(val label: String) : Message

/** Types text into the focused text field of the open screen. */
@Serializable
@SerialName("type-text")
data class TypeText(val text: String) : Message
