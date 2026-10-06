package com.nebs.gradle

import com.nebs.core.MessageCodec
import com.nebs.core.message.Message
import com.nebs.core.message.Response
import org.gradle.testkit.runner.GradleRunner
import java.net.StandardProtocolFamily
import java.net.UnixDomainSocketAddress
import java.nio.channels.Channels
import java.nio.channels.ServerSocketChannel
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.concurrent.thread
import kotlin.io.path.deleteRecursively
import kotlin.io.path.writeText
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals

/** Runs the plugin in a real Gradle build against a fake client socket. */
class NebsPluginFunctionalTest {
    private lateinit var dir: Path
    private lateinit var socket: Path
    private lateinit var server: ServerSocketChannel
    private val received = CopyOnWriteArrayList<Message>()

    @BeforeTest
    fun setUp() {
        // Keep the path short: Unix socket paths are limited to ~104 characters on macOS.
        dir = Files.createTempDirectory("nebs")
        socket = dir.resolve("s.sock")
        server = ServerSocketChannel.open(StandardProtocolFamily.UNIX).bind(UnixDomainSocketAddress.of(socket))
        thread(isDaemon = true) {
            while (server.isOpen) {
                val client = runCatching { server.accept() }.getOrNull() ?: break
                client.use {
                    val reader = Channels.newReader(it, Charsets.UTF_8).buffered()
                    val writer = Channels.newWriter(it, Charsets.UTF_8)
                    val message = MessageCodec.read(reader) ?: return@use
                    received += message
                    MessageCodec.write(writer, Response.ok("handled $message"))
                }
            }
        }
        dir.resolve("settings.gradle.kts").writeText("")
        dir.resolve("build.gradle.kts").writeText(
            """
            plugins { id("com.nebs.socket") }
            nebs { socketPath = "$socket" }
            """.trimIndent(),
        )
    }

    @AfterTest
    @OptIn(kotlin.io.path.ExperimentalPathApi::class)
    fun tearDown() {
        server.close()
        dir.deleteRecursively()
    }

    private fun gradle(vararg args: String) =
        GradleRunner.create().withProjectDir(dir.toFile()).withPluginClasspath().withArguments(*args)

    @Test
    fun `nebsConnect sends a connect message`() {
        val result = gradle("nebsConnect", "--host=mc.example.com", "--port=25570").build()
        assertContains(result.output, "handled ConnectToServer(host=mc.example.com, port=25570)")
        assertEquals(listOf(MessageCodec.decode("""{"type":"connect","host":"mc.example.com","port":25570}""")), received)
    }

    @Test
    fun `nebs runs a cli command line`() {
        gradle("nebs", "--command=connect localhost:25566").build()
        assertEquals(listOf(MessageCodec.decode("""{"type":"connect","host":"localhost","port":25566}""")), received)
    }

    @Test
    fun `invalid command fails the build`() {
        val result = gradle("nebs", "--command=bogus").buildAndFail()
        assertContains(result.output, "unknown command 'bogus'")
    }

    @Test
    fun `unreachable socket fails the build`() {
        val result = gradle("nebsConnect", "--host=localhost", "--socket=${dir.resolve("missing.sock")}").buildAndFail()
        assertContains(result.output, "Could not reach client")
    }
}
