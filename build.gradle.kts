plugins {
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.fabric.loom) apply false
}

allprojects {
    group = "com.nebs"
    version = "1.0.0"
}
