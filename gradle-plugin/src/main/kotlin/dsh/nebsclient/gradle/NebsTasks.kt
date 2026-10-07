package dsh.nebsclient.gradle

import dsh.nebsclient.core.ClientRegistry
import dsh.nebsclient.core.ClientTarget
import dsh.nebsclient.core.SocketDefaults
import dsh.nebsclient.core.command.Commands
import dsh.nebsclient.core.message.ConnectToServer
import dsh.nebsclient.core.message.Message
import dsh.nebsclient.core.message.Subscribe
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.TaskAction
import org.gradle.api.tasks.UntrackedTask
import org.gradle.api.tasks.options.Option
import java.io.IOException
import java.nio.file.Path

private val prettyJson = Json { prettyPrint = true }

internal const val UNTRACKED_REASON = "Talks to running game clients; there are no outputs to cache."

/** Base for all nebs tasks. */
@UntrackedTask(because = UNTRACKED_REASON)
abstract class NebsTask : DefaultTask() {
    @get:Input
    @get:Option(option = "home", description = "nebs home (default: \$NEBS_HOME, or .nebs in the root project).")
    abstract val home: Property<String>

    protected fun homePath(): Path = Path.of(home.get())

    /** Runs [block], turning nebs' usage/state errors into build failures. */
    protected fun <T> failOnError(block: () -> T): T = try {
        block()
    } catch (e: IllegalArgumentException) {
        throw GradleException(e.message ?: e.toString(), e)
    } catch (e: IllegalStateException) {
        throw GradleException(e.message ?: e.toString(), e)
    }
}

/** A task that acts on some running clients: `--client` (repeatable), `--all`, `--socket`, or the only one running. */
@UntrackedTask(because = UNTRACKED_REASON)
abstract class NebsTargetedTask : NebsTask() {
    @get:Input
    @get:Optional
    @get:Option(option = "socket", description = "Send to the client listening on this socket.")
    abstract val socketPath: Property<String>

    @get:Input
    @get:Option(option = "client", description = "Send to this client (repeatable).")
    abstract val clients: ListProperty<String>

    @get:Input
    @get:Optional
    @get:Option(option = "all", description = "Send to every running client.")
    abstract val all: Property<Boolean>

    protected fun targets(): List<ClientTarget> = failOnError {
        ClientRegistry.select(homePath(), socketPath.orNull?.let(Path::of), clients.get(), all.getOrElse(false))
    }
}

/** Base for tasks that send one [Message] to the selected clients and fail the build if any rejects it. */
@UntrackedTask(because = UNTRACKED_REASON)
abstract class NebsSocketTask : NebsTargetedTask() {
    protected abstract fun message(): Message

    @TaskAction
    fun send() {
        val message = failOnError { message() }
        if (message is Subscribe) throw GradleException("subscribe streams events forever; use nebs-cli for it")
        val targets = targets()
        val labelled = targets.size > 1 || all.getOrElse(false)

        val failed = ClientRegistry.broadcast(targets, message).filter { (target, result) ->
            val prefix = if (labelled) "${target.name}: " else ""
            result.fold(
                onSuccess = { reply ->
                    val detail = reply.detail.ifEmpty { "ok" }
                    if (reply.success) logger.lifecycle("$prefix$detail") else logger.error("${prefix}error: $detail")
                    reply.data.forEach { (key, value) -> logger.lifecycle("  $key: $value") }
                    reply.result?.let { result ->
                        logger.lifecycle(prettyJson.encodeToString(JsonElement.serializer(), result).lines().joinToString("\n") { "  $it" })
                    }
                    !reply.success
                },
                onFailure = { e ->
                    val why = if (e is IOException) "Could not reach client at ${target.socket} (${e.message}). Is it running with ${SocketDefaults.ENABLE_ARG}?" else e.message
                    logger.error("$prefix$why")
                    true
                },
            )
        }
        if (failed.isNotEmpty()) throw GradleException("Failed for ${failed.joinToString { it.first.name }}")
    }
}

/** Runs any client command line, e.g. `connect localhost 25566`. */
@UntrackedTask(because = UNTRACKED_REASON)
abstract class NebsCommandTask : NebsSocketTask() {
    @get:Input
    @get:Option(option = "command", description = "The client command to run, e.g. \"connect localhost 25566\".")
    abstract val command: Property<String>

    override fun message(): Message {
        val line = command.orNull ?: throw GradleException(
            "No command given. Usage: --command=\"<command>\". Commands:\n" + Commands.help(),
        )
        return Commands.parse(line)
    }
}

/** Sends [ConnectToServer]: the clients join [host]:[port]. */
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
