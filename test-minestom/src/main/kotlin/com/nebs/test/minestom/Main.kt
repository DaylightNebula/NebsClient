package com.nebs.test.minestom

import com.nebs.api.NebsClient
import net.minestom.server.MinecraftServer
import net.minestom.server.coordinate.Pos
import net.minestom.server.entity.Player
import net.minestom.server.event.player.AsyncPlayerConfigurationEvent
import net.minestom.server.instance.block.Block
import net.minestom.server.instance.generator.GenerationUnit
import net.minestom.server.instance.generator.Generator
import java.lang.Thread.sleep
import java.util.function.Consumer


fun main() {
    val server = MinecraftServer.init()

    // Create the instance
    val instanceManager = MinecraftServer.getInstanceManager()
    val instanceContainer = instanceManager.createInstanceContainer()

    // Set the ChunkGenerator
    instanceContainer.setGenerator(Generator { unit: GenerationUnit? ->
        unit!!.modifier().fillHeight(0, 40, Block.GRASS_BLOCK)
    })

    // Add an event callback to specify the spawning instance (and the spawn position)
    val globalEventHandler = MinecraftServer.getGlobalEventHandler()
    globalEventHandler.addListener<AsyncPlayerConfigurationEvent?>(
        AsyncPlayerConfigurationEvent::class.java,
        Consumer { event: AsyncPlayerConfigurationEvent? ->
            val player: Player = event!!.getPlayer()
            event.setSpawningInstance(instanceContainer)
            player.setRespawnPoint(Pos(0.0, 42.0, 0.0))
        })

    Thread {
        sleep(2500)

        var client = NebsClient.running().firstOrNull()
            ?: NebsClient()
        client.connect("localhost")
    }.start()

    server.start("0.0.0.0", 25565)
}