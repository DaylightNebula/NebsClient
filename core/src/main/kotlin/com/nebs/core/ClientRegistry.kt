package com.nebs.core

import com.nebs.core.command.CommandException
import com.nebs.core.message.Message
import com.nebs.core.message.Quit
import com.nebs.core.message.Response
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.util.UUID
import java.util.concurrent.ExecutionException
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException

/** A running client's entry in `<home>/clients/`, written by the mod when its socket opens. */
@Serializable
data class ClientEntry(
    val name: String,
    val uuid: String,
    val pid: Long,
    val socket: String,
    val gameDir: String,
    /** Process start time (epoch ms), so a reused pid isn't mistaken for this client. */
    val startedAt: Long? = null,
) {
    companion object {
        fun forCurrentProcess(name: String, uuid: String, socket: Path, gameDir: Path): ClientEntry {
            val self = ProcessHandle.current()
            return ClientEntry(
                name = name,
                uuid = uuid,
                pid = self.pid(),
                socket = socket.toAbsolutePath().toString(),
                gameDir = gameDir.toAbsolutePath().normalize().toString(),
                startedAt = self.info().startInstant().map { it.toEpochMilli() }.orElse(null),
            )
        }
    }
}

/** A client that commands can be sent to: a registered client, or a raw socket given with `--socket`. */
class ClientTarget(val name: String, val socket: Path, val entry: ClientEntry? = null) {
    val pid: Long? get() = entry?.pid

    /** @throws IOException if the client isn't reachable. */
    fun send(message: Message): Response = SocketClient(socket).use { it.send(message) }

    fun ping(): Response? = try {
        send(com.nebs.core.message.Ping)
    } catch (_: IOException) {
        null
    }

    /** Asks the client to quit and waits for its process to exit, killing it after [timeoutMs]. Returns whether it's gone. */
    fun stop(timeoutMs: Long = 30_000): Boolean {
        val process = entry?.pid?.let { ProcessHandle.of(it).orElse(null) }
        try {
            send(Quit)
        } catch (_: IOException) {
            // Not listening (hung or still starting); fall through to killing it.
        }
        if (process == null) return true
        return stopProcess(process, timeoutMs)
    }

    override fun toString() = name
}

internal fun stopProcess(process: ProcessHandle, timeoutMs: Long): Boolean {
    try {
        process.onExit().get(timeoutMs, TimeUnit.MILLISECONDS)
    } catch (_: TimeoutException) {
        process.destroy()
        try {
            process.onExit().get(10, TimeUnit.SECONDS)
        } catch (_: TimeoutException) {
            process.destroyForcibly()
        }
    }
    return !process.isAlive
}

/**
 * Discovery of running clients. Every client started with `--socket-comm` listens on its own socket
 * (two processes can't share one) and drops a small JSON file in `<home>/clients/`; this object
 * reads that folder to list, pick, and broadcast to clients.
 *
 * Sockets live in the temp dir rather than the home folder because Unix socket paths are limited
 * to ~104 characters on macOS.
 */
object ClientRegistry {
    private val json = Json {
        ignoreUnknownKeys = true
        prettyPrint = true
    }

    /** Default socket for the client with process id [pid]. */
    fun socketFor(pid: Long): Path = Path.of(System.getProperty("java.io.tmpdir"), "nebs-$pid.sock")

    /** Writes [entry] into [home]'s registry and returns the file, to be deleted when the client stops. */
    fun register(home: Path, entry: ClientEntry): Path {
        val dir = NebsHome.clients(home)
        Files.createDirectories(dir)
        val file = dir.resolve("${entry.name}-${entry.pid}.json")
        val temp = Files.createTempFile(dir, ".entry", ".tmp")
        Files.writeString(temp, json.encodeToString(ClientEntry.serializer(), entry))
        Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
        return file
    }

    /** Running clients registered in [home], sorted by name. Entries of exited clients are deleted. */
    fun active(home: Path): List<ClientTarget> {
        val dir = NebsHome.clients(home)
        if (!Files.isDirectory(dir)) return emptyList()
        val files = Files.list(dir).use { s -> s.filter { it.fileName.toString().endsWith(".json") }.toList() }
        return files.mapNotNull { file ->
            val entry = try {
                json.decodeFromString(ClientEntry.serializer(), Files.readString(file))
            } catch (_: Exception) {
                null // half-written or foreign file
            }
            if (entry == null || !isRunning(entry)) {
                if (entry != null) Files.deleteIfExists(file)
                null
            } else {
                ClientTarget(entry.name, Path.of(entry.socket), entry)
            }
        }.sortedWith(compareBy({ it.name.lowercase() }, { it.pid }))
    }

    private fun isRunning(entry: ClientEntry): Boolean {
        val process = ProcessHandle.of(entry.pid).orElse(null) ?: return false
        if (!process.isAlive) return false
        val started = process.info().startInstant().map { it.toEpochMilli() }.orElse(null)
        return entry.startedAt == null || started == null || started == entry.startedAt
    }

    /**
     * Picks the clients a command goes to.
     *
     * - [socket] given: just that socket.
     * - [names] given: those clients (case-insensitive).
     * - [all]: every running client.
     * - otherwise: the only running client, or an error explaining how to choose.
     *
     * @throws CommandException when the selection doesn't match exactly the clients asked for.
     */
    fun select(home: Path, socket: Path? = null, names: List<String> = emptyList(), all: Boolean = false): List<ClientTarget> {
        if (socket != null) return listOf(ClientTarget(socket.toString(), socket))
        val running = active(home)
        val list = running.joinToString { it.name }
        if (running.isEmpty()) {
            throw CommandException(
                "no clients are running in $home. Launch one with 'client launch', or start a client with " +
                    "${SocketDefaults.ENABLE_ARG} ${SocketDefaults.HOME_ARG} $home",
            )
        }
        return when {
            names.isNotEmpty() -> names.flatMap { name ->
                running.filter { it.name.equals(name, ignoreCase = true) }
                    .ifEmpty { throw CommandException("no running client named '$name' (running: $list)") }
            }.distinctBy { it.pid }
            all -> running
            running.size == 1 -> running
            else -> throw CommandException("${running.size} clients are running ($list); choose with --client <name> or --all")
        }
    }

    /** Sends [message] to every target in parallel. Results are in the same order as [targets]. */
    fun broadcast(targets: List<ClientTarget>, message: Message): List<Pair<ClientTarget, Result<Response>>> {
        if (targets.size == 1) return listOf(targets[0] to runCatching { targets[0].send(message) })
        val pool = Executors.newFixedThreadPool(targets.size.coerceAtMost(16))
        try {
            val futures = targets.map { target -> target to pool.submit<Response> { target.send(message) } }
            return futures.map { (target, future) ->
                target to try {
                    Result.success(future.get())
                } catch (e: ExecutionException) {
                    Result.failure(e.cause ?: e)
                }
            }
        } finally {
            pool.shutdown()
        }
    }

    /** Accepts UUIDs with or without dashes (Minecraft's `--uuid` argument has none). */
    fun parseUuid(value: String): UUID =
        if ('-' in value) UUID.fromString(value)
        else UUID.fromString(value.replaceFirst(Regex("(\\p{XDigit}{8})(\\p{XDigit}{4})(\\p{XDigit}{4})(\\p{XDigit}{4})(\\p{XDigit}+)"), "$1-$2-$3-$4-$5"))
}
