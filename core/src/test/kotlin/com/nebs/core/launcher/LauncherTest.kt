package com.nebs.core.launcher

import com.nebs.core.SocketDefaults
import java.nio.file.Files
import java.nio.file.Path
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class LauncherTest {
    private fun fixture(name: String) = LauncherTest::class.java.getResource("/fixtures/$name")!!.readText()
    private val version = metaJson.decodeFromString(VersionJson.serializer(), fixture("version.json"))
    private val fabric = metaJson.decodeFromString(FabricProfile.serializer(), fixture("fabric-profile.json"))
    private val macArm = Platform("osx", "aarch64")
    private val linux = Platform("linux", "amd64")

    @Test
    fun `maven coordinates map to repository paths`() {
        assertEquals("org/lwjgl/lwjgl/3.4.3/lwjgl-3.4.3-natives-macos.jar", Maven.path("org.lwjgl:lwjgl:3.4.3:natives-macos"))
        assertEquals("net/fabricmc/fabric-loader/0.19.5/fabric-loader-0.19.5.jar", Maven.path("net.fabricmc:fabric-loader:0.19.5"))
        assertEquals("org.lwjgl:lwjgl:natives-macos", Maven.key("org.lwjgl:lwjgl:3.4.3:natives-macos"))
    }

    @Test
    fun `os rules pick platform libraries`() {
        val objc = version.libraries.single { it.name.startsWith("ca.weblite") }
        assertTrue(Rules.allowed(objc.rules, macArm))
        assertFalse(Rules.allowed(objc.rules, linux))
        assertTrue(Rules.allowed(listOf(Rule("allow"), Rule("disallow", Rule.Os(arch = "x86"))), linux))
        assertFalse(Rules.allowed(listOf(Rule("allow"), Rule("disallow", Rule.Os(arch = "x86"))), Platform("windows", "x86")))
    }

    @Test
    fun `offline uuid matches the server algorithm`() {
        assertEquals(UUID.fromString("b50ad385-829d-3141-a216-7e7d7539ba7f"), OfflineProfile.offlineUuid("Notch"))
        val generated = OfflineProfile.create()
        assertTrue(generated.name.matches(Regex("Nebs\\d{4}")))
        assertEquals(OfflineProfile.offlineUuid(generated.name), generated.uuid)
        assertFailsWith<IllegalArgumentException> { OfflineProfile.create("bad name!") }
    }

    private fun command(platform: Platform, width: Int? = null, socket: Path? = null) = LaunchCommand.build(
        LaunchCommand.Input(
            java = Path.of("/t/java"),
            templateDir = Path.of("/t"),
            version = version,
            fabric = fabric,
            clientJar = Path.of("/t/versions/26.3/26.3.jar"),
            profile = OfflineProfile.create("Alice"),
            instanceDir = Path.of("/i/Alice"),
            home = Path.of("/h"),
            socket = socket,
            width = width,
            height = width,
            platform = platform,
        ),
    )

    @Test
    fun `launch command for macOS`() {
        val cmd = command(macArm, socket = Path.of("/tmp/a.sock"))
        assertEquals("/t/java", cmd.first())
        assertTrue("-XstartOnFirstThread" in cmd)

        val mainIndex = cmd.indexOf("net.fabricmc.loader.impl.launch.knot.KnotClient")
        assertTrue(mainIndex > 0)
        val jvm = cmd.subList(0, mainIndex)
        assertTrue("-Dfabric.addMods=/t/mods" in jvm)
        assertTrue("-Dnebs.template=/t" in jvm)
        assertTrue("-Dnebs.home=/h" in jvm)
        assertTrue(jvm.any { it.startsWith("-DFabricMcEmu=") })
        assertTrue("-Dlog4j.configurationFile=/t/assets/log_configs/client-1.21.2.xml" in jvm)

        val classpath = cmd[cmd.indexOf("-cp") + 1].split(java.io.File.pathSeparator)
        // Fabric's asm replaces vanilla's; all macOS natives variants are kept; the game jar comes last.
        assertTrue("/t/libraries/org/ow2/asm/asm/9.10.1/asm-9.10.1.jar" in classpath)
        assertFalse(classpath.any { "asm-9.0.jar" in it })
        assertTrue("/t/libraries/org/lwjgl/lwjgl/3.4.3/lwjgl-3.4.3-natives-macos-arm64.jar" in classpath)
        assertFalse(classpath.any { "natives-linux" in it })
        assertEquals("/t/versions/26.3/26.3.jar", classpath.last())

        val game = cmd.subList(mainIndex + 1, cmd.size)
        fun arg(name: String) = game[game.indexOf(name) + 1]
        assertEquals("Alice", arg("--username"))
        assertEquals(OfflineProfile.offlineUuid("Alice").toString().replace("-", ""), arg("--uuid"))
        assertEquals("0", arg("--accessToken"))
        assertEquals("/i/Alice", arg("--gameDir"))
        assertEquals("/t/assets", arg("--assetsDir"))
        assertEquals("/tmp/a.sock", arg(SocketDefaults.PATH_ARG))
        assertTrue("--offlineDeveloperMode" in game)
        assertTrue(SocketDefaults.ENABLE_ARG in game)
        assertFalse("--width" in game)
        assertFalse(cmd.any { "\${" in it })
    }

    @Test
    fun `launch command for linux with resolution`() {
        val cmd = command(linux, width = 640)
        assertFalse("-XstartOnFirstThread" in cmd)
        // Without an override the client picks its own per-pid socket.
        assertFalse(SocketDefaults.PATH_ARG in cmd)
        assertEquals("640", cmd[cmd.indexOf("--width") + 1])
        val classpath = cmd[cmd.indexOf("-cp") + 1]
        assertTrue("natives-linux" in classpath)
        assertFalse("objc-bridge" in classpath)
    }

    @Test
    fun `an installed template is reused without reinstalling`() {
        val dir = Files.createTempDirectory("nebs-template")
        val info = TemplateInfo(NebsVersions("26.3", "0.19.5", "x", "y"), "fabric-loader-0.19.5-26.3", "runtime/bin/java")
        Files.writeString(dir.resolve(Template.MARKER), metaJson.encodeToString(TemplateInfo.serializer(), info))
        var installed = false
        val template = TemplateInstaller.ensureInstalled(InstallSpec(templateDir = dir)) { installed = true }
        assertFalse(installed)
        assertEquals(info, template.info)
    }

    @Test
    fun `launching without auto-install needs an installed template`() {
        val home = Files.createTempDirectory("nebs-home")
        assertFailsWith<TemplateNotInstalledException> { ClientLauncher.launch(LaunchSpec(home = home, installIfMissing = false)) }
    }
}
