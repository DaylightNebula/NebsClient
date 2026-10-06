package com.nebs.core

import com.nebs.core.command.Commands
import kotlin.test.Test
import kotlin.test.assertEquals

/** Every command parses from a sample line and survives the wire format unchanged. */
class AllCommandsTest {
    private val samples = mapOf(
        "connect" to "connect localhost 25566",
        "disconnect" to "disconnect",
        "ping" to "ping",
        "wait-for" to "wait-for in-world --timeout 5",
        "respawn" to "respawn",
        "quit" to "quit",
        "spawn" to "spawn --name Bob",
        "status" to "status",
        "inventory" to "inventory",
        "block" to "block 1 -60 2",
        "blocks" to "blocks 0 0 0 3 3 3",
        "look-target" to "look-target",
        "entities" to "entities --radius 10 --type pig",
        "players" to "players",
        "world" to "world",
        "screen" to "screen",
        "chat-history" to "chat-history 5",
        "scoreboard" to "scoreboard",
        "effects" to "effects",
        "look" to "look 90 -10",
        "look-at" to "look-at 1.5 64 -3",
        "move" to "move forward 20",
        "jump" to "jump",
        "sneak" to "sneak on",
        "sprint" to "sprint off",
        "stop" to "stop",
        "goto" to "goto 10 -4 --timeout 30",
        "follow" to "follow Alice --distance 2",
        "attack" to "attack 42",
        "use" to "use --ticks 40",
        "use-on" to "use-on 1 2 3 north",
        "mine" to "mine 1 2 3",
        "place" to "place 1 2 3",
        "select-slot" to "select-slot 4",
        "hold" to "hold diamond_sword",
        "drop" to "drop --all",
        "swap-hands" to "swap-hands",
        "interact" to "interact 42",
        "open-inventory" to "open-inventory",
        "close-screen" to "close-screen",
        "click-slot" to "click-slot 36 --mode quick_move",
        "click-button" to "click-button Back to Server List",
        "type-text" to "type-text hello world",
        "chat" to "chat hello there",
        "command" to "command /give @s dirt",
        "wait-for-chat" to "wait-for-chat joined the game --since 3",
        "screenshot" to "screenshot test-shot",
        "set-window" to "set-window 1280 720",
        "set-option" to "set-option fov 90",
        "options" to "options",
        "hud" to "hud off",
        "f3" to "f3 on",
        "perf" to "perf",
        "logs" to "logs 20 --errors",
        "reload-resources" to "reload-resources",
        "subscribe" to "subscribe chat death",
    )

    @Test
    fun `every command has a sample`() {
        assertEquals(Commands.specs.map { it.name }.toSet(), samples.keys)
    }

    @Test
    fun `every command round trips through the codec`() {
        samples.forEach { (name, line) ->
            val message = Commands.parse(line)
            assertEquals(message, MessageCodec.decode(MessageCodec.encode(message)), "round trip of '$name'")
        }
    }
}
