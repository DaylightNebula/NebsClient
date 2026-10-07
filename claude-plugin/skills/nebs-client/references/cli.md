# nebs-cli

```
nebs-cli [--home DIR] [--client NAME]... [--all] [--socket PATH] [command [args...]]
```

With a command, it sends one message and exits: exit code 0 on success, 1 if any client failed, 2
for bad options. Without a command, it opens an interactive `> ` shell (avoid that from an agent;
pass the command on the command line instead). `nebs-cli help` prints every command.

Getting the CLI: see [setup.md](setup.md). In this repository, use
`./gradlew :cli:installDist` and then `cli/build/install/nebs-cli/bin/nebs-cli`, or run without
installing: `./gradlew -q :cli:run --args="client list"`.

## Global options (before the command)

| Option | Meaning |
|--------|---------|
| `--home DIR` | nebs home. Default: `$NEBS_HOME`, else `./.nebs`. Use the same home for launch and for later commands. |
| *(no client option)* | The only running client; refused, with the names listed, if several are running. |
| `--client NAME` | That client (case-insensitive). Repeat to pick several. |
| `--all` | Every running client, in parallel; reply lines are prefixed `Name: `. |
| `--socket PATH` | Whatever listens on that socket, registered or not. |

## Local commands (no client needed)

| Command | Options (all optional) |
|---------|------------------------|
| `client list` | — Running clients: name, state, server, pid, game folder. |
| `client install` | `--template DIR`, `--mod JAR` (repeatable; replaces the bundled nebs mod), `--java PATH` (skip downloading Mojang's runtime). Installs or repairs the template; only fetches what's missing. |
| `client launch` | `--name N`, `--uuid U`, `--instance DIR`, `--template DIR`, `--width W --height H`, `--wait` (block until loaded). Installs the template first if needed. |
| `client stop` | Client names, or `--all`; with neither, the only running client. |
| `claude-skill` | `--dir DIR` or `--user`. Installs this skill (default `./.claude/skills`; `--user` = `~/.claude/skills`). |

## Client commands

All commands in [commands.md](commands.md), e.g. `status`, `mine 1 64 2`, `screenshot name`.

Output: a summary line, then any `key: value` data, then for queries the `result` as indented JSON.
Errors go to stderr as `error: …`.

## Recipes

```bash
# Start two clients and put both on a local server
nebs-cli client launch --name Alice --wait
nebs-cli client launch --name Bob --wait
nebs-cli --all connect localhost 25565
nebs-cli --all wait-for in-world --timeout 60

# Act as one of them
nebs-cli --client Alice command give @s diamond_pickaxe
nebs-cli --client Alice hold diamond_pickaxe
nebs-cli --client Alice mine 10 64 -3
nebs-cli --client Alice inventory

# Check what a GUI shows, then click through it
nebs-cli --client Bob screen
nebs-cli --client Bob click-button "Done"

# Watch chat (runs until killed; start it in the background)
nebs-cli --client Alice subscribe chat death &

# Debug a misbehaving mod
nebs-cli --client Alice logs 200 --errors
nebs-cli --client Alice screenshot after-bug

# Clean up
nebs-cli client stop --all
```

Tips:

- Prefer `--client NAME` in scripts even with one client, so a stray client doesn't break them.
- `connect` returns as soon as connecting starts. Follow it with `wait-for in-world` before
  sending world commands.
- Arguments after the command are split on spaces; `chat`, `command` and `type-text` take the
  rest of the line as their text.
