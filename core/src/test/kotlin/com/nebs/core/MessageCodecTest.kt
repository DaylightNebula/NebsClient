package com.nebs.core

import com.nebs.core.message.ConnectToServer
import com.nebs.core.message.Ping
import com.nebs.core.message.Response
import com.nebs.core.message.SpawnClient
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
    fun `lifecycle messages round trip`() {
        assertEquals("""{"type":"ping"}""", MessageCodec.encode(Ping))
        assertEquals(Ping, MessageCodec.decode("""{"type":"ping"}"""))
        assertEquals(SpawnClient(name = "Bob"), MessageCodec.decode("""{"type":"spawn","name":"Bob"}"""))
        val reply = Response.ok("pong", mapOf("name" to "Bob", "state" to "menu"))
        assertEquals(reply, MessageCodec.decode(MessageCodec.encode(reply)))
        assertEquals(Response(true, "old peer"), MessageCodec.decode("""{"type":"response","success":true,"detail":"old peer"}"""))
    }

    @Test
    fun `invalid port is rejected`() {
        assertFailsWith<IllegalArgumentException> { ConnectToServer("localhost", 70000) }
    }
}
