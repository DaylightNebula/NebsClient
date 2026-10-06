package com.nebs.core

import com.nebs.core.command.CommandException
import com.nebs.core.command.Commands
import com.nebs.core.message.ConnectToServer
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
    fun `bad input is rejected`() {
        assertFailsWith<CommandException> { Commands.parse("bogus") }
        assertFailsWith<CommandException> { Commands.parse("connect localhost abc") }
        assertFailsWith<CommandException> { Commands.parse("") }
    }
}
