package com.nebs.core

import com.nebs.core.message.ConnectToServer
import com.nebs.core.message.Response
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class MessageCodecTest {
    @Test
    fun `connect message round trips with type discriminator`() {
        val message = ConnectToServer("mc.example.com", 25570)
        val line = MessageCodec.encode(message)
        assertEquals("""{"type":"connect","host":"mc.example.com","port":25570}""", line)
        assertEquals(message, MessageCodec.decode(line))
    }

    @Test
    fun `connect port defaults when omitted`() {
        assertEquals(ConnectToServer("localhost", 25565), MessageCodec.decode("""{"type":"connect","host":"localhost"}"""))
    }

    @Test
    fun `response round trips`() {
        val message = Response.error("nope")
        assertEquals(message, MessageCodec.decode(MessageCodec.encode(message)))
    }

    @Test
    fun `invalid port is rejected`() {
        assertFailsWith<IllegalArgumentException> { ConnectToServer("localhost", 70000) }
    }
}
