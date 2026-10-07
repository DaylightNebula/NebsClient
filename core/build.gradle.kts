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

// Versions the launcher installs by default, kept in sync with what the mod is built against.
tasks.processResources {
    val props = mapOf(
        "minecraft" to libs.versions.minecraft.get(),
        "fabric_loader" to libs.versions.fabric.loader.get(),
        "fabric_api" to libs.versions.fabric.api.get(),
        "fabric_language_kotlin" to libs.versions.fabric.language.kotlin.get(),
    )
    inputs.properties(props)
    filesMatching("nebs-versions.properties") {
        expand(props)
    }
}

// Ships the Claude Code skill (claude-plugin/skills/nebs-client) in the jar, so every front end can
// install the instructions matching its own version (see ClaudeSkill). Jars can't list a resource
// directory, so an index of the files is written next to them.
val skillSource = rootProject.layout.projectDirectory.dir("claude-plugin/skills")
val bundleClaudeSkill = tasks.register<Sync>("bundleClaudeSkill") {
    val generated = layout.buildDirectory.dir("generated/claude-skill")
    from(skillSource) { into("nebs-claude/skills") }
    into(generated)
    doLast {
        val root = generated.get().dir("nebs-claude").asFile
        val files = root.resolve("skills").walkTopDown().filter { it.isFile }
            .map { it.relativeTo(root.resolve("skills")).invariantSeparatorsPath }
            .sorted().toList()
        root.resolve("index.txt").writeText(files.joinToString("\n", postfix = "\n"))
    }
}
sourceSets.main {
    resources.srcDir(files(layout.buildDirectory.dir("generated/claude-skill")).builtBy(bundleClaudeSkill))
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
