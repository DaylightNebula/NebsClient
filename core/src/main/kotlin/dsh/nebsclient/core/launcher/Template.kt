package dsh.nebsclient.core.launcher

import dsh.nebsclient.core.NebsHome
import kotlinx.serialization.Serializable
import java.nio.file.Files
import java.nio.file.Path

/** Thrown when a template directory has not been (fully) installed. */
class TemplateNotInstalledException(dir: Path) :
    IllegalStateException("No installed client template at $dir. Install one first (e.g. `nebs-cli client install`).")

/** Contents of `nebs-template.json`, written once an install completes. */
@Serializable
data class TemplateInfo(
    val versions: NebsVersions,
    /** Id of the Fabric launch profile, e.g. `fabric-loader-0.19.5-26.3`. */
    val profileId: String,
    /** Java executable, relative to the template directory when it lives inside it. */
    val java: String,
)

/** An installed client template: a shared, read-only game install that instances are launched from. */
class Template(dir: Path, val info: TemplateInfo) {
    val dir: Path = dir.toAbsolutePath().normalize()

    val librariesDir: Path get() = dir.resolve("libraries")
    val assetsDir: Path get() = dir.resolve("assets")
    val modsDir: Path get() = dir.resolve("mods")
    val java: Path get() = dir.resolve(info.java)

    internal fun versionDir(id: String): Path = dir.resolve("versions").resolve(id)
    internal fun versionJson(): VersionJson = readVersion(info.versions.minecraft)
    internal fun fabricProfile(): FabricProfile =
        metaJson.decodeFromString(FabricProfile.serializer(), Files.readString(versionDir(info.profileId).resolve("${info.profileId}.json")))
    internal fun clientJar(): Path = versionDir(info.versions.minecraft).resolve("${info.versions.minecraft}.jar")

    private fun readVersion(id: String): VersionJson =
        metaJson.decodeFromString(VersionJson.serializer(), Files.readString(versionDir(id).resolve("$id.json")))

    companion object {
        const val MARKER = "nebs-template.json"

        fun isInstalled(dir: Path = NebsHome.template()): Boolean = Files.isRegularFile(dir.resolve(MARKER))

        /** @throws TemplateNotInstalledException if [dir] has no completed install. */
        fun load(dir: Path = NebsHome.template()): Template {
            val marker = dir.resolve(MARKER)
            if (!Files.isRegularFile(marker)) throw TemplateNotInstalledException(dir)
            return Template(dir, metaJson.decodeFromString(TemplateInfo.serializer(), Files.readString(marker)))
        }
    }
}
