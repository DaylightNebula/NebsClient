package dsh.nebsclient.mod.handler

import dsh.nebsclient.core.message.Response
import dsh.nebsclient.core.message.WaitFor
import dsh.nebsclient.mod.runtime.ClientThread
import dsh.nebsclient.mod.runtime.Ticker
import net.minecraft.client.gui.screens.TitleScreen
import net.minecraft.client.multiplayer.ClientLevel

object SessionHandlers {
    fun disconnect(): Response = ClientThread.call { mc ->
        if (mc.level == null) throw IllegalStateException("Not connected to a server")
        val server = mc.currentServer?.ip
        mc.disconnectFromWorld(ClientLevel.DEFAULT_QUIT_MESSAGE)
        mc.gui.setScreen(TitleScreen())
        Response.ok(if (server != null) "Disconnected from $server" else "Disconnected")
    }

    fun respawn(): Response = ClientThread.withPlayer { mc, player ->
        if (!player.isDeadOrDying) throw IllegalStateException("The player isn't dead")
        player.respawn()
        mc.gui.setScreen(null)
        Response.ok("Respawned")
    }

    fun waitFor(message: WaitFor): Response {
        Ticker.run((message.timeout * 1000).toLong(), "Timed out after ${message.timeout}s waiting for '${message.state}'") { mc ->
            val reached = when (message.state) {
                "alive" -> mc.player?.isDeadOrDying == false
                "dead" -> mc.player?.isDeadOrDying == true
                else -> PingHandler.state(mc) == message.state
            }
            if (reached) Unit else null
        }
        return Response.ok("Reached '${message.state}'")
    }
}
