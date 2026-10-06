import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlin.jvm)
    application
}

val javaVersion = libs.versions.java.lib.get()

apply(from = rootProject.file("gradle/bundle-nebs-mod.gradle.kts"))

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

dependencies {
    testImplementation(kotlin("test"))
}

// Real-client integration test: downloads ~700 MB on first run and opens game windows.
// Enable with `./gradlew :cli:test -Pnebs.integration=true`. The template is kept under build/ between runs.
tasks.test {
    useJUnitPlatform()
    val integration = providers.gradleProperty("nebs.integration").orElse("false")
    inputs.property("nebs.integration", integration)
    systemProperty("nebs.integration", integration.get())
    systemProperty("nebs.integration.dir", layout.buildDirectory.dir("nebs-it").get().asFile.absolutePath)
    if (integration.get().toBoolean()) outputs.upToDateWhen { false }
}
