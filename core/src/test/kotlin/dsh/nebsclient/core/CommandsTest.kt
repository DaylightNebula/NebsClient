package dsh.nebsclient.core

import dsh.nebsclient.core.command.CommandException
import dsh.nebsclient.core.command.Commands
import dsh.nebsclient.core.message.ConnectToServer
import dsh.nebsclient.core.message.Ping
import dsh.nebsclient.core.message.Quit
import dsh.nebsclient.core.message.SpawnClient
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class CommandsTest {
    @Test
    fun `connect accepts host, host port, and host colon port`() {
        assertEquals(ConnectToServer("localhost"), Commands.parse("connect localhost"))
        assertEquals(ConnectToServer("localhost", 25566), Commands.parse("connect localhost 25566"))
        assertEquals(ConnectToServer("localhost", 25566), Commands.parse("  CONNECT   localhost:25566 "))
    }

    @Test
    fun `lifecycle commands`() {
        assertEquals(Ping, Commands.parse("ping"))
        assertEquals(Quit, Commands.parse("quit"))
        assertEquals(SpawnClient(), Commands.parse("spawn"))
        assertEquals(
            SpawnClient(name = "Bob", uuid = "b50ad385-829d-3141-a216-7e7d7539ba7f", instance = "/tmp/bob"),
            Commands.parse("spawn --name Bob --uuid=b50ad385-829d-3141-a216-7e7d7539ba7f --instance /tmp/bob"),
        )
        assertFailsWith<CommandException> { Commands.parse("spawn --uuid nope") }
        assertFailsWith<CommandException> { Commands.parse("spawn --bogus 1") }
        assertFailsWith<CommandException> { Commands.parse("ping extra") }
    }

    @Test
    fun `bad input is rejected`() {
        assertFailsWith<CommandException> { Commands.parse("bogus") }
        assertFailsWith<CommandException> { Commands.parse("connect localhost abc") }
        assertFailsWith<CommandException> { Commands.parse("") }
    }
}
