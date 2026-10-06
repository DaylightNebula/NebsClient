package com.nebs.mod.handler

import com.nebs.core.message.ConnectToServer
import com.nebs.core.message.Response
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.screens.ConnectScreen
import net.minecraft.client.gui.screens.TitleScreen
import net.minecraft.client.multiplayer.ClientLevel
import net.minecraft.client.multiplayer.ServerData
import net.minecraft.client.multiplayer.resolver.ServerAddress

object ConnectToServerHandler {
    fun handle(message: ConnectToServer): Response {
        val address = ServerAddress(message.host, message.port)
        val minecraft = Minecraft.getInstance()

        minecraft.execute {
            WindowFocus.request(minecraft)

            // Leave the current world/server first, the same way the pause menu's disconnect button does.
            if (minecraft.level != null) {
                minecraft.disconnectFromWorld(ClientLevel.DEFAULT_QUIT_MESSAGE)
            }
            val serverData = ServerData(address.toString(), address.toString(), ServerData.Type.OTHER)
            ConnectScreen.startConnecting(TitleScreen(), minecraft, address, serverData, false, null)
        }

        return Response.ok("Connecting to $address")
    }
}
