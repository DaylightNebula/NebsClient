package com.nebs.api

import com.nebs.core.ClientEntry
import com.nebs.core.ClientRegistry
import com.nebs.core.ClientTarget
import com.nebs.core.NebsHome
import com.nebs.core.SocketClient
import com.nebs.core.command.Commands
import com.nebs.core.launcher.ClientLauncher
import com.nebs.core.launcher.ClientProcess
import com.nebs.core.launcher.LaunchSpec
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
import kotlinx.serialization.DeserializationStrategy
import kotlinx.serialization.KSerializer
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import java.io.IOException
import java.nio.file.Path
import java.time.Duration
import java.util.concurrent.TimeoutException
import kotlin.concurrent.thread
import kotlin.time.toKotlinDuration

/**
 * A Minecraft client running the nebs mod, controlled from Java or Kotlin.
 *
 * Creating one launches a new offline client (installing the client template first if needed)
 * and, by default, waits until it has loaded. Closing it quits the game, so use it with
 * try-with-resources / `use {}`:
 *
 * ```
 * NebsClient().use { client ->
 *     client.connect("localhost")
 *     client.lookAt(0, 64, 0)
 * }
 * ```
 *
 * Use [attach] for clients that are already running (closing those leaves them running).
 *
 * Every method blocks until the client has done the work and throws [NebsException] when it
 * fails. Calls on one instance are thread-safe; they're executed one at a time.
 */
public class NebsClient private constructor(init: Init) : AutoCloseable {
    private class Init(
        val target: ClientTarget,
        val process: ClientProcess?,
        val quitOnClose: Boolean,
        val home: Path,
        val readyTimeout: Duration?,
    )

    private val target: ClientTarget = init.target
    private val process: ClientProcess? = init.process
    private val quitOnClose: Boolean = init.quitOnClose

    /** The nebs home this client belongs to. */
    public val home: Path = init.home

    /** Launches a new client; see [ClientOptions] for the settings. */
    @JvmOverloads
    public constructor(options: ClientOptions = ClientOptions()) : this(launch(options))

    private var connection: SocketClient? = null
    private var closed = false

    init {
        init.readyTimeout?.let { waitUntilReady(it) }
    }

    /** Player name. */
    public val name: String get() = process?.info?.name ?: target.entry?.name ?: target.name

    /** Socket the client listens on. */
    public val socket: Path get() = target.socket

    /** Process id of the game, when known. */
    public val pid: Long? get() = process?.pid ?: target.pid

    /** The game folder, when known. */
    public val gameDirectory: Path? get() = process?.instanceDir ?: target.entry?.gameDir?.let(Path::of)

    override fun toString(): String = "NebsClient($name)"

    // ---------------------------------------------------------------- plumbing

    /** Sends any [Message] and returns the raw reply (does not throw on `success = false`). */
    @Synchronized
    public fun send(message: Message): Response {
        check(!closed) { "This NebsClient is closed" }
        return try {
            open().send(message)
        } catch (_: IOException) {
            // The connection may have gone stale (e.g. the client restarted its socket); retry once on a fresh one.
            connection?.close()
            connection = null
            try {
                open().send(message)
            } catch (e: IOException) {
                connection = null
                throw NebsException("Could not reach $name at $socket: ${e.message}", e)
            }
        }
    }

    private fun open(): SocketClient = connection ?: SocketClient(target.socket).also { connection = it }

    /** Runs a command line exactly like `nebs-cli`, e.g. `run("mine 10 64 -3")`. */
    public fun run(commandLine: String): Response = call(Commands.parse(commandLine))

    private fun call(message: Message): Response {
        val reply = send(message)
        if (!reply.success) throw NebsException(reply.detail)
        return reply
    }

    private fun <T> query(message: Message, serializer: DeserializationStrategy<T>): T {
        val result = call(message).result ?: throw NebsException("No result for $message")
        return json.decodeFromJsonElement(serializer, result)
    }

    private fun <T> queryList(message: Message, field: String, serializer: KSerializer<T>): List<T> {
        val result = call(message).result?.jsonObject?.get(field) ?: throw NebsException("No $field in result")
        return json.decodeFromJsonElement(ListSerializer(serializer), result)
    }

    // ---------------------------------------------------------------- session

