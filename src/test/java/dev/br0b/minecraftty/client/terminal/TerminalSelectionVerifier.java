package dev.br0b.minecraftty.client.terminal;

import com.jediterm.core.compatibility.Point;
import com.jediterm.terminal.model.JediTerminal;
import com.jediterm.terminal.model.StyleState;
import com.jediterm.terminal.model.TerminalSelection;
import com.jediterm.terminal.model.TerminalTextBuffer;

public final class TerminalSelectionVerifier {
	private TerminalSelectionVerifier() {
	}

	public static void main(String[] args) {
		selectsSingleLine();
		selectsMultipleLines();
		selectsReversedRange();
		selectsHistoryLines();
		preservesWrappedLines();
		removesDoubleWidthPlaceholders();
		selectsWord();
		selectsLine();
		ignoresEmptyLineSelection();
		selectsHistoryLine();
	}

	private static void selectsSingleLine() {
		Fixture fixture = new Fixture(20, 4);
		fixture.write("hello world");
		fixture.expect("single line", point(0, 0), point(5, 0), "hello");
	}

	private static void selectsMultipleLines() {
		Fixture fixture = new Fixture(20, 4);
		fixture.write("alpha");
		fixture.newLine();
		fixture.write("beta");
		fixture.expect("multi line", point(2, 0), point(2, 1), "pha\nbe");
	}

	private static void selectsReversedRange() {
		Fixture fixture = new Fixture(20, 4);
		fixture.write("alpha");
		fixture.newLine();
		fixture.write("beta");
		fixture.expect("reversed", point(2, 1), point(2, 0), "pha\nbe");
	}

	private static void selectsHistoryLines() {
		Fixture fixture = new Fixture(20, 2);
		fixture.write("one");
		fixture.newLine();
		fixture.write("two");
		fixture.newLine();
		fixture.write("three");
		fixture.expect("history", point(0, -1), point(3, 0), "one\ntwo");
	}

	private static void preservesWrappedLines() {
		Fixture fixture = new Fixture(20, 4);
		fixture.write("wrap");
		fixture.newLine();
		fixture.write("ped");
		fixture.buffer.setLineWrapped(0, true);
		fixture.expect("wrapped", point(0, 0), point(3, 1), "wrapped");
	}

	private static void removesDoubleWidthPlaceholders() {
		Fixture fixture = new Fixture(20, 4);
		fixture.write("\u754C!");
		fixture.expect("wide", point(0, 0), point(3, 0), "\u754C!");
	}

	private static void selectsWord() {
		Fixture fixture = new Fixture(20, 4);
		fixture.write("alpha beta");
		fixture.expectSelection("word alpha", TerminalSelectionUtil.wordSelection(point(1, 0), fixture.buffer), "alpha");
		fixture.expectSelection("word beta", TerminalSelectionUtil.wordSelection(point(7, 0), fixture.buffer), "beta");
	}

	private static void selectsLine() {
		Fixture fixture = new Fixture(20, 4);
		fixture.write("line text");
		fixture.expectSelection("line", TerminalSelectionUtil.lineSelection(point(3, 0), fixture.buffer), "line text");
	}

	private static void ignoresEmptyLineSelection() {
		Fixture fixture = new Fixture(20, 4);
		if (TerminalSelectionUtil.lineSelection(point(0, 0), fixture.buffer) != null) {
			throw new AssertionError("empty line: expected no selection");
		}
	}

	private static void selectsHistoryLine() {
		Fixture fixture = new Fixture(20, 2);
		fixture.write("older");
		fixture.newLine();
		fixture.write("newer");
		fixture.newLine();
		fixture.write("live");
		fixture.expectSelection("history line", TerminalSelectionUtil.lineSelection(point(1, -1), fixture.buffer),
				"older");
	}

	private static Point point(int x, int y) {
		return new Point(x, y);
	}

	private static final class Fixture {
		private final StyleState styleState = new StyleState();
		private final TerminalTextBuffer buffer;
		private final JediTerminal terminal;

		private Fixture(int columns, int rows) {
			MinecrafttyTerminalDisplay display = new MinecrafttyTerminalDisplay();
			buffer = new TerminalTextBuffer(columns, rows, styleState, 100);
			terminal = new JediTerminal(display, buffer, styleState);
		}

		private void write(String text) {
			terminal.writeCharacters(text);
		}

		private void newLine() {
			terminal.crnl();
		}

		private void expect(String name, Point start, Point end, String expected) {
			expectSelection(name, new TerminalSelection(start, end), expected);
		}

		private void expectSelection(String name, TerminalSelection selection, String expected) {
			String actual = TerminalSelectionUtil.selectionText(selection, buffer);
			if (!actual.equals(expected)) {
				throw new AssertionError(name + ": expected " + describe(expected) + ", got " + describe(actual));
			}
		}

		private String describe(String text) {
			return '"' + text.replace("\n", "\\n") + '"';
		}
	}
}
