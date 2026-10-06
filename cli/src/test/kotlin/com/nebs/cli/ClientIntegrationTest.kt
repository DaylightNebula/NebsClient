package com.nebs.cli

import com.nebs.core.ClientRegistry
import com.nebs.core.launcher.BundledMods
import com.nebs.core.launcher.ClientLauncher
import com.nebs.core.launcher.ClientProcess
import com.nebs.core.launcher.InstallSpec
import com.nebs.core.launcher.LaunchSpec
import com.nebs.core.launcher.OfflineProfile
import com.nebs.core.launcher.TemplateInstaller
import com.nebs.core.message.Ping
import com.nebs.core.message.SpawnClient
import org.junit.jupiter.api.Assumptions.assumeTrue
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.deleteRecursively
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.fail

/**
 * Installs a real template, launches two offline clients, has one spawn a third, checks they all
 * show up in the registry, then stops them all. Only runs with `-Pnebs.integration=true`
 * (needs network and a display).
 */
class ClientIntegrationTest {
    @Test
    @OptIn(kotlin.io.path.ExperimentalPathApi::class)
    fun `install, launch, list, broadcast, spawn and stop real clients`() {
        assumeTrue(System.getProperty("nebs.integration").toBoolean(), "set -Pnebs.integration=true to run")
        val home = Path.of(System.getProperty("nebs.integration.dir"))
        home.resolve("instances").deleteRecursively()
        home.resolve("clients").deleteRecursively()

        val mod = BundledMods.extract(ClientIntegrationTest::class.java, Files.createTempDirectory("nebs-mod"))
            ?: fail("cli has no bundled mod jar")
        TemplateInstaller.install(InstallSpec(home = home, modJars = listOf(mod)))

        val launched = mutableListOf<ClientProcess>()
        try {
            val alice = ClientLauncher.launch(LaunchSpec(home = home, name = "Alice")).also(launched::add)
            val bob = ClientLauncher.launch(LaunchSpec(home = home, name = "Bob")).also(launched::add)
            listOf(alice, bob).forEach { it.awaitReady() }

            val spawned = alice.send(SpawnClient(name = "Carol"))
            assertTrue(spawned.success, spawned.detail)
            launched += ClientProcess.attach(home.resolve("instances/Carol")) ?: fail("Carol has no instance file")
            launched.last().awaitReady()

            val clients = ClientRegistry.active(home)
            assertEquals(listOf("Alice", "Bob", "Carol"), clients.map { it.name })
            for ((client, result) in ClientRegistry.broadcast(clients, Ping)) {
                val data = result.getOrThrow().data
                assertEquals(OfflineProfile.offlineUuid(client.name).toString(), data["uuid"])
                assertEquals("menu", data["state"])
            }

            assertTrue(ClientRegistry.select(home, all = true).all { it.stop() })
            assertTrue(ClientRegistry.active(home).isEmpty())
        } finally {
            launched.forEach { it.quit() }
        }
    }
}
