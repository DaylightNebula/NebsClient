package com.nebs.core.launcher

import com.nebs.core.NebsHome
import com.nebs.core.SocketDefaults
import java.io.File
import java.nio.file.Path

/** Builds the java command line for a client. Separate from [ClientLauncher] so it can be tested without a process. */
internal object LaunchCommand {
    data class Input(
        val java: Path,
        val templateDir: Path,
        val version: VersionJson,
        val fabric: FabricProfile,
        val clientJar: Path,
        val profile: OfflineProfile,
        val instanceDir: Path,
        val home: Path,
        /** Socket override; by default the client picks `<tmpdir>/nebs-<pid>.sock` itself. */
        val socket: Path? = null,
        val width: Int? = null,
        val height: Int? = null,
        val memory: String = "2G",
        val extraJvmArgs: List<String> = emptyList(),
        val platform: Platform = Platform.current,
    )

    fun build(input: Input): List<String> = with(input) {
        val libraries = templateDir.resolve("libraries")
        val assets = templateDir.resolve("assets")

        // Fabric's libraries replace vanilla ones with the same group/artifact/classifier.
        val classpath = LinkedHashMap<String, Path>()
        version.libraries.filter { Rules.allowed(it.rules, platform) }.forEach { classpath[Maven.key(it.name)] = libraries.resolve(it.path) }
        fabric.libraries.forEach { classpath[Maven.key(it.name)] = libraries.resolve(it.path) }

        val features = mapOf("has_custom_resolution" to (width != null && height != null))
        val placeholders = mapOf(
            "natives_directory" to instanceDir.resolve("natives").toString(),
            "launcher_name" to "nebs",
            "launcher_version" to "1.0",
            "classpath" to (classpath.values + listOf(clientJar)).joinToString(File.pathSeparator),
            "classpath_separator" to File.pathSeparator,
            "library_directory" to libraries.toString(),
            "version_name" to fabric.id,
            "version_type" to version.type,
            "game_directory" to instanceDir.toString(),
            "assets_root" to assets.toString(),
            "assets_index_name" to version.assetIndex.id,
            "auth_player_name" to profile.name,
            "auth_uuid" to profile.uuid.toString().replace("-", ""),
            // No account: a dummy token, plus --offlineDeveloperMode below so the game never contacts auth services.
            "auth_access_token" to "0",
            "auth_xuid" to "0",
            "clientid" to "0",
            "user_type" to "legacy",
            "resolution_width" to (width ?: 854).toString(),
            "resolution_height" to (height ?: 480).toString(),
        )

        buildList {
            add(java.toString())
            add("-Xmx$memory")
            addAll(Rules.resolve(version.arguments.jvm, platform, features, placeholders))
            addAll(Rules.resolve(fabric.arguments.jvm, platform, features, placeholders))
            version.logging?.client?.let {
                add(Rules.substitute(it.argument, mapOf("path" to assets.resolve("log_configs").resolve(it.file.id).toString())))
            }
            add("-Dfabric.addMods=${templateDir.resolve("mods")}")
            add("-D${ClientLauncher.TEMPLATE_PROPERTY}=$templateDir")
            add("-D${NebsHome.PROPERTY}=$home")
            addAll(extraJvmArgs)
            add(fabric.mainClass)
            addAll(Rules.resolve(version.arguments.game, platform, features, placeholders))
            addAll(Rules.resolve(fabric.arguments.game, platform, features, placeholders))
            add("--offlineDeveloperMode")
            add(SocketDefaults.ENABLE_ARG)
            socket?.let {
                add(SocketDefaults.PATH_ARG)
                add(it.toString())
            }
        }
    }
}
