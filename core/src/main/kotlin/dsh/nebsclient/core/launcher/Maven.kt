package dsh.nebsclient.core.launcher

internal object Maven {
    const val MOJANG_LIBRARIES = "https://libraries.minecraft.net/"
    const val FABRIC = "https://maven.fabricmc.net/"

    private data class Coordinate(val group: String, val artifact: String, val version: String, val classifier: String?, val ext: String)

    private fun parse(name: String): Coordinate {
        val (coords, ext) = name.split('@', limit = 2).let { it[0] to (it.getOrNull(1) ?: "jar") }
        val parts = coords.split(':')
        require(parts.size in 3..4) { "Invalid Maven coordinate '$name'" }
        return Coordinate(parts[0], parts[1], parts[2], parts.getOrNull(3), ext)
    }

    /** `group:artifact:version[:classifier][@ext]` → `group/path/artifact/version/artifact-version[-classifier].ext` */
    fun path(name: String): String {
        val c = parse(name)
        val classifier = c.classifier?.let { "-$it" } ?: ""
        return "${c.group.replace('.', '/')}/${c.artifact}/${c.version}/${c.artifact}-${c.version}$classifier.${c.ext}"
    }

    /** Identity of a library ignoring its version, used to let Fabric's libraries replace vanilla ones. */
    fun key(name: String): String {
        val c = parse(name)
        return listOfNotNull(c.group, c.artifact, c.classifier).joinToString(":")
    }
}
