package com.nebs.core.launcher

import com.nebs.core.ClientTarget
import com.nebs.core.SocketClient
import com.nebs.core.stopProcess
import com.nebs.core.message.Message
import com.nebs.core.message.Ping
import com.nebs.core.message.Quit
import com.nebs.core.message.Response
import kotlinx.serialization.Serializable
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import java.util.UUID
import java.util.concurrent.TimeoutException
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

/** Contents of `nebs-instance.json`, written into an instance directory when a client is launched there. */
@Serializable
data class InstanceInfo(
    val name: String,
    val uuid: String,
    val socket: String,
    val pid: Long,
    val template: String,
) {
    fun write(instanceDir: Path) {
        Files.writeString(instanceDir.resolve(FILE), metaJson.encodeToString(serializer(), this))
    }

    companion object {
        const val FILE = "nebs-instance.json"

        fun read(instanceDir: Path): InstanceInfo? {
            val file = instanceDir.resolve(FILE)
            if (!Files.isRegularFile(file)) return null
            return metaJson.decodeFromString(serializer(), Files.readString(file))
        }
    }
}

/** A client launched by [ClientLauncher], either just now or found again from its instance directory. */
class ClientProcess internal constructor(
    val instanceDir: Path,
    val info: InstanceInfo,
    private val handle: ProcessHandle?,
) {
    val pid: Long get() = info.pid
    val socket: Path get() = Path.of(info.socket)
    val profile: OfflineProfile get() = OfflineProfile(info.name, UUID.fromString(info.uuid))
    val isAlive: Boolean get() = handle?.isAlive ?: false
    val log: Path get() = instanceDir.resolve("logs/launcher-output.log")

    /** Sends [message] to this client. @throws IOException if the client isn't reachable. */
    fun send(message: Message): Response = SocketClient(socket).use { it.send(message) }

    /** Returns the client's ping reply, or `null` if it isn't listening yet. */
    fun ping(): Response? = try {
        send(Ping)
    } catch (_: IOException) {
        null
    }

    /**
     * Waits until the client has finished loading (its ping reports a state other than `loading`).
     *
     * @throws IllegalStateException if the process exits first.
     * @throws TimeoutException if it isn't ready within [timeout].
     */
    fun awaitReady(timeout: Duration = 5.minutes): Response {
        val deadline = System.nanoTime() + timeout.inWholeNanoseconds
        while (System.nanoTime() < deadline) {
            if (handle != null && !handle.isAlive) {
                throw IllegalStateException("Client ${info.name} exited before it was ready; see $log")
            }
            val reply = ping()
            if (reply != null && reply.success && reply.data["state"] != "loading") return reply
            Thread.sleep(500)
        }
        throw TimeoutException("Client ${info.name} wasn't ready after $timeout; see $log")
    }

    /** Asks the client to quit, and kills it if it hasn't exited within [timeout]. Returns whether it is gone. */
    fun quit(timeout: Duration = 30.seconds): Boolean {
        if (handle == null || !handle.isAlive) return true
        try {
            send(Quit)
        } catch (_: IOException) {
            // Not listening (still loading or hung); fall through to killing it.
        }
        return stopProcess(handle, timeout.inWholeMilliseconds)
    }

    /** This client as a command target. */
    fun toTarget(): ClientTarget = ClientTarget(info.name, socket)

    companion object {
        /** Finds the client launched in [instanceDir], or `null` if nothing was ever launched there. */
        fun attach(instanceDir: Path): ClientProcess? {
            val info = InstanceInfo.read(instanceDir) ?: return null
            return ClientProcess(instanceDir.toAbsolutePath().normalize(), info, ProcessHandle.of(info.pid).orElse(null))
        }

    }
}