    /** Joins a server and returns once the client is in the world (or fails after [timeout]). */
    @JvmOverloads
    public fun connect(host: String, port: Int = ConnectToServer.DEFAULT_PORT, timeout: Duration = Duration.ofSeconds(60)) {
        call(ConnectToServer(host, port))
        try {
            waitFor(ClientState.IN_WORLD, timeout)
        } catch (e: NebsException) {
            // Explain why, e.g. "You are not white-listed on this server!"
            val screen = runCatching { screen() }.getOrNull()
            val reason = screen?.buttons?.filter { !it.active }?.joinToString(": ") { it.label }
            throw NebsException(if (reason.isNullOrBlank()) e.message ?: "Couldn't connect" else "Couldn't connect to $host:$port: $reason", e)
        }
    }

    /** Leaves the server and returns to the title screen. */
    public fun disconnect() {
        call(Disconnect)
    }

    /** Name, uuid, state and server. */
    public fun ping(): PingInfo {
        val data = call(Ping).data
        return PingInfo(data["name"], data["uuid"], ClientState.of(data["state"] ?: "loading"), data["server"])
    }

    /** Waits until the client reaches [state]. */
    @JvmOverloads
    public fun waitFor(state: ClientState, timeout: Duration = Duration.ofSeconds(30)) {
        call(WaitFor(state.wire, timeout.toMillis() / 1000.0))
    }

    /** Waits until the client has finished loading. */
    @JvmOverloads
    public fun waitUntilReady(timeout: Duration = Duration.ofMinutes(5)) {
        try {
            if (process != null) {
                process.awaitReady(timeout.toKotlinDuration())
                return
            }
            val deadline = System.nanoTime() + timeout.toNanos()
            while (System.nanoTime() < deadline) {
                val ready = try {
                    ping().state != ClientState.LOADING
                } catch (_: NebsException) {
                    false
                }
                if (ready) return
                Thread.sleep(500)
            }
            throw TimeoutException("$name wasn't ready after $timeout")
        } catch (e: TimeoutException) {
            throw NebsException(e.message ?: "Timed out", e)
        } catch (e: IllegalStateException) {
            throw NebsException(e.message ?: "Client exited", e)
        }
    }

    public fun respawn() {
        call(Respawn)
    }

    /**
     * Quits the game and waits for it to exit (killing it if it hangs). Closes this object.
     * Safe to call more than once.
     */
    public fun exit() {
        if (closed) return
        try {
            if (process != null) {
                process.quit()
            } else {
                target.stop()
            }
        } finally {
            disconnectSocket()
        }
    }

    /** Quits the game if this object launched or spawned it (see [ClientOptions.quitOnClose]); otherwise just disconnects. */
    override fun close() {
        if (quitOnClose) exit() else disconnectSocket()
    }

    @Synchronized
    private fun disconnectSocket() {
        connection?.close()
        connection = null
        closed = true
    }

    /**
     * Has this client launch another one in the same nebs home and returns it. The new client
     * quits when it is closed, unless [ClientOptions.quitOnClose] is off.
     */
    @JvmOverloads
    public fun spawn(options: ClientOptions = ClientOptions()): NebsClient {
        val data = call(
            SpawnClient(options.name, options.uuid?.toString(), options.instance?.toString(), options.template?.toString()),
        ).data
        val entry = ClientEntry(
            name = data.getValue("name"),
            uuid = data.getValue("uuid"),
            pid = data.getValue("pid").toLong(),
            socket = data.getValue("socket"),
            gameDir = data.getValue("instance"),
        )
        val target = ClientTarget(entry.name, Path.of(entry.socket), entry)
        return NebsClient(Init(target, null, options.quitOnClose, home, options.readyTimeout.takeIf { options.waitForReady }))
    }

    // ---------------------------------------------------------------- observation

    public fun status(): PlayerStatus = query(GetStatus, PlayerStatus.serializer())

    public fun inventory(): Inventory = query(GetInventory, Inventory.serializer())

    public fun block(x: Int, y: Int, z: Int): BlockInfo = query(GetBlock(x, y, z), BlockInfo.serializer())

    /** Non-air blocks in a box (corners inclusive, at most 32768 blocks). */
    public fun blocks(x1: Int, y1: Int, z1: Int, x2: Int, y2: Int, z2: Int): BlocksInfo =
        query(GetBlocks(x1, y1, z1, x2, y2, z2), BlocksInfo.serializer())

