package dsh.nebsclient.mod.handler

import dsh.nebsclient.core.message.Response
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.screens.ConnectScreen
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException

object PingHandler {
    fun handle(): Response {
        val minecraft = Minecraft.getInstance()
        // Reading state on the client thread doubles as a readiness check: before the main loop is
        // running the task never executes and we report "loading".
        val data = try {
            CompletableFuture.supplyAsync({ snapshot(minecraft) }, minecraft).get(2, TimeUnit.SECONDS)
        } catch (_: TimeoutException) {
            null
        }
        return Response.ok("pong", data ?: mapOf("state" to "loading"))
    }

    /** `loading`, `in-world`, `connecting` or `menu`. Call on the client thread. */
    fun state(minecraft: Minecraft): String = when {
        minecraft.gui.overlay() != null -> "loading"
        minecraft.level != null -> "in-world"
        minecraft.gui.screen() is ConnectScreen || minecraft.connection != null -> "connecting"
        else -> "menu"
    }

    private fun snapshot(minecraft: Minecraft): Map<String, String> = buildMap {
        put("name", minecraft.user.name)
        put("uuid", minecraft.user.profileId.toString())
        put("state", state(minecraft))
        minecraft.currentServer?.let { put("server", it.ip) }
    }
}
