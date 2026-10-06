import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
    `maven-publish`
}

val javaVersion = libs.versions.java.lib.get()

dependencies {
    api(libs.kotlinx.serialization.json)
    testImplementation(kotlin("test"))
}

java {
    sourceCompatibility = JavaVersion.toVersion(javaVersion)
    targetCompatibility = JavaVersion.toVersion(javaVersion)
}

kotlin {
    compilerOptions {
        jvmTarget = JvmTarget.fromTarget(javaVersion)
        freeCompilerArgs.add("-Xjdk-release=$javaVersion")
    }
}

tasks.test {
    useJUnitPlatform()
}

// Published so the Gradle plugin (which depends on core) can be consumed from mavenLocal.
publishing {
    publications {
        create<MavenPublication>("maven") {
            from(components["java"])
        }
    }
}
