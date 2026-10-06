package com.nebs.mod

import com.nebs.core.SocketDefaults
import com.nebs.mod.socket.SocketBridge
import net.fabricmc.api.ClientModInitializer
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents
import net.fabricmc.loader.api.FabricLoader
import org.slf4j.LoggerFactory
import java.nio.file.Path

object NebsClientMod : ClientModInitializer {
    const val MOD_ID = "nebs_client"
    val logger = LoggerFactory.getLogger(MOD_ID)

    override fun onInitializeClient() {
        val args = FabricLoader.getInstance().getLaunchArguments(true)
        if (SocketDefaults.ENABLE_ARG !in args) {
            logger.info("Socket communication disabled (launch with {} to enable)", SocketDefaults.ENABLE_ARG)
            return
        }

        val bridge = SocketBridge(socketPath(args))
        bridge.start()
        ClientLifecycleEvents.CLIENT_STOPPING.register { bridge.close() }
    }

    /** Supports `--socket-path <path>` and `--socket-path=<path>`, falling back to the shared default. */
    private fun socketPath(args: Array<String>): Path {
        val prefix = "${SocketDefaults.PATH_ARG}="
        args.forEachIndexed { i, arg ->
            if (arg == SocketDefaults.PATH_ARG) args.getOrNull(i + 1)?.let { return Path.of(it) }
            if (arg.startsWith(prefix)) return Path.of(arg.removePrefix(prefix))
        }
        return SocketDefaults.defaultPath()
    }
}
