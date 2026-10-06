# nebs-client

A Gradle (Kotlin DSL) multi-module project. External programs can control Minecraft clients through local sockets: one client, a chosen few, or all of them at once.

| Module | What it is |
|--------|------------|
| [`core`](core) | Shared message model (`Message` interface + implementations), wire codec, socket client, command parsing, the registry of running clients, and the client launcher (template install + offline clients). Used by every other module. |
| [`mod`](mod) | Fabric client mod (Minecraft 26.3). When started with `--socket-comm` it listens on its own local socket, registers itself in the nebs home, and runs the messages it receives. |
| [`cli`](cli) | Command-line tool for sending messages to the mod and for installing and launching test clients. |
| [`gradle-plugin`](gradle-plugin) | Gradle plugin (`com.nebs.socket`) whose tasks run the same commands as the CLI. |

The CLI and the Gradle plugin both embed the mod jar, so they can install it into a client template without being pointed at it.

Requirements: JDK 25 or newer to build. The mod targets Java 25 (Minecraft's requirement); `core`, `cli` and `gradle-plugin` target Java 17 so the plugin works in any Gradle 9 build. Gradle is provided through the wrapper (`./gradlew`).

## Building

```bash
./gradlew build
```

- Mod jar: `mod/build/libs/nebs-client-mod-1.0.0.jar` (bundles `core`; needs Fabric API and Fabric Language Kotlin installed)
- CLI: `./gradlew :cli:installDist`, then run `cli/build/install/nebs-cli/bin/nebs-cli`
- Real-client integration test (needs network and a display; it uses `cli/build/nebs-it` as its home and downloads about 700 MB there the first time): `./gradlew :cli:test -Pnebs.integration=true`

## The nebs home folder

Everything nebs keeps on disk lives in one **home** folder, `./.nebs` in the current directory by default:

```
.nebs/
  template/                  installed client (game, libraries, assets, Java runtime, mods)
  instances/<name>/          game folder of each launched client
  clients/<name>-<pid>.json  one entry per running client: name, uuid, pid, socket, game folder
```

The home is chosen in this order:

1. an explicit option: `--home DIR` (CLI or Gradle task) or `nebs { home = … }` (Gradle)
2. the `NEBS_HOME` environment variable
3. `.nebs` in the current directory (Gradle: in the root project directory)

`.nebs/` is in this repo's `.gitignore`; add it to other projects' ignore files too. Each home has its own template (about 700 MB), so point several projects at one shared home with `NEBS_HOME` if you don't want a copy in each.

## Enabling socket communication in the mod

Socket communication is **off by default**. It only turns on when the client is launched with `--socket-comm`:

| Launch argument | Effect |
|-----------------|--------|
| `--socket-comm` | Opens the client's socket and registers the client in the nebs home. Without this argument the mod does nothing. |
| `--nebs-home <dir>` | Optional. Which home to register in. Defaults to `NEBS_HOME`, or `.nebs` in the game directory. |
| `--socket-path <path>` | Optional. Where to create the socket. Defaults to `<tmpdir>/nebs-<pid>.sock`. |

Each client listens on its **own** socket, because two processes can't listen on one socket path. To find the clients, the CLI and Gradle read the registry entries in `<home>/clients/`. The sockets live in the temp dir because Unix socket paths are limited to about 104 characters on macOS. Each client deletes its own registry entry when it exits, and entries left behind by crashed clients are cleaned up automatically.

Clients started by nebs (`client launch`, `spawn`) register in the right home automatically. For a client you start yourself, add `--socket-comm --nebs-home /path/to/project/.nebs` to its game arguments (not the JVM arguments). In development, `./gradlew :mod:runClient` passes `--socket-comm`, so that client registers in `mod/run/.nebs` unless you set `NEBS_HOME`.

The socket is a Unix domain socket. It works on macOS, Linux and Windows 10+.

## CLI usage

```
nebs-cli [--home DIR] [--client NAME]... [--all] [--socket PATH] [command [args...]]
```

Run it with a command to send one message and exit. The exit code is 0 on success and 1 if any client failed. Run it with no command to open an interactive shell (`> ` prompt).

**Choosing clients.** Client commands go to:

| Option | Clients |
|--------|---------|
| *(none)* | the only running client. If several are running, the command is refused and the running names are listed. |
| `--client NAME` | the named client (case-insensitive). Repeat it to pick several. |
| `--all` | every running client, in parallel. Each reply line is prefixed with the client's name. |
| `--socket PATH` | whatever is listening on that socket, registered or not. |

| Command | Message sent | Description |
|---------|--------------|-------------|
| `connect <host> [port]` | `ConnectToServer` | Tells the client to join the server at `host:port`. The game window is brought to the front and focused, and if the client is already in a world it leaves that world first. The port defaults to `25565`. `connect <host>:<port>` also works. |
| `ping` | `Ping` | Prints the client's `name`, `uuid`, `state` (`loading`, `menu`, `connecting` or `in-world`) and `server`. |
| `quit` | `Quit` | Shuts the client down. |
| `spawn [--name N] [--uuid U] [--instance DIR] [--template DIR]` | `SpawnClient` | The client launches another offline client in the same home (see [Test clients](#test-clients)). All options are optional. Prints the new client's `name`, `uuid`, `socket`, `pid` and `instance`. |
| `client list` | — | Running clients in the home: name, state, server, pid and game folder. |
| `client install` / `launch` / `stop` | — | Install and manage test clients. See [Test clients](#test-clients). |
| `help` | — | Shows usage. |
| `exit` | — | Leaves the interactive shell. |

Examples:

```bash
nebs-cli client list
nebs-cli connect localhost 25566                       # the only running client
nebs-cli --client Alice connect localhost 25566
nebs-cli --client Alice --client Bob ping
nebs-cli --all connect 192.168.1.20:25565
nebs-cli --home ~/shared-nebs --all ping

# interactive; options given at startup apply to every command
nebs-cli --all
> connect localhost 25566
Alice: Connecting to localhost:25566
Bob: Connecting to localhost:25566
> exit

# via Gradle without installing
./gradlew -q :cli:run --args="client list"
```

## Test clients

`core` can install a complete Fabric client and run as many offline copies of it as you like. This is for testing the CLI and Gradle plugin, and for letting an automated agent start its own clients.

- The **template** (`<home>/template`) is a shared, read-only install: Mojang's Java runtime, the game, its libraries and assets, Fabric Loader, Fabric API, Fabric Language Kotlin and the nebs mod.
- An **instance** (`<home>/instances/<player name>`) is one client's own game folder: its options, saves, logs and config. Instances read the game and mods from the template.

Both folders are **optional everywhere**: they default to the locations above, and any command or API call can take a different folder.

Launched clients:

- **never log in**. They start with `--offlineDeveloperMode` and a dummy token, so they make no calls to Microsoft/Mojang auth. The name defaults to a random `NebsNNNN`. The UUID defaults to the one an offline-mode server would give that name. You can set either.
- can only join **offline-mode servers** (`online-mode=false` in `server.properties`).
- start with socket communication on, registered in the home they were launched from.
- keep running after the CLI or Gradle exits. Stop them with `client stop` / `quit`.
- get an `options.txt` with first-launch prompts off, pause-on-focus-loss off and `maxFps:30`, so several can run side by side.
- always run with **vsync off** (re-applied on every launch). With vsync on, macOS blocks a window that isn't on screen during its first frame, and a client started in the background never finishes loading.

**Launching installs the template automatically** if it isn't installed yet, so `client launch` is all you need, even in an empty folder. The first install downloads about 700 MB. Clients launched in parallel share one install: the first one installs, and the others wait for it and then reuse the template. Run `client install` yourself to install ahead of time, or to repair or update a template (it only fetches what's missing or changed). Clients need a display; on a headless Linux machine, run them under `xvfb-run`.

### From the CLI

```bash
nebs-cli client launch --name Alice --wait                # installs ./.nebs/template first if needed
nebs-cli client install                                   # install/repair ahead of time
nebs-cli client install --template /tmp/tpl               # or anywhere else
nebs-cli client launch --instance /tmp/bob --uuid 0d0c4d2c-…   # any folder; random name
nebs-cli client list
nebs-cli --client Alice spawn --name Carol                # Alice launches Carol
nebs-cli --all connect localhost 25565
nebs-cli client stop Alice                                # or: client stop --all
```

| Command | Options (all optional) |
|---------|------------------------|
| `client install` | `--template DIR`, `--mod JAR` (repeatable; replaces the bundled nebs mod), `--java PATH` (skip downloading Mojang's runtime) |
| `client launch` | `--name N`, `--uuid U`, `--instance DIR`, `--template DIR`, `--width W --height H`, `--wait` (block until loaded) |
| `client list` | — |
| `client stop` | client names, or `--all`; with neither, stops the only running client |

A client started by `spawn` registers in its parent's home and uses the template its parent was launched from. If the parent wasn't started by nebs, it uses `<home>/template`, which the parent installs with its own mod jar if needed. The `spawn` reply arrives once the child has started, so it can take a while when an install happens first.

### From Kotlin

```kotlin
val home = NebsHome.resolve()                                       // or any Path
val alice = ClientLauncher.launch(LaunchSpec(home = home, name = "Alice"))  // installs the template if missing; every field is optional
alice.awaitReady()

val clients = ClientRegistry.active(home)                           // every running client in the home
ClientRegistry.broadcast(clients, ConnectToServer("localhost"))     // in parallel, one result per client
ClientRegistry.select(home, names = listOf("Alice")).single().stop()
```

## Gradle plugin

The `gradle-plugin` module provides the plugin `com.nebs.socket`. Its tasks do everything `nebs-cli` does, so a build can control clients. For example, a dev build can join a test server after deploying to it.

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
    // All optional.
    home = layout.projectDirectory.dir("tools/.nebs")    // default: $NEBS_HOME, or <root project>/.nebs
    templateDir = file("/shared/nebs-template")           // default: <home>/template
    instancesDir = layout.buildDirectory.dir("clients")   // default: <home>/instances
    socketPath = "/custom/path.sock"                      // always talk to this socket
    modJars.from("path/to/extra-mod.jar")                 // replaces the bundled nebs mod
}
```

### Tasks

Every task accepts `--home=<dir>`. Tasks that talk to clients pick them like the CLI does: `--client=<name>` (repeatable), `--all` or `--socket=<path>`, or the only running client when none of these is given.

| Task | Options | Description |
|------|---------|-------------|
| `nebs` | `--command="<client command>"` + client choice | Runs any client command line (`connect`, `ping`, `quit`, `spawn`). |
| `nebsConnect` | `--host=<host>`, `--port=<port>` (optional) + client choice | Sends `connect`. |
| `nebsListClients` | — | Same as `client list`. |
| `nebsInstallClient` | `--template=<dir>`, `--java=<path>` | Same as `client install`. |
| `nebsLaunchClient` | `--name`, `--uuid`, `--instance`, `--template`, `--wait` | Same as `client launch`, including the automatic install. |
| `nebsStopClient` | client choice | Same as `client stop`. |

```bash
./gradlew nebsLaunchClient --name=Alice --wait     # installs the template first if needed
./gradlew nebsLaunchClient --name=Bob --wait
./gradlew nebsListClients
./gradlew nebsConnect --host=localhost --port=25566 --all
./gradlew nebs --command=ping --client=Alice
./gradlew nebsStopClient --all
```

A task fails the build if no client matches, or if any selected client can't be reached or rejects the message. On success it prints each client's reply.

You can also register preconfigured tasks:

```kotlin
import com.nebs.gradle.NebsCommandTask
import com.nebs.gradle.NebsConnectTask
import com.nebs.gradle.NebsLaunchClientTask

tasks.register<NebsConnectTask>("joinLocal") {
    host = "localhost"
    port = "25566"
    all = true
}
tasks.register<NebsCommandTask>("aliceJoinsTestServer") {
    command = "connect test.example.com"
    clients.add("Alice")
}
tasks.register<NebsLaunchClientTask>("launchBot") {
    playerName = "Bot1"
    waitUntilReady = true
}
```

## Wire protocol

Messages are newline-delimited JSON in UTF-8, one message per line. The `type` field identifies the message. The client replies to every message with a `response` on the same connection. A connection can send any number of messages.

| `type` | Direction | Fields |
|--------|-----------|--------|
| `connect` | app → client | `host` (string), `port` (int, optional, default `25565`) |
| `ping` | app → client | — |
| `quit` | app → client | — |
| `spawn` | app → client | `name`, `uuid`, `instance`, `template` (all optional strings) |
| `response` | client → app | `success` (bool), `detail` (string), `data` (string map, optional) |

```
→ {"type":"connect","host":"localhost","port":25566}
← {"type":"response","success":true,"detail":"Connecting to localhost:25566","data":{}}
→ {"type":"ping"}
← {"type":"response","success":true,"detail":"pong","data":{"name":"Alice","uuid":"…","state":"menu"}}
```

Any language can talk to a client: read its `socket` from its file in `<home>/clients/`, then write JSON lines to it. For example, from a shell:

```bash
socket=$(grep -o '"socket": *"[^"]*"' .nebs/clients/Alice-*.json | cut -d'"' -f4)
echo '{"type":"connect","host":"localhost"}' | nc -U "$socket"
```

## Adding a new message

1. Add a `@Serializable @SerialName("your-type") data class ... : Message` in `core/src/main/kotlin/com/nebs/core/message/`.
   `Message` is a sealed interface, so the codec picks up the new class automatically.
2. The mod's `MessageDispatcher` uses an exhaustive `when`, so the build fails until you handle the new message there.
   Add a handler under `mod/.../handler/`. Run any game-state changes on the client thread with `Minecraft.getInstance().execute { ... }`.
3. Add its command syntax to `Commands` in `core/.../command/Commands.kt` (`specs` and `parse`).
   The CLI and the Gradle plugin's `nebs` task get the command automatically.
   Client selection (`--client`, `--all`, …) works for it automatically.
   Optionally add a dedicated typed task to `gradle-plugin/.../NebsTasks.kt` (extend `NebsSocketTask`). Document the command in the tables above.

For example, a `screenshot` message would follow the same steps. Its handler saves the image in the client's game folder and returns the file's path in the reply's `data`, so no binary data goes over the socket. Then `nebs-cli --all screenshot` collects one file per client.
