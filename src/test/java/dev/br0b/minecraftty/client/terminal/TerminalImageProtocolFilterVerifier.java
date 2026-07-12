package dev.br0b.minecraftty.client.terminal;

import com.jediterm.core.util.Ascii;
import com.jediterm.terminal.TerminalDataStream;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

public final class TerminalImageProtocolFilterVerifier {
	private TerminalImageProtocolFilterVerifier() {
	}

	public static void main(String[] args) {
		kittySingleChunkIsSuppressed();
		kittyMultiChunkIsSuppressedAtEnd();
		kittyExplicitFinalChunkIsSuppressed();
		nonKittyApcIsSuppressedWithoutCount();
		filterContinuesAfterSuppression();
		apcBodyCanEndWithBel();
		apcBodyCanEndWithEscBackslash();
		apcBodyCanEndWithC1St();
		hostTerminalImageCapabilityEnvIsRemoved();
	}

	private static void kittySingleChunkIsSuppressed() {
		TerminalImageProtocolFilter filter = new TerminalImageProtocolFilter();
		expect("single chunk", "", filter.consumeApplicationProgramCommand("Ga=T,f=100;AAAA"));
		expect("single count", 1, filter.suppressedImages());
	}

	private static void kittyMultiChunkIsSuppressedAtEnd() {
		TerminalImageProtocolFilter filter = new TerminalImageProtocolFilter();
		expect("first chunk", "", filter.consumeApplicationProgramCommand("Gm=1;AAAA"));
		expect("middle chunk", "", filter.consumeApplicationProgramCommand("Gm=1;BBBB"));
		expect("final chunk", "", filter.consumeApplicationProgramCommand("Gm=0;CCCC"));
		expect("multi count", 1, filter.suppressedImages());
	}

	private static void kittyExplicitFinalChunkIsSuppressed() {
		TerminalImageProtocolFilter filter = new TerminalImageProtocolFilter();
		expect("explicit final", "", filter.consumeApplicationProgramCommand("Gm=0;AAAA"));
	}

	private static void nonKittyApcIsSuppressedWithoutCount() {
		TerminalImageProtocolFilter filter = new TerminalImageProtocolFilter();
		expect("non kitty", "", filter.consumeApplicationProgramCommand("qnot-kitty"));
		expect("non kitty count", 0, filter.suppressedImages());
	}

	private static void filterContinuesAfterSuppression() {
		TerminalImageProtocolFilter filter = new TerminalImageProtocolFilter();
		expect("image", "", filter.consumeApplicationProgramCommand("Ga=T;AAAA"));
		expect("next text unaffected", "", filter.consumeApplicationProgramCommand("not-kitty"));
		expect("second image", "", filter.consumeApplicationProgramCommand("Ga=T;BBBB"));
		expect("continued count", 2, filter.suppressedImages());
	}

	private static void apcBodyCanEndWithBel() {
		expect("BEL terminator", "Ga=T;", readBody("Ga=T;AAAA" + Ascii.BEL_CHAR));
	}

	private static void apcBodyCanEndWithEscBackslash() {
		expect("ESC backslash terminator", "Ga=T;", readBody("Ga=T;AAAA\u001B\\tail"));
	}

	private static void apcBodyCanEndWithC1St() {
		expect("C1 ST terminator", "Ga=T;", readBody("Ga=T;AAAA\u009Ctail"));
	}

	private static void hostTerminalImageCapabilityEnvIsRemoved() {
		Map<String, String> env = new HashMap<>();
		env.put("TERM_PROGRAM", "ghostty");
		env.put("KITTY_WINDOW_ID", "1");
		env.put("GHOSTTY_RESOURCES_DIR", "/tmp/ghostty");
		env.put("WEZTERM_EXECUTABLE", "/usr/bin/wezterm");
		env.put("SHELL", "/usr/bin/zsh");
		TerminalSession.removeHostTerminalImageCapabilityEnv(env);
		expect("TERM_PROGRAM removed", false, env.containsKey("TERM_PROGRAM"));
		expect("KITTY_WINDOW_ID removed", false, env.containsKey("KITTY_WINDOW_ID"));
		expect("GHOSTTY_RESOURCES_DIR removed", false, env.containsKey("GHOSTTY_RESOURCES_DIR"));
		expect("WEZTERM_EXECUTABLE removed", false, env.containsKey("WEZTERM_EXECUTABLE"));
		expect("SHELL preserved", "/usr/bin/zsh", env.get("SHELL"));
	}

	private static String readBody(String text) {
		try {
			return TerminalApplicationProgramCommandReader.readBody(new StringTerminalDataStream(text));
		} catch (IOException e) {
			throw new AssertionError("Unexpected IOException", e);
		}
	}

	private static void expect(String name, String expected, String actual) {
		if (!expected.equals(actual)) {
			throw new AssertionError(name + ": expected " + quote(expected) + ", got " + quote(actual));
		}
	}

	private static void expect(String name, int expected, int actual) {
		if (expected != actual) {
			throw new AssertionError(name + ": expected " + expected + ", got " + actual);
		}
	}

	private static void expect(String name, boolean expected, boolean actual) {
		if (expected != actual) {
			throw new AssertionError(name + ": expected " + expected + ", got " + actual);
		}
	}

	private static String quote(String text) {
		return "\"" + text + "\"";
	}

	private static final class StringTerminalDataStream implements TerminalDataStream {
		private final String text;
		private int offset;

		private StringTerminalDataStream(String text) {
			this.text = text;
		}

		@Override
		public char getChar() throws IOException {
			if (offset >= text.length()) {
				throw new EOF();
			}
			return text.charAt(offset++);
		}

		@Override
		public void pushChar(char c) {
			throw new UnsupportedOperationException();
		}

		@Override
		public String readNonControlCharacters(int maxChars) {
			throw new UnsupportedOperationException();
		}

		@Override
		public void pushBackBuffer(char[] bytes, int length) {
			throw new UnsupportedOperationException();
		}

		@Override
		public boolean isEmpty() {
			return offset >= text.length();
		}
	}
}
