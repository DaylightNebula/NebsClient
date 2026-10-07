package dsh.nebsclient.core.message

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Saves a screenshot as `<game dir>/screenshots/<name>.png` (default name: `nebs-<timestamp>`).
 * The reply's `data` holds the `path`, `width` and `height`.
 */
@Serializable
@SerialName("screenshot")
data class TakeScreenshot(val name: String? = null) : Message {
    init {
        require(name == null || name.matches(Regex("[A-Za-z0-9._-]{1,100}"))) { "name may only contain letters, digits, '.', '_' and '-'" }
    }
}

/** Resizes the game window, in screen points (on high-DPI displays the framebuffer and screenshots are larger). */
@Serializable
@SerialName("set-window")
data class SetWindowSize(val width: Int, val height: Int) : Message {
    init {
        require(width in 160..8192 && height in 120..8192) { "size must be at least 160x120 and at most 8192x8192" }
    }
}

/** Sets a video/game option by name (as listed by `options`), e.g. `fov 90` or `renderDistance 8`. */
@Serializable
@SerialName("set-option")
data class SetOption(val name: String, val value: String) : Message

/** Lists the options `set-option` accepts, with their current values. */
@Serializable
@SerialName("options")
data object GetOptions : Message

/** Shows or hides the HUD (like F1). */
@Serializable
@SerialName("hud")
data class SetHud(val visible: Boolean) : Message

/** Shows or hides the debug overlay (like F3). */
@Serializable
@SerialName("f3")
data class SetDebugOverlay(val visible: Boolean) : Message

/** FPS, memory, loaded chunks and entity counts. */
@Serializable
@SerialName("perf")
data object GetPerf : Message

/** The last [lines] lines of the client log, or only warnings/errors and stack traces with [errors]. */
@Serializable
@SerialName("logs")
data class GetLogs(val lines: Int = 50, val errors: Boolean = false) : Message {
    init {
        require(lines in 1..5000) { "lines must be between 1 and 5000" }
    }
}

/** Reloads resource packs (F3+T), replying when done. */
@Serializable
@SerialName("reload-resources")
data object ReloadResources : Message
