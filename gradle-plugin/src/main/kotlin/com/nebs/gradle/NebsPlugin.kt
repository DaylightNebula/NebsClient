package com.nebs.gradle

import com.nebs.core.NebsHome
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.provider.Property

/** Configuration shared by every nebs task in the project. Everything is optional. */
abstract class NebsExtension {
    /**
     * nebs home: client template, instance folders and the list of running clients.
     * Defaults to `$NEBS_HOME`, or `.nebs` in the root project directory.
     */
    abstract val home: DirectoryProperty

    /** Client template to install into / launch from. Defaults to `<home>/template`. */
    abstract val templateDir: DirectoryProperty

    /** Where launched clients get their instance folders. Defaults to `<home>/instances`. */
    abstract val instancesDir: DirectoryProperty

    /** Talk to the client on this socket instead of choosing from the running clients. */
    abstract val socketPath: Property<String>

    /** Mod jars to install into the template. Defaults to the nebs mod bundled with this plugin. */
    abstract val modJars: ConfigurableFileCollection
}

/**
 * Adds tasks that do what `nebs-cli` does:
 *
 * - `nebs --command="connect localhost 25566"` runs any client command; `nebsConnect` sends `connect`.
 *   Both go to the only running client, or to `--client=<name>` (repeatable) / `--all` / `--socket=<path>`.
 * - `nebsListClients`, `nebsInstallClient`, `nebsLaunchClient`, `nebsStopClient` manage clients.
 *
 * Builds can also register their own preconfigured tasks of any of these types.
 */
class NebsPlugin : Plugin<Project> {
    override fun apply(project: Project) {
        val extension = project.extensions.create("nebs", NebsExtension::class.java)
        val rootDir = project.rootProject.layout.projectDirectory
        extension.home.convention(
            project.providers.environmentVariable(NebsHome.ENV)
                .map { rootDir.dir(it) } // absolute values replace the root dir
                .orElse(rootDir.dir(NebsHome.DIR_NAME)),
        )

        val home = extension.home.map { it.asFile.absolutePath }
        project.tasks.withType(NebsTask::class.java).configureEach { task ->
            task.group = GROUP
            task.home.convention(home)
        }
        project.tasks.withType(NebsTargetedTask::class.java).configureEach { task ->
            task.socketPath.convention(extension.socketPath)
        }
        project.tasks.withType(NebsInstallClientTask::class.java).configureEach { task ->
            task.template.convention(extension.templateDir.map { it.asFile.absolutePath })
            task.modJars.convention(extension.modJars)
        }
        project.tasks.withType(NebsLaunchClientTask::class.java).configureEach { task ->
            task.template.convention(extension.templateDir.map { it.asFile.absolutePath })
            task.modJars.convention(extension.modJars)
            task.instancesDir.convention(extension.instancesDir.map { it.asFile.absolutePath })
        }

        project.tasks.register("nebs", NebsCommandTask::class.java) { task ->
            task.description = "Sends a client command, e.g. --command=\"connect localhost 25566\" [--client=N | --all]."
        }
        project.tasks.register("nebsConnect", NebsConnectTask::class.java) { task ->
            task.description = "Tells clients to join a server: --host=<host> [--port=<port>] [--client=N | --all]."
        }
        project.tasks.register("nebsListClients", NebsListClientsTask::class.java) { task ->
            task.description = "Lists running clients with their state and server."
        }
        project.tasks.register("nebsInstallClient", NebsInstallClientTask::class.java) { task ->
            task.description = "Downloads a complete Fabric client with the nebs mod into the template folder."
        }
        project.tasks.register("nebsLaunchClient", NebsLaunchClientTask::class.java) { task ->
            task.description = "Starts an offline client: [--name=N] [--uuid=U] [--instance=DIR] [--wait]."
        }
        project.tasks.register("nebsStopClient", NebsStopClientTask::class.java) { task ->
            task.description = "Stops running clients: [--client=N]... or --all."
        }
    }

    companion object {
        const val GROUP = "nebs"
    }
}
