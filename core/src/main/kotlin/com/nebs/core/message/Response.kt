package com.nebs.core.message

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Reply sent by the client for every message it receives.
 *
 * [data] carries structured results for messages that return any (e.g. `ping`, `spawn`).
 *
 * Wire form: `{"type":"response","success":true,"detail":"Connecting to mc.example.com:25565","data":{}}`
 */
@Serializable
@SerialName("response")
data class Response(
    val success: Boolean,
    val detail: String = "",
    val data: Map<String, String> = emptyMap(),
) : Message {
    companion object {
        fun ok(detail: String = "", data: Map<String, String> = emptyMap()) = Response(true, detail, data)
        fun error(detail: String) = Response(false, detail)
    }
}
