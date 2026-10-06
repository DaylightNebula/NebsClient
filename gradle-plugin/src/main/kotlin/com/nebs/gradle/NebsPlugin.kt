package com.nebs.gradle

import com.nebs.core.SocketDefaults
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.provider.Property

/** Configuration shared by every nebs task in the project. */
abstract class NebsExtension {
    /** Socket to talk to. Defaults to `$NEBS_SOCKET`, or `<java.io.tmpdir>/nebs-client.sock`. */
    abstract val socketPath: Property<String>
}

/**
 * Adds tasks that send the same commands as `nebs-cli` to a running Nebs Client:
 *
 * - `nebs --command="connect localhost 25566"` runs any CLI command.
 * - `nebsConnect --host=localhost [--port=25566]` joins a server.
 *
 * Builds can also register their own preconfigured tasks of type [NebsCommandTask] or [NebsConnectTask].
 */
class NebsPlugin : Plugin<Project> {
    override fun apply(project: Project) {
        val extension = project.extensions.create("nebs", NebsExtension::class.java)
        extension.socketPath.convention(SocketDefaults.defaultPath().toString())

        project.tasks.withType(NebsSocketTask::class.java).configureEach { task ->
            task.group = GROUP
            task.socketPath.convention(extension.socketPath)
        }

        project.tasks.register("nebs", NebsCommandTask::class.java) { task ->
            task.description = "Sends a nebs-cli command to the client, e.g. --command=\"connect localhost 25566\"."
        }
        project.tasks.register("nebsConnect", NebsConnectTask::class.java) { task ->
            task.description = "Tells the client to join a server: --host=<host> [--port=<port>]."
        }
    }

    companion object {
        const val GROUP = "nebs"
    }
}
