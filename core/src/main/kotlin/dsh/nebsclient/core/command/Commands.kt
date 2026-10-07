package dsh.nebsclient.core.command

import dsh.nebsclient.core.message.Attack
import dsh.nebsclient.core.message.ClickButton
import dsh.nebsclient.core.message.ClickSlot
import dsh.nebsclient.core.message.CloseScreen
import dsh.nebsclient.core.message.ConnectToServer
import dsh.nebsclient.core.message.Disconnect
import dsh.nebsclient.core.message.DropItem
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
import java.util.UUID

/** Thrown when a command line can't be turned into a [Message]. */
class CommandException(message: String) : IllegalArgumentException(message)

/** A command's arguments: positional values plus `--flags`, with typed accessors that explain mistakes. */
class Args internal constructor(private val command: String, private val flags: Flags, private val raw: List<String>) {
    val positional: List<String> get() = flags.positional

    fun count(min: Int, max: Int = min) {
        val n = positional.size
        if (n < min || n > max) {
            val expected = if (min == max) "$min" else if (max == Int.MAX_VALUE) "at least $min" else "$min-$max"
            throw CommandException("$command takes $expected argument(s), got $n")
        }
    }

    fun string(i: Int): String = positional[i]
    fun int(i: Int, name: String): Int = number(positional[i], name) { it.toIntOrNull() }
    fun double(i: Int, name: String): Double = number(positional[i], name) { it.toDoubleOrNull() }
    fun onOff(i: Int): Boolean = when (positional[i].lowercase()) {
        "on", "true", "yes", "show" -> true
        "off", "false", "no", "hide" -> false
        else -> throw CommandException("expected on or off, got '${positional[i]}'")
    }

    /** Everything after the command name, untouched (for free text like chat messages). */
    fun text(): String = raw.joinToString(" ").also { if (it.isBlank()) throw CommandException("$command needs some text") }

    fun flag(name: String): String? = flags[name]
    fun intFlag(name: String): Int? = flags[name]?.let { number(it, "--$name") { s -> s.toIntOrNull() } }
    fun doubleFlag(name: String): Double? = flags[name]?.let { number(it, "--$name") { s -> s.toDoubleOrNull() } }
    fun switch(name: String): Boolean = name in flags

    private fun <T> number(text: String, name: String, parse: (String) -> T?): T =
        parse(text) ?: throw CommandException("invalid $name '$text'")
}

/**
 * The text commands shared by every front end (the CLI and the Gradle plugin), so both accept
 * exactly the same syntax. Each [Spec] turns a command line into a [Message].
 */
object Commands {
    /**
     * @property usage arguments after the name, for help.
     * @property raw take the rest of the line as free text instead of parsing flags.
     */
    class Spec(
        val name: String,
        val usage: String,
        val description: String,
        val group: String,
        val valueFlags: Set<String> = emptySet(),
        val switches: Set<String> = emptySet(),
        val raw: Boolean = false,
        val parse: (Args) -> Message,
    ) {
        val fullUsage: String get() = if (usage.isEmpty()) name else "$name $usage"
    }

    private const val SESSION = "Session"
    private const val OBSERVE = "Observation"
    private const val MOVE = "Movement"
    private const val INTERACT = "Interaction"
    private const val GUI = "Inventory and screens"
    private const val CHAT = "Chat"
    private const val CAPTURE = "Capture and debugging"
    private const val EVENTS = "Events"

    private fun noArgs(message: Message): (Args) -> Message = { it.count(0); message }

