package dev.br0b.minecraftty.client.terminal;

public final class TerminalCellWidthVerifier {
	private TerminalCellWidthVerifier() {
	}

	public static void main(String[] args) {
		expectCells("ascii", "A", 1);
		expectCells("space", " ", 1);
		expectBlank("space blank", " ", true);
		expectCells("combining acute", "\u0301", 0);
		expectBlank("combining blank", "\u0301", true);
		expectCells("latin plus combining", "e\u0301", 1);
		expectCluster("latin plus combining cluster", "e\u0301x", 0, "e\u0301");
		expectCells("cjk", "\u754C", 2);
		expectCells("nerd font pua", new String(Character.toChars(0xF0219)), 1);
		expectCells("emoji presentation", "\uD83D\uDE00", 2);
		expectCells("emoji variation sequence", "\u2764\uFE0F", 2);
		expectCluster("emoji variation cluster", "\u2764\uFE0Fx", 0, "\u2764\uFE0F");
		expectCells("zwj emoji sequence", "\uD83D\uDC69\u200D\uD83D\uDCBB", 2);
		expectCluster("zwj emoji cluster", "\uD83D\uDC69\u200D\uD83D\uDCBBx", 0,
				"\uD83D\uDC69\u200D\uD83D\uDCBB");
		expectCells("regional indicator flag", "\uD83C\uDDFA\uD83C\uDDF8", 2);
		expectCluster("regional indicator flag cluster", "\uD83C\uDDFA\uD83C\uDDF8x", 0,
				"\uD83C\uDDFA\uD83C\uDDF8");
	}

	private static void expectCells(String name, String text, int expected) {
		int actual = TerminalCellWidth.cells(text);
		if (actual != expected) {
			throw new AssertionError(name + ": expected " + expected + " cells, got " + actual);
		}
	}

	private static void expectBlank(String name, String text, boolean expected) {
		boolean actual = TerminalCellWidth.isBlankCluster(text);
		if (actual != expected) {
			throw new AssertionError(name + ": expected blank=" + expected + ", got " + actual);
		}
	}

	private static void expectCluster(String name, String text, int offset, String expected) {
		int next = TerminalCellWidth.nextCluster(text, offset);
		String actual = text.substring(offset, next);
		if (!actual.equals(expected)) {
			throw new AssertionError(name + ": expected cluster " + describe(expected) + ", got " + describe(actual));
		}
	}

	private static String describe(String text) {
		StringBuilder builder = new StringBuilder();
		for (int offset = 0; offset < text.length(); ) {
			int codePoint = text.codePointAt(offset);
			if (!builder.isEmpty()) {
				builder.append(' ');
			}
			builder.append(String.format("U+%04X", codePoint));
			offset += Character.charCount(codePoint);
		}
		return builder.toString();
	}
}
