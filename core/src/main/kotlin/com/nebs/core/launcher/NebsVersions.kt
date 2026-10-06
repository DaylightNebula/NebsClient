package com.nebs.core.launcher

import kotlinx.serialization.Serializable
import java.util.Properties

/** The game, loader and mod versions a template is built from. */
@Serializable
data class NebsVersions(
    val minecraft: String,
    val fabricLoader: String,
    val fabricApi: String,
    val fabricLanguageKotlin: String,
) {
    companion object {
        /** The versions this build of nebs was compiled against (generated from the version catalog). */
        val bundled: NebsVersions by lazy {
            val props = Properties()
            NebsVersions::class.java.getResourceAsStream("/nebs-versions.properties")
                ?.use(props::load)
                ?: error("nebs-versions.properties is missing from the classpath")
            NebsVersions(
                minecraft = props.getProperty("minecraft"),
                fabricLoader = props.getProperty("fabric_loader"),
                fabricApi = props.getProperty("fabric_api"),
                fabricLanguageKotlin = props.getProperty("fabric_language_kotlin"),
            )
        }
    }
}
