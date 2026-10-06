package com.nebs.core.message

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

/**
 * Turns the connection into an event stream: after the ok [Response], the client pushes an [Event]
 * line whenever one of [events] (default: all of [Event.TYPES]) happens, until the connection closes.
 */
@Serializable
@SerialName("subscribe")
data class Subscribe(val events: List<String> = emptyList()) : Message {
    init {
        val unknown = events - Event.TYPES.toSet()
        require(unknown.isEmpty()) { "unknown event(s) ${unknown.joinToString()}; known: ${Event.TYPES.joinToString()}" }
    }
}

/** Something that happened in the client, pushed to subscribers. [time] is epoch milliseconds. */
@Serializable
@SerialName("event")
data class Event(val event: String, val data: JsonObject, val time: Long) : Message {
    companion object {
        val TYPES = listOf(
            "chat", // player chat: sender, message
            "system", // system/game messages: message
            "action-bar", // overlay text above the hotbar: message
            "join", // joined a server: server
            "disconnect", // left or lost the server
            "death", // the player died
            "respawn", // the player respawned
            "health", // health or food changed: health, food
            "screen", // a screen opened or closed: screen, title
            "inventory", // inventory contents changed
        )
    }
}
