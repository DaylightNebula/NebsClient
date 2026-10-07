package dsh.nebsclient.mod.handler

import dsh.nebsclient.core.message.Response
import net.minecraft.client.Minecraft

object QuitHandler {
    fun handle(): Response {
        val minecraft = Minecraft.getInstance()
        minecraft.execute { minecraft.stop() }
        return Response.ok("Quitting")
    }
}
