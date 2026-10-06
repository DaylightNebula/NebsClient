package com.nebs.mod.handler

import com.nebs.core.message.ConnectToServer
import com.nebs.core.message.Message
import com.nebs.core.message.Response

/**
 * Routes each core [Message] to its handler. The `when` is exhaustive over the sealed [Message]
 * hierarchy, so adding a message to `core` won't compile until it is handled here.
 *
 * Called from socket threads; handlers that touch game state must hop onto the client thread.
 */
object MessageDispatcher {
    fun dispatch(message: Message): Response = when (message) {
        is ConnectToServer -> ConnectToServerHandler.handle(message)
        is Response -> Response.error("Response messages are only sent by the client")
    }
}
