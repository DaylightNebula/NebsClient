# Gradle plugin `com.nebs.socket`

Tasks that do what `nebs-cli` does, plus the full [Java/Kotlin API](kotlin.md) on the build script
classpath. Adding the plugin to a build: [setup.md](setup.md).

```kotlin
// build.gradle.kts
plugins {
    id("com.nebs.socket") version "<version>"
}

nebs {                                                  // everything optional
    home = layout.projectDirectory.dir("tools/.nebs")    // default: $NEBS_HOME, or <root project>/.nebs
    templateDir = file("/shared/nebs-template")           // default: <home>/template
    instancesDir = layout.buildDirectory.dir("clients")   // default: <home>/instances
    socketPath = "/custom/path.sock"                      // always talk to this socket
    modJars.from("path/to/extra-mod.jar")                 // installed instead of the bundled nebs mod
}
```

## Tasks

Every task takes `--home=<dir>`. Tasks that talk to clients choose them like the CLI:
`--client=<name>` (repeatable), `--all`, `--socket=<path>`, or the only running client.

| Task | Options | Does |
|------|---------|------|
| `nebs` | `--command="<command line>"` + client choice | Any command from [commands.md](commands.md), except `subscribe`. |
| `nebsConnect` | `--host=<host>` `--port=<port>` + client choice | `connect`. |
| `nebsListClients` | — | `client list`. |
| `nebsInstallClient` | `--template=<dir>` `--java=<path>` | `client install`. |
| `nebsLaunchClient` | `--name` `--uuid` `--instance` `--template` `--wait` | `client launch` (installs the template first if needed). |
| `nebsStopClient` | client choice | `client stop`. |
| `nebsInstallClaudeSkill` | `--dir=<dir>` or `--user` | Installs this skill into `<root project>/.claude/skills` (or `~/.claude/skills`). |

```bash
./gradlew nebsLaunchClient --name=Alice --wait
./gradlew nebsConnect --host=localhost --port=25566 --client=Alice
./gradlew nebs --command="status" --client=Alice
./gradlew nebs --command="screenshot after-deploy" --all
./gradlew nebsStopClient --all
```

A task fails the build if no client matches, or if any selected client is unreachable or rejects
the message; on success it prints each reply. Replies are logged at lifecycle level, so `-q` hides
them; leave it off when you need the reply.

## Preconfigured tasks

```kotlin
import com.nebs.gradle.NebsCommandTask
import com.nebs.gradle.NebsConnectTask
import com.nebs.gradle.NebsLaunchClientTask

tasks.register<NebsLaunchClientTask>("launchBot") {
    playerName = "Bot1"
    waitUntilReady = true
}
tasks.register<NebsConnectTask>("joinLocal") {
    host = "localhost"
    port = "25566"
    all = true
    mustRunAfter("launchBot")
}
tasks.register<NebsCommandTask>("botScreenshot") {
    command = "screenshot deployed"
    clients.add("Bot1")
}
```

Other task types: `NebsListClientsTask`, `NebsInstallClientTask`, `NebsStopClientTask`.

## Using the API in build logic

The plugin puts `nebs-api` on the build script classpath:

```kotlin
import com.nebs.api.NebsClient

tasks.register("smokeTest") {
    dependsOn("deployToTestServer")          // your own task
    doLast {
        NebsClient { name = "Tester" }.use { client ->
            client.connect("localhost", 25566)
            check(client.status().health > 0) { "player is dead on join" }
            client.screenshot("smoke-test")
        }
    }
}
```

Mark such tasks as not cacheable (`outputs.upToDateWhen { false }`) if they declare outputs. They
talk to a live game.
