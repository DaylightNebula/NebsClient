package dsh.nebsclient.cli

import dsh.nebsclient.core.ClaudeSkill
import dsh.nebsclient.core.ClientRegistry
import dsh.nebsclient.core.NebsHome
import dsh.nebsclient.core.SocketDefaults
import dsh.nebsclient.core.command.CommandException
import dsh.nebsclient.core.command.Commands
import dsh.nebsclient.core.command.Flags
import dsh.nebsclient.core.message.Subscribe
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlin.concurrent.thread
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
    appendLine("  claude-skill [--dir DIR | --user]")
    appendLine("                          Install the Claude Code skill for nebs into ./.claude/skills,")
    appendLine("                          DIR, or ~/.claude/skills (--user).")
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
            "claude-skill" -> installClaudeSkill(Flags.parse(words.drop(1), setOf("dir"), setOf("user")))
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

/** `claude-skill [--dir DIR | --user]`: installs the Claude Code skill bundled in this jar. */
private fun installClaudeSkill(flags: Flags): Boolean {
    if (flags.positional.isNotEmpty()) throw CommandException("claude-skill takes no arguments")
    val dir = flags["dir"]?.let { Path.of(it).toAbsolutePath().normalize() }
    if (dir != null && "user" in flags) throw CommandException("use either --dir or --user")
    val installed = ClaudeSkill.install(dir ?: if ("user" in flags) ClaudeSkill.userDir() else ClaudeSkill.projectDir())
    println("Installed the Claude skill into $installed")
    return true
}

private val prettyJson = Json { prettyPrint = true }

/** Pretty-printed JSON, indented under a reply line. */
fun formatResult(result: JsonElement): String =
    prettyJson.encodeToString(JsonElement.serializer(), result).lines().joinToString("\n") { "  $it" }

/** Sends a client command to the selected clients and prints each reply. */
private fun send(options: Options, words: List<String>): Boolean {
    val message = Commands.parse(words)
    val targets = ClientRegistry.select(options.home, options.socket, options.clients, options.all)
    val labelled = targets.size > 1 || options.all
    if (message is Subscribe) return subscribe(message, targets, labelled)

    var ok = true
    for ((target, result) in ClientRegistry.broadcast(targets, message)) {
        val prefix = if (labelled) "${target.name}: " else ""
        result.fold(
            onSuccess = { reply ->
                val detail = reply.detail.ifEmpty { "ok" }
                if (reply.success) println("$prefix$detail") else System.err.println("${prefix}error: $detail")
                reply.data.forEach { (key, value) -> println("  $key: $value") }
                reply.result?.let { println(formatResult(it)) }
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

/** Prints events from every target, one line each, until all connections close or the user presses Ctrl-C. */
private fun subscribe(message: Subscribe, targets: List<dsh.nebsclient.core.ClientTarget>, labelled: Boolean): Boolean {
    var ok = true
    val threads = targets.map { target ->
        thread(name = "subscribe-${target.name}") {
            val prefix = if (labelled) "${target.name}: " else ""
            try {
                dsh.nebsclient.core.SocketClient(target.socket).use { client ->
                    client.subscribe(message) { event -> println("$prefix${event.event} ${event.data}") }
                }
                System.err.println("${prefix}connection closed")
            } catch (e: IOException) {
                System.err.println("${prefix}error: ${e.message}")
                ok = false
            }
        }
    }
    System.err.println("Streaming events from ${targets.joinToString { it.name }} (Ctrl-C to stop)")
    threads.forEach { it.join() }
    return ok
}
