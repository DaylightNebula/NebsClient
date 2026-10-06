package com.nebs.core.message

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

/**
 * Reply sent by the client for every message it receives.
 *
 * [data] carries simple key/value results (e.g. `ping`, `spawn`); [result] carries structured
 * results (e.g. `inventory`, `entities`).
 *
 * Wire form: `{"type":"response","success":true,"detail":"Connecting to mc.example.com:25565","data":{}}`
 */
@Serializable
@SerialName("response")
data class Response(
    val success: Boolean,
    val detail: String = "",
    val data: Map<String, String> = emptyMap(),
    val result: JsonElement? = null,
) : Message {
    companion object {
        fun ok(detail: String = "", data: Map<String, String> = emptyMap()) = Response(true, detail, data)
        fun result(detail: String, result: JsonElement) = Response(true, detail, result = result)
        fun error(detail: String) = Response(false, detail)
    }
}
