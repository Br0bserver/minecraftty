# minecraftty

Minecraft Terminal: a Linux-only Fabric client mod that opens a real local shell inside Minecraft.

This is an MVP. It aims to run normal shells, CLI tools, and common TUI programs through a PTY-backed terminal screen. It does not try to be a full Alacritty/Ghostty-class terminal emulator yet.

## Requirements

- Linux
- JDK 25
- Minecraft/Fabric `26.1.2`
- Fabric Loader `0.19.3`

The project pins Gradle to `/usr/lib/jvm/java-25-openjdk` through `gradle.properties`.

## Usage

- Press `F12` in the client to open the terminal.
- The first launch asks for explicit confirmation because commands run on the host as the same user that launched Minecraft.
- The terminal starts `$SHELL -l`, falling back to `/bin/bash`.
- `Esc` is sent to the terminal, so Vim and other TUI programs can use it normally.
- `F12` returns to the previous screen without killing the shell.
- Use `exit` or `Ctrl+D` to end the shell.

## Development

```sh
./gradlew compileJava compileClientJava
./gradlew build
./gradlew runClient
```

The built mod jar is written to `build/libs/`.

## Scope

Explicitly targeted:

- bash/zsh/fish basics
- common CLI tools
- common TUI programs such as `vim`, `nano`, `less`, and `htop`
- resize propagation from the Minecraft window to the PTY

Best effort:

- tmux/screen
- complex modern TUI programs
- mouse mode and non-standard terminal escape protocols

Not promised by the MVP:

- GPU font rendering
- ligatures
- perfect Unicode/emoji width handling
- image protocols
- full xterm compatibility

## License

GPL-3.0-only. See `LICENSE` and `THIRD_PARTY_NOTICES.md`.
