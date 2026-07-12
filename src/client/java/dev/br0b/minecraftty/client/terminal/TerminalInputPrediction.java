package dev.br0b.minecraftty.client.terminal;

import com.jediterm.core.typeahead.TypeAheadTerminalModel;
import com.jediterm.terminal.model.TerminalLine;
import com.jediterm.terminal.model.TerminalTextBuffer;
import org.jetbrains.annotations.NotNull;

import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;

final class TerminalInputPrediction implements TypeAheadTerminalModel {
	private static final int MAX_PENDING_STATES = 256;
	private static final long LATENCY_THRESHOLD_NANOS = TimeUnit.MILLISECONDS.toNanos(100);

	private final TerminalTextBuffer textBuffer;
	private final MinecrafttyTerminalDisplay display;
	private final ShellType shellType;
	private final BooleanSupplier enabled;
	private final ArrayDeque<State> pendingStates = new ArrayDeque<>();
	private int leftmostCursorX = -1;

	TerminalInputPrediction(TerminalTextBuffer textBuffer, MinecrafttyTerminalDisplay display, String shell) {
		this(textBuffer, display, shell, () -> true);
	}

	TerminalInputPrediction(TerminalTextBuffer textBuffer, MinecrafttyTerminalDisplay display, String shell,
			BooleanSupplier enabled) {
		this.textBuffer = textBuffer;
		this.display = display;
		this.shellType = TypeAheadTerminalModel.commandLineToShellType(List.of(shell));
		this.enabled = enabled;
	}

	synchronized void onUserInput(byte[] bytes) {
		Action action = action(bytes);
		if (action == null || !canPredict()) {
			clearPredictions();
			return;
		}

		State base = pendingStates.isEmpty() ? actualState() : pendingStates.peekLast();
		if (base == null) {
			clearPredictions();
			return;
		}
		if (pendingStates.isEmpty()) {
			leftmostCursorX = editableStart(base);
		}

		State predicted = apply(base, action);
		if (predicted == null) {
			clearPredictions();
			return;
		}

		pendingStates.addLast(predicted);
		while (pendingStates.size() > MAX_PENDING_STATES) {
			pendingStates.removeFirst();
		}
	}

	synchronized void onRawInput() {
		clearPredictions();
	}

	synchronized Overlay overlay(State actual) {
		reconcile(actual);
		if (pendingStates.isEmpty() || actual == null || !canPredict()) {
			return Overlay.NONE;
		}

		State predicted = pendingStates.peekLast();
		if (predicted.cursorY != actual.cursorY) {
			clearPredictions();
			return Overlay.NONE;
		}

		int firstDiff = firstDifferentColumn(actual.line, predicted.line);
		String text = "";
		if (firstDiff < predicted.line.length()) {
			int end = Math.min(predicted.line.length(), textBuffer.getWidth());
			text = predicted.line.substring(firstDiff, end);
		}
		return new Overlay(true, predicted.cursorY - 1, predicted.cursorX, firstDiff, text);
	}

	State actualState() {
		textBuffer.lock();
		try {
			int cursorX = clamp(display.cursorX(), 0, Math.max(0, textBuffer.getWidth() - 1));
			int cursorY = clamp(display.cursorY(), 1, Math.max(1, textBuffer.getHeight()));
			TerminalLine line = textBuffer.getLine(cursorY - 1);
			String text = trimToWidth(line.getText(), textBuffer.getWidth());
			return new State(text, cursorX, cursorY);
		} finally {
			textBuffer.unlock();
		}
	}

	synchronized void reconcile(State actual) {
		if (pendingStates.isEmpty()) {
			return;
		}
		if (actual == null || !canPredict()) {
			clearPredictions();
			return;
		}

		int matchedIndex = -1;
		int index = 0;
		for (State state : pendingStates) {
			if (state.sameAs(actual)) {
				matchedIndex = index;
			}
			index++;
		}

		if (matchedIndex >= 0) {
			for (int i = 0; i <= matchedIndex; i++) {
				pendingStates.removeFirst();
			}
			if (pendingStates.isEmpty()) {
				leftmostCursorX = -1;
			}
			return;
		}

		State last = pendingStates.peekLast();
		if (actual.cursorY != last.cursorY
				|| (!last.line.startsWith(actual.line) && !actual.line.startsWith(last.line))) {
			clearPredictions();
		}
	}

	@Override
	public synchronized void insertCharacter(char ch, int index) {
		State base = pendingStates.isEmpty() ? actualState() : pendingStates.peekLast();
		if (base == null || index < 0 || index > textBuffer.getWidth() - 1) {
			clearPredictions();
			return;
		}
		State predicted = insert(base, ch, index);
		if (predicted == null) {
			clearPredictions();
			return;
		}
		pendingStates.addLast(predicted);
	}

	@Override
	public synchronized void removeCharacters(int from, int count) {
		State base = pendingStates.isEmpty() ? actualState() : pendingStates.peekLast();
		if (base == null || count <= 0 || from < 0) {
			return;
		}
		StringBuilder line = new StringBuilder(base.line);
		if (from < line.length()) {
			line.delete(from, Math.min(line.length(), from + count));
		}
		pendingStates.addLast(new State(line.toString(), Math.min(from, line.length()), base.cursorY));
	}

	@Override
	public synchronized void moveCursor(int index) {
		State base = pendingStates.isEmpty() ? actualState() : pendingStates.peekLast();
		if (base != null) {
			pendingStates.addLast(new State(base.line, clamp(index, 0, Math.max(0, textBuffer.getWidth() - 1)),
					base.cursorY));
		}
	}

