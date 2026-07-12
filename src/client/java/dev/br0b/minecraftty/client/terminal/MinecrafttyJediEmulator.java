package dev.br0b.minecraftty.client.terminal;

import com.jediterm.core.util.Ascii;
import com.jediterm.terminal.Terminal;
import com.jediterm.terminal.TerminalDataStream;
import com.jediterm.terminal.emulator.JediEmulator;

import java.io.IOException;

final class MinecrafttyJediEmulator extends JediEmulator {
	private final TerminalImageProtocolFilter imageProtocolFilter = new TerminalImageProtocolFilter();

	MinecrafttyJediEmulator(TerminalDataStream dataStream, Terminal terminal) {
		super(dataStream, terminal);
	}

	@Override
	public void processChar(char ch, Terminal terminal) throws IOException {
		if (ch == TerminalApplicationProgramCommandReader.APC) {
			consumeApplicationProgramCommand(terminal);
			return;
		}
		if (ch == Ascii.ESC) {
			char next = myDataStream.getChar();
			if (next == '_') {
				consumeApplicationProgramCommand(terminal);
				return;
			}
			myDataStream.pushChar(next);
		}
		super.processChar(ch, terminal);
	}

	int suppressedImages() {
		return imageProtocolFilter.suppressedImages();
	}

	private void consumeApplicationProgramCommand(Terminal terminal) throws IOException {
		String placeholder = imageProtocolFilter.consumeApplicationProgramCommand(
				TerminalApplicationProgramCommandReader.readBody(myDataStream));
		if (!placeholder.isEmpty()) {
			terminal.writeCharacters(placeholder);
		}
	}
}
