package com.nebs.core.launcher

import com.nebs.core.NebsHome
import kotlinx.serialization.decodeFromString
import java.nio.channels.FileChannel
import java.nio.file.Files
import java.nio.file.StandardOpenOption
import java.nio.file.Path
import java.nio.file.StandardCopyOption

/**
 * What to install.
 *
 * @property home nebs home whose `template` folder is the default [templateDir]; see [NebsHome.resolve].
 * @property templateDir where to install; defaults to `<home>/template`.
 * @property modJars mod jars copied into the template's `mods` folder; defaults to the nebs mod
 *   bundled with the calling application (see [BundledMods]).
 * @property java use this Java executable instead of downloading Mojang's runtime.
 */
data class InstallSpec(
    val home: Path? = null,
    val templateDir: Path? = null,
    val versions: NebsVersions = NebsVersions.bundled,
    val modJars: List<Path> = emptyList(),
    val java: Path? = null,
    val onProgress: ProgressListener = ProgressListener.NONE,
)

/**
 * Downloads a complete, ready-to-launch Fabric client into a template directory: Mojang's Java
 * runtime, the game jar, libraries, assets, Fabric Loader, Fabric API, Fabric Language Kotlin and
 * any extra mods.
 *
 * Installing is idempotent: files that are already present and valid are skipped, so re-running
 * completes or repairs a partial install. The `nebs-template.json` marker is written last.
 *
 * Installs into the same folder are serialized (across threads and processes), so clients launched
 * in parallel can all trigger [ensureInstalled] safely.
 */
object TemplateInstaller {
    private const val VERSION_MANIFEST = "https://piston-meta.mojang.com/mc/game/version_manifest_v2.json"
    private const val RUNTIME_INDEX =
        "https://launchermeta.mojang.com/v1/products/java-runtime/2ec0cc96c44e5a76b9c8b7c39df7210883d12871/all.json"
    private const val FABRIC_META = "https://meta.fabricmc.net/v2/versions/loader"
    private const val ASSETS = "https://resources.download.minecraft.net"

    /** Installs (or repairs/updates) the template. */
    fun install(spec: InstallSpec = InstallSpec()): Template {
        val dir = templateDir(spec)
        return locked(dir) { installUnlocked(dir, spec) }
    }

    /**
     * Returns the template, installing it first if it isn't installed yet.
     * [onInstall] runs just before an install starts (e.g. to tell the user).
     */
    fun ensureInstalled(spec: InstallSpec = InstallSpec(), onInstall: (Path) -> Unit = {}): Template {
        val dir = templateDir(spec)
        if (Template.isInstalled(dir)) return Template.load(dir)
        return locked(dir) {
            // Someone else may have finished installing while we waited for the lock.
            if (Template.isInstalled(dir)) {
                Template.load(dir)
            } else {
                onInstall(dir)
                installUnlocked(dir, spec)
            }
        }
    }

    private fun templateDir(spec: InstallSpec): Path =
        (spec.templateDir ?: NebsHome.template(NebsHome.resolve(spec.home))).toAbsolutePath().normalize()

    private val jvmLocks = java.util.concurrent.ConcurrentHashMap<Path, Any>()

    /** Holds an in-JVM lock (file locks don't exclude threads of one JVM) and a file lock (other processes). */
    private fun <T> locked(dir: Path, block: () -> T): T = synchronized(jvmLocks.computeIfAbsent(dir) { Any() }) {
        Files.createDirectories(dir)
        FileChannel.open(dir.resolve(".install.lock"), StandardOpenOption.CREATE, StandardOpenOption.WRITE).use { channel ->
            channel.lock().use { block() }
        }
    }

