# nebs-client

A Gradle (Kotlin DSL) multi-module project. External programs can control Minecraft clients through local sockets: one client, a chosen few, or all of them at once.

| Module | What it is |
|--------|------------|
| [`core`](core) | Shared message model (`Message` interface + implementations), wire codec, socket client, command parsing, the registry of running clients, and the client launcher (template install + offline clients). Used by every other module. |
| [`mod`](mod) | Fabric client mod (Minecraft 26.3). When started with `--socket-comm` it listens on its own local socket, registers itself in the nebs home, and runs the messages it receives. |
| [`cli`](cli) | Command-line tool for sending messages to the mod and for installing and launching test clients. |
| [`gradle-plugin`](gradle-plugin) | Gradle plugin (`com.nebs.socket`) whose tasks run the same commands as the CLI. |
| [`api`](api) | Java/Kotlin library (`NebsClient`) for controlling clients from code: programs, tests and `.kts` scripts. |

The CLI and the Gradle plugin both embed the mod jar, so they can install it into a client template without being pointed at it.

Requirements: JDK 25 or newer to build. The mod targets Java 25 (Minecraft's requirement); `core`, `cli` and `gradle-plugin` target Java 17 so the plugin works in any Gradle 9 build. Gradle is provided through the wrapper (`./gradlew`).

## Building

```bash
./gradlew build
```

- Mod jar: `mod/build/libs/nebs-client-mod-0.1.0.jar` (bundles `core`; needs Fabric API and Fabric Language Kotlin installed)
- CLI: `./gradlew :cli:installDist`, then run `cli/build/install/nebs-cli/bin/nebs-cli`
- Real-client integration test (needs network and a display; it uses `cli/build/nebs-it` as its home and downloads about 700 MB there the first time): `./gradlew :cli:test -Pnebs.integration=true`

## Releases

The version is set in `gradle.properties` (currently `0.1.0`).

- **CI** (`.github/workflows/ci.yml`) runs `./gradlew build`, including the tests, on every push to `master` and on every pull request. The real-client integration test skips itself there. Test reports are uploaded when the build fails.
- **Releases** (`.github/workflows/release.yml`) run when you push a version tag. The workflow builds and tests with the version taken from the tag, then creates a GitHub release with the mod jar, the CLI distributions (`.zip` and `.tar`) and the self-contained API jar (`nebs-api-<version>-all.jar`) attached. The release notes are generated from merged pull requests.

```bash
git tag v0.1.0
git push origin v0.1.0
```

Tags must look like `v1.2.3`. A tag with a suffix, like `v1.2.3-beta.1`, makes a pre-release. Remember to bump `version` in `gradle.properties` afterwards, so local builds don't keep the released version number.

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

Replies are printed as a summary line, followed by any key/value `data` and, for queries, the structured `result` as JSON. Run `nebs-cli help` for the same list as below.

**Commands that need the client to be in a world** (everything except `connect`, `ping`, `wait-for`, `quit`, `spawn`, the screen commands, and the capture commands) fail with "Not in a world" otherwise. Coordinates are block coordinates; `yaw` is 0 = south, 90 = west, and `pitch` goes from -90 (up) to 90 (down). Durations are in ticks (20 per second) or in seconds where the option is called `--timeout`.

#### Session
| Command | What it does |
|---------|--------------|
| `connect <host> [port]` | Join a server (port defaults to `25565`; `host:port` also works). Leaves any current world first and brings the window to the front. |
| `disconnect` | Leave the server and return to the title screen. |
| `ping` | `name`, `uuid`, `state` (`loading`, `menu`, `connecting`, `in-world`) and `server`. |
| `wait-for <state> [--timeout S]` | Wait for `loading`, `menu`, `connecting`, `in-world`, `alive` or `dead` (default 30 s). |
| `respawn` | Press "Respawn" on the death screen. |
| `quit` | Shut the client down. |
| `spawn [--name N] [--uuid U] [--instance DIR] [--template DIR]` | The client launches another offline client in the same home (see [Test clients](#test-clients)). |

#### Observation
| Command | Result |
|---------|--------|
| `status` | Position, block position, yaw/pitch, health, absorption, food, saturation, XP, game mode, dimension, on-ground/in-water/sprinting/sneaking, dead, selected slot, held item. |
| `inventory` | Every non-empty slot (hotbar 0-8, main 9-35, armor 36-39, offhand 40) with item id, count, name, damage and enchantments. |
| `block <x> <y> <z>` | Block id, state properties, and whether the chunk is loaded. |
| `blocks <x1> <y1> <z1> <x2> <y2> <z2>` | Non-air blocks in a box (up to 32768 blocks): per-block counts plus a list (first 4096). |
| `look-target` | The block (with face and distance) or entity under the crosshair. |
| `entities [--radius R] [--type T]` | Nearby entities, nearest first: id, type, name, uuid, position, distance, health. `--type` takes `pig` or `minecraft:pig`. |
| `players` | Tab list: name, uuid, latency, game mode. |
| `world` | Dimension, time of day, day, game time, rain/thunder, difficulty, server. |
| `screen` | The open screen: type, title, buttons (with `#index`), text fields, and for containers every slot plus the carried item. |
| `chat-history [count]` | The last messages (default 20), each with a `seq` number, `type` (`chat`, `system`, `action-bar`), sender and text. |
| `scoreboard` | The sidebar: title and lines. |
| `effects` | Active potion effects with level and remaining ticks. |

#### Movement
A new `move`, `goto` or `follow` replaces the one in progress; `stop` ends it and releases every key nebs is holding.

| Command | What it does |
|---------|--------------|
| `look <yaw> <pitch>` / `look-at <x> <y> <z>` | Turn the camera. |
| `move <forward\|back\|left\|right> [ticks]` | Hold a movement key for some ticks, or until `stop`. |
| `jump` | Jump once. |
| `sneak <on\|off>` / `sprint <on\|off>` | Hold or release sneak or sprint. |
| `stop` | Stop moving and release all keys. |
| `goto <x> <z> [--timeout S]` | Walk in a straight line to a position, jumping up single-block steps. It is not a pathfinder: it fails if it gets stuck. |
| `follow <player\|entity-id> [--distance D]` | Keep walking toward a player or entity (default within 3 blocks) until `stop`. |

#### Interaction
| Command | What it does |
|---------|--------------|
| `attack [entity-id]` | Attack an entity, or whatever is under the crosshair. Fails if the entity is out of reach. |
| `use [--ticks N]` | Right-click with the held item (on the crosshair target, or in the air). `--ticks` keeps holding, e.g. 40 to eat. |
| `use-on <x> <y> <z> [face]` | Right-click a block face (default `up`): doors, buttons, levers, beds, chests. |
| `mine <x> <y> <z> [--timeout S]` | Break a block with the held item, at normal speed. Fails if something is in the way or it's out of reach. |
| `place <x> <y> <z>` | Place the held block at a position, against any solid neighbour within reach. |
| `select-slot <0-8>` | Select a hotbar slot. |
| `hold <item>` | Put an item (`diamond_sword` or `minecraft:diamond_sword`) in the main hand, moving it from the inventory if needed. |
| `drop [--all]` | Drop one of the held item, or the whole stack. |
| `swap-hands` | Swap main-hand and off-hand items. |
| `interact <entity-id>` | Right-click an entity (trade with villagers, mount, …). |

#### Inventory and screens
| Command | What it does |
|---------|--------------|
| `open-inventory` / `close-screen` | Open the inventory; close whatever screen or container is open. |
| `click-slot <slot> [--button N] [--mode M]` | Click a slot of the open container (numbers from `screen`). Modes: `pickup` (default), `quick_move` (shift-click), `swap` (with `--button` = hotbar slot), `clone`, `throw`, `quick_craft`, `pickup_all`. |
| `click-button <label\|#index>` | Click a button by its label (exact, then partial match) or its index from `screen`. Works on any screen, including the title and multiplayer menus. |
| `type-text <text…>` | Type into the focused text field (or the only one on screen). |

#### Chat
| Command | What it does |
|---------|--------------|
| `chat <message…>` | Send a chat message. A leading `/` runs it as a command. |
| `command <command…>` | Run a command, with or without `/`, e.g. `command give @s diamond 3`. |
| `wait-for-chat <regex> [--timeout S] [--since SEQ]` | Wait for a chat or system message matching the regex, arriving after this request or after message `SEQ` (from `chat-history`). |

#### Capture and debugging
| Command | What it does |
|---------|--------------|
| `screenshot [name]` | Save `<game dir>/screenshots/<name>.png` (default `nebs-<timestamp>`). Replies with `path`, `width` and `height`. |
| `set-window <width> <height>` | Resize the window, in screen points. On high-DPI displays, screenshots are larger. |
| `options` / `set-option <name> <value>` | List or change any game option by name, e.g. `fov 90`, `renderDistance 8`, `guiScale 2`, `graphicsPreset fast`. |
| `hud <on\|off>` / `f3 <on\|off>` | Show or hide the HUD (F1) or the debug overlay (F3). Hiding the HUD also hides F3. |
| `perf` | FPS, memory, loaded chunks, entity count, window size in pixels. |
| `logs [lines] [--errors]` | The end of the client log; `--errors` keeps only warnings, errors and stack traces. |
| `reload-resources` | Reload resource packs (F3+T) and reply when finished. |

#### Events
| Command | What it does |
|---------|--------------|
| `subscribe [event…]` | Stream events as they happen until Ctrl-C (CLI only). Events: `chat`, `system`, `action-bar`, `join`, `disconnect`, `death`, `respawn`, `health`, `screen`, `inventory`. With `--all`, each line is prefixed with the client's name. |

#### Local commands
| Command | What it does |
|---------|--------------|
| `client list` | Running clients in the home: name, state, server, pid and game folder. |
| `client install` / `launch` / `stop` | Install and manage test clients. See [Test clients](#test-clients). |
| `help` | Show usage. |
| `exit` | Leave the interactive shell. |

Examples:

```bash
nebs-cli client list
nebs-cli --all status
nebs-cli --client Alice command give @s diamond_pickaxe
nebs-cli --client Alice hold diamond_pickaxe
nebs-cli --client Alice mine 10 64 -3
nebs-cli --all screenshot before-join
nebs-cli --client Alice subscribe chat death
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

## Java / Kotlin API

The `api` module wraps everything above in one class, `com.nebs.api.NebsClient`. It works the same from Java and Kotlin, including in `.kts` scripts.

```kotlin
NebsClient().use { client ->          // launches a client and waits until it has loaded
    client.connect("localhost")         // returns once the client is in the world
    client.lookAt(0, 64, 0)
    val status = client.status()        // typed results: status.health, status.heldItem?.id, ...
    client.screenshot("spawn")
}                                       // close() quits the game
```

```java
try (NebsClient client = new NebsClient(new ClientOptions().name("Bob"))) {
    client.connect("localhost", 25565);
    client.mine(10, 64, -3);
    System.out.println(client.inventory().count("cobblestone"));
}
```

**Creating clients**

| Code | What you get |
|------|--------------|
| `NebsClient()` / `new NebsClient()` | A newly launched offline client. It installs the template first if needed and waits until the client has loaded. `close()` quits it. |
| `NebsClient { name = "Bob"; waitForReady = false }` (Kotlin), `new NebsClient(new ClientOptions().name("Bob").waitForReady(false))` (Java) | The same, configured. Options: `name`, `uuid`, `home`, `template`, `instance`, `waitForReady`, `readyTimeout`, `installIfMissing`, `quitOnClose`, `memory`, `windowSize`, `installProgress`. With `waitForReady = false` creation returns at once; call `waitUntilReady()` later. |
| `NebsClient.attach("Alice")`, `NebsClient.attach()` | A client that's already running, by name, or the only one running. `close()` leaves it running; call `exit()` to quit it. |
| `NebsClient.atSocket(path)` | Whatever is listening on a socket. |
| `NebsClient.running()` | Every running client in the home. |
| `client.spawn(options)` | A new client launched by this one. It quits when closed. |

**Methods** mirror the [commands](#cli-usage), with Java-friendly names and overloads:
- `connect`, `disconnect`, `ping`, `waitFor(ClientState.IN_WORLD)`, `waitUntilReady`, `respawn`, `exit`;
- `status`, `inventory`, `block`, `blocks`, `lookTarget`, `entities`, `nearestEntity`, `players`, `world`, `screen`, `chatHistory`, `scoreboard`, `effects`;
- `look`, `lookAt`, `move(MoveDirection.FORWARD, ticks)`, `startMoving`, `jump`, `sneak`, `sprint`, `stop`, `walkTo` (`goto` is a Java keyword), `follow`;
- `attack`, `use`, `useOn`, `mine`, `place`, `selectSlot`, `hold`, `drop`, `swapHands`, `interact`;
- `openInventory`, `closeScreen`, `clickSlot`, `clickButton`, `typeText`;
- `chat`, `command`, `waitForChat`;
- `screenshot`, `setWindowSize`, `options`, `setOption`, `setHudVisible`, `setDebugOverlay`, `perf`, `logs`, `reloadResources`;
- `subscribe(listOf(Events.CHAT)) { event -> … }`, which returns an `AutoCloseable` subscription;
- `run("mine 1 2 3")` for any CLI command line, and `send(message)` for raw protocol messages.

Every method blocks until the client has finished, and throws `NebsException` (unchecked) with the client's explanation if it fails. Queries return typed objects (`PlayerStatus`, `Inventory`, `EntityInfo`, `ScreenInfo`, …). Calls on one instance are thread-safe.

**Getting the library**

- In this build or another Gradle build: `implementation(project(":api"))`, or `implementation("com.nebs:nebs-api:<version>")` after `./gradlew publishToMavenLocal`.
- As one self-contained jar: `./gradlew :api:allJar` → `api/build/libs/nebs-api-<version>-all.jar`. Each GitHub release also attaches it.

**Scripts.** Run a plain `.kts` script with the jar on the classpath; [examples/hello.kts](examples/hello.kts) launches a client, joins a server, walks and takes a screenshot:

```bash
kotlinc -cp api/build/libs/nebs-api-0.1.0-all.jar -script examples/hello.kts localhost 25565
```

`.main.kts` scripts work too, but in Kotlin 2.4.20 `@file:DependsOn` must be the **first line** of the file (even a comment or `#!` before it makes Kotlin ignore it), and the jar path must be **absolute**:

```kotlin
@file:DependsOn("/path/to/nebs-api-0.1.0-all.jar")
import com.nebs.api.NebsClient
NebsClient().use { it.connect("localhost") }
```

A Java version is in [examples/HelloNebs.java](examples/HelloNebs.java): `java -cp api/build/libs/nebs-api-0.1.0-all.jar examples/HelloNebs.java`.

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
    id("com.nebs.socket") version "0.1.0"
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

Build scripts that apply the plugin can also use the [Java / Kotlin API](#java--kotlin-api) directly in their own tasks:

```kotlin
import com.nebs.api.NebsClient

tasks.register("smokeTest") {
    doLast {
        NebsClient { name = "Tester" }.use { client ->
            client.connect("localhost", 25566)
            check(client.status().health > 0)
            client.screenshot("smoke-test")
        }
    }
}
```

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

Every command above is one message type, named as the command (e.g. `{"type":"mine","x":1,"y":64,"z":-3,"timeout":30.0}`). Its fields are the command's arguments. The exact shapes are the classes in `core/src/main/kotlin/com/nebs/core/message/`. Two message types travel the other way:

| `type` | Direction | Fields |
|--------|-----------|--------|
| `response` | client → app | `success` (bool), `detail` (string), `data` (string map), `result` (any JSON, for queries; optional) |
| `event` | client → app, after `subscribe` | `event` (string), `data` (object), `time` (epoch ms) |

```
→ {"type":"connect","host":"localhost","port":25566}
← {"type":"response","success":true,"detail":"Connecting to localhost:25566","data":{},"result":null}
→ {"type":"block","x":4,"y":-61,"z":-2}
← {"type":"response","success":true,"detail":"minecraft:grass_block","data":{},"result":{"x":4,"y":-61,"z":-2,"block":"minecraft:grass_block","properties":{"snowy":"false"},"loaded":true}}
→ {"type":"subscribe","events":["chat"]}
← {"type":"response","success":true,"detail":"Subscribed to chat","data":{},"result":null}
← {"type":"event","event":"chat","data":{"seq":6,"sender":"Bob","message":"<Bob> hi"},"time":1791266000000}
```

A `subscribe` turns the connection into an event stream, so use a separate connection for it.

Any language can talk to a client: read its `socket` from its file in `<home>/clients/`, then write JSON lines to it. For example, from a shell:

```bash
socket=$(grep -o '"socket": *"[^"]*"' .nebs/clients/Alice-*.json | cut -d'"' -f4)
echo '{"type":"connect","host":"localhost"}' | nc -U "$socket"
```

## Adding a new message

1. Add a `@Serializable @SerialName("your-type") data class ... : Message` in `core/src/main/kotlin/com/nebs/core/message/`.
   `Message` is a sealed interface, so the codec picks up the new class automatically.
2. The mod's `MessageDispatcher` uses an exhaustive `when`, so the build fails until you handle the new message there.
   Add a handler under `mod/.../handler/`. The helpers in `mod/.../runtime/` cover the common needs:
   - `ClientThread.call` / `withPlayer` run code on the client thread and return its result.
   - `Ticker.run` repeats a step every tick until it returns a value, for anything that takes time.
   - `Input.hold` keeps a key pressed.
   - `Json` turns items, blocks and entities into JSON.
   Throwing `IllegalStateException` or `IllegalArgumentException` turns into an error reply with that message.
3. Add a `Spec` for it to `Commands.specs` in `core/.../command/Commands.kt`: name, usage, description, group and parser.
   Add a sample line to `AllCommandsTest`; the test fails until you do, and it checks the message survives the wire format.
   The CLI and the Gradle plugin's `nebs` task get the command automatically.
   Client selection (`--client`, `--all`, …) works for it automatically.
   Optionally add a dedicated typed task to `gradle-plugin/.../NebsTasks.kt` (extend `NebsSocketTask`). Document the command in the tables above.
