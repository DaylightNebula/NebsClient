package dsh.nebsclient.mod.handler

import dsh.nebsclient.core.message.Response
import dsh.nebsclient.core.message.WaitForChat
import dsh.nebsclient.mod.events.EventHub
import dsh.nebsclient.mod.runtime.ClientThread
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray

object ChatHandlers {
    private const val MAX_CHAT = 256

    fun chat(message: String): Response {
        if (message.startsWith("/")) return command(message.drop(1))
        if (message.length > MAX_CHAT) throw IllegalArgumentException("Chat messages are limited to $MAX_CHAT characters")
        return ClientThread.withPlayer { _, p ->
            p.connection.sendChat(message)
            Response.ok("Sent: $message")
        }
    }

    fun command(command: String): Response = ClientThread.withPlayer { _, p ->
        p.connection.sendCommand(command)
        Response.ok("Ran /$command")
    }

    fun history(count: Int): Response {
        val lines = EventHub.history(count)
        return Response.result(
            "${lines.size} messages (latest seq ${EventHub.lastSeq()})",
            buildJsonObject {
                put("lastSeq", EventHub.lastSeq())
                putJsonArray("messages") { lines.forEach { add(it.toJson()) } }
            },
        )
    }

    fun waitForChat(m: WaitForChat): Response {
        val line = EventHub.awaitChat(Regex(m.pattern), m.since ?: EventHub.lastSeq(), (m.timeout * 1000).toLong())
        return Response(true, line.text, data = mapOf("seq" to line.seq.toString(), "type" to line.type) + (line.sender?.let { mapOf("sender" to it) } ?: emptyMap()))
    }
}
