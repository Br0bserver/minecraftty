package dev.br0b.minecraftty.client.terminal;

import com.jediterm.core.util.Ascii;
import com.jediterm.terminal.TerminalDataStream;

import java.io.IOException;

final class TerminalApplicationProgramCommandReader {
	static final char APC = 0x9F;
	static final char ST = 0x9C;
	private static final int MAX_PREFIX_LENGTH = 4096;

	private TerminalApplicationProgramCommandReader() {
	}

	static String readBody(TerminalDataStream stream) throws IOException {
		StringBuilder builder = new StringBuilder();
		boolean collecting = true;
		while (true) {
			char ch = stream.getChar();
			if (ch == Ascii.BEL || ch == ST) {
				return builder.toString();
			}
			if (ch == Ascii.ESC) {
				char next = stream.getChar();
				if (next == '\\') {
					return builder.toString();
				}
				if (collecting) {
					append(builder, ch);
					collecting = append(builder, next);
				}
				continue;
			}
			if (collecting) {
				collecting = append(builder, ch);
				if (ch == ';') {
					collecting = false;
				}
			}
		}
	}

	private static boolean append(StringBuilder builder, char ch) {
		if (builder.length() >= MAX_PREFIX_LENGTH) {
			return false;
		}
		builder.append(ch);
		return builder.length() < MAX_PREFIX_LENGTH;
	}
}
