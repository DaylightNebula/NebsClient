import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.fabric.loom)
    alias(libs.plugins.kotlin.jvm)
}

val javaVersion = libs.versions.java.mod.get()

base {
    archivesName = "nebs-client-mod"
}

loom {
    runs {
        named("client") {
            // Enable the socket bridge for dev runs (`./gradlew :mod:runClient`).
            programArguments.add("--socket-comm")
        }
    }
}

dependencies {
    minecraft(libs.minecraft)
    implementation(libs.fabric.loader)
    implementation(libs.fabric.api)
    implementation(libs.fabric.language.kotlin)

    // Shared message model. Kotlin stdlib + kotlinx.serialization are provided at runtime
    // by fabric-language-kotlin, so only the core classes themselves are bundled (jar-in-jar).
    implementation(project(":core"))
    include(project(":core"))
}

tasks.processResources {
    val props = mapOf(
        "version" to project.version,
        "minecraft_version" to libs.versions.minecraft.get(),
        "loader_version" to libs.versions.fabric.loader.get(),
        "java_version" to javaVersion,
    )
    inputs.properties(props)
    filesMatching("fabric.mod.json") {
        expand(props)
    }
}

java {
    sourceCompatibility = JavaVersion.toVersion(javaVersion)
    targetCompatibility = JavaVersion.toVersion(javaVersion)
}

tasks.withType<JavaCompile>().configureEach {
    options.release = javaVersion.toInt()
}

kotlin {
    compilerOptions.jvmTarget = JvmTarget.fromTarget(javaVersion)
}
