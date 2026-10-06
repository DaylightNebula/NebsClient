package com.nebs.mod.handler

import com.nebs.core.message.Attack
import com.nebs.core.message.ClickButton
import com.nebs.core.message.ClickSlot
import com.nebs.core.message.CloseScreen
import com.nebs.core.message.ConnectToServer
import com.nebs.core.message.Disconnect
import com.nebs.core.message.DropItem
import com.nebs.core.message.Event
import com.nebs.core.message.Follow
import com.nebs.core.message.GetBlock
import com.nebs.core.message.GetBlocks
import com.nebs.core.message.GetChatHistory
import com.nebs.core.message.GetEffects
import com.nebs.core.message.GetEntities
import com.nebs.core.message.GetInventory
import com.nebs.core.message.GetLogs
import com.nebs.core.message.GetLookTarget
import com.nebs.core.message.GetOptions
import com.nebs.core.message.GetPerf
import com.nebs.core.message.GetPlayers
import com.nebs.core.message.GetScoreboard
import com.nebs.core.message.GetScreen
import com.nebs.core.message.GetStatus
import com.nebs.core.message.GetWorld
import com.nebs.core.message.Goto
import com.nebs.core.message.HoldItem
import com.nebs.core.message.InteractEntity
import com.nebs.core.message.Jump
import com.nebs.core.message.Look
import com.nebs.core.message.LookAt
import com.nebs.core.message.Message
import com.nebs.core.message.Mine
import com.nebs.core.message.Move
import com.nebs.core.message.OpenInventory
import com.nebs.core.message.Ping
import com.nebs.core.message.Place
import com.nebs.core.message.Quit
import com.nebs.core.message.ReloadResources
import com.nebs.core.message.Respawn
import com.nebs.core.message.Response
import com.nebs.core.message.RunCommand
import com.nebs.core.message.SelectSlot
import com.nebs.core.message.SendChat
import com.nebs.core.message.SetDebugOverlay
import com.nebs.core.message.SetHud
import com.nebs.core.message.SetOption
import com.nebs.core.message.SetWindowSize
import com.nebs.core.message.Sneak
import com.nebs.core.message.SpawnClient
import com.nebs.core.message.Sprint
import com.nebs.core.message.StopMoving
import com.nebs.core.message.Subscribe
import com.nebs.core.message.SwapHands
import com.nebs.core.message.TakeScreenshot
import com.nebs.core.message.TypeText
import com.nebs.core.message.UseItem
import com.nebs.core.message.UseOn
import com.nebs.core.message.WaitFor
import com.nebs.core.message.WaitForChat
import com.nebs.mod.NebsClientMod.logger

/**
 * Routes each core [Message] to its handler. The `when` is exhaustive over the sealed [Message]
 * hierarchy, so adding a message to `core` won't compile until it is handled here.
 *
 * Called from socket threads; handlers hop onto the client thread themselves. Any exception a
 * handler throws becomes an error [Response].
 */
object MessageDispatcher {
    fun dispatch(message: Message): Response = try {
        route(message)
    } catch (e: Exception) {
        if (e !is IllegalStateException && e !is IllegalArgumentException) logger.warn("Failed to handle {}", message, e)
        Response.error(e.message ?: e.javaClass.simpleName)
    }

    private fun route(message: Message): Response = when (message) {
        // Session
        is ConnectToServer -> ConnectToServerHandler.handle(message)
        Disconnect -> SessionHandlers.disconnect()
        Ping -> PingHandler.handle()
        is WaitFor -> SessionHandlers.waitFor(message)
        Respawn -> SessionHandlers.respawn()
        Quit -> QuitHandler.handle()
        is SpawnClient -> SpawnHandler.handle(message)

        // Observation
        GetStatus -> ObservationHandlers.status()
        GetInventory -> ObservationHandlers.inventory()
        is GetBlock -> ObservationHandlers.block(message.x, message.y, message.z)
        is GetBlocks -> ObservationHandlers.blocks(message)
        GetLookTarget -> ObservationHandlers.lookTarget()
        is GetEntities -> ObservationHandlers.entities(message)
        GetPlayers -> ObservationHandlers.players()
        GetWorld -> ObservationHandlers.world()
        GetScreen -> GuiHandlers.screen()
        is GetChatHistory -> ChatHandlers.history(message.count)
        GetScoreboard -> ObservationHandlers.scoreboard()
        GetEffects -> ObservationHandlers.effects()

        // Movement
        is Look -> MovementHandlers.look(message)
        is LookAt -> MovementHandlers.lookAt(message)
        is Move -> MovementHandlers.move(message)
        Jump -> MovementHandlers.jump()
        is Sneak -> MovementHandlers.sneak(message.on)
        is Sprint -> MovementHandlers.sprint(message.on)
        StopMoving -> MovementHandlers.stop()
        is Goto -> MovementHandlers.goto(message)
        is Follow -> MovementHandlers.follow(message)

        // Interaction
        is Attack -> InteractionHandlers.attack(message)
        is UseItem -> InteractionHandlers.use(message)
        is UseOn -> InteractionHandlers.useOn(message)
        is Mine -> InteractionHandlers.mine(message)
        is Place -> InteractionHandlers.place(message)
        is SelectSlot -> InteractionHandlers.selectSlot(message.slot)
        is HoldItem -> InteractionHandlers.hold(message)
        is DropItem -> InteractionHandlers.drop(message.all)
        SwapHands -> InteractionHandlers.swapHands()
        is InteractEntity -> InteractionHandlers.interact(message.entity)

        // Inventory and screens
        OpenInventory -> GuiHandlers.openInventory()
        CloseScreen -> GuiHandlers.closeScreen()
        is ClickSlot -> GuiHandlers.clickSlot(message)
        is ClickButton -> GuiHandlers.clickButton(message)
        is TypeText -> GuiHandlers.typeText(message.text)

        // Chat
        is SendChat -> ChatHandlers.chat(message.message)
        is RunCommand -> ChatHandlers.command(message.command)
        is WaitForChat -> ChatHandlers.waitForChat(message)

        // Capture and debugging
        is TakeScreenshot -> CaptureHandlers.screenshot(message)
        is SetWindowSize -> CaptureHandlers.setWindow(message)
        is SetOption -> CaptureHandlers.setOption(message)
        GetOptions -> CaptureHandlers.options()
        is SetHud -> CaptureHandlers.hud(message.visible)
        is SetDebugOverlay -> CaptureHandlers.debugOverlay(message.visible)
        GetPerf -> CaptureHandlers.perf()
        is GetLogs -> CaptureHandlers.logs(message)
        ReloadResources -> CaptureHandlers.reloadResources()

        // Subscriptions are handled by the socket bridge, which owns the connection.
        is Subscribe -> Response.error("subscribe must be the only message on its connection")
        is Event, is Response -> Response.error("${message.javaClass.simpleName} messages are only sent by the client")
    }
}
