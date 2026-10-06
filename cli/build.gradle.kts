import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlin.jvm)
    application
}

val javaVersion = libs.versions.java.lib.get()

dependencies {
    implementation(project(":core"))
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

application {
    applicationName = "nebs-cli"
    mainClass = "com.nebs.cli.MainKt"
}

// Let `./gradlew :cli:run --args="..."` read from the terminal in interactive mode.
tasks.named<JavaExec>("run") {
    standardInput = System.`in`
}
