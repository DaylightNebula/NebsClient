package com.nebs.gradle

import com.nebs.core.SocketClient
import com.nebs.core.SocketDefaults
import com.nebs.core.command.Commands
import com.nebs.core.message.ConnectToServer
import com.nebs.core.message.Message
import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.TaskAction
import org.gradle.api.tasks.UntrackedTask
import org.gradle.api.tasks.options.Option
import java.io.IOException
import java.nio.file.Path

private const val UNTRACKED_REASON = "Sends a message to a running game; there are no outputs to cache."

/** Base for tasks that send one [Message] to the client and fail the build if it is rejected. */
@UntrackedTask(because = UNTRACKED_REASON)
abstract class NebsSocketTask : DefaultTask() {
    @get:Input
    @get:Option(option = "socket", description = "Socket to connect to.")
    abstract val socketPath: Property<String>

    protected abstract fun message(): Message

    @TaskAction
    fun send() {
        val message = try {
            message()
        } catch (e: IllegalArgumentException) {
            throw GradleException(e.message ?: "Invalid command", e)
        }

        val socket = Path.of(socketPath.get())
        val reply = try {
            SocketClient(socket).use { it.send(message) }
        } catch (e: IOException) {
            throw GradleException(
                "Could not reach client at $socket (${e.message}). Is it running with ${SocketDefaults.ENABLE_ARG}?", e,
            )
        }

        if (!reply.success) throw GradleException(reply.detail)
        logger.lifecycle(reply.detail.ifEmpty { "ok" })
    }
}

/** Runs any `nebs-cli` command line, e.g. `connect localhost 25566`. */
@UntrackedTask(because = UNTRACKED_REASON)
abstract class NebsCommandTask : NebsSocketTask() {
    @get:Input
    @get:Option(option = "command", description = "The nebs-cli command to run, e.g. \"connect localhost 25566\".")
    abstract val command: Property<String>

    override fun message(): Message {
        val line = command.orNull ?: throw GradleException(
            "No command given. Usage: --command=\"<command>\". Commands:\n" +
                Commands.specs.joinToString("\n") { "  ${it.usage.padEnd(24)}${it.description}" },
        )
        return Commands.parse(line)
    }
}

/** Sends [ConnectToServer]: the client joins [host]:[port]. */
@UntrackedTask(because = UNTRACKED_REASON)
abstract class NebsConnectTask : NebsSocketTask() {
    @get:Input
    @get:Option(option = "host", description = "Server host name or IP.")
    abstract val host: Property<String>

    /** A string so it can be set from the command line with `--port`. */
    @get:Input
    @get:Optional
    @get:Option(option = "port", description = "Server port (default 25565).")
    abstract val port: Property<String>

    override fun message(): Message {
        val host = host.orNull ?: throw GradleException("No host given. Usage: --host=<host> [--port=<port>]")
        val port = port.orNull?.let { it.toIntOrNull() ?: throw GradleException("Invalid port '$it'") }
        return if (port == null) ConnectToServer(host) else ConnectToServer(host, port)
    }
}
