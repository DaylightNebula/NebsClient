# Java API

The same API as [kotlin.md](kotlin.md), designed to be used from Java: fluent `ClientOptions`,
overloads for every optional parameter, getters for properties (`getName()`, `getX()`), and
unchecked `NebsException`. Artifact `nebs-api` (see [setup.md](setup.md)). What each method does:
[commands.md](commands.md).

```java
import com.nebs.api.*;
import java.time.Duration;
import java.util.List;

try (NebsClient client = new NebsClient(new ClientOptions().name("Javy"))) {   // launches and waits until loaded
    client.connect("localhost", 25565);                                          // returns once in the world
    PlayerStatus s = client.status();
    client.walkTo(s.getBlockX() - 5, s.getBlockZ());
    client.command("give @s diamond_pickaxe");
    client.hold("diamond_pickaxe");
    client.mine(10, 64, -3, Duration.ofSeconds(60));
    System.out.println(client.inventory().count("cobblestone"));
    System.out.println(client.screenshot("done").getPath());
}                                                                                 // close() quits the game
```

## Getting a client

| Code | `close()` |
|------|-----------|
| `new NebsClient()` / `new NebsClient(new ClientOptions().name("Bob").memory("3G"))` | Quits the game |
| `NebsClient.attach("Alice")`, `NebsClient.attach()` | Leaves it running (`exit()` quits) |
| `NebsClient.atSocket(Path.of("/tmp/nebs-123.sock"))` | Leaves it running |
| `NebsClient.running()` → `List<NebsClient>` | Leaves them running |
| `client.spawn(new ClientOptions().name("Carol"))` | Quits it |

`ClientOptions` setters (all optional, chainable): `name`, `uuid(UUID)`, `home(Path)`,
`template(Path)`, `instance(Path)`, `waitForReady(boolean)`, `readyTimeout(Duration)`,
`installIfMissing(boolean)`, `quitOnClose(boolean)`, `memory("2G")`, `windowSize(w, h)`,
`installProgress(ProgressListener)`.

## Notes for Java

- `goto` is a Java keyword, so the method is `walkTo(x, z)`.
- Enums: `ClientState.IN_WORLD`, `MoveDirection.FORWARD`, `BlockFace.NORTH`,
  `ClickMode.QUICK_MOVE`. Event names are constants in `Events` (`Events.CHAT`).
- Result types are Kotlin data classes: read fields through getters (`status.getHealth()`,
  `item.getId()`); nullable fields return `null` (`status.getHeldItem()`).
- Subscriptions take a lambda and are `AutoCloseable`:

  ```java
  try (Subscription sub = client.subscribe(List.of(Events.CHAT), e -> System.out.println(e.getData()))) {
      client.chat("hello");
      Thread.sleep(1000);
  }
  ```
- Every call blocks; `NebsException` is a `RuntimeException`. Calls on one instance are
  thread-safe.
- `client.run("mine 1 64 2")` runs any CLI command line and returns the raw `Response`.

## Running a single file

```bash
java -cp nebs-api-0.2.0-all.jar HelloNebs.java localhost 25565
```

The `-all` jar (attached to every GitHub release, or `./gradlew :api:allJar` in this repo) bundles
Kotlin and every dependency, so no other classpath entries are needed.

## JUnit 5

```java
class ServerSmokeTest {
    static NebsClient client;

    @BeforeAll static void start() {
        client = new NebsClient(new ClientOptions().name("CI"));
        client.connect("localhost", 25565);
    }

    @AfterAll static void stop() { client.close(); }

    @Test void spawnHasAFloor() {
        PlayerStatus s = client.status();
        assertFalse(client.block(s.getBlockX(), s.getBlockY() - 1, s.getBlockZ()).isAir());
    }
}
```
