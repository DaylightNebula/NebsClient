package com.nebs.cli

import com.nebs.core.SocketClient
import com.nebs.core.SocketDefaults
import com.nebs.core.command.Commands
import java.io.IOException
import java.nio.file.Path
import kotlin.system.exitProcess

private val USAGE = buildString {
    appendLine("Usage: nebs-cli [--socket <path>] [command [args...]]")
    appendLine()
    appendLine("Talks to the Nebs Client mod over its local socket. The client must be")
    appendLine("started with ${SocketDefaults.ENABLE_ARG}. With no command, starts an interactive shell.")
    appendLine()
    appendLine("Options:")
    appendLine("  --socket <path>         Socket to connect to (default: \$${SocketDefaults.PATH_ENV},")
    appendLine("                          or ${SocketDefaults.defaultPath()})")
    appendLine()
    appendLine("Commands:")
    Commands.specs.forEach { appendLine("  ${it.usage.padEnd(24)}${it.description}") }
    appendLine("  ${"help".padEnd(24)}Show this message.")
    append("  ${"exit, quit".padEnd(24)}Leave the interactive shell.")
}

fun main(args: Array<String>) {
    var socket = SocketDefaults.defaultPath()
    val rest = args.toMutableList()
    while (rest.firstOrNull()?.startsWith("--socket") == true) {
        val flag = rest.removeAt(0)
        socket = Path.of(
            if (flag.startsWith("--socket=")) flag.removePrefix("--socket=")
            else rest.removeFirstOrNull() ?: fail("--socket requires a path")
        )
    }

    if (rest.isEmpty()) {
        interactive(socket)
    } else {
        exitProcess(if (run(socket, rest)) 0 else 1)
    }
}

private fun interactive(socket: Path) {
    println("nebs-cli — socket: $socket (type 'help' for commands)")
    while (true) {
        print("> ")
        System.out.flush()
        val words = readlnOrNull()?.trim()?.split(Regex("\\s+"))?.filter { it.isNotEmpty() } ?: return
        if (words.isEmpty()) continue
        if (words[0] in setOf("exit", "quit")) return
        run(socket, words)
    }
}

/** Runs one command. Returns whether it succeeded. */
private fun run(socket: Path, words: List<String>): Boolean {
    if (words.first().lowercase() in setOf("help", "-h", "--help")) {
        println(USAGE)
        return true
    }

    val message = try {
        Commands.parse(words)
    } catch (e: IllegalArgumentException) {
        System.err.println("error: ${e.message} (try 'help')")
        return false
    }

    val reply = try {
        SocketClient(socket).use { it.send(message) }
    } catch (e: IOException) {
        System.err.println("error: could not reach client at $socket (${e.message}). Is it running with ${SocketDefaults.ENABLE_ARG}?")
        return false
    }

    if (reply.success) println(reply.detail.ifEmpty { "ok" }) else System.err.println("error: ${reply.detail}")
    return reply.success
}

private fun fail(message: String): Nothing {
    System.err.println("error: $message")
    exitProcess(2)
}
