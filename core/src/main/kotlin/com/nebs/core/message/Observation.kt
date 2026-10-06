package com.nebs.core.message

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

// Read-only queries. Each is answered with a Response whose `result` holds the data as JSON.

/** Position, rotation, health, food, XP, game mode, dimension, movement flags, selected slot. */
@Serializable
@SerialName("status")
data object GetStatus : Message

/** Every inventory slot with its item: hotbar 0-8, main 9-35, armor 36-39, offhand 40. */
@Serializable
@SerialName("inventory")
data object GetInventory : Message

/** The block state at a position. */
@Serializable
@SerialName("block")
data class GetBlock(val x: Int, val y: Int, val z: Int) : Message

/** Non-air blocks in a box (corners inclusive, at most [MAX_VOLUME] blocks). */
@Serializable
@SerialName("blocks")
data class GetBlocks(val x1: Int, val y1: Int, val z1: Int, val x2: Int, val y2: Int, val z2: Int) : Message {
    init {
        val volume = (Math.abs(x2 - x1) + 1L) * (Math.abs(y2 - y1) + 1L) * (Math.abs(z2 - z1) + 1L)
        require(volume <= MAX_VOLUME) { "box is $volume blocks; at most $MAX_VOLUME allowed" }
    }

    companion object {
        const val MAX_VOLUME = 32_768L
    }
}

/** The block or entity under the crosshair. */
@Serializable
@SerialName("look-target")
data object GetLookTarget : Message

/** Entities within [radius] blocks, nearest first, optionally only of [entityType] (e.g. `zombie` or `minecraft:zombie`). */
@Serializable
@SerialName("entities")
data class GetEntities(val radius: Double = 32.0, val entityType: String? = null) : Message {
    init {
        require(radius > 0 && radius <= 256) { "radius must be between 0 and 256" }
    }
}

/** The tab list: online players with latency and game mode. */
@Serializable
@SerialName("players")
data object GetPlayers : Message

/** Dimension, time, weather, difficulty and server info. */
@Serializable
@SerialName("world")
data object GetWorld : Message

/** The open screen: type, title, buttons, text fields and container slots. */
@Serializable
@SerialName("screen")
data object GetScreen : Message

/** The last [count] chat/system messages, each with a sequence number usable with `wait-for-chat --since`. */
@Serializable
@SerialName("chat-history")
data class GetChatHistory(val count: Int = 20) : Message {
    init {
        require(count in 1..500) { "count must be between 1 and 500" }
    }
}

/** The sidebar scoreboard: title and lines. */
@Serializable
@SerialName("scoreboard")
data object GetScoreboard : Message

/** Active potion effects. */
@Serializable
@SerialName("effects")
data object GetEffects : Message
