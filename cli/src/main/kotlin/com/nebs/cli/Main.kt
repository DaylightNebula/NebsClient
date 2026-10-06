package com.nebs.cli

import com.nebs.core.ClientRegistry
import com.nebs.core.NebsHome
import com.nebs.core.SocketDefaults
import com.nebs.core.command.CommandException
import com.nebs.core.command.Commands
import java.io.IOException
import java.nio.file.Path
import kotlin.system.exitProcess

/** Global options: where the nebs home is, and which clients socket commands go to. */
data class Options(
    val home: Path,
    val socket: Path? = null,
    val clients: List<String> = emptyList(),
    val all: Boolean = false,
)

private val USAGE = buildString {
    appendLine("Usage: nebs-cli [--home DIR] [--client NAME]... [--all] [--socket PATH] [command [args...]]")
    appendLine()
    appendLine("Controls Minecraft clients running the nebs mod. With no command, starts an interactive shell.")
    appendLine()
    appendLine("Options:")
    appendLine("  --home DIR              nebs home (default: \$${NebsHome.ENV}, or ./${NebsHome.DIR_NAME}). Holds the client")
    appendLine("                          template, instance folders and the list of running clients.")
    appendLine("  --client NAME           Send to this client (repeatable). See 'client list'.")
    appendLine("  --all                   Send to every running client.")
    appendLine("  --socket PATH           Send to the client listening on this socket, registered or not.")
    appendLine("                          Without any of these, commands go to the only running client.")
    appendLine()
    appendLine("Client commands (sent over the socket):")
    appendLine(Commands.help())
    appendLine()
    appendLine("Local commands:")
    appendLine(ClientCommands.USAGE)
    appendLine("  ${"help".padEnd(24)}Show this message.")
    append("  ${"exit".padEnd(24)}Leave the interactive shell.")
}

fun main(args: Array<String>) {
    val (options, rest) = try {
        parseOptions(args.toList())
    } catch (e: CommandException) {
        System.err.println("error: ${e.message}")
        exitProcess(2)
    }

    if (rest.isEmpty()) {
        interactive(options)
    } else {
        exitProcess(if (run(options, rest)) 0 else 1)
    }
}

/** Splits leading global options from the command. */
private fun parseOptions(args: List<String>): Pair<Options, List<String>> {
    var home: Path? = null
    var socket: Path? = null
    val clients = mutableListOf<String>()
    var all = false
    var i = 0
    while (i < args.size && args[i].startsWith("--")) {
        val arg = args[i++]
        val flag = arg.substringBefore('=')
        fun value(): String = if ('=' in arg) arg.substringAfter('=') else args.getOrNull(i++) ?: throw CommandException("$flag requires a value")
        when (flag) {
            "--home" -> home = Path.of(value())
            "--socket" -> socket = Path.of(value())
            "--client" -> clients += value()
            "--all" -> all = true
            "--help", "-h" -> return Options(NebsHome.resolve(home)) to listOf("help")
            else -> throw CommandException("unknown option $flag")
        }
    }
    return Options(NebsHome.resolve(home), socket, clients, all) to args.drop(i)
}

private fun interactive(options: Options) {
    println("nebs-cli — home: ${options.home} (type 'help' for commands)")
    while (true) {
        print("> ")
        System.out.flush()
        val words = readlnOrNull()?.trim()?.split(Regex("\\s+"))?.filter { it.isNotEmpty() } ?: return
        if (words.isEmpty()) continue
        if (words[0] == "exit") return
        run(options, words)
    }
}

/** Runs one command. Returns whether it succeeded. */
private fun run(options: Options, words: List<String>): Boolean {
    val command = words.first().lowercase()
    try {
        return when (command) {
            "help", "-h", "--help" -> {
                println(USAGE)
                true
            }
            "client" -> ClientCommands.run(options, words.drop(1))
            else -> send(options, words)
        }
    } catch (e: CommandException) {
        System.err.println("error: ${e.message}")
        return false
    } catch (e: Exception) {
        // Missing template, instance already running, download failure, client never became ready, ...
        System.err.println("error: ${e.message}")
        return false
    }
}

/** Sends a client command to the selected clients and prints each reply. */
private fun send(options: Options, words: List<String>): Boolean {
    val message = Commands.parse(words)
    val targets = ClientRegistry.select(options.home, options.socket, options.clients, options.all)
    val labelled = targets.size > 1 || options.all

    var ok = true
    for ((target, result) in ClientRegistry.broadcast(targets, message)) {
        val prefix = if (labelled) "${target.name}: " else ""
        result.fold(
            onSuccess = { reply ->
                val detail = reply.detail.ifEmpty { "ok" }
                if (reply.success) println("$prefix$detail") else System.err.println("${prefix}error: $detail")
                reply.data.forEach { (key, value) -> println("  $key: $value") }
                ok = ok && reply.success
            },
            onFailure = { e ->
                val why = if (e is IOException) "could not reach client at ${target.socket} (${e.message}). Is it running with ${SocketDefaults.ENABLE_ARG}?" else e.message
                System.err.println("${prefix}error: $why")
                ok = false
            },
        )
    }
    return ok
}
