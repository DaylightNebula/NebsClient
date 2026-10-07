---
name: nebs-client
description: Launch and control Minecraft (Fabric 26.3) test clients with nebs-client — from the nebs-cli command line, the Java/Kotlin API (com.nebs.api.NebsClient, including .kts scripts), or the Gradle plugin com.nebs.socket. Use when a task involves starting offline Minecraft clients, joining a test server, moving/mining/placing/chatting as a player, reading player status, inventory, blocks, entities, screens or chat, taking screenshots, or writing automated tests and smoke tests against a Minecraft server or mod.
---

# nebs-client

nebs-client controls real Minecraft clients from outside the game. A Fabric mod inside each client
listens on a local socket. Three front ends send it the same commands:

| Front end | Use it for | Reference |
|-----------|------------|-----------|
| `nebs-cli` | One-off commands from a shell, interactive exploration, quick checks | [references/cli.md](references/cli.md) |
| Java / Kotlin API (`com.nebs.api.NebsClient`) | Programs, JUnit tests, `.kts` / `.main.kts` scripts | [references/kotlin.md](references/kotlin.md), [references/java.md](references/java.md) |
| Gradle plugin `com.nebs.socket` | Build tasks: launch clients, join a test server after deploying, smoke tests | [references/gradle.md](references/gradle.md) |

Every client command (status, mine, chat, screenshot, …) is listed once in
[references/commands.md](references/commands.md); all three front ends accept the same commands.
How to add the library, the CLI or the plugin to a project (JitPack, release jars) is in
[references/setup.md](references/setup.md).

## Facts to keep in mind

- **The nebs home** (`./.nebs` by default; `NEBS_HOME` or `--home` overrides it) holds everything:
  `template/` (the installed game, ~700 MB, downloaded on first launch), `instances/<name>/` (each
  client's game folder: logs, screenshots, options) and `clients/` (one registry file per running
  client). Add `.nebs/` to `.gitignore`.
- **Launching installs the template automatically** if missing. The first launch downloads about
  700 MB and takes minutes; later launches take seconds to tens of seconds. Use generous timeouts.
- **Clients are offline.** They never log in, so they can only join servers with
  `online-mode=false`. Names default to `NebsNNNN`; set one with `--name` / `name`.
- **Clients need a display.** On headless Linux, wrap the launching process in `xvfb-run`.
- **Clients outlive their launcher.** A client keeps running after the CLI, Gradle or your program
  exits. Always stop what you start: `nebs-cli client stop --all`, `close()` / `use {}` /
  try-with-resources in code, or the `nebsStopClient` task.
- **Choosing clients.** With one client running, commands go to it. With several, pick with
  `--client NAME` (repeatable) or `--all`; in code, use `NebsClient.attach("Name")`.
- **Most commands need the client in a world.** Everything except `connect`, `ping`, `wait-for`,
  `quit`, `spawn`, screen commands and capture commands fails with "Not in a world" otherwise.
  Connect first, then act.
- **Units.** Block coordinates; yaw 0 = south, 90 = west; pitch -90 (up) to 90 (down); durations in
  ticks (20/s) except options named `--timeout`, which are seconds.
- `goto` / `walkTo` walks in a straight line and jumps single blocks. It is **not** a pathfinder,
  and fails if it gets stuck. Break long routes into short straight legs.

## Typical workflows

**Check a server or mod by hand (CLI):**

```bash
nebs-cli client launch --name Alice --wait     # installs the template first if needed
nebs-cli connect localhost 25565
nebs-cli status
nebs-cli screenshot check-1                    # replies with the PNG path; read it to look at the game
nebs-cli client stop --all
```

**Automated test (Kotlin):**

```kotlin
NebsClient { name = "Tester" }.use { client ->
    client.connect("localhost", 25565)
    client.command("give @s diamond_pickaxe")
    client.hold("diamond_pickaxe")
    client.mine(10, 64, -3)
    check(client.inventory().count("cobblestone") > 0)
}
```

**After deploying to a test server (Gradle):**

```bash
./gradlew nebsLaunchClient --name=Bot --wait
./gradlew nebsConnect --host=localhost --port=25566
```

## Looking at the game

- `screenshot [name]` saves a PNG into the client's `screenshots/` folder and replies with its path.
  Open that file to see what the player sees. Hide the HUD (`hud off`) for clean shots.
- `screen` describes the open GUI: buttons with `#index`, text fields, container slots. Use it
  before `click-button`, `click-slot` or `type-text`.
- `logs --errors` shows warnings, errors and stack traces from the client log, which is the first
  place to look when a mod misbehaves.
- `chat-history` and `wait-for-chat <regex>` read server replies to commands.

## When something fails

| Symptom | Fix |
|---------|-----|
| "no clients are running in …" | Launch one (`client launch`), or check the home: `--home`/`NEBS_HOME` must match where it was launched. |
| "N clients are running …; choose with --client" | Add `--client NAME` or `--all`. |
| "Not in a world; connect to a server first" | `connect <host>` first, or `wait-for in-world`. |
| Connect fails with "Failed to connect" / disconnect screen | Is the server running, offline-mode, the same game version (26.3)? Check `screen` for the reason. |
| Launch hangs on headless Linux | Run under `xvfb-run`. |
| Command times out | Raise `--timeout`; first launches and chunk loading are slow. |
