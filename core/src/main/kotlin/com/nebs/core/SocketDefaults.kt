package com.nebs.core

import java.nio.file.Path

/** Settings shared by the mod (socket server) and any client application. */
object SocketDefaults {
    /** Client launch argument that enables the socket bridge in the mod. */
    const val ENABLE_ARG = "--socket-comm"

    /** Optional client launch argument (`--socket-path <path>`) overriding [defaultPath]. */
    const val PATH_ARG = "--socket-path"

    /** Environment variable that overrides [defaultPath] for both sides. */
    const val PATH_ENV = "NEBS_SOCKET"

    private const val FILE_NAME = "nebs-client.sock"

    /** `$NEBS_SOCKET` if set, otherwise `<java.io.tmpdir>/nebs-client.sock`. */
    fun defaultPath(): Path =
        System.getenv(PATH_ENV)?.takeIf { it.isNotBlank() }?.let(Path::of)
            ?: Path.of(System.getProperty("java.io.tmpdir"), FILE_NAME)
}
