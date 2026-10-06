package com.nebs.core.launcher

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement

// Models for the subset of Mojang's and Fabric's launcher metadata the installer needs.

internal val metaJson = Json {
    ignoreUnknownKeys = true
    prettyPrint = true
}

@Serializable
internal data class Download(
    val url: String,
    val sha1: String? = null,
    val size: Long? = null,
    val path: String? = null,
)

@Serializable
internal data class VersionManifest(val versions: List<Entry>) {
    @Serializable
    data class Entry(val id: String, val url: String, val sha1: String? = null)
}

@Serializable
internal data class Rule(
    val action: String,
    val os: Os? = null,
    val features: Map<String, Boolean>? = null,
) {
    @Serializable
    data class Os(val name: String? = null, val arch: String? = null)
}

/**
 * A classpath entry. Vanilla libraries carry `downloads.artifact`; Fabric ones only carry a Maven
 * [name] and repository [url].
 */
@Serializable
internal data class Library(
    val name: String,
    val downloads: Downloads? = null,
    val rules: List<Rule>? = null,
    val url: String? = null,
    val sha1: String? = null,
    val size: Long? = null,
) {
    @Serializable
    data class Downloads(val artifact: Download? = null)

    /** Path relative to the template's `libraries` directory. */
    val path: String get() = downloads?.artifact?.path ?: Maven.path(name)

    val download: Download
        get() = downloads?.artifact
            ?: Download(url = (url ?: Maven.MOJANG_LIBRARIES).trimEnd('/') + "/" + path, sha1 = sha1, size = size)
}

/** Each entry is either a plain string or `{"rules": [...], "value": "..." | [...]}`. */
@Serializable
internal data class Arguments(
    val game: List<JsonElement> = emptyList(),
    val jvm: List<JsonElement> = emptyList(),
)

@Serializable
internal data class AssetIndexRef(val id: String, val url: String, val sha1: String? = null, val size: Long? = null)

@Serializable
internal data class AssetIndex(val objects: Map<String, Object>) {
    @Serializable
    data class Object(val hash: String, val size: Long)
}

@Serializable
internal data class Logging(val client: Client? = null) {
    @Serializable
    data class Client(val argument: String, val file: LogFile)

    @Serializable
    data class LogFile(val id: String, val url: String, val sha1: String? = null, val size: Long? = null)
}

@Serializable
internal data class JavaVersion(val component: String, val majorVersion: Int)

@Serializable
internal data class VersionJson(
    val id: String,
    val mainClass: String,
    val arguments: Arguments = Arguments(),
    val assetIndex: AssetIndexRef,
    val downloads: Map<String, Download>,
    val libraries: List<Library>,
    val logging: Logging? = null,
    val javaVersion: JavaVersion? = null,
    val type: String = "release",
)

@Serializable
internal data class FabricProfile(
    val id: String,
    val inheritsFrom: String,
    val mainClass: String,
    val arguments: Arguments = Arguments(),
    val libraries: List<Library>,
)

@Serializable
internal data class RuntimeVersion(val manifest: Download)

@Serializable
internal data class RuntimeManifest(val files: Map<String, File>) {
    @Serializable
    data class File(
        val type: String,
        val executable: Boolean = false,
        val downloads: Downloads? = null,
        val target: String? = null,
    )

    @Serializable
    data class Downloads(val raw: Download)
}
