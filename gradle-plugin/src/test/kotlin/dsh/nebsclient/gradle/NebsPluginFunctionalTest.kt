package dsh.nebsclient.gradle

import dsh.nebsclient.core.ClientEntry
import dsh.nebsclient.core.ClientRegistry
import dsh.nebsclient.core.MessageCodec
import dsh.nebsclient.core.message.Message
import dsh.nebsclient.core.message.Response
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.gradle.testkit.runner.GradleRunner
import java.net.StandardProtocolFamily
import java.net.UnixDomainSocketAddress
import java.nio.channels.Channels
import java.nio.channels.ServerSocketChannel
import java.nio.file.Files
import java.nio.file.Path
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.concurrent.thread
import kotlin.io.path.deleteRecursively
import kotlin.io.path.writeText
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Runs the plugin in a real Gradle build against fake clients registered in the project's `.nebs`. */
class NebsPluginFunctionalTest {
    private lateinit var dir: Path
    private val servers = mutableListOf<ServerSocketChannel>()
    private val received = ConcurrentHashMap<String, MutableList<Message>>()

    @BeforeTest
    fun setUp() {
        // Keep paths short: Unix socket paths are limited to ~104 characters on macOS.
        dir = Files.createTempDirectory("nebs")
        dir.resolve("settings.gradle.kts").writeText("")
        dir.resolve("build.gradle.kts").writeText("""plugins { id("dsh.nebsclient.socket") }""")
    }

    @AfterTest
    @OptIn(kotlin.io.path.ExperimentalPathApi::class)
    fun tearDown() {
        servers.forEach { it.close() }
        dir.deleteRecursively()
    }

    /** A fake client that records messages and replies with its name; registered in `<dir>/.nebs` unless [register] is false. */
    private fun fakeClient(name: String, register: Boolean = true): Path {
        val socket = dir.resolve("$name.sock")
        val server = ServerSocketChannel.open(StandardProtocolFamily.UNIX).bind(UnixDomainSocketAddress.of(socket))
        servers += server
        thread(isDaemon = true) {
            while (server.isOpen) {
                val client = runCatching { server.accept() }.getOrNull() ?: break
                client.use {
                    val message = MessageCodec.read(Channels.newReader(it, Charsets.UTF_8).buffered()) ?: return@use
                    received.getOrPut(name) { CopyOnWriteArrayList() } += message
                    val reply = Response(true, "$name handled $message", mapOf("state" to "menu"), buildJsonObject { put("echo", name) })
                    MessageCodec.write(Channels.newWriter(it, Charsets.UTF_8), reply)
                }
            }
        }
        if (register) {
            val file = ClientRegistry.register(dir.resolve(".nebs"), ClientEntry.forCurrentProcess(name, UUID.randomUUID().toString(), socket, dir))
            Files.move(file, file.resolveSibling("$name-fake.json")) // fake clients share this test's pid
        }
        return socket
    }

    private fun gradle(vararg args: String) =
        GradleRunner.create().withProjectDir(dir.toFile()).withPluginClasspath().withArguments(*args)

    private fun connect(host: String, port: Int) = MessageCodec.decode("""{"type":"connect","host":"$host","port":$port}""")

    @Test
    fun `the only running client is picked automatically`() {
        fakeClient("Alice")
        val result = gradle("nebsConnect", "--host=mc.example.com", "--port=25570").build()
        assertContains(result.output, "Alice handled ConnectToServer(host=mc.example.com, port=25570)")
        assertEquals(listOf(connect("mc.example.com", 25570)), received["Alice"]?.toList())
    }

    @Test
    fun `several clients need --client or --all`() {
        fakeClient("Alice")
        fakeClient("Bob")
        assertContains(gradle("nebs", "--command=ping").buildAndFail().output, "choose with --client")

        gradle("nebs", "--command=connect a.b:1", "--client=bob").build()
        assertEquals(listOf(connect("a.b", 1)), received["Bob"]?.toList())
        assertEquals(null, received["Alice"]?.toList())

        val all = gradle("nebsConnect", "--host=c.d", "--all").build().output
        assertContains(all, "Alice: Alice handled")
        assertContains(all, "Bob: Bob handled")
        assertEquals(connect("c.d", 25565), received["Alice"]!!.single())
    }

    @Test
    fun `list shows running clients`() {
        fakeClient("Alice")
        fakeClient("Bob")
        val output = gradle("nebsListClients").build().output
        assertContains(output, "Alice")
        assertContains(output, "Bob")
        assertContains(output, "menu")
    }

    @Test
    fun `socket option reaches unregistered clients`() {
        val socket = fakeClient("Loner", register = false)
        gradle("nebs", "--command=ping", "--socket=$socket").build()
        assertEquals(1, received["Loner"]!!.size)
    }

    @Test
    fun `home can be moved`() {
        val elsewhere = Files.createTempDirectory("nebs-elsewhere")
        dir.resolve("build.gradle.kts").writeText(
            """
            plugins { id("dsh.nebsclient.socket") }
            nebs { home = file("${elsewhere.toString().replace("\\", "/")}") }
            """.trimIndent(),
        )
        fakeClient("Alice") // registered in <dir>/.nebs, not in the configured home
        assertContains(gradle("nebs", "--command=ping").buildAndFail().output, "no clients are running in $elsewhere")
        gradle("nebs", "--command=ping", "--home=${dir.resolve(".nebs")}").build()
    }

    @Test
    fun `structured results are printed`() {
        fakeClient("Alice")
        assertContains(gradle("nebs", "--command=status").build().output, "\"echo\": \"Alice\"")
    }

    @Test
    fun `subscribe is refused`() {
        fakeClient("Alice")
        assertContains(gradle("nebs", "--command=subscribe").buildAndFail().output, "use nebs-cli")
    }

    @Test
    fun `build scripts can use the NebsClient API`() {
        fakeClient("Alice")
        dir.resolve("build.gradle.kts").writeText(
            """
            import dsh.nebsclient.api.NebsClient

            plugins { id("dsh.nebsclient.socket") }

            tasks.register("lookAround") {
                val home = file(".nebs").toPath()
                doLast {
                    NebsClient.attach(home = home).use { client ->
                        client.lookAt(0, 64, 0)
                        println("status of " + client.name + ": " + client.ping().state)
                    }
                }
            }
            """.trimIndent(),
        )
        val output = gradle("lookAround").build().output
        assertContains(output, "status of Alice")
        assertTrue(received["Alice"]!!.any { it.javaClass.simpleName == "LookAt" })
    }

    @Test
    fun `invalid command fails the build`() {
        fakeClient("Alice")
        assertContains(gradle("nebs", "--command=bogus").buildAndFail().output, "unknown command 'bogus'")
    }
}