	@Override
	public void forceRedraw() {
	}

	@Override
	public synchronized void clearPredictions() {
		pendingStates.clear();
		leftmostCursorX = -1;
	}

	@Override
	public void lock() {
		textBuffer.lock();
	}

	@Override
	public void unlock() {
		textBuffer.unlock();
	}

	@Override
	public boolean isUsingAlternateBuffer() {
		return textBuffer.isUsingAlternateBuffer() || display.alternateScreenBuffer();
	}

	@Override
	public @NotNull LineWithCursorX getCurrentLineWithCursor() {
		State state = actualState();
		return new LineWithCursorX(new StringBuffer(state.line), state.cursorX);
	}

	@Override
	public int getTerminalWidth() {
		return textBuffer.getWidth();
	}

	@Override
	public boolean isTypeAheadEnabled() {
		return canPredict();
	}

	@Override
	public long getLatencyThreshold() {
		return LATENCY_THRESHOLD_NANOS;
	}

	@Override
	public ShellType getShellType() {
		return shellType;
	}

	private State apply(State base, Action action) {
		return switch (action.type) {
			case CHARACTER -> insert(base, action.character, base.cursorX);
			case BACKSPACE -> backspace(base);
			case LEFT -> move(base, -1);
			case RIGHT -> move(base, 1);
			case HOME -> new State(base.line, Math.max(0, leftmostCursorX), base.cursorY);
			case END -> new State(base.line, Math.min(base.line.length(), textBuffer.getWidth() - 1), base.cursorY);
		};
	}

	private State insert(State base, char character, int index) {
		if (index < 0 || index >= textBuffer.getWidth() || base.line.length() >= textBuffer.getWidth()) {
			return null;
		}
		StringBuilder line = new StringBuilder(base.line);
		while (line.length() < index) {
			line.append(' ');
		}
		line.insert(index, character);
		if (line.length() > textBuffer.getWidth()) {
			line.setLength(textBuffer.getWidth());
		}
		return new State(line.toString(), Math.min(index + 1, textBuffer.getWidth() - 1), base.cursorY);
	}

	private State backspace(State base) {
		if (leftmostCursorX >= 0 && base.cursorX <= leftmostCursorX) {
			return null;
		}
		if (base.cursorX <= 0) {
			return null;
		}
		StringBuilder line = new StringBuilder(base.line);
		int nextCursor = base.cursorX - 1;
		if (nextCursor < line.length()) {
			line.deleteCharAt(nextCursor);
		}
		return new State(line.toString(), nextCursor, base.cursorY);
	}

	private State move(State base, int amount) {
		int left = Math.max(0, leftmostCursorX);
		int right = Math.min(base.line.length(), textBuffer.getWidth() - 1);
		int cursor = clamp(base.cursorX + amount, left, right);
		return new State(base.line, cursor, base.cursorY);
	}

	private boolean canPredict() {
		return enabled.getAsBoolean()
				&& !isUsingAlternateBuffer() && !display.sendsMouseReports() && display.cursorVisible();
	}

	private static Action action(byte[] bytes) {
		if (bytes == null || bytes.length == 0) {
			return null;
		}
		if (bytes.length == 1) {
			int value = bytes[0] & 0xFF;
			if (value >= 0x20 && value <= 0x7E) {
				return new Action(ActionType.CHARACTER, (char) value);
			}
			if (value == 0x7F) {
				return new Action(ActionType.BACKSPACE, '\0');
			}
		}
		String sequence = new String(bytes, StandardCharsets.UTF_8);
		return switch (sequence) {
			case "\u001B[D" -> new Action(ActionType.LEFT, '\0');
			case "\u001B[C" -> new Action(ActionType.RIGHT, '\0');
			case "\u001B[H" -> new Action(ActionType.HOME, '\0');
			case "\u001B[F" -> new Action(ActionType.END, '\0');
			default -> null;
		};
	}

	private static int firstDifferentColumn(String actual, String predicted) {
		int limit = Math.min(actual.length(), predicted.length());
		for (int i = 0; i < limit; i++) {
			if (actual.charAt(i) != predicted.charAt(i)) {
				return i;
			}
		}
		return limit;
	}

	private static int editableStart(State state) {
		int cursor = Math.min(state.cursorX, state.line.length());
		String prefix = state.line.substring(0, cursor);
		int start = Math.max(Math.max(prefix.lastIndexOf("$ "), prefix.lastIndexOf("# ")), prefix.lastIndexOf("> "));
		return start >= 0 ? start + 2 : 0;
	}

	private static String trimToWidth(String text, int width) {
		return text.length() <= width ? text : text.substring(0, width);
	}

	private static int clamp(int value, int min, int max) {
		return Math.max(min, Math.min(max, value));
	}

	record State(String line, int cursorX, int cursorY) {
		boolean sameAs(State other) {
			return cursorX == other.cursorX && cursorY == other.cursorY && line.equals(other.line);
		}
	}

	record Overlay(boolean visible, int row, int cursorX, int textColumn, String text) {
		static final Overlay NONE = new Overlay(false, 0, 0, 0, "");
	}

	private record Action(ActionType type, char character) {
	}

	private enum ActionType {
		CHARACTER,
		BACKSPACE,
		LEFT,
		RIGHT,
		HOME,
		END
	}
}
