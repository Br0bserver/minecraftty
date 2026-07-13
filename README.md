# minecraftty

Minecraft Terminal: a Linux-only Fabric client mod that opens a real local shell inside Minecraft.

The current MVP runs normal shells, CLI tools, and modern TUI programs through a PTY-backed terminal screen. It includes mouse reporting, scrollback, text selection, clipboard integration, responsive shell input, and an in-game settings screen.

It is usable for regular terminal workflows, but it is not intended to match every Alacritty, Kitty, Ghostty, or xterm feature yet.

## Requirements

- Linux
- JDK 25
- Minecraft/Fabric `26.1.2`
- Fabric Loader `0.19.3`

The project pins Gradle to `/usr/lib/jvm/java-25-openjdk` through `gradle.properties`.

## Usage

- Press `F12` in the client to open the terminal. This and the settings key can be changed in Minecraft's Controls screen.
- The first launch asks for explicit confirmation because commands run on the host as the same user that launched Minecraft.
- The terminal starts `$SHELL -l`, falling back to `/bin/bash`.
- `Esc` is sent to the terminal, so Vim and other TUI programs can use it normally.
- `F12` returns to the previous screen without killing the shell.
- Use `exit` or `Ctrl+D` to end the shell.

### Controls

| Input | Action |
| --- | --- |
| `F12` (default) | Open the terminal or return to the previous screen |
| `F10` (default) | Open settings; press it again to save and return |
| `Ctrl+Shift+C` / `Ctrl+Insert` | Copy the current selection |
| `Ctrl+Shift+V` / `Shift+Insert` | Paste clipboard text |
| `Shift+PageUp` / `Shift+PageDown` | Move through scrollback |
| Mouse wheel | Scroll history, or report wheel input to mouse-aware TUIs |
| Drag | Select text in a normal shell |
| `Shift` + drag | Force text selection while a TUI has mouse reporting enabled |
| Double-click / triple-click | Select a word / line |

Regular `Ctrl+C` is still sent to the shell or TUI and is never treated as a copy shortcut.

### Settings

Press `F10` or use the `Settings` button in the terminal status bar. The current settings include:

- shell executable;
- font scale;
- terminal panel size;
- background opacity;
- local input prediction.

`F10` and `Done` save changes. `Esc` and `Cancel` discard the current draft. Layout and visual changes apply when returning to the terminal; shell changes apply when a new terminal session starts.

Input prediction draws a conservative temporary overlay while the shell echo is still in flight, keeping fast ASCII input and the cursor visually responsive. It does not mutate JediTerm's real buffer and automatically disables itself for alternate-screen and mouse-reporting TUI flows.

## Current Capabilities

- Real Linux PTY sessions through `pty4j`.
- Terminal parsing and state through `jediterm-core`.
- Custom glyph atlas with a bundled JetBrains Mono Nerd Font and fallback fonts.
- Built-in rendering for common box drawing, block elements, and Powerline separators.
- Unicode and bracketed clipboard paste.
- Terminal mouse reporting for applications such as Vim, yazi, and opencode.
- Scrollback navigation and selection across visible history.
- Explicit clipboard copy with word and line selection.
- Conservative local input prediction for responsive shell typing.
- Resize propagation from the Minecraft window to the PTY.

## Development

```sh
./gradlew compileJava compileClientJava
./gradlew build
./gradlew runClient
```

The built mod jar is written to `build/libs/`.

## Scope

Working targets include:

- bash/zsh/fish basics
- common CLI tools
- common TUI programs such as Vim, nano, less, htop, yazi, and opencode
- mouse-aware and alternate-screen terminal applications

Best effort:

- tmux/screen
- complex Unicode, emoji, CJK, Nerd Font, and private-use character width behavior
- uncommon or application-specific terminal escape protocols

Current limitations:

- ligatures
- perfect Unicode/emoji width handling
- Kitty graphics and other terminal image protocols
- full xterm compatibility

Kitty/APC image payloads are currently suppressed, and inherited host-terminal image capability variables are removed before starting the shell. This prevents applications from reserving space for image previews that minecraftty cannot render yet.

## License

GPL-3.0-only. See `LICENSE` and `THIRD_PARTY_NOTICES.md`.
