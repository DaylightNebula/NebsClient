package dsh.nebsclient.core

/** Client launch arguments understood by the mod. */
object SocketDefaults {
    /** Enables the socket bridge in the mod. */
    const val ENABLE_ARG = "--socket-comm"

    /** Optional (`--socket-path <path>`): listen here instead of `<tmpdir>/nebs-<pid>.sock`. */
    const val PATH_ARG = "--socket-path"

    /** Optional (`--nebs-home <dir>`): the nebs home to register in instead of [NebsHome.resolve]'s default. */
    const val HOME_ARG = "--nebs-home"
}