    /** What the crosshair is on. Block targets have the block's fields at the top level. */
    public fun lookTarget(): LookTarget = query(GetLookTarget, LookTarget.serializer())

    /** Entities within [radius], nearest first, optionally only of [type] (`pig` or `minecraft:pig`). */
    @JvmOverloads
    public fun entities(radius: Double = 32.0, type: String? = null): List<EntityInfo> =
        queryList(GetEntities(radius, type), "entities", EntityInfo.serializer())

    /** The nearest entity of [type] within [radius], or `null`. */
    @JvmOverloads
    public fun nearestEntity(type: String, radius: Double = 32.0): EntityInfo? = entities(radius, type).firstOrNull()

    public fun players(): List<PlayerListEntry> = queryList(GetPlayers, "players", PlayerListEntry.serializer())

    public fun world(): WorldInfo = query(GetWorld, WorldInfo.serializer())

    public fun screen(): ScreenInfo = query(GetScreen, ScreenInfo.serializer())

    @JvmOverloads
    public fun chatHistory(count: Int = 20): ChatHistory = query(GetChatHistory(count), ChatHistory.serializer())

    public fun scoreboard(): Scoreboard = query(GetScoreboard, Scoreboard.serializer())

    public fun effects(): List<EffectInfo> = queryList(GetEffects, "effects", EffectInfo.serializer())

    // ---------------------------------------------------------------- movement

    /** Sets the view direction: yaw 0 = south, 90 = west; pitch -90 (up) to 90 (down). */
    public fun look(yaw: Float, pitch: Float) {
        call(Look(yaw, pitch))
    }

    public fun lookAt(x: Double, y: Double, z: Double) {
        call(LookAt(x, y, z))
    }

    /** Looks at the point (x, y, z); handy with whole numbers, e.g. `lookAt(0, 64, 0)`. */
    public fun lookAt(x: Int, y: Int, z: Int): Unit = lookAt(x.toDouble(), y.toDouble(), z.toDouble())

    /** Holds a movement key for [ticks] (20 per second) and returns when it's released. */
    public fun move(direction: MoveDirection, ticks: Int) {
        call(Move(direction.wire, ticks))
    }

    /** Holds a movement key until [stop]. */
    public fun startMoving(direction: MoveDirection) {
        call(Move(direction.wire, null))
    }

    public fun jump() {
        call(Jump)
    }

    public fun sneak(on: Boolean) {
        call(Sneak(on))
    }

    public fun sprint(on: Boolean) {
        call(Sprint(on))
    }

    /** Stops any movement and releases every key nebs is holding. */
    public fun stop() {
        call(StopMoving)
    }

    /** Walks in a straight line to (x, z), jumping up single steps. Not a pathfinder: fails if stuck. */
    @JvmOverloads
    public fun walkTo(x: Double, z: Double, timeout: Duration = Duration.ofSeconds(60)) {
        call(Goto(x, z, timeout.toMillis() / 1000.0))
    }

    @JvmOverloads
    public fun walkTo(x: Int, z: Int, timeout: Duration = Duration.ofSeconds(60)): Unit = walkTo(x + 0.5, z + 0.5, timeout)

    /** Keeps walking toward a player (by name) or entity (by id) until [stop]. */
    @JvmOverloads
    public fun follow(target: String, distance: Double = 3.0) {
        call(Follow(target, distance))
    }

    // ---------------------------------------------------------------- interaction

    /** Attacks whatever is under the crosshair. */
    public fun attack() {
        call(Attack(null))
    }

    public fun attack(entityId: Int) {
        call(Attack(entityId))
    }

    /** Right-clicks with the held item; with [ticks], keeps holding (eat, drink, bow). */
    @JvmOverloads
    public fun use(ticks: Int = 0) {
        call(UseItem(ticks))
    }

    @JvmOverloads
    public fun useOn(x: Int, y: Int, z: Int, face: BlockFace = BlockFace.UP) {
        call(UseOn(x, y, z, face.wire))
    }

    /** Breaks a block, returning once it's gone. */
    @JvmOverloads
    public fun mine(x: Int, y: Int, z: Int, timeout: Duration = Duration.ofSeconds(30)) {
        call(Mine(x, y, z, timeout.toMillis() / 1000.0))
    }

