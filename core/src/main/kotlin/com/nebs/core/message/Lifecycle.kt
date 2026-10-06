package com.nebs.core.message

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Asks the client for its status. The [Response] `data` holds `name`, `uuid`, `state` (`loading`,
 * `menu`, `connecting` or `in-world`) and, when connected, `server`.
 *
 * Wire form: `{"type":"ping"}`
 */
@Serializable
@SerialName("ping")
data object Ping : Message

/**
 * Asks the client to shut down cleanly.
 *
 * Wire form: `{"type":"quit"}`
 */
@Serializable
@SerialName("quit")
data object Quit : Message

/**
 * Asks the client to launch another offline client from an installed template. Every field is
 * optional; see `com.nebs.core.launcher.LaunchSpec` for the defaults. The [Response] `data` holds
 * `name`, `uuid`, `socket`, `pid` and `instance` of the new client.
 *
 * Wire form: `{"type":"spawn","name":"Alice"}`
 */
@Serializable
@SerialName("spawn")
data class SpawnClient(
    val name: String? = null,
    val uuid: String? = null,
    val instance: String? = null,
    val template: String? = null,
) : Message
