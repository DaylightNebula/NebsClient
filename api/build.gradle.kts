import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
    `maven-publish`
}

val javaVersion = libs.versions.java.lib.get()

// Lets NebsClient() install client templates without being pointed at the mod jar.
apply(from = rootProject.file("gradle/bundle-nebs-mod.gradle.kts"))

dependencies {
    api(project(":core"))
    testImplementation(kotlin("test"))
}

java {
    sourceCompatibility = JavaVersion.toVersion(javaVersion)
    targetCompatibility = JavaVersion.toVersion(javaVersion)
    withSourcesJar()
}

kotlin {
    explicitApi()
    compilerOptions {
        jvmTarget = JvmTarget.fromTarget(javaVersion)
        freeCompilerArgs.add("-Xjdk-release=$javaVersion")
    }
}

tasks.test {
    useJUnitPlatform()
}

// Everything in one jar (API, core, Kotlin stdlib, kotlinx.serialization, bundled mod), for
// `.main.kts` scripts (`@file:DependsOn("path/to/nebs-api-all.jar")`) and plain Java programs.
val allJar = tasks.register<Jar>("allJar") {
    group = "build"
    description = "Builds a self-contained jar of the API and its dependencies."
    archiveBaseName = "nebs-api"
    archiveClassifier = "all"
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
    includeEmptyDirs = false
    from(sourceSets.main.map { it.output })
    from(configurations.runtimeClasspath.map { cp -> cp.map { if (it.isDirectory) it else zipTree(it) } })
    // No multi-release entries (only module descriptors live there): even an empty META-INF/versions/
    // folder makes Kotlin's script dependency resolver ignore the whole jar.
    exclude("META-INF/*.SF", "META-INF/*.DSA", "META-INF/*.RSA", "META-INF/versions/**", "module-info.class")
}

tasks.assemble {
    dependsOn(allJar)
}

publishing {
    publications {
        create<MavenPublication>("maven") {
            artifactId = "nebs-api"
            from(components["java"])
        }
    }
}

tasks.named<Jar>("sourcesJar") {
    exclude("nebs-mods/**") // the bundled mod jar is a resource, not source
}