    /** Places the held block at a position. */
    public fun place(x: Int, y: Int, z: Int) {
        call(Place(x, y, z))
    }

    public fun selectSlot(slot: Int) {
        call(SelectSlot(slot))
    }

    /** Puts an item (`diamond_sword` or `minecraft:diamond_sword`) in the main hand. */
    public fun hold(item: String) {
        call(HoldItem(item))
    }

    @JvmOverloads
    public fun drop(all: Boolean = false) {
        call(DropItem(all))
    }

    public fun swapHands() {
        call(SwapHands)
    }

    public fun interact(entityId: Int) {
        call(InteractEntity(entityId))
    }

    // ---------------------------------------------------------------- screens

    public fun openInventory() {
        call(OpenInventory)
    }

    public fun closeScreen() {
        call(CloseScreen)
    }

    @JvmOverloads
    public fun clickSlot(slot: Int, button: Int = 0, mode: ClickMode = ClickMode.PICKUP) {
        call(ClickSlot(slot, button, mode.wire))
    }

    /** Clicks a button by label (exact, then partial match) or by `#index`. */
    public fun clickButton(label: String) {
        call(ClickButton(label))
    }

    public fun clickButton(index: Int): Unit = clickButton("#$index")

    public fun typeText(text: String) {
        call(TypeText(text))
    }

    // ---------------------------------------------------------------- chat

    /** Sends a chat message (a leading `/` runs it as a command). */
    public fun chat(message: String) {
        call(SendChat(message))
    }

    /** Runs a command, with or without the leading `/`. */
    public fun command(command: String) {
        call(RunCommand(command.removePrefix("/")))
    }

    /**
     * Waits for a chat or system message matching [regex] that arrives after message [since]
     * (from [chatHistory]) or, by default, after this call.
     */
    @JvmOverloads
    public fun waitForChat(regex: String, timeout: Duration = Duration.ofSeconds(30), since: Long? = null): ChatMessage {
        val reply = call(WaitForChat(regex, timeout.toMillis() / 1000.0, since))
        return ChatMessage(reply.data["seq"]?.toLong() ?: 0, System.currentTimeMillis(), reply.data["type"] ?: "system", reply.data["sender"], reply.detail)
    }

    // ---------------------------------------------------------------- capture and debugging

    /** Saves a screenshot as `<game dir>/screenshots/<name>.png`. */
    @JvmOverloads
    public fun screenshot(name: String? = null): ScreenshotInfo {
        val data = call(TakeScreenshot(name)).data
        return ScreenshotInfo(Path.of(data.getValue("path")), data.getValue("width").toInt(), data.getValue("height").toInt())
    }

    /** Resizes the window, in screen points. */
    public fun setWindowSize(width: Int, height: Int) {
        call(SetWindowSize(width, height))
    }

    /** Every option [setOption] accepts, with its current value. */
    public fun options(): Map<String, String> = query(GetOptions, MapSerializer(String.serializer(), String.serializer()))

    /** Sets a game option, e.g. `setOption("fov", 90)` or `setOption("graphicsPreset", "fast")`. */
    public fun setOption(name: String, value: Any) {
        call(SetOption(name, value.toString()))
    }

    public fun setHudVisible(visible: Boolean) {
        call(SetHud(visible))
    }

    public fun setDebugOverlay(visible: Boolean) {
        call(SetDebugOverlay(visible))
    }

    public fun perf(): PerfInfo = query(GetPerf, PerfInfo.serializer())

    /** The last [lines] lines of the client log; with [errorsOnly], only warnings, errors and stack traces. */
    @JvmOverloads
    public fun logs(lines: Int = 50, errorsOnly: Boolean = false): List<String> =
        call(GetLogs(lines, errorsOnly)).result?.jsonObject?.get("lines")?.jsonArray?.map { (it as JsonPrimitive).content } ?: emptyList()

    public fun reloadResources() {
        call(ReloadResources)
    }

    // ---------------------------------------------------------------- events

    /** Calls [listener] for every event (see [Events]) until the returned [Subscription] is closed. */
    public fun subscribe(listener: EventListener): Subscription = subscribe(emptyList(), listener)

