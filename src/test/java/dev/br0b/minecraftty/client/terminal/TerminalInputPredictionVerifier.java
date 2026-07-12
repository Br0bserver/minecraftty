package dev.br0b.minecraftty.client.terminal;

import com.jediterm.terminal.model.JediTerminal;
import com.jediterm.terminal.model.StyleState;
import com.jediterm.terminal.model.TerminalTextBuffer;

import java.nio.charset.StandardCharsets;

public final class TerminalInputPredictionVerifier {
	private TerminalInputPredictionVerifier() {
	}

	public static void main(String[] args) {
		predictsFastAscii();
		consumesEchoOneStateAtATime();
		predictsBackspaceAndMovement();
		disabledInAlternateBuffer();
		rawInputClearsPrediction();
	}

	private static void predictsFastAscii() {
		Fixture fixture = new Fixture();
		fixture.writeTerminal("$ ");
		fixture.predict("a");
		fixture.expectOverlay("single char", true, 3, 2, "a");
		fixture.predict("b");
		fixture.expectOverlay("two chars", true, 4, 2, "ab");
	}

	private static void consumesEchoOneStateAtATime() {
		Fixture fixture = new Fixture();
		fixture.writeTerminal("$ ");
		fixture.predict("a");
		fixture.predict("b");
		fixture.writeTerminal("a");
		fixture.expectOverlay("after first echo", true, 4, 3, "b");
		fixture.writeTerminal("b");
		fixture.expectOverlay("after second echo", false, 4, 0, "");
	}

	private static void predictsBackspaceAndMovement() {
		Fixture fixture = new Fixture();
		fixture.writeTerminal("$ ab");
		fixture.predict("\u001B[D");
		fixture.expectOverlay("left", true, 3, 4, "");
		fixture.predict("\u001B[C");
		fixture.expectOverlay("right cancels left", false, 4, 0, "");
		fixture.predict(new byte[]{0x7F});
		fixture.expectOverlay("backspace", true, 3, 3, "");
		fixture.predict("\u001B[H");
		fixture.expectOverlay("home", true, 2, 3, "");
		fixture.predict("\u001B[F");
		fixture.expectOverlay("end", true, 3, 3, "");
	}

	private static void disabledInAlternateBuffer() {
		Fixture fixture = new Fixture();
		fixture.writeTerminal("$ ");
		fixture.display.useAlternateScreenBuffer(true);
		fixture.predict("a");
		fixture.expectOverlay("alternate screen", false, 2, 0, "");
	}

	private static void rawInputClearsPrediction() {
		Fixture fixture = new Fixture();
		fixture.writeTerminal("$ ");
		fixture.predict("a");
		fixture.prediction.onRawInput();
		fixture.expectOverlay("raw input", false, 2, 0, "");
	}

	private static final class Fixture {
		private final MinecrafttyTerminalDisplay display = new MinecrafttyTerminalDisplay();
		private final StyleState styleState = new StyleState();
		private final TerminalTextBuffer textBuffer = new TerminalTextBuffer(80, 24, styleState, 100);
		private final JediTerminal terminal = new JediTerminal(display, textBuffer, styleState);
		private final TerminalInputPrediction prediction = new TerminalInputPrediction(textBuffer, display, "/bin/zsh");

		private void writeTerminal(String text) {
			terminal.writeCharacters(text);
		}

		private void predict(String text) {
			predict(text.getBytes(StandardCharsets.UTF_8));
		}

		private void predict(byte[] bytes) {
			prediction.onUserInput(bytes);
		}

		private void expectOverlay(String name, boolean visible, int cursorX, int textColumn, String text) {
			TerminalInputPrediction.State actual = prediction.actualState();
			TerminalInputPrediction.Overlay overlay = prediction.overlay(actual);
			if (overlay.visible() != visible) {
				throw new AssertionError(name + ": expected visible=" + visible + ", got " + overlay.visible());
			}
			int actualCursor = overlay.visible() ? overlay.cursorX() : actual.cursorX();
			if (actualCursor != cursorX) {
				throw new AssertionError(name + ": expected cursorX=" + cursorX + ", got " + actualCursor);
			}
			if (overlay.visible() && overlay.textColumn() != textColumn) {
				throw new AssertionError(name + ": expected textColumn=" + textColumn + ", got " + overlay.textColumn());
			}
			if (!overlay.text().equals(text)) {
				throw new AssertionError(name + ": expected text=" + describe(text) + ", got " + describe(overlay.text()));
			}
		}

		private String describe(String text) {
			return '"' + text + '"';
		}
	}
}
