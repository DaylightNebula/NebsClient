package com.nebs.core

import com.nebs.core.command.CommandException
import com.nebs.core.message.Ping
import com.nebs.core.message.Response
import java.net.StandardProtocolFamily
import java.net.UnixDomainSocketAddress
import java.nio.channels.Channels
import java.nio.channels.ServerSocketChannel
import java.nio.file.Files
import java.nio.file.Path
import java.util.UUID
import kotlin.concurrent.thread
import kotlin.io.path.deleteRecursively
import kotlin.io.path.exists
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ClientRegistryTest {
    private val home: Path = Files.createTempDirectory("nebs-home")
    private val servers = mutableListOf<ServerSocketChannel>()

    @AfterTest
    @OptIn(kotlin.io.path.ExperimentalPathApi::class)
    fun tearDown() {
        servers.forEach { it.close() }
        home.deleteRecursively()
    }

    /** Registers a fake client in the current process that answers every message with its name. */
    private fun fakeClient(name: String): Path {
        val socket = home.resolve("$name.sock")
        val server = ServerSocketChannel.open(StandardProtocolFamily.UNIX).bind(UnixDomainSocketAddress.of(socket))
        servers += server
        thread(isDaemon = true) {
            while (server.isOpen) {
                val client = runCatching { server.accept() }.getOrNull() ?: break
                client.use {
                    val reader = Channels.newReader(it, Charsets.UTF_8).buffered()
                    MessageCodec.read(reader) ?: return@use
                    MessageCodec.write(Channels.newWriter(it, Charsets.UTF_8), Response.ok(name, mapOf("state" to "menu")))
                }
            }
        }
        // Same pid for every fake client, so give each a distinct file name.
        val entry = ClientEntry.forCurrentProcess(name, UUID.randomUUID().toString(), socket, home)
        val file = ClientRegistry.register(home, entry)
        Files.move(file, file.resolveSibling("$name-fake.json"))
        return socket
    }

    @Test
    fun `home resolution prefers explicit, then property, then env, then working dir`() {
        assertEquals(Path.of("/x").toAbsolutePath(), NebsHome.resolve(Path.of("/x")))
        if (System.getProperty(NebsHome.PROPERTY) == null && System.getenv(NebsHome.ENV) == null) {
            assertEquals(Path.of("/base/.nebs"), NebsHome.resolve(base = Path.of("/base")))
            System.setProperty(NebsHome.PROPERTY, "/prop")
            try {
                assertEquals(Path.of("/prop"), NebsHome.resolve(base = Path.of("/base")))
            } finally {
                System.clearProperty(NebsHome.PROPERTY)
            }
        }
    }

    @Test
    fun `selection rules`() {
        assertContains(assertFailsWith<CommandException> { ClientRegistry.select(home) }.message!!, "no clients are running")

        fakeClient("Alice")
        assertEquals(listOf("Alice"), ClientRegistry.select(home).map { it.name })

        fakeClient("Bob")
        assertContains(assertFailsWith<CommandException> { ClientRegistry.select(home) }.message!!, "choose with --client")
        assertEquals(listOf("Alice", "Bob"), ClientRegistry.select(home, all = true).map { it.name })
        assertEquals(listOf("Bob"), ClientRegistry.select(home, names = listOf("bob")).map { it.name })
        assertContains(
            assertFailsWith<CommandException> { ClientRegistry.select(home, names = listOf("Carol")) }.message!!,
            "running: Alice, Bob",
        )
        val raw = Path.of("/tmp/x.sock")
        assertEquals(raw, ClientRegistry.select(home, socket = raw).single().socket)
    }

    @Test
    fun `broadcast reaches every client and reports failures per client`() {
        fakeClient("Alice")
        fakeClient("Bob")
        val targets = ClientRegistry.active(home) + ClientTarget("Ghost", home.resolve("missing.sock"))
        val results = ClientRegistry.broadcast(targets, Ping)
        assertEquals(listOf("Alice", "Bob"), results.take(2).map { it.second.getOrThrow().detail })
        assertTrue(results[2].second.isFailure)
    }

    @Test
    fun `entries of exited processes are pruned`() {
        val dead = ProcessBuilder("true").start().apply { waitFor() }
        val entry = ClientEntry("Zed", UUID.randomUUID().toString(), dead.pid(), "/tmp/zed.sock", "/tmp")
        val file = ClientRegistry.register(home, entry)
        assertTrue(ClientRegistry.active(home).isEmpty())
        assertFalse(file.exists())
    }

    @Test
    fun `minecraft's undashed uuids parse`() {
        val uuid = UUID.randomUUID()
        assertEquals(uuid, ClientRegistry.parseUuid(uuid.toString().replace("-", "")))
        assertEquals(uuid, ClientRegistry.parseUuid(uuid.toString()))
    }
}
