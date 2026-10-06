package com.nebs.core.command

import com.nebs.core.message.ConnectToServer
import com.nebs.core.message.Message
import com.nebs.core.message.Ping
import com.nebs.core.message.Quit
import com.nebs.core.message.SpawnClient
import java.util.UUID

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
        Spec("ping", "Show the client's name, uuid and state (loading, menu, connecting, in-world)."),
        Spec("quit", "Shut the client down."),
        Spec(
            "spawn [--name N] [--uuid U] [--instance DIR] [--template DIR]",
            "Have the client launch another offline client. All options are optional.",
        ),
    )

    /** Help text for [specs]: one line per command, or two when the usage is too long to align. */
    fun help(indent: String = "  ", column: Int = 24): String = specs.joinToString("\n") {
        if (it.usage.length < column) "$indent${it.usage.padEnd(column)}${it.description}"
        else "$indent${it.usage}\n$indent${" ".repeat(column)}${it.description}"
    }

    /** Parses a whitespace-separated command line, e.g. `connect localhost 25566`. */
    fun parse(line: String): Message = parse(line.trim().split(Regex("\\s+")).filter { it.isNotEmpty() })

    fun parse(words: List<String>): Message {
        if (words.isEmpty()) throw CommandException("no command given")
        val params = words.drop(1)
        return when (val command = words.first().lowercase()) {
            "connect" -> connect(params)
            "ping" -> noParams(command, params, Ping)
            "quit" -> noParams(command, params, Quit)
            "spawn" -> spawn(params)
            else -> throw CommandException("unknown command '$command'")
        }
    }

    private fun noParams(command: String, params: List<String>, message: Message): Message {
        if (params.isNotEmpty()) throw CommandException("$command takes no arguments")
        return message
    }

    private fun spawn(params: List<String>): SpawnClient {
        val flags = Flags.parse(params, setOf("name", "uuid", "instance", "template"))
        if (flags.positional.isNotEmpty()) throw CommandException("unexpected argument '${flags.positional.first()}'")
        flags["uuid"]?.let {
            try {
                UUID.fromString(it)
            } catch (_: IllegalArgumentException) {
                throw CommandException("invalid uuid '$it'")
            }
        }
        return SpawnClient(flags["name"], flags["uuid"], flags["instance"], flags["template"])
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