    val specs: List<Spec> = listOf(
        // Session
        Spec("connect", "<host> [port]", "Join a server (port defaults to ${ConnectToServer.DEFAULT_PORT}; <host>:<port> also works). Brings the window to the front.", SESSION) { connect(it) },
        Spec("disconnect", "", "Leave the server and return to the title screen.", SESSION, parse = noArgs(Disconnect)),
        Spec("ping", "", "Name, uuid and state (loading, menu, connecting, in-world).", SESSION, parse = noArgs(Ping)),
        Spec("wait-for", "<${WaitFor.STATES.joinToString("|")}> [--timeout S]", "Wait until the client reaches a state.", SESSION, setOf("timeout")) {
            it.count(1); WaitFor(it.string(0), it.doubleFlag("timeout") ?: 30.0)
        },
        Spec("respawn", "", "Respawn after death.", SESSION, parse = noArgs(Respawn)),
        Spec("quit", "", "Shut the client down.", SESSION, parse = noArgs(Quit)),
        Spec("spawn", "[--name N] [--uuid U] [--instance DIR] [--template DIR]", "Launch another offline client.", SESSION, setOf("name", "uuid", "instance", "template")) { spawn(it) },

        // Observation
        Spec("status", "", "Position, rotation, health, food, XP, game mode, dimension.", OBSERVE, parse = noArgs(GetStatus)),
        Spec("inventory", "", "Every inventory slot and the selected hotbar slot.", OBSERVE, parse = noArgs(GetInventory)),
        Spec("block", "<x> <y> <z>", "The block at a position.", OBSERVE) { it.count(3); GetBlock(it.int(0, "x"), it.int(1, "y"), it.int(2, "z")) },
        Spec("blocks", "<x1> <y1> <z1> <x2> <y2> <z2>", "Non-air blocks in a box (up to ${GetBlocks.MAX_VOLUME} blocks).", OBSERVE) {
            it.count(6)
            GetBlocks(it.int(0, "x1"), it.int(1, "y1"), it.int(2, "z1"), it.int(3, "x2"), it.int(4, "y2"), it.int(5, "z2"))
        },
        Spec("look-target", "", "The block or entity under the crosshair.", OBSERVE, parse = noArgs(GetLookTarget)),
        Spec("entities", "[--radius R] [--type T]", "Nearby entities, nearest first.", OBSERVE, setOf("radius", "type")) {
            it.count(0); GetEntities(it.doubleFlag("radius") ?: 32.0, it.flag("type"))
        },
        Spec("players", "", "Players in the tab list.", OBSERVE, parse = noArgs(GetPlayers)),
        Spec("world", "", "Dimension, time, weather, difficulty, server.", OBSERVE, parse = noArgs(GetWorld)),
        Spec("screen", "", "The open screen: buttons, text fields, container slots.", OBSERVE, parse = noArgs(GetScreen)),
        Spec("chat-history", "[count]", "Recent chat and system messages with sequence numbers.", OBSERVE) {
            it.count(0, 1); GetChatHistory(if (it.positional.isEmpty()) 20 else it.int(0, "count"))
        },
        Spec("scoreboard", "", "The sidebar scoreboard.", OBSERVE, parse = noArgs(GetScoreboard)),
        Spec("effects", "", "Active potion effects.", OBSERVE, parse = noArgs(GetEffects)),

        // Movement
        Spec("look", "<yaw> <pitch>", "Set the view direction in degrees.", MOVE) { it.count(2); Look(it.double(0, "yaw").toFloat(), it.double(1, "pitch").toFloat()) },
        Spec("look-at", "<x> <y> <z>", "Look at a point.", MOVE) { it.count(3); LookAt(it.double(0, "x"), it.double(1, "y"), it.double(2, "z")) },
        Spec("move", "<${Move.DIRECTIONS.joinToString("|")}> [ticks]", "Hold a movement key for some ticks (20 per second), or until 'stop'.", MOVE) {
            it.count(1, 2); Move(it.string(0), if (it.positional.size == 2) it.int(1, "ticks") else null)
        },
        Spec("jump", "", "Jump once.", MOVE, parse = noArgs(Jump)),
        Spec("sneak", "<on|off>", "Hold or release sneak.", MOVE) { it.count(1); Sneak(it.onOff(0)) },
        Spec("sprint", "<on|off>", "Hold or release sprint.", MOVE) { it.count(1); Sprint(it.onOff(0)) },
        Spec("stop", "", "Stop moving and release all held keys.", MOVE, parse = noArgs(StopMoving)),
        Spec("goto", "<x> <z> [--timeout S]", "Walk in a straight line to a position (no pathfinding).", MOVE, setOf("timeout")) {
            it.count(2); Goto(it.double(0, "x"), it.double(1, "z"), it.doubleFlag("timeout") ?: 60.0)
        },
        Spec("follow", "<player|entity-id> [--distance D]", "Keep walking toward a player or entity until 'stop'.", MOVE, setOf("distance")) {
            it.count(1); Follow(it.string(0), it.doubleFlag("distance") ?: 3.0)
        },

        // Interaction
        Spec("attack", "[entity-id]", "Attack an entity, or whatever is under the crosshair.", INTERACT) {
            it.count(0, 1); Attack(if (it.positional.isEmpty()) null else it.int(0, "entity id"))
        },
        Spec("use", "[--ticks N]", "Right-click with the held item; --ticks keeps holding (eat, drink, bow).", INTERACT, setOf("ticks")) {
            it.count(0); UseItem(it.intFlag("ticks") ?: 0)
        },
        Spec("use-on", "<x> <y> <z> [face]", "Right-click a block face (default: up).", INTERACT) {
            it.count(3, 4); UseOn(it.int(0, "x"), it.int(1, "y"), it.int(2, "z"), if (it.positional.size == 4) it.string(3) else "up")
        },
        Spec("mine", "<x> <y> <z> [--timeout S]", "Break a block.", INTERACT, setOf("timeout")) {
            it.count(3); Mine(it.int(0, "x"), it.int(1, "y"), it.int(2, "z"), it.doubleFlag("timeout") ?: 30.0)
        },
        Spec("place", "<x> <y> <z>", "Place the held block at a position.", INTERACT) { it.count(3); Place(it.int(0, "x"), it.int(1, "y"), it.int(2, "z")) },
        Spec("select-slot", "<0-8>", "Select a hotbar slot.", INTERACT) { it.count(1); SelectSlot(it.int(0, "slot")) },
        Spec("hold", "<item>", "Hold an item from the inventory, e.g. diamond_sword.", INTERACT) { it.count(1); HoldItem(it.string(0)) },
        Spec("drop", "[--all]", "Drop one of the held item, or the whole stack.", INTERACT, switches = setOf("all")) { it.count(0); DropItem(it.switch("all")) },
        Spec("swap-hands", "", "Swap main-hand and off-hand items.", INTERACT, parse = noArgs(SwapHands)),
        Spec("interact", "<entity-id>", "Right-click an entity.", INTERACT) { it.count(1); InteractEntity(it.int(0, "entity id")) },

        // Inventory and screens
        Spec("open-inventory", "", "Open the inventory screen.", GUI, parse = noArgs(OpenInventory)),
        Spec("close-screen", "", "Close the open screen.", GUI, parse = noArgs(CloseScreen)),
        Spec("click-slot", "<slot> [--button N] [--mode ${ClickSlot.MODES.joinToString("|")}]", "Click a container slot (numbers from 'screen').", GUI, setOf("button", "mode")) {
            it.count(1); ClickSlot(it.int(0, "slot"), it.intFlag("button") ?: 0, it.flag("mode") ?: "pickup")
        },
        Spec("click-button", "<label|#index>", "Click a button on the open screen.", GUI, raw = true) { ClickButton(it.text()) },
        Spec("type-text", "<text...>", "Type into the focused text field.", GUI, raw = true) { TypeText(it.text()) },

        // Chat
        Spec("chat", "<message...>", "Send a chat message (a leading / runs a command).", CHAT, raw = true) { SendChat(it.text()) },
        Spec("command", "<command...>", "Run a command, e.g. 'command gamemode creative'.", CHAT, raw = true) { RunCommand(it.text().removePrefix("/")) },
        Spec("wait-for-chat", "<regex> [--timeout S] [--since SEQ]", "Wait for a matching chat or system message.", CHAT, setOf("timeout", "since")) {
            it.count(1, Int.MAX_VALUE)
            WaitForChat(it.positional.joinToString(" "), it.doubleFlag("timeout") ?: 30.0, it.flag("since")?.let { s -> s.toLongOrNull() ?: throw CommandException("invalid --since '$s'") })
        },

        // Capture and debugging
        Spec("screenshot", "[name]", "Save a screenshot; replies with its path.", CAPTURE) { it.count(0, 1); TakeScreenshot(it.positional.firstOrNull()) },
        Spec("set-window", "<width> <height>", "Resize the game window.", CAPTURE) { it.count(2); SetWindowSize(it.int(0, "width"), it.int(1, "height")) },
        Spec("set-option", "<name> <value>", "Set a game option (see 'options').", CAPTURE) { it.count(2); SetOption(it.string(0), it.string(1)) },
        Spec("options", "", "List settable options and their values.", CAPTURE, parse = noArgs(GetOptions)),
        Spec("hud", "<on|off>", "Show or hide the HUD (F1).", CAPTURE) { it.count(1); SetHud(it.onOff(0)) },
        Spec("f3", "<on|off>", "Show or hide the debug overlay.", CAPTURE) { it.count(1); SetDebugOverlay(it.onOff(0)) },
        Spec("perf", "", "FPS, memory, loaded chunks, entities.", CAPTURE, parse = noArgs(GetPerf)),
        Spec("logs", "[lines] [--errors]", "Recent client log lines; --errors keeps only warnings, errors and stack traces.", CAPTURE, switches = setOf("errors")) {
            it.count(0, 1); GetLogs(if (it.positional.isEmpty()) 50 else it.int(0, "lines"), it.switch("errors"))
        },
        Spec("reload-resources", "", "Reload resource packs.", CAPTURE, parse = noArgs(ReloadResources)),

        // Events
        Spec("subscribe", "[event...]", "Stream events until interrupted (default: all). CLI only.", EVENTS) { Subscribe(it.positional) },
    )