    private fun installUnlocked(dir: Path, spec: InstallSpec): Template {
        val modJars = spec.modJars.ifEmpty {
            listOf(
                BundledMods.extract()
                    ?: throw IllegalStateException("No nebs mod jar to install: this application doesn't bundle one, so pass the mod jar explicitly"),
            )
        }
        val versions = spec.versions
        val platform = Platform.current
        val downloader = Downloader(spec.onProgress)
        Files.createDirectories(dir)

        // Game version + client jar
        spec.onProgress.onProgress("metadata", 0, 1)
        val manifest = metaJson.decodeFromString(VersionManifest.serializer(), downloader.fetchString(VERSION_MANIFEST))
        val entry = manifest.versions.firstOrNull { it.id == versions.minecraft }
            ?: throw IllegalArgumentException("Unknown Minecraft version ${versions.minecraft}")
        val versionDir = dir.resolve("versions").resolve(versions.minecraft)
        downloader.download(Downloader.Task(entry.url, versionDir.resolve("${versions.minecraft}.json"), entry.sha1))
        val version = metaJson.decodeFromString(VersionJson.serializer(), Files.readString(versionDir.resolve("${versions.minecraft}.json")))

        // Fabric launch profile
        val profileText = downloader.fetchString("$FABRIC_META/${versions.minecraft}/${versions.fabricLoader}/profile/json")
        val profile = metaJson.decodeFromString(FabricProfile.serializer(), profileText)
        val profileDir = dir.resolve("versions").resolve(profile.id)
        Files.createDirectories(profileDir)
        Files.writeString(profileDir.resolve("${profile.id}.json"), profileText)
        spec.onProgress.onProgress("metadata", 1, 1)

        // Game jar, libraries, log config
        val client = version.downloads["client"] ?: error("Version ${version.id} has no client download")
        val files = mutableListOf(Downloader.Task(client.url, versionDir.resolve("${version.id}.jar"), client.sha1, client.size))
        (version.libraries.filter { Rules.allowed(it.rules, platform) } + profile.libraries).mapTo(files) { lib ->
            val download = lib.download
            Downloader.Task(download.url, dir.resolve("libraries").resolve(lib.path), download.sha1, download.size)
        }
        version.logging?.client?.file?.let {
            files += Downloader.Task(it.url, dir.resolve("assets/log_configs").resolve(it.id), it.sha1, it.size)
        }
        downloader.downloadAll("libraries", files)

        // Assets
        val indexFile = dir.resolve("assets/indexes").resolve("${version.assetIndex.id}.json")
        downloader.download(Downloader.Task(version.assetIndex.url, indexFile, version.assetIndex.sha1, version.assetIndex.size))
        val index = metaJson.decodeFromString(AssetIndex.serializer(), Files.readString(indexFile))
        val objects = index.objects.values.distinctBy { it.hash }.map {
            val prefix = it.hash.take(2)
            Downloader.Task("$ASSETS/$prefix/${it.hash}", dir.resolve("assets/objects/$prefix/${it.hash}"), it.hash, it.size)
        }
        downloader.downloadAll("assets", objects)

        // Java runtime
        val java = spec.java?.toAbsolutePath()?.toString()
            ?: dir.relativize(installRuntime(dir, version, platform, downloader)).toString()

        // Mods
        installMods(dir, versions, modJars, downloader)

        val info = TemplateInfo(versions, profile.id, java)
        Files.writeString(dir.resolve(Template.MARKER), metaJson.encodeToString(TemplateInfo.serializer(), info))
        return Template(dir, info)
    }

    /** Installs Mojang's Java runtime for [version] and returns the path of its `java` executable. */
    private fun installRuntime(dir: Path, version: VersionJson, platform: Platform, downloader: Downloader): Path {
        val component = version.javaVersion?.component ?: error("Version ${version.id} doesn't declare a Java runtime")
        val index = metaJson.decodeFromString<Map<String, Map<String, List<RuntimeVersion>>>>(downloader.fetchString(RUNTIME_INDEX))
        val runtime = index[platform.runtimeKey]?.get(component)?.firstOrNull()
            ?: error("Mojang provides no $component runtime for ${platform.runtimeKey}; pass a Java executable instead")
        val manifest = metaJson.decodeFromString(RuntimeManifest.serializer(), downloader.fetchString(runtime.manifest.url))

        val root = dir.resolve("runtime").resolve(component)
        manifest.files.filterValues { it.type == "directory" }.keys.forEach { Files.createDirectories(root.resolve(it)) }
        val files = manifest.files.filterValues { it.type == "file" && it.downloads != null }
        downloader.downloadAll("java runtime", files.map { (path, file) ->
            Downloader.Task(file.downloads!!.raw.url, root.resolve(path), file.downloads.raw.sha1, file.downloads.raw.size)
        })
        files.filterValues { it.executable }.keys.forEach { root.resolve(it).toFile().setExecutable(true) }
        manifest.files.filterValues { it.type == "link" && it.target != null }.forEach { (path, file) ->
            val link = root.resolve(path)
            if (!Files.exists(link, java.nio.file.LinkOption.NOFOLLOW_LINKS)) {
                Files.createDirectories(link.parent)
                Files.createSymbolicLink(link, Path.of(file.target!!))
            }
        }

        val exe = if (platform.os == "windows") "bin/javaw.exe" else "bin/java"
        val javaPath = files.keys.filter { it.endsWith(exe) }.minByOrNull { it.length }
            ?: error("Downloaded runtime has no $exe")
        return root.resolve(javaPath)
    }

    private fun installMods(dir: Path, versions: NebsVersions, modJars: List<Path>, downloader: Downloader) {
        val modsDir = dir.resolve("mods")
        Files.createDirectories(modsDir)
        val fabricMods = listOf(
            "net.fabricmc.fabric-api:fabric-api:${versions.fabricApi}",
            "net.fabricmc:fabric-language-kotlin:${versions.fabricLanguageKotlin}",
        ).map { name ->
            val path = Maven.path(name)
            val url = Maven.FABRIC + path
            val sha1 = runCatching { downloader.fetchString("$url.sha1").trim().take(40) }.getOrNull()
            Downloader.Task(url, modsDir.resolve(path.substringAfterLast('/')), sha1)
        }
        downloader.downloadAll("mods", fabricMods)

        modJars.forEach { Files.copy(it, modsDir.resolve(it.fileName.toString()), StandardCopyOption.REPLACE_EXISTING) }

        // Drop jars left over from older installs so two versions of a mod never load together.
        val keep = fabricMods.map { it.target.fileName.toString() }.toSet() + modJars.map { it.fileName.toString() }
        Files.list(modsDir).use { stream ->
            stream.filter { it.fileName.toString().endsWith(".jar") && it.fileName.toString() !in keep }.forEach(Files::delete)
        }
    }
}
