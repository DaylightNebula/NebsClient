# Kotlin API

Artifact: `nebs-api` (see [setup.md](setup.md) for repositories and coordinates). Package
`dsh.nebsclient.api`. Method-by-method list of what each command does: [commands.md](commands.md).

```kotlin
import dsh.nebsclient.api.*
import java.time.Duration

NebsClient { name = "Tester" }.use { client ->     // launches, installs template if needed, waits until loaded
    client.connect("localhost", 25565)            // returns once in the world; throws with the server's reason otherwise
    val s = client.status()
    client.walkTo(s.blockX + 5, s.blockZ)
    client.lookAt(s.x, s.y + 1.6, s.z)
    println(client.screenshot("arrived").path)
}                                                 // close() quits the game
```

## Getting a client

| Code | Result | `close()` |
|------|--------|-----------|
| `NebsClient()` | Launch a new offline client with defaults. | Quits it |
| `NebsClient { name = "Bob"; memory = "3G" }` | Launch, configured (`ClientOptions`). | Quits it (unless `quitOnClose = false`) |
| `NebsClient.attach("Alice")` / `NebsClient.attach()` | Already running client by name, or the only one. | Leaves it running; `exit()` quits |
| `NebsClient.atSocket(path)` | Whatever listens on that socket. | Leaves it running |
| `NebsClient.running()` | All clients in the home. | Leaves them running |
| `client.spawn(ClientOptions().name("Carol"))` | A new client launched *by* this client. | Quits it |

`ClientOptions` (all optional): `name`, `uuid: UUID`, `home: Path`, `template: Path`,
`instance: Path`, `waitForReady = true`, `readyTimeout: java.time.Duration = 5 min`,
`installIfMissing = true`, `quitOnClose = true`, `memory = "2G"`, `width` / `height`,
`installProgress: ProgressListener`. With `waitForReady = false`, call `waitUntilReady()` later.

Properties: `name`, `socket`, `pid`, `gameDirectory` (screenshots and logs live under it), `home`.

## Behaviour

- Every call blocks until the client finished, and throws `NebsException` (unchecked) with the
  client's explanation on failure. Wrap expected failures (`mine` out of reach, etc.) in
  `runCatching`.
- Calls on one instance are thread-safe and run one at a time. Use one `NebsClient` per player and
  separate threads/coroutines to drive several players at once.
- Timeouts are `java.time.Duration`: `client.mine(1, 64, 2, Duration.ofSeconds(60))`.
- Clients you don't close keep running after the program exits. Prefer `use {}`.
- `client.run("mine 1 64 2")` accepts any CLI command line and returns the raw `Response`
  (`success`, `detail`, `data`, `result`); `send(message)` sends a `dsh.nebsclient.core.message.Message`.

## Query results

`status(): PlayerStatus` (x, y, z, blockX/Y/Z, yaw, pitch, health, maxHealth, food, xpLevel,
gameMode, dimension, onGround, dead, selectedSlot, heldItem: ItemInfo?, …),
`inventory(): Inventory` (`slots`, `slot(n)`, `count("cobblestone")`),
`block(): BlockInfo` (`block`, `properties`, `loaded`, `isAir`), `blocks(): BlocksInfo`,
`entities(): List<EntityInfo>` / `nearestEntity("zombie")`, `lookTarget(): LookTarget`,
`players()`, `world(): WorldInfo`, `screen(): ScreenInfo` (`buttons`, `textFields`, `container`),
`chatHistory(): ChatHistory` (`lastSeq`, `messages`), `scoreboard()`, `effects()`, `perf()`,
`logs(lines, errorsOnly): List<String>`, `options(): Map<String, String>`.

## Events

```kotlin
client.subscribe(listOf(Events.CHAT, Events.DEATH)) { event ->   // called on a background thread
    println("${event.type}: ${event.data}")
}.use {
    client.command("kill @s")
    Thread.sleep(2000)
}
```

To wait for a specific chat line, `waitForChat(regex, timeout, since = history.lastSeq)` is
simpler than a subscription. Grab `chatHistory().lastSeq` *before* triggering the message.

## Tests (JUnit 5)

```kotlin
class ServerSmokeTest {
    companion object {
        private lateinit var client: NebsClient
        @JvmStatic @BeforeAll fun start() { client = NebsClient { name = "CI" }; client.connect("localhost", 25565) }
        @JvmStatic @AfterAll fun stop() { client.close() }
    }

    @Test fun `spawn has a floor`() {
        val s = client.status()
        assertFalse(client.block(s.blockX, s.blockY - 1, s.blockZ).isAir)
    }
}
```

Launching takes seconds even with the template installed, so share one client per test class.
CI machines need a display (`xvfb-run ./gradlew test` on Linux).

## Scripts

Plain `.kts` with the self-contained jar (`nebs-api-<version>-all.jar`, attached to every GitHub
release):

```bash
kotlinc -cp nebs-api-0.2.0-all.jar -script hello.kts localhost 25565
```

`.main.kts`: `@file:DependsOn` must be the **very first line** (no comment or shebang before it),
with an **absolute** path to the `-all` jar:

```kotlin
@file:DependsOn("/abs/path/nebs-api-0.2.0-all.jar")
import dsh.nebsclient.api.NebsClient
NebsClient().use { it.connect("localhost") }
```

## Lower level (`core`)

`dsh.nebsclient.core` is what the API is built on: `ClientLauncher.launch(LaunchSpec(...))`,
`ClientRegistry.active(home)`, `ClientRegistry.broadcast(clients, message)`,
`TemplateInstaller.install(InstallSpec(...))`. Use it only when the API lacks something, e.g.
broadcasting one message to many clients in parallel.
