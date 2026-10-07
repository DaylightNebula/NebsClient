# Client commands

Every command below works in all front ends:

- CLI: `nebs-cli [--client NAME | --all] <command> [args]`
- Gradle: `./gradlew nebs --command="<command> [args]" [--client=NAME | --all]`
- API: the typed method in brackets, or `client.run("<command> [args]")` for any command line

Commands marked **W** need the client to be in a world; otherwise they fail with "Not in a world".
Coordinates are block coordinates. Yaw: 0 = south, 90 = west, 180 = north, 270 = east. Pitch: -90
(up) to 90 (down). Durations are ticks (20 per second), except `--timeout`, which is seconds.

## Session

| Command | API | What it does |
|---------|-----|--------------|
| `connect <host> [port]` | `connect(host, port)` | Join a server (port 25565 by default; `host:port` works too). Leaves any current world first. The API waits until in the world; the CLI returns once connecting starts, so follow with `wait-for in-world`. |
| `disconnect` | `disconnect()` | Leave the server, back to the title screen. |
| `ping` | `ping()` | `name`, `uuid`, `state` (`loading`, `menu`, `connecting`, `in-world`), `server`. |
| `wait-for <state> [--timeout S]` | `waitFor(ClientState.X, timeout)` | Wait for `loading`, `menu`, `connecting`, `in-world`, `alive` or `dead` (default 30 s). |
| `respawn` | `respawn()` | **W** Press "Respawn" on the death screen. |
| `quit` | `exit()` | Shut the client down. |
| `spawn [--name N] [--uuid U] [--instance DIR] [--template DIR]` | `spawn(options)` | This client launches another offline client in the same home. |

## Observation (all **W** except `screen`)

| Command | API | Result |
|---------|-----|--------|
| `status` | `status(): PlayerStatus` | Position, block position, yaw/pitch, health, absorption, food, saturation, XP, game mode, dimension, on-ground/in-water/sprinting/sneaking, dead, selected slot, held item. |
| `inventory` | `inventory(): Inventory` | Non-empty slots: hotbar 0-8, main 9-35, armor 36-39, offhand 40. `Inventory.count("cobblestone")` sums an item. |
| `block <x> <y> <z>` | `block(x, y, z): BlockInfo` | Block id, state properties, whether the chunk is loaded. |
| `blocks <x1> <y1> <z1> <x2> <y2> <z2>` | `blocks(...): BlocksInfo` | Non-air blocks in a box (≤ 32768 blocks): counts per block plus a list (first 4096). |
| `look-target` | `lookTarget(): LookTarget` | Block (with face, distance) or entity under the crosshair. |
| `entities [--radius R] [--type T]` | `entities(radius, type)`, `nearestEntity(type)` | Nearby entities, nearest first: id, type, name, uuid, position, distance, health. `--type pig` or `minecraft:pig`. |
| `players` | `players()` | Tab list: name, uuid, latency, game mode. |
| `world` | `world(): WorldInfo` | Dimension, time of day, day, game time, rain/thunder, difficulty, server. |
| `screen` | `screen(): ScreenInfo` | Open screen: type, title, buttons (`#index`), text fields, container slots and carried item. Works anywhere, including menus. |
| `chat-history [count]` | `chatHistory(count)` | Last messages (default 20) with `seq`, `type` (`chat`, `system`, `action-bar`), sender, text. |
| `scoreboard` | `scoreboard()` | Sidebar title and lines. |
| `effects` | `effects()` | Active potion effects with level and remaining ticks. |

## Movement (**W**)

A new `move`, `goto` or `follow` replaces the one in progress; `stop` ends it and releases all keys.

| Command | API | What it does |
|---------|-----|--------------|
| `look <yaw> <pitch>` | `look(yaw, pitch)` | Turn the camera. |
| `look-at <x> <y> <z>` | `lookAt(x, y, z)` | Face a point. |
| `move <forward\|back\|left\|right> [ticks]` | `move(MoveDirection.FORWARD, ticks)`, `startMoving(dir)` | Hold a movement key for some ticks, or until `stop`. |
| `jump` | `jump()` | Jump once. |
| `sneak <on\|off>` / `sprint <on\|off>` | `sneak(bool)` / `sprint(bool)` | Hold or release. |
| `stop` | `stop()` | Stop moving, release every key. |
| `goto <x> <z> [--timeout S]` | `walkTo(x, z, timeout)` | Straight-line walk, jumping single-block steps. Not a pathfinder; fails if stuck. |
| `follow <player\|entity-id> [--distance D]` | `follow(target, distance)` | Keep walking toward a player/entity (default within 3 blocks) until `stop`. |

## Interaction (**W**)

