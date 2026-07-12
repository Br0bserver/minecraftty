package dev.br0b.minecraftty.client.terminal;

import com.jediterm.core.compatibility.Point;
import com.jediterm.terminal.model.SelectionUtil;
import com.jediterm.terminal.model.TerminalLine;
import com.jediterm.terminal.model.TerminalSelection;
import com.jediterm.terminal.model.TerminalTextBuffer;

final class TerminalSelectionUtil {
	private TerminalSelectionUtil() {
	}

	static TerminalSelection wordSelection(Point point, TerminalTextBuffer buffer) {
		if (!isValidRow(point.y, buffer)) {
			return null;
		}
		String text = buffer.getLine(point.y).getText();
		if (text.isEmpty()) {
			return null;
		}

		int column = Math.max(0, Math.min(point.x, text.length() - 1));
		Point start = SelectionUtil.getPreviousSeparator(new Point(column, point.y), buffer);
		Point end = SelectionUtil.getNextSeparator(new Point(column, point.y), buffer);
		if (end.y == start.y) {
			end.x = Math.min(buffer.getWidth(), end.x + 1);
		}
		TerminalSelection selection = new TerminalSelection(start, end);
		return isEmpty(selection) ? null : selection;
	}

	static TerminalSelection lineSelection(Point point, TerminalTextBuffer buffer) {
		if (!isValidRow(point.y, buffer)) {
			return null;
		}
		TerminalLine line = buffer.getLine(point.y);
		int end = Math.min(line.getText().length(), buffer.getWidth());
		if (end <= 0) {
			return null;
		}
		return new TerminalSelection(new Point(0, point.y), new Point(end, point.y));
	}

	static String selectionText(TerminalSelection selection, TerminalTextBuffer buffer) {
		if (selection == null || selection.getEnd() == null || isEmpty(selection)) {
			return "";
		}
		return SelectionUtil.getSelectionText(selection, buffer);
	}

	static boolean isEmpty(TerminalSelection selection) {
		return selection == null || selection.getEnd() == null
				|| compare(selection.getStart(), selection.getEnd()) == 0;
	}

	static int compare(Point first, Point second) {
		if (first.y != second.y) {
			return Integer.compare(first.y, second.y);
		}
		return Integer.compare(first.x, second.x);
	}

	private static boolean isValidRow(int row, TerminalTextBuffer buffer) {
		return row >= -buffer.getHistoryLinesCount() && row < buffer.getHeight();
	}
}
