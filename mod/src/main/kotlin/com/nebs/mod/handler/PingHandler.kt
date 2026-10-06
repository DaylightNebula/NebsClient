package com.nebs.mod.handler

import com.nebs.core.message.Response
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.screens.ConnectScreen
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import java.util.function.Supplier

object PingHandler {
    fun handle(): Response {
        val minecraft = Minecraft.getInstance()
        // Reading state on the client thread doubles as a readiness check: before the main loop is
        // running the task never executes and we report "loading".
        val data = try {
            minecraft.submit(Supplier { snapshot(minecraft) }).get(2, TimeUnit.SECONDS)
        } catch (_: TimeoutException) {
            null
        }
        return Response.ok("pong", data ?: mapOf("state" to "loading"))
    }

    private fun snapshot(minecraft: Minecraft): Map<String, String> = buildMap {
        put("name", minecraft.user.name)
        put("uuid", minecraft.user.profileId.toString())
        put(
            "state",
            when {
                minecraft.gui.overlay() != null -> "loading"
                minecraft.level != null -> "in-world"
                minecraft.gui.screen() is ConnectScreen || minecraft.connection != null -> "connecting"
                else -> "menu"
            },
        )
        minecraft.currentServer?.let { put("server", it.ip) }
    }
}
