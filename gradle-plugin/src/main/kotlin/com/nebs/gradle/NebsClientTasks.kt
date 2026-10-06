package com.nebs.gradle

import com.nebs.core.ClientRegistry
import com.nebs.core.launcher.ClientLauncher
import com.nebs.core.launcher.InstallSpec
import com.nebs.core.launcher.LaunchSpec
import com.nebs.core.launcher.ProgressListener
import com.nebs.core.launcher.TemplateInstaller
import com.nebs.core.message.Ping
import org.gradle.api.GradleException
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import org.gradle.api.tasks.UntrackedTask
import org.gradle.api.tasks.options.Option
import java.nio.file.Path
import java.util.UUID
import kotlin.time.Duration.Companion.minutes

/** Lists running clients with their state and server. */
@UntrackedTask(because = UNTRACKED_REASON)
abstract class NebsListClientsTask : NebsTask() {
    @TaskAction
    fun list() {
        val clients = ClientRegistry.active(homePath())
        if (clients.isEmpty()) {
            logger.lifecycle("No clients running in ${homePath()}")
            return
        }
        ClientRegistry.broadcast(clients, Ping).forEach { (client, result) ->
            val data = result.getOrNull()?.data.orEmpty()
            logger.lifecycle(
                "${client.name.padEnd(16)} ${(data["state"] ?: "unreachable").padEnd(12)} ${(data["server"] ?: "-").padEnd(20)} " +
                    "pid ${client.pid}  ${client.entry?.gameDir}",
            )
        }
    }
}

/** Downloads a complete Fabric client (with the nebs mod) into the template folder. */
@UntrackedTask(because = UNTRACKED_REASON)
abstract class NebsInstallClientTask : NebsTask() {
    @get:Input
    @get:Optional
    @get:Option(option = "template", description = "Template folder (default <home>/template).")
    abstract val template: Property<String>

    /** Mods to install. When empty, the nebs mod bundled with this plugin is used. */
    @get:InputFiles
    @get:Optional
    @get:PathSensitive(PathSensitivity.NONE)
    abstract val modJars: ConfigurableFileCollection

    @get:Input
    @get:Optional
    @get:Option(option = "java", description = "Java executable to use instead of downloading Mojang's runtime.")
    abstract val java: Property<String>

    @TaskAction
    fun install() {
        val template = TemplateInstaller.install(
            InstallSpec(
                home = homePath(),
                templateDir = template.orNull?.let(Path::of),
                modJars = modJars.files.map { it.toPath() }, // empty: the nebs mod bundled with the plugin
                java = java.orNull?.let(Path::of),
                onProgress = progressLogger(),
            ),
        )
        logger.lifecycle("Installed Minecraft ${template.info.versions.minecraft} (${template.info.profileId}) into ${template.dir}")
    }
}

/** Logs each install stage once it completes. */
private fun NebsTask.progressLogger() = ProgressListener { stage, done, total ->
    if (done == total) logger.lifecycle("$stage: $total files")
}

/** Starts an offline client from the template in its own instance folder, installing the template first if needed. */
@UntrackedTask(because = UNTRACKED_REASON)
abstract class NebsLaunchClientTask : NebsTask() {
    /** Mods to install if the template has to be installed first. When empty, the bundled nebs mod is used. */
    @get:InputFiles
    @get:Optional
    @get:PathSensitive(PathSensitivity.NONE)
    abstract val modJars: ConfigurableFileCollection

    @get:Input
    @get:Optional
    @get:Option(option = "template", description = "Template folder (default <home>/template).")
    abstract val template: Property<String>

    @get:Input
    @get:Optional
    abstract val instancesDir: Property<String>

    @get:Input
    @get:Optional
    @get:Option(option = "name", description = "Player name (default: random NebsNNNN).")
    abstract val playerName: Property<String>

    @get:Input
    @get:Optional
    @get:Option(option = "uuid", description = "Player UUID (default: the offline-mode UUID for the name).")
    abstract val uuid: Property<String>

    @get:Input
    @get:Optional
    @get:Option(option = "instance", description = "Instance folder (default: <home>/instances/<name>).")
    abstract val instance: Property<String>

    @get:Input
    @get:Optional
    @get:Option(option = "wait", description = "Wait until the client has finished loading.")
    abstract val waitUntilReady: Property<Boolean>

    @TaskAction
    fun launch() {
        val client = failOnError {
            ClientLauncher.launch(
                LaunchSpec(
                    home = homePath(),
                    template = template.orNull?.let(Path::of),
                    instanceDir = instance.orNull?.let(Path::of),
                    instancesDir = instancesDir.orNull?.let(Path::of),
                    name = playerName.orNull,
                    uuid = uuid.orNull?.let(UUID::fromString),
                    modJars = modJars.files.map { it.toPath() },
                    onInstall = { logger.lifecycle("No client template at $it yet; installing it first (about 700 MB)...") },
                    installProgress = progressLogger(),
                ),
            )
        }
        logger.lifecycle("Launched ${client.info.name} (uuid ${client.info.uuid}, pid ${client.pid})")
        logger.lifecycle("  instance: ${client.instanceDir}")
        logger.lifecycle("  log:      ${client.log}")
        if (waitUntilReady.getOrElse(false)) {
            val state = client.awaitReady(5.minutes).data["state"]
            logger.lifecycle("Ready ($state)")
        }
    }
}

/** Asks clients to quit, killing them if they don't. */
@UntrackedTask(because = UNTRACKED_REASON)
abstract class NebsStopClientTask : NebsTargetedTask() {
    @TaskAction
    fun stop() {
        val stuck = targets().filterNot { client ->
            client.stop().also { logger.lifecycle("${client.name}: ${if (it) "stopped" else "still running"}") }
        }
        if (stuck.isNotEmpty()) throw GradleException("Could not stop ${stuck.joinToString { it.name }}")
    }
}
