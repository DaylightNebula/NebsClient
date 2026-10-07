package dsh.nebsclient.core

import java.nio.file.Path

/**
 * The nebs home folder: client templates, instance folders, and the registry of running clients.
 *
 * Resolved from, in order: an explicit path, the `nebs.home` system property (set on clients nebs
 * launches), the `NEBS_HOME` environment variable, or `.nebs` in [base] (the working directory).
 *
 * ```
 * .nebs/
 *   template/            installed client (see TemplateInstaller)
 *   instances/<name>/    game folders of launched clients
 *   clients/<name>-<pid>.json   one file per running client (see ClientRegistry)
 * ```
 */
object NebsHome {
    const val ENV = "NEBS_HOME"
    const val PROPERTY = "nebs.home"
    const val DIR_NAME = ".nebs"

    fun resolve(explicit: Path? = null, base: Path = Path.of("")): Path {
        val home = explicit
            ?: System.getProperty(PROPERTY)?.takeIf { it.isNotBlank() }?.let(Path::of)
            ?: System.getenv(ENV)?.takeIf { it.isNotBlank() }?.let(Path::of)
            ?: base.resolve(DIR_NAME)
        return home.toAbsolutePath().normalize()
    }

    fun template(home: Path = resolve()): Path = home.resolve("template")
    fun instances(home: Path = resolve()): Path = home.resolve("instances")
    fun clients(home: Path = resolve()): Path = home.resolve("clients")
}
