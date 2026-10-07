package dsh.nebsclient.mod.handler

import dsh.nebsclient.core.launcher.ClientLauncher
import dsh.nebsclient.core.launcher.LaunchSpec
import dsh.nebsclient.core.message.Response
import dsh.nebsclient.core.message.SpawnClient
import dsh.nebsclient.mod.NebsClientMod
import net.fabricmc.loader.api.FabricLoader
import java.nio.file.Files
import java.nio.file.Path
import java.util.UUID

/**
 * Launches a child client in the same nebs home as this one, so it shows up in the same client list.
 * The template is, in order: the message's template, the template this client was itself launched
 * from (`-Dnebs.template`), or `<home>/template`. A missing template is installed first, with this
 * mod's own jar; the reply is sent once the child has started.
 */
object SpawnHandler {
    fun handle(message: SpawnClient): Response {
        val template = (message.template ?: System.getProperty(ClientLauncher.TEMPLATE_PROPERTY))?.let(Path::of)
        val child = try {
            ClientLauncher.launch(
                LaunchSpec(
                    home = NebsClientMod.home,
                    template = template,
                    instanceDir = message.instance?.let(Path::of),
                    name = message.name,
                    uuid = message.uuid?.let(UUID::fromString),
                    modJars = ownJar(),
                ),
            )
        } catch (e: Exception) {
            // Missing template, instance already running, bad name/uuid, or the process failed to start.
            return Response.error(e.message ?: "Could not launch client")
        }
        return Response.ok(
            "Spawned ${child.info.name} (pid ${child.pid})",
            mapOf(
                "name" to child.info.name,
                "uuid" to child.info.uuid,
                "socket" to child.info.socket,
                "pid" to child.pid.toString(),
                "instance" to child.instanceDir.toString(),
            ),
        )
    }

    /** This mod's jar, to install into a new template. Empty in a dev run, where the mod isn't a jar. */
    private fun ownJar(): List<Path> =
        FabricLoader.getInstance().getModContainer(NebsClientMod.MOD_ID).orElseThrow().origin.paths
            .filter { Files.isRegularFile(it) && it.toString().endsWith(".jar") }
}
