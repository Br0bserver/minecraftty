package dev.br0b.minecraftty.client.terminal;

import com.jediterm.core.typeahead.TerminalTypeAheadManager;
import com.jediterm.terminal.Terminal;
import com.jediterm.terminal.TerminalDataStream;
import com.jediterm.terminal.TerminalExecutorServiceManager;
import com.jediterm.terminal.TerminalStarter;
import com.jediterm.terminal.TtyConnector;
import com.jediterm.terminal.emulator.JediEmulator;
import com.jediterm.terminal.model.JediTerminal;
import org.jetbrains.annotations.NotNull;

final class MinecrafttyTerminalStarter extends TerminalStarter {
	MinecrafttyTerminalStarter(@NotNull JediTerminal terminal,
			@NotNull TtyConnector ttyConnector,
			@NotNull TerminalDataStream dataStream,
			TerminalTypeAheadManager typeAheadManager,
			@NotNull TerminalExecutorServiceManager executorServiceManager) {
		super(terminal, ttyConnector, dataStream, typeAheadManager, executorServiceManager);
	}

	@Override
	protected JediEmulator createEmulator(TerminalDataStream dataStream, Terminal terminal) {
		return new MinecrafttyJediEmulator(dataStream, terminal);
	}
}
