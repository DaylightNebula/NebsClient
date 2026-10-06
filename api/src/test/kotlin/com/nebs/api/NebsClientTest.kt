package com.nebs.api

import com.nebs.core.message.ConnectToServer
import com.nebs.core.message.LookAt
import com.nebs.core.message.Quit
import com.nebs.core.message.Subscribe
import java.nio.file.Files
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class NebsClientTest {
    @Test
    fun `kotlin usage against a running client`() {
        FakeClient().use { fake ->
            NebsClient.attach(home = fake.home).use { client ->
                assertEquals("Alice", client.name)
                assertEquals(ClientState.IN_WORLD, client.ping().state)

                client.connect("localhost")
                client.lookAt(0, 64, 0)
                assertTrue(ConnectToServer("localhost") in fake.received)
                assertTrue(LookAt(0.0, 64.0, 0.0) in fake.received)

                val status = client.status()
                assertEquals(20.0, status.health)
                assertEquals("minecraft:diamond_sword", status.heldItem?.id)

                val inventory = client.inventory()
                assertEquals(42, inventory.count("dirt"))
                assertEquals(5, inventory.slot(0)?.enchantments?.get("minecraft:sharpness"))

                assertEquals("Pig", client.nearestEntity("pig")?.name)
                assertTrue(client.entities().last().player)
                assertEquals("minecraft:grass_block", client.lookTarget().block)
                assertEquals(1920, client.screenshot("x").width)
                assertEquals(7, client.waitForChat("hello").seq)

                val error = assertFailsWith<NebsException> { client.mine(1, 2, 3) }
                assertContains(error.message!!, "Too far")
            }
            // Closing an attached client must not quit the game.
            assertTrue(fake.received.none { it == Quit })
        }
    }

    @Test
    fun `run accepts cli command lines`() {
        FakeClient().use { fake ->
            NebsClient.attach("alice", fake.home).use { it.run("look 90 0") }
            assertTrue(fake.received.any { it.toString().startsWith("Look(") })
        }
    }

    @Test
    fun `subscriptions stream events`() {
        FakeClient().use { fake ->
            NebsClient.attach(home = fake.home).use { client ->
                val event = CompletableFuture<ClientEvent>()
                client.subscribe(listOf(Events.CHAT)) { event.complete(it) }.use {
                    assertEquals("<Bob> hi", event.get(5, TimeUnit.SECONDS).data["message"])
                }
                assertTrue(Subscribe(listOf("chat")) in fake.received)
                assertFailsWith<NebsException> { client.subscribe(listOf("nope")) {} }
            }
        }
    }

    @Test
    fun `exit quits attached clients explicitly`() {
        FakeClient().use { fake ->
            val client = NebsClient.attach(home = fake.home)
            client.exit()
            assertTrue(Quit in fake.received)
            assertFailsWith<IllegalStateException> { client.status() }
        }
    }

    @Test
    fun `attaching with nothing running explains how to start a client`() {
        val error = assertFailsWith<NebsException> { NebsClient.attach(home = Files.createTempDirectory("empty")) }
        assertContains(error.message!!, "no clients are running")
    }
}
