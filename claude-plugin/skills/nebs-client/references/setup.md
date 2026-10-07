# Getting nebs-client

Requirements: Java 17+ for the library, the CLI and the plugin (Gradle 9+). The game itself runs
on a Java 25 runtime that nebs downloads from Mojang, so you don't install it. Clients need a
display. Servers they join must be Minecraft 26.3 with `online-mode=false`.

Versions: `<tag>` below is a GitHub release tag such as `v0.2.0`, or a commit hash. Releases:
https://github.com/DaylightNebula/NebsClient/releases

## Library (Java / Kotlin) from JitPack

```kotlin
// settings.gradle.kts
dependencyResolutionManagement {
    repositories {
        mavenCentral()
        maven("https://jitpack.io")
    }
}

// build.gradle.kts
dependencies {
    implementation("com.github.DaylightNebula.NebsClient:nebs-api:<tag>")
    // or, for tests only:
    // testImplementation("com.github.DaylightNebula.NebsClient:nebs-api:<tag>")
}
```

Maven:

```xml
<repositories>
  <repository><id>jitpack.io</id><url>https://jitpack.io</url></repository>
</repositories>
<dependency>
  <groupId>com.github.DaylightNebula.NebsClient</groupId>
  <artifactId>nebs-api</artifactId>
  <version>TAG</version>
</dependency>
```

The lower-level module is `com.github.DaylightNebula.NebsClient:core:<tag>` (pulled in by
`nebs-api` already).

## Gradle plugin from JitPack

JitPack doesn't serve Gradle plugin markers, so map the plugin id to its module:

```kotlin
// settings.gradle.kts
pluginManagement {
    repositories {
        gradlePluginPortal()
        maven("https://jitpack.io")
    }
    resolutionStrategy {
        eachPlugin {
            if (requested.id.id == "dsh.nebsclient.socket") {
                useModule("com.github.DaylightNebula.NebsClient:gradle-plugin:${requested.version}")
            }
        }
    }
}

// build.gradle.kts
plugins {
    id("dsh.nebsclient.socket") version "<tag>"
}
```

## CLI

Download `nebs-cli-<version>.zip` (or `.tar`) from the GitHub release, unpack it, and run
`bin/nebs-cli` (`bin\nebs-cli.bat` on Windows). From a checkout: `./gradlew :cli:installDist`, then
`cli/build/install/nebs-cli/bin/nebs-cli`.

## Self-contained jar (scripts, plain `java`)

`nebs-api-<version>-all.jar` on each GitHub release contains the API with every dependency. Use it
with `kotlinc -cp … -script`, `.main.kts` `@file:DependsOn("/abs/path/…-all.jar")`, or
`java -cp … Program.java`.

## Installing this skill into a project

The skill ships inside every nebs jar, so it always matches the version you use:

| From | Command |
|------|---------|
| Gradle plugin | `./gradlew nebsInstallClaudeSkill` (`--user` for `~/.claude/skills`) |
| CLI | `nebs-cli claude-skill` (`--dir DIR` or `--user`) |
| Library / `-all` jar | `ClaudeSkill.install(Path.of(".claude/skills"))` (`dsh.nebsclient.core.ClaudeSkill`), or `java -cp nebs-api-<version>-all.jar dsh.nebsclient.core.ClaudeSkill [dir]` |
| Claude Code plugin | `/plugin marketplace add DaylightNebula/NebsClient`, then `/plugin install nebs-client@nebs` |

## Troubleshooting JitPack

- The first request for a new tag triggers a build on JitPack, which can take several minutes and
  may time out your build once. Retry, or open https://jitpack.io/#DaylightNebula/NebsClient and
  press "Get it" to build ahead of time. The build log is linked there.
- Use the exact tag, including its `v`.