    /** Calls [listener] for the given [events] (see [Events]) until the returned [Subscription] is closed. */
    public fun subscribe(events: Collection<String>, listener: EventListener): Subscription {
        val subscribe = try {
            Subscribe(events.toList())
        } catch (e: IllegalArgumentException) {
            throw NebsException(e.message ?: "Invalid events", e)
        }
        val client = try {
            SocketClient(target.socket)
        } catch (e: IOException) {
            throw NebsException("Could not reach $name at $socket: ${e.message}", e)
        }
        val started = java.util.concurrent.CompletableFuture<Unit>()
        thread(isDaemon = true, name = "nebs-events-$name") {
            try {
                client.subscribe(subscribe) { event: Event ->
                    listener.onEvent(ClientEvent(event.event, event.data.toStringMap(), event.time))
                }
                started.complete(Unit)
            } catch (e: IOException) {
                started.completeExceptionally(NebsException("Subscription failed: ${e.message}", e))
            }
        }
        // Surface an immediate rejection; otherwise the stream is running.
        try {
            started.get(500, java.util.concurrent.TimeUnit.MILLISECONDS)
        } catch (_: TimeoutException) {
        } catch (e: java.util.concurrent.ExecutionException) {
            throw e.cause ?: e
        }
        return object : Subscription {
            override fun close() = client.close()
        }
    }

    public companion object {
        private val json = Json { ignoreUnknownKeys = true }

        private fun JsonObject.toStringMap(): Map<String, String> =
            mapValues { (_, v) -> if (v is JsonPrimitive) v.content else v.toString() }

        private fun launch(options: ClientOptions): Init {
            val home = NebsHome.resolve(options.home)
            val process = try {
                ClientLauncher.launch(
                    LaunchSpec(
                        home = home,
                        template = options.template,
                        instanceDir = options.instance,
                        name = options.name,
                        uuid = options.uuid,
                        width = options.width,
                        height = options.height,
                        memory = options.memory,
                        installIfMissing = options.installIfMissing,
                        onInstall = { System.err.println("nebs: installing the client template into $it (about 700 MB, first time only)...") },
                        installProgress = options.installProgress,
                    ),
                )
            } catch (e: IllegalStateException) {
                throw NebsException(e.message ?: "Could not launch a client", e)
            } catch (e: IllegalArgumentException) {
                throw NebsException(e.message ?: "Could not launch a client", e)
            } catch (e: IOException) {
                throw NebsException("Could not launch a client: ${e.message}", e)
            }
            return Init(process.toTarget(), process, options.quitOnClose, home, options.readyTimeout.takeIf { options.waitForReady })
        }

        private fun attachInit(target: ClientTarget, home: Path, waitForReady: Boolean) =
            Init(target, null, quitOnClose = false, home = home, readyTimeout = if (waitForReady) Duration.ofMinutes(5) else null)

        /**
         * Attaches to a running client by player name, or to the only running client when [name] is
         * `null`. Closing it leaves the game running; call [exit] to quit it.
         */
        @JvmStatic
        @JvmOverloads
        public fun attach(name: String? = null, home: Path? = null, waitForReady: Boolean = true): NebsClient {
            val resolvedHome = NebsHome.resolve(home)
            val target = try {
                ClientRegistry.select(resolvedHome, names = listOfNotNull(name)).single()
            } catch (e: IllegalArgumentException) {
                throw NebsException(e.message ?: "No such client", e)
            }
            return NebsClient(attachInit(target, resolvedHome, waitForReady))
        }

        /** Attaches to whatever client listens on [socket], registered or not. */
        @JvmStatic
        @JvmOverloads
        public fun atSocket(socket: Path, waitForReady: Boolean = true): NebsClient =
            NebsClient(attachInit(ClientTarget(socket.toString(), socket), NebsHome.resolve(), waitForReady))

        /** Every client running in [home] (default: the usual nebs home). Closing them leaves them running. */
        @JvmStatic
        @JvmOverloads
        public fun running(home: Path? = null): List<NebsClient> {
            val resolvedHome = NebsHome.resolve(home)
            return ClientRegistry.active(resolvedHome).map { NebsClient(attachInit(it, resolvedHome, waitForReady = false)) }
        }
    }
}

/** Kotlin: launches a client configured in a block, e.g. `NebsClient { name = "Bob" }`. */
@JvmSynthetic
public fun NebsClient(configure: ClientOptions.() -> Unit): NebsClient = NebsClient(ClientOptions().apply(configure))
