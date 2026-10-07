package dsh.nebsclient.mod.handler

import dsh.nebsclient.core.message.GetLogs
import dsh.nebsclient.core.message.Response
import dsh.nebsclient.core.message.SetOption
import dsh.nebsclient.core.message.SetWindowSize
import dsh.nebsclient.core.message.TakeScreenshot
import dsh.nebsclient.mod.runtime.ClientThread
import dsh.nebsclient.mod.runtime.Ticker
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.add
import net.minecraft.client.OptionInstance
import net.minecraft.client.Options
import net.minecraft.client.Screenshot
import net.minecraft.util.StringRepresentable
import java.lang.reflect.Modifier
import java.nio.file.Files
import java.nio.file.Path
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit

object CaptureHandlers {
    private val TIMESTAMP = DateTimeFormatter.ofPattern("yyyy-MM-dd_HH.mm.ss.SSS")

    fun screenshot(m: TakeScreenshot): Response {
        val done = CompletableFuture<Response>()
        ClientThread.call { mc ->
            val dir = mc.gameDirectory.toPath().resolve(Screenshot.SCREENSHOT_DIR)
            Files.createDirectories(dir)
            val name = (m.name ?: "nebs-${LocalDateTime.now().format(TIMESTAMP)}").let { if (it.endsWith(".png")) it else "$it.png" }
            val file = dir.resolve(name).toAbsolutePath()
            // The pixels are read back from the GPU asynchronously; the callback runs once they're ready.
            Screenshot.takeScreenshot(mc.gameRenderer.mainRenderTarget()) { image ->
                try {
                    image.writeToFile(file)
                    done.complete(
                        Response(
                            true,
                            "Saved $file",
                            data = mapOf("path" to file.toString(), "width" to image.width.toString(), "height" to image.height.toString()),
                        ),
                    )
                } catch (e: Exception) {
                    done.completeExceptionally(e)
                } finally {
                    image.close()
                }
            }
        }
        return Ticker.await(done, 15_000, "Screenshot timed out")
    }

    fun setWindow(m: SetWindowSize): Response = ClientThread.call { mc ->
        mc.window.setWindowed(m.width, m.height)
        // Sizes are in screen points; on high-DPI displays the framebuffer (and screenshots) are larger.
        Response.ok("Window resized to ${m.width}x${m.height} points")
    }

    /** Every `OptionInstance` getter on [Options], by name (e.g. `fov`, `renderDistance`). */
    private fun optionInstances(options: Options): Map<String, OptionInstance<*>> =
        Options::class.java.methods
            .filter { it.parameterCount == 0 && !Modifier.isStatic(it.modifiers) && OptionInstance::class.java.isAssignableFrom(it.returnType) }
            .associate { it.name to it.invoke(options) as OptionInstance<*> }
            .toSortedMap(String.CASE_INSENSITIVE_ORDER)

    private fun display(value: Any?): String = when (value) {
        is StringRepresentable -> value.serializedName
        is Enum<*> -> value.name.lowercase()
        else -> value.toString()
    }

    fun options(): Response = ClientThread.call { mc ->
        val options = optionInstances(mc.options)
        Response.result(
            "${options.size} options",
            buildJsonObject { options.forEach { (name, option) -> put(name, display(option.get())) } },
        )
    }

    fun setOption(m: SetOption): Response = ClientThread.call { mc ->
        val (name, option) = optionInstances(mc.options).entries.firstOrNull { it.key.equals(m.name, ignoreCase = true) }
            ?.let { it.key to it.value }
            ?: throw IllegalArgumentException("Unknown option '${m.name}'; see 'options'")
        val current = option.get()
        val value: Any = when (current) {
            is Boolean -> when (m.value.lowercase()) {
                "true", "on", "yes" -> true
                "false", "off", "no" -> false
                else -> throw IllegalArgumentException("$name takes true or false")
            }
            is Int -> m.value.toIntOrNull() ?: throw IllegalArgumentException("$name takes a whole number")
            is Double -> m.value.toDoubleOrNull() ?: throw IllegalArgumentException("$name takes a number")
            is Enum<*> -> current.javaClass.enumConstants.firstOrNull { display(it).equals(m.value, ignoreCase = true) || it.name.equals(m.value, ignoreCase = true) }
                ?: throw IllegalArgumentException("$name takes one of ${current.javaClass.enumConstants.joinToString { display(it) }}")
            is String -> m.value
            else -> throw IllegalArgumentException("$name can't be set from text")
        }
        @Suppress("UNCHECKED_CAST")
        (option as OptionInstance<Any>).set(value)
        mc.options.save()
        if (option.get() != value) throw IllegalArgumentException("$name didn't accept '${m.value}' (out of range?); it is now ${display(option.get())}")
        Response.ok("$name = ${display(option.get())}")
    }

    fun hud(visible: Boolean): Response = ClientThread.call { mc ->
        if (mc.gui.hud.isHidden == visible) mc.gui.hud.toggle()
        Response.ok(if (visible) "HUD shown" else "HUD hidden")
    }

    fun debugOverlay(visible: Boolean): Response = ClientThread.call { mc ->
        mc.debugEntries.setOverlayVisible(visible)
        Response.ok(if (visible) "Debug overlay shown" else "Debug overlay hidden")
    }

    fun perf(): Response = ClientThread.call { mc ->
        val runtime = Runtime.getRuntime()
        val usedMb = (runtime.totalMemory() - runtime.freeMemory()) / (1024 * 1024)
        val maxMb = runtime.maxMemory() / (1024 * 1024)
        val chunks = mc.level?.chunkSource?.loadedChunksCount ?: 0
        val entities = mc.level?.entitiesForRendering()?.count() ?: 0
        Response.result(
            "${mc.fps} fps, ${usedMb}/${maxMb} MB",
            buildJsonObject {
                put("fps", mc.fps)
                put("memoryUsedMb", usedMb)
                put("memoryMaxMb", maxMb)
                put("loadedChunks", chunks)
                put("entities", entities)
                put("windowWidth", mc.window.width)
                put("windowHeight", mc.window.height)
            },
        )
    }

    private val PROBLEM = Regex("""/(WARN|ERROR|FATAL)]|^\s+at |^Caused by:|Exception|^\s+\.\.\. \d+ more""")

    fun logs(m: GetLogs): Response {
        val file: Path = ClientThread.call { mc -> mc.gameDirectory.toPath().resolve("logs/latest.log") }
        if (!Files.exists(file)) throw IllegalStateException("No log file at $file")
        val all = Files.readAllLines(file)
        val lines = (if (m.errors) all.filter { PROBLEM.containsMatchIn(it) } else all).takeLast(m.lines)
        return Response.result(
            "${lines.size} lines from $file",
            buildJsonObject {
                put("file", file.toString())
                putJsonArray("lines") { lines.forEach { add(it) } }
            },
        )
    }

    fun reloadResources(): Response {
        val future = ClientThread.call { mc -> mc.reloadResourcePacks() }
        future.get(180, TimeUnit.SECONDS)
        return Response.ok("Resources reloaded")
    }
}
