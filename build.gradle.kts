plugins {
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.fabric.loom) apply false
}

// The version lives in gradle.properties so releases can override it: -Pversion=1.2.3
// The group can be overridden too (-Pnebs.group=...), which JitPack builds use; see jitpack.yml.
allprojects {
    group = providers.gradleProperty("nebs.group").getOrElse("com.nebs")
}