    private val byName = specs.associateBy { it.name }

    /** Help text for [specs], grouped: one line per command, or two when the usage is too long to align. */
    fun help(indent: String = "  ", column: Int = 34): String = specs.groupBy { it.group }.entries.joinToString("\n\n") { (group, specs) ->
        "$indent$group:\n" + specs.joinToString("\n") {
            val usage = it.fullUsage
            if (usage.length < column) "$indent$indent${usage.padEnd(column)}${it.description}"
            else "$indent$indent$usage\n$indent$indent${" ".repeat(column)}${it.description}"
        }
    }

    /** Parses a whitespace-separated command line, e.g. `connect localhost 25566`. */
    fun parse(line: String): Message = parse(line.trim().split(Regex("\\s+")).filter { it.isNotEmpty() })

    fun parse(words: List<String>): Message {
        if (words.isEmpty()) throw CommandException("no command given")
        val name = words.first().lowercase()
        val spec = byName[name] ?: throw CommandException("unknown command '$name'")
        val rest = words.drop(1)
        val flags = if (spec.raw) Flags.parse(emptyList(), emptySet()) else Flags.parse(rest, spec.valueFlags, spec.switches)
        return try {
            spec.parse(Args(name, flags, rest))
        } catch (e: CommandException) {
            throw e
        } catch (e: IllegalArgumentException) {
            // Validation in the message classes (ranges, allowed values).
            throw CommandException("$name: ${e.message}")
        }
    }

    private fun spawn(args: Args): SpawnClient {
        args.count(0)
        args.flag("uuid")?.let {
            try {
                UUID.fromString(it)
            } catch (_: IllegalArgumentException) {
                throw CommandException("invalid uuid '$it'")
            }
        }
        return SpawnClient(args.flag("name"), args.flag("uuid"), args.flag("instance"), args.flag("template"))
    }

    private fun connect(args: Args): ConnectToServer {
        args.count(1, 2)
        if (args.positional.size == 2) return ConnectToServer(args.string(0), args.int(1, "port"))
        val value = args.string(0)
        val host = value.substringBeforeLast(':')
        val port = value.substringAfterLast(':', "")
        return if (port.isEmpty() || host.isEmpty()) ConnectToServer(value) else ConnectToServer(host, args.run { port.toIntOrNull() } ?: throw CommandException("invalid port '$port'"))
    }
}
