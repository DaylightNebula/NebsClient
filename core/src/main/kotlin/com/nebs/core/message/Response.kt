package com.nebs.core.message

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Reply sent by the client for every message it receives.
 *
 * Wire form: `{"type":"response","success":true,"detail":"Connecting to mc.example.com:25565"}`
 */
@Serializable
@SerialName("response")
data class Response(
    val success: Boolean,
    val detail: String = "",
) : Message {
    companion object {
        fun ok(detail: String = "") = Response(true, detail)
        fun error(detail: String) = Response(false, detail)
    }
}
