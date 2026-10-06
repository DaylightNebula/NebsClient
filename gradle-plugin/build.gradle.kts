import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    `java-gradle-plugin`
    alias(libs.plugins.kotlin.jvm)
    `maven-publish`
}

val javaVersion = libs.versions.java.lib.get()

dependencies {
    implementation(project(":core"))
    testImplementation(kotlin("test"))
}

gradlePlugin {
    plugins {
        create("nebs") {
            id = "com.nebs.socket"
            implementationClass = "com.nebs.gradle.NebsPlugin"
            displayName = "Nebs Client socket"
            description = "Gradle tasks that send nebs-cli commands to a running Nebs Client mod."
        }
    }
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
