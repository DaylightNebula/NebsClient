package dsh.nebsclient.mod

import dsh.nebsclient.core.ClientEntry
import dsh.nebsclient.core.ClientRegistry
import dsh.nebsclient.core.NebsHome
import dsh.nebsclient.core.SocketDefaults
import dsh.nebsclient.mod.events.EventHub
import dsh.nebsclient.mod.runtime.Ticker
import dsh.nebsclient.mod.socket.SocketBridge
import net.fabricmc.api.ClientModInitializer
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents
import net.fabricmc.loader.api.FabricLoader
import net.minecraft.client.Minecraft
import org.slf4j.LoggerFactory
import java.nio.file.Files
import java.nio.file.Path

object NebsClientMod : ClientModInitializer {
    const val MOD_ID = "nebs_client"
    val logger = LoggerFactory.getLogger(MOD_ID)

    /** The nebs home this client registered in; `null` when socket communication is off. */
    var home: Path? = null
        private set

    override fun onInitializeClient() {
        val args = FabricLoader.getInstance().getLaunchArguments(true)
        if (SocketDefaults.ENABLE_ARG !in args) {
            logger.info("Socket communication disabled (launch with {} to enable)", SocketDefaults.ENABLE_ARG)
            return
        }

        // Relative defaults resolve against the game directory, which is the working directory.
        val home = NebsHome.resolve(argValue(args, SocketDefaults.HOME_ARG)?.let(Path::of))
        val socket = argValue(args, SocketDefaults.PATH_ARG)?.let(Path::of)
            ?: ClientRegistry.socketFor(ProcessHandle.current().pid())

        Ticker.init()
        EventHub.init()
        val bridge = SocketBridge(socket)
        bridge.start()
        this.home = home

        val user = Minecraft.getInstance().user
        val entry = ClientEntry.forCurrentProcess(
            name = argValue(args, "--username") ?: user.name,
            uuid = argValue(args, "--uuid")?.let { ClientRegistry.parseUuid(it).toString() } ?: user.profileId.toString(),
            socket = socket,
            gameDir = FabricLoader.getInstance().gameDir,
        )
        val registration = ClientRegistry.register(home, entry)
        logger.info("Registered as {} in {}", entry.name, registration.parent)

        ClientLifecycleEvents.CLIENT_STOPPING.register {
            bridge.close()
            Files.deleteIfExists(registration)
        }
    }

    /** Supports `--flag <value>` and `--flag=<value>`. */
    private fun argValue(args: Array<String>, flag: String): String? {
        val prefix = "$flag="
        args.forEachIndexed { i, arg ->
            if (arg == flag) return args.getOrNull(i + 1)?.takeIf { it.isNotEmpty() }
            if (arg.startsWith(prefix)) return arg.removePrefix(prefix)
        }
        return null
    }
}
