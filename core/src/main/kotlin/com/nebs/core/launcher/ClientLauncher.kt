package com.nebs.core.launcher

import com.nebs.core.ClientRegistry
import com.nebs.core.NebsHome
import java.nio.file.Files
import java.nio.file.Path
import java.util.UUID

/**
 * How to launch a client. Everything is optional.
 *
 * @property home nebs home the client registers in and takes default folders from; see [NebsHome.resolve].
 * @property template installed template to launch from; defaults to `<home>/template`.
 * @property instanceDir game directory for this client; defaults to `<instancesDir>/<name>`.
 * @property instancesDir parent of the default instance directory; defaults to `<home>/instances`.
 * @property name player name; defaults to a random `NebsNNNN`.
 * @property uuid player UUID; defaults to the offline-mode UUID for [name].
 * @property socketPath socket the client listens on; defaults to [ClientRegistry.socketFor] its pid.
 * @property installIfMissing install the template first if it isn't installed (otherwise fail).
 * @property modJars mods to install when installing the template; defaults to the bundled nebs mod.
 * @property onInstall called with the template folder just before an automatic install starts.
 * @property installProgress progress of an automatic install.
 */
data class LaunchSpec(
    val home: Path? = null,
    val template: Path? = null,
    val instanceDir: Path? = null,
    val instancesDir: Path? = null,
    val name: String? = null,
    val uuid: UUID? = null,
    val socketPath: Path? = null,
    val width: Int? = null,
    val height: Int? = null,
    val memory: String = "2G",
    val extraJvmArgs: List<String> = emptyList(),
    val installIfMissing: Boolean = true,
    val modJars: List<Path> = emptyList(),
    val onInstall: (Path) -> Unit = {},
    val installProgress: ProgressListener = ProgressListener.NONE,
)

/** Launches offline clients from an installed [Template], each in its own instance directory. */
object ClientLauncher {
    /** System property passed to every launched client so it can spawn siblings from the same template. */
    const val TEMPLATE_PROPERTY = "nebs.template"

    /** Written to a new instance's `options.txt`: skip first-launch prompts and keep background clients cheap. */
    private val DEFAULT_OPTIONS = mapOf(
        "onboardAccessibility" to "false",
        "skipMultiplayerWarning" to "true",
        "joinedFirstServer" to "true",
        "tutorialStep" to "none",
        "pauseOnLostFocus" to "false",
        "narrator" to "0",
        "maxFps" to "30",
    )

    /**
     * Enforced on every launch. With vsync on, macOS blocks the first buffer swap of a window that
     * isn't on screen, so a client started in the background never finishes loading.
     */
    private val REQUIRED_OPTIONS = mapOf("enableVsync" to "false")

    /**
     * Starts a client and returns once the process has started; use [ClientProcess.awaitReady] to wait
     * for it to load. If the template isn't installed yet, it is installed first (unless
     * [LaunchSpec.installIfMissing] is off). The client is detached: it keeps running after this
     * JVM exits, even if it is interrupted or killed, until it is quit.
     *
     * @throws TemplateNotInstalledException if the template isn't installed and installing is off.
     * @throws IllegalStateException if a client is already running in the instance directory.
     */
    fun launch(spec: LaunchSpec = LaunchSpec()): ClientProcess {
        val home = NebsHome.resolve(spec.home)
        val templateDir = spec.template ?: NebsHome.template(home)
        val template = if (spec.installIfMissing) {
            TemplateInstaller.ensureInstalled(
                InstallSpec(home = home, templateDir = templateDir, modJars = spec.modJars, onProgress = spec.installProgress),
                spec.onInstall,
            )
        } else {
            Template.load(templateDir)
        }
        val profile = OfflineProfile.create(spec.name, spec.uuid)
        val instanceDir = (spec.instanceDir ?: (spec.instancesDir ?: NebsHome.instances(home)).resolve(profile.name))
            .toAbsolutePath().normalize()

        ClientProcess.attach(instanceDir)?.takeIf { it.isAlive }?.let {
            throw IllegalStateException("A client (pid ${it.pid}) is already running in $instanceDir")
        }

        Files.createDirectories(instanceDir.resolve("logs"))
        writeOptions(instanceDir.resolve("options.txt"))

        val command = LaunchCommand.build(
            LaunchCommand.Input(
                java = template.java,
                templateDir = template.dir,
                version = template.versionJson(),
                fabric = template.fabricProfile(),
                clientJar = template.clientJar(),
                profile = profile,
                instanceDir = instanceDir,
                home = home,
                socket = spec.socketPath?.toAbsolutePath(),
                width = spec.width,
                height = spec.height,
                memory = spec.memory,
                extraJvmArgs = spec.extraJvmArgs,
            ),
        )

        val process = startDetached(command, instanceDir, instanceDir.resolve("logs/launcher-output.log"))

        val info = InstanceInfo(
            name = profile.name,
            uuid = profile.uuid.toString(),
            socket = (spec.socketPath?.toAbsolutePath() ?: ClientRegistry.socketFor(process.pid())).toString(),
            pid = process.pid(),
            template = template.dir.toString(),
        )
        info.write(instanceDir)
        return ClientProcess(instanceDir, info, process)
    }

    /**
     * Starts the game so it outlives whatever launched it: it keeps running when the launching
     * program exits, is interrupted (Ctrl-C), killed, or its terminal is closed. Only `quit`/stop
     * (or killing the game itself) ends it.
     *
     * On macOS/Linux a `/bin/sh` with job control (`set -m`) starts the game as a background job in
     * its own process group, so signals sent to the launcher's group never reach it, then exits;
     * the game is re-parented to init/launchd. Windows has no process groups to escape, so the game
     * is started directly.
     */
    private fun startDetached(command: List<String>, dir: Path, log: Path): ProcessHandle {
        if (Platform.current.os == "windows") {
            return ProcessBuilder(command)
                .directory(dir.toFile())
                .redirectErrorStream(true)
                .redirectOutput(log.toFile())
                .redirectInput(ProcessBuilder.Redirect.from(java.io.File("NUL")))
                .start()
                .toHandle()
        }
        val script = "set -m; \"\$@\" </dev/null >\"\$NEBS_LOG\" 2>&1 & echo \$!"
        val shell = ProcessBuilder(listOf("/bin/sh", "-c", script, "nebs-launch") + command)
            .directory(dir.toFile())
            .redirectErrorStream(true)
            .redirectInput(ProcessBuilder.Redirect.from(java.io.File("/dev/null")))
            .apply { environment()["NEBS_LOG"] = log.toString() }
            .start()
        val output = shell.inputStream.bufferedReader().readText().trim()
        shell.waitFor()
        val pid = output.lines().lastOrNull()?.trim()?.toLongOrNull()
            ?: throw IllegalStateException("Couldn't start the client: $output")
        return ProcessHandle.of(pid).orElseThrow { IllegalStateException("The client exited immediately; see $log") }
    }

    private fun writeOptions(file: Path) {
        val options = LinkedHashMap<String, String>()
        if (Files.exists(file)) {
            Files.readAllLines(file).forEach { line ->
                val key = line.substringBefore(':', "")
                if (key.isNotEmpty()) options[key] = line.substringAfter(':')
            }
        } else {
            options.putAll(DEFAULT_OPTIONS)
        }
        options.putAll(REQUIRED_OPTIONS)
        Files.write(file, options.map { (key, value) -> "$key:$value" })
    }
}
