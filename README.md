# nebs-client

A Gradle (Kotlin DSL) multi-module project. External programs can control a Minecraft client through a local socket.

| Module | What it is |
|--------|------------|
| [`core`](core) | Shared message model (`Message` interface + implementations), wire codec, socket client, command parsing, and socket defaults. Used by every other module. |
| [`mod`](mod) | Fabric client mod (Minecraft 26.3). When started with `--socket-comm` it listens on a local socket and runs the messages it receives. |
| [`cli`](cli) | Command-line tool for sending messages to the mod. Use it to test the socket. |
| [`gradle-plugin`](gradle-plugin) | Gradle plugin (`com.nebs.socket`) whose tasks run the same commands as the CLI. |

Requirements: JDK 25 or newer to build. The mod targets Java 25 (Minecraft's requirement); `core`, `cli` and `gradle-plugin` target Java 17 so the plugin works in any Gradle 9 build. Gradle is provided through the wrapper (`./gradlew`).

## Building

```bash
./gradlew build
```

- Mod jar: `mod/build/libs/nebs-client-mod-1.0.0.jar` (bundles `core`; needs Fabric API and Fabric Language Kotlin installed)
- CLI: `./gradlew :cli:installDist`, then run `cli/build/install/nebs-cli/bin/nebs-cli`

## Enabling socket communication in the mod

Socket communication is **off by default**. It only turns on when the client is launched with the `--socket-comm` argument:

| Launch argument | Effect |
|-----------------|--------|
| `--socket-comm` | Opens the socket. Without this argument the mod does nothing. |
| `--socket-path <path>` | Optional. Sets where the socket file is created. |

The socket path is chosen in this order:

1. `--socket-path <path>` (mod) or `--socket <path>` (CLI)
2. The `NEBS_SOCKET` environment variable
3. `<java.io.tmpdir>/nebs-client.sock`

The default path is the same for the mod and the CLI when both run as the same user, so in most cases you don't need to configure anything.

In a launcher, add `--socket-comm` to the game arguments (not the JVM arguments). In development, `./gradlew :mod:runClient` passes `--socket-comm` for you.

The socket is a Unix domain socket. It works on macOS, Linux and Windows 10+. On macOS the socket path must be shorter than about 104 characters.

## CLI usage

```
nebs-cli [--socket <path>] [command [args...]]
```

Run it with a command to send one message and exit. The exit code is 0 on success and 1 on failure. Run it with no command to open an interactive shell (`> ` prompt).

| Command | Message sent | Description |
|---------|--------------|-------------|
| `connect <host> [port]` | `ConnectToServer` | Tells the client to join the server at `host:port`. The game window is brought to the front and focused, and if the client is already in a world it leaves that world first. The port defaults to `25565`. `connect <host>:<port>` also works. |
| `help` | — | Shows usage. |
| `exit` / `quit` | — | Leaves the interactive shell. |

Examples:

```bash
# one-shot
nebs-cli connect mc.hypixel.net
nebs-cli connect localhost 25566
nebs-cli --socket /tmp/other.sock connect 192.168.1.20:25565

# interactive
nebs-cli
> connect localhost 25566
Connecting to localhost:25566
> exit

# via Gradle without installing
./gradlew -q :cli:run --args="connect localhost 25566"
```

## Gradle plugin

The `gradle-plugin` module provides the plugin `com.nebs.socket`. Its tasks accept the same commands as `nebs-cli`, so a build can control the client. For example, a dev build can join a test server after deploying to it.

### Using it in another build

Publish the plugin and `core` to your local Maven repository:

```bash
./gradlew publishToMavenLocal
```

Then in the consuming build:

```kotlin
// settings.gradle.kts
pluginManagement {
    repositories {
        mavenLocal()
        gradlePluginPortal()
    }
}
dependencyResolutionManagement { repositories { mavenLocal(); mavenCentral() } }

// build.gradle.kts
plugins {
    id("com.nebs.socket") version "1.0.0"
}

nebs {
    socketPath = "/custom/path.sock" // optional; same default as the CLI
}
```

### Tasks

| Task | Options | Description |
|------|---------|-------------|
| `nebs` | `--command="<cli command>"`, `--socket=<path>` | Runs any CLI command line. |
| `nebsConnect` | `--host=<host>`, `--port=<port>` (optional), `--socket=<path>` | Sends `connect`. |

```bash
./gradlew nebs --command="connect localhost 25566"
./gradlew nebsConnect --host=localhost --port=25566
```

The task fails the build if the client can't be reached or rejects the message. On success it prints the client's reply.

You can also register preconfigured tasks:

```kotlin
import com.nebs.gradle.NebsCommandTask
import com.nebs.gradle.NebsConnectTask

tasks.register<NebsConnectTask>("joinLocal") {
    host = "localhost"
    port = "25566"
}
tasks.register<NebsCommandTask>("joinTestServer") {
    command = "connect test.example.com"
}
```

## Wire protocol

Messages are newline-delimited JSON in UTF-8, one message per line. The `type` field identifies the message. The client replies to every message with a `response` on the same connection. A connection can send any number of messages.

| `type` | Direction | Fields |
|--------|-----------|--------|
| `connect` | app → client | `host` (string), `port` (int, optional, default `25565`) |
| `response` | client → app | `success` (bool), `detail` (string) |

```
→ {"type":"connect","host":"localhost","port":25566}
← {"type":"response","success":true,"detail":"Connecting to localhost:25566"}
```

Any language can talk to the socket. For example, from a shell:

```bash
# macOS/Linux default location ($TMPDIR is what java.io.tmpdir resolves to on macOS)
echo '{"type":"connect","host":"localhost"}' | nc -U "${TMPDIR:-/tmp}/nebs-client.sock"
```

## Adding a new message

1. Add a `@Serializable @SerialName("your-type") data class ... : Message` in `core/src/main/kotlin/com/nebs/core/message/`.
   `Message` is a sealed interface, so the codec picks up the new class automatically.
2. The mod's `MessageDispatcher` uses an exhaustive `when`, so the build fails until you handle the new message there.
   Add a handler under `mod/.../handler/`. Run any game-state changes on the client thread with `Minecraft.getInstance().execute { ... }`.
3. Add its command syntax to `Commands` in `core/.../command/Commands.kt` (`specs` and `parse`).
   The CLI and the Gradle plugin's `nebs` task get the command automatically.
   Optionally add a dedicated typed task to `gradle-plugin/.../NebsTasks.kt`. Document the command in the tables above.
