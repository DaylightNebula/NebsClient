package dsh.nebsclient.mod.handler

import dsh.nebsclient.core.message.Attack
import dsh.nebsclient.core.message.ClickButton
import dsh.nebsclient.core.message.ClickSlot
import dsh.nebsclient.core.message.CloseScreen
import dsh.nebsclient.core.message.ConnectToServer
import dsh.nebsclient.core.message.Disconnect
import dsh.nebsclient.core.message.DropItem
import dsh.nebsclient.core.message.Event
import dsh.nebsclient.core.message.Follow
import dsh.nebsclient.core.message.GetBlock
import dsh.nebsclient.core.message.GetBlocks
import dsh.nebsclient.core.message.GetChatHistory
import dsh.nebsclient.core.message.GetEffects
import dsh.nebsclient.core.message.GetEntities
import dsh.nebsclient.core.message.GetInventory
import dsh.nebsclient.core.message.GetLogs
import dsh.nebsclient.core.message.GetLookTarget
import dsh.nebsclient.core.message.GetOptions
import dsh.nebsclient.core.message.GetPerf
import dsh.nebsclient.core.message.GetPlayers
import dsh.nebsclient.core.message.GetScoreboard
import dsh.nebsclient.core.message.GetScreen
import dsh.nebsclient.core.message.GetStatus
import dsh.nebsclient.core.message.GetWorld
import dsh.nebsclient.core.message.Goto
import dsh.nebsclient.core.message.HoldItem
import dsh.nebsclient.core.message.InteractEntity
import dsh.nebsclient.core.message.Jump
import dsh.nebsclient.core.message.Look
import dsh.nebsclient.core.message.LookAt
import dsh.nebsclient.core.message.Message
import dsh.nebsclient.core.message.Mine
import dsh.nebsclient.core.message.Move
import dsh.nebsclient.core.message.OpenInventory
import dsh.nebsclient.core.message.Ping
import dsh.nebsclient.core.message.Place
import dsh.nebsclient.core.message.Quit
import dsh.nebsclient.core.message.ReloadResources
import dsh.nebsclient.core.message.Respawn
import dsh.nebsclient.core.message.Response
import dsh.nebsclient.core.message.RunCommand
import dsh.nebsclient.core.message.SelectSlot
import dsh.nebsclient.core.message.SendChat
import dsh.nebsclient.core.message.SetDebugOverlay
import dsh.nebsclient.core.message.SetHud
import dsh.nebsclient.core.message.SetOption
import dsh.nebsclient.core.message.SetWindowSize
import dsh.nebsclient.core.message.Sneak
import dsh.nebsclient.core.message.SpawnClient
import dsh.nebsclient.core.message.Sprint
import dsh.nebsclient.core.message.StopMoving
import dsh.nebsclient.core.message.Subscribe
import dsh.nebsclient.core.message.SwapHands
import dsh.nebsclient.core.message.TakeScreenshot
import dsh.nebsclient.core.message.TypeText
import dsh.nebsclient.core.message.UseItem
import dsh.nebsclient.core.message.UseOn
import dsh.nebsclient.core.message.WaitFor
import dsh.nebsclient.core.message.WaitForChat
import dsh.nebsclient.mod.NebsClientMod.logger

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