| Command | API | What it does |
|---------|-----|--------------|
| `attack [entity-id]` | `attack()`, `attack(id)` | Attack an entity, or whatever is under the crosshair. Fails out of reach. |
| `use [--ticks N]` | `use(ticks)` | Right-click with the held item. `--ticks 40` keeps holding, e.g. to eat. |
| `use-on <x> <y> <z> [face]` | `useOn(x, y, z, BlockFace.UP)` | Right-click a block face (default `up`): doors, buttons, levers, beds, chests. |
| `mine <x> <y> <z> [--timeout S]` | `mine(x, y, z, timeout)` | Break a block at normal speed with the held item. Fails if obstructed or out of reach. |
| `place <x> <y> <z>` | `place(x, y, z)` | Place the held block against any solid neighbour within reach. |
| `select-slot <0-8>` | `selectSlot(n)` | Select a hotbar slot. |
| `hold <item>` | `hold("diamond_sword")` | Put an item in the main hand, moving it from the inventory if needed. |
| `drop [--all]` | `drop(all)` | Drop one of the held item, or the stack. |
| `swap-hands` | `swapHands()` | Swap main and off hand. |
| `interact <entity-id>` | `interact(id)` | Right-click an entity (trade, mount, …). |

## Inventory and screens

| Command | API | What it does |
|---------|-----|--------------|
| `open-inventory` / `close-screen` | `openInventory()` / `closeScreen()` | Open the inventory (**W**); close any screen. |
| `click-slot <slot> [--button N] [--mode M]` | `clickSlot(slot, button, ClickMode.X)` | Click a slot of the open container (numbers from `screen`). Modes: `pickup` (default), `quick_move` (shift-click), `swap` (`--button` = hotbar slot), `clone`, `throw`, `quick_craft`, `pickup_all`. |
| `click-button <label\|#index>` | `clickButton("Done")`, `clickButton(3)` | Click a button by label (exact, then partial) or index. Works on every screen, including title and multiplayer menus. |
| `type-text <text…>` | `typeText(text)` | Type into the focused (or only) text field. |

## Chat (**W**)

| Command | API | What it does |
|---------|-----|--------------|
| `chat <message…>` | `chat(msg)` | Send chat. A leading `/` runs it as a command. |
| `command <command…>` | `command("give @s diamond 3")` | Run a command, with or without `/`. |
| `wait-for-chat <regex> [--timeout S] [--since SEQ]` | `waitForChat(regex, timeout, since)` | Wait for a chat/system message matching the regex, after this request or after message `SEQ`. |

## Capture and debugging

| Command | API | What it does |
|---------|-----|--------------|
| `screenshot [name]` | `screenshot(name): ScreenshotInfo` | Save `<game dir>/screenshots/<name>.png`; replies with `path`, `width`, `height`. |
| `set-window <width> <height>` | `setWindowSize(w, h)` | Resize the window (screen points; high-DPI screenshots are larger). |
| `options` / `set-option <name> <value>` | `options()` / `setOption(name, value)` | List or change game options: `fov 90`, `renderDistance 8`, `guiScale 2`, `graphicsPreset fast`. |
| `hud <on\|off>` / `f3 <on\|off>` | `setHudVisible(b)` / `setDebugOverlay(b)` | Show or hide the HUD (F1) or debug overlay (F3). |
| `perf` | `perf()` | FPS, memory, loaded chunks, entities, window size in pixels. |
| `logs [lines] [--errors]` | `logs(lines, errorsOnly)` | Tail of the client log; `--errors` keeps warnings, errors and stack traces. |
| `reload-resources` | `reloadResources()` | Reload resource packs (F3+T), replying when done. |

## Events

| Command | API | What it does |
|---------|-----|--------------|
| `subscribe [event…]` | `subscribe(listOf(Events.CHAT)) { e -> }` | Stream events until stopped. Events: `chat`, `system`, `action-bar`, `join`, `disconnect`, `death`, `respawn`, `health`, `screen`, `inventory`. CLI only, not Gradle. In the CLI it never exits on its own, so run it in the background. |

## Wire protocol (for other languages)

Each client's registry file `<home>/clients/<name>-<pid>.json` has its `socket` (a Unix domain
socket). Send newline-delimited JSON, one message per line; `type` is the command name and the other
fields are its arguments. Every message gets a `response`:

```
→ {"type":"block","x":4,"y":-61,"z":-2}
← {"type":"response","success":true,"detail":"minecraft:grass_block","data":{},"result":{"x":4,"y":-61,"z":-2,"block":"minecraft:grass_block","properties":{"snowy":"false"},"loaded":true}}
```

The exact message shapes are the classes in `dsh.nebsclient.core.message`.
