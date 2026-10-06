package com.nebs.cli

import com.nebs.core.ClientRegistry
import com.nebs.core.command.CommandException
import com.nebs.core.command.Flags
import com.nebs.core.launcher.ClientLauncher
import com.nebs.core.launcher.InstallSpec
import com.nebs.core.launcher.LaunchSpec
import com.nebs.core.launcher.ProgressListener
import com.nebs.core.launcher.TemplateInstaller
import com.nebs.core.message.Ping
import java.nio.file.Path
import java.util.UUID
import kotlin.time.Duration.Companion.minutes

/** `nebs-cli client ...`: install the client template and manage running clients. */
object ClientCommands {
    val USAGE = """
        |  client list             Show running clients: name, state, server, pid, game folder.
        |  client install [--template DIR] [--mod JAR]... [--java PATH]
        |                          Download a complete Fabric client (with the nebs mod) into the template.
        |  client launch [--name N] [--uuid U] [--instance DIR] [--template DIR] [--width W --height H] [--wait]
        |                          Start an offline client from the template in its own instance folder,
        |                          installing the template first if needed.
        |  client stop [NAME]... [--all]
        |                          Ask clients to quit (killing them if they don't).
        |
        |  Folders are optional: the template defaults to <home>/template and instances to
        |  <home>/instances/<name>.
    """.trimMargin()

    /** Runs a `client` subcommand. Returns whether it succeeded. */
    fun run(options: Options, args: List<String>): Boolean {
        val sub = args.firstOrNull() ?: throw CommandException("usage: client list|install|launch|stop")
        val rest = args.drop(1)
        return when (sub) {
            "list" -> list(options, Flags.parse(rest, emptySet()))
            "install" -> install(options, Flags.parse(rest, setOf("template", "mod", "java")))
            "launch" -> launch(options, Flags.parse(rest, setOf("template", "instance", "name", "uuid", "width", "height"), setOf("wait")))
            "stop" -> stop(options, Flags.parse(rest, emptySet(), setOf("all")))
            else -> throw CommandException("unknown client command '$sub'")
        }
    }

    private fun list(options: Options, flags: Flags): Boolean {
        if (flags.positional.isNotEmpty()) throw CommandException("client list takes no arguments")
        val clients = ClientRegistry.active(options.home)
        if (clients.isEmpty()) {
            println("No clients running in ${options.home}")
            return true
        }
        val rows = ClientRegistry.broadcast(clients, Ping).map { (client, result) ->
            val data = result.getOrNull()?.data.orEmpty()
            listOf(client.name, data["state"] ?: "unreachable", data["server"] ?: "-", client.pid.toString(), client.entry?.gameDir ?: "")
        }
        printTable(listOf("NAME", "STATE", "SERVER", "PID", "GAME DIR"), rows)
        return true
    }

    private fun install(options: Options, flags: Flags): Boolean {
        val template = TemplateInstaller.install(
            InstallSpec(
                home = options.home,
                templateDir = flags["template"]?.let(Path::of),
                modJars = flags.all("mod").map(Path::of), // empty: the nebs mod bundled with the CLI
                java = flags["java"]?.let(Path::of),
                onProgress = progressPrinter(),
            ),
        )
        println("Installed Minecraft ${template.info.versions.minecraft} (${template.info.profileId}) into ${template.dir}")
        return true
    }

    private fun launch(options: Options, flags: Flags): Boolean {
        fun int(flag: String) = flags[flag]?.let { it.toIntOrNull() ?: throw CommandException("invalid --$flag '$it'") }
        val client = ClientLauncher.launch(
            LaunchSpec(
                home = options.home,
                template = flags["template"]?.let(Path::of),
                instanceDir = flags["instance"]?.let(Path::of),
                name = flags["name"],
                uuid = flags["uuid"]?.let {
                    try {
                        UUID.fromString(it)
                    } catch (_: IllegalArgumentException) {
                        throw CommandException("invalid uuid '$it'")
                    }
                },
                width = int("width"),
                height = int("height"),
                onInstall = { println("No client template at $it yet; installing it first (about 700 MB)...") },
                installProgress = progressPrinter(),
            ),
        )
        println("Launched ${client.info.name} (uuid ${client.info.uuid}, pid ${client.pid})")
        println("  instance: ${client.instanceDir}")
        println("  log:      ${client.log}")
        if ("wait" in flags) {
            print("Waiting for the client to load...")
            System.out.flush()
            val state = client.awaitReady(5.minutes).data["state"]
            println(" ready ($state)")
        }
        println("Send it commands with: nebs-cli --client ${client.info.name} <command>")
        return true
    }

    private fun stop(options: Options, flags: Flags): Boolean {
        val targets = ClientRegistry.select(
            options.home,
            options.socket,
            names = options.clients + flags.positional,
            all = options.all || "all" in flags,
        )
        var ok = true
        targets.forEach {
            val stopped = it.stop()
            println("${it.name}: ${if (stopped) "stopped" else "still running (pid ${it.pid})"}")
            ok = ok && stopped
        }
        return ok
    }

    private fun printTable(header: List<String>, rows: List<List<String>>) {
        val widths = header.indices.map { col -> (rows.map { it[col] } + header[col]).maxOf { it.length } }
        (listOf(header) + rows).forEach { row ->
            println(row.mapIndexed { col, cell -> if (col == row.lastIndex) cell else cell.padEnd(widths[col] + 2) }.joinToString(""))
        }
    }

    private fun progressPrinter(): ProgressListener {
        var last = ""
        return ProgressListener { stage, done, total ->
            synchronized(this) {
                val line = "  $stage $done/$total"
                if (line != last) {
                    last = line
                    System.err.print("\r$line")
                    if (done == total) System.err.println()
                }
            }
        }
    }
}
