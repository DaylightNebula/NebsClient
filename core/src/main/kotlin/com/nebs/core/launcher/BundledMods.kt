package com.nebs.core.launcher

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption

/**
 * Access to the nebs mod jar that front ends (CLI, Gradle plugin) embed as the resource
 * [RESOURCE], so installs work without having to point at the mod jar. [TemplateInstaller] uses it
 * when no mod jars are given.
 */
object BundledMods {
    const val RESOURCE = "nebs-mods/nebs-client-mod.jar"

    /** Extracts the embedded mod jar into [dir] and returns it, or returns `null` if [anchor]'s class loader has none. */
    fun extract(anchor: Class<*> = BundledMods::class.java, dir: Path = Files.createTempDirectory("nebs-mods")): Path? {
        val stream = anchor.classLoader.getResourceAsStream(RESOURCE) ?: return null
        Files.createDirectories(dir)
        val target = dir.resolve(RESOURCE.substringAfterLast('/'))
        stream.use { Files.copy(it, target, StandardCopyOption.REPLACE_EXISTING) }
        return target
    }
}
