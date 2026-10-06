package com.nebs.core.command

import com.nebs.core.message.ConnectToServer
import com.nebs.core.message.Message

/** Thrown when a command line can't be turned into a [Message]. */
class CommandException(message: String) : IllegalArgumentException(message)

/**
 * The text commands shared by every front end (the CLI and the Gradle plugin), so both accept
 * exactly the same syntax.
 */
object Commands {
    data class Spec(val usage: String, val description: String)

    val specs = listOf(
        Spec(
            "connect <host> [port]",
            "Tell the client to join a server (port defaults to ${ConnectToServer.DEFAULT_PORT}). Also accepts <host>:<port>.",
        ),
    )

    /** Parses a whitespace-separated command line, e.g. `connect localhost 25566`. */
    fun parse(line: String): Message = parse(line.trim().split(Regex("\\s+")).filter { it.isNotEmpty() })

    fun parse(words: List<String>): Message {
        if (words.isEmpty()) throw CommandException("no command given")
        val params = words.drop(1)
        return when (val command = words.first().lowercase()) {
            "connect" -> connect(params)
            else -> throw CommandException("unknown command '$command'")
        }
    }

    private fun connect(params: List<String>): ConnectToServer {
        fun port(text: String) = text.toIntOrNull() ?: throw CommandException("invalid port '$text'")
        return when (params.size) {
            1 -> {
                val host = params[0].substringBeforeLast(':')
                val port = params[0].substringAfterLast(':', "")
                if (port.isEmpty() || host.isEmpty()) ConnectToServer(params[0]) else ConnectToServer(host, port(port))
            }
            2 -> ConnectToServer(params[0], port(params[1]))
            else -> throw CommandException("usage: connect <host> [port]")
        }
    }
}
