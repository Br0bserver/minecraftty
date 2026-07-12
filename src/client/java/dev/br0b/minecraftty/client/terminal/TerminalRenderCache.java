package dev.br0b.minecraftty.client.terminal;

import com.jediterm.terminal.TextStyle;
import com.jediterm.terminal.model.CharBuffer;
import com.jediterm.terminal.model.TerminalLine;
import com.jediterm.terminal.model.TerminalLineUtil;
import com.jediterm.terminal.model.TextBufferChangesListener;
import com.jediterm.terminal.util.CharUtils;
import net.minecraft.client.gui.GuiGraphicsExtractor;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

final class TerminalRenderCache implements TextBufferChangesListener {
	private final Map<Integer, CachedLine> lines = new HashMap<>();
	private long cacheHits;
	private long cacheMisses;

	void drawLine(GuiGraphicsExtractor graphics, TerminalGlyphAtlas glyphAtlas, TerminalLine line, int bufferRow,
			int visibleRow, int terminalX, int terminalY, int columns, int charWidth, int charHeight) {
		drawLineBackground(graphics, line, bufferRow, visibleRow, terminalX, terminalY, columns, charWidth, charHeight);
		drawLineForeground(graphics, glyphAtlas, line, bufferRow, visibleRow, terminalX, terminalY, columns,
				charWidth, charHeight);
	}

	void drawLineBackground(GuiGraphicsExtractor graphics, TerminalLine line, int bufferRow, int visibleRow,
			int terminalX, int terminalY, int columns, int charWidth, int charHeight) {
		CachedLine cachedLine = cachedLine(line, bufferRow, columns, charWidth, charHeight);
		int y = terminalY + visibleRow * charHeight;
		for (BackgroundRun run : cachedLine.backgroundRuns) {
			int x = terminalX + run.column * charWidth;
			graphics.fill(x, y, x + run.cells * charWidth, y + charHeight, run.color);
		}
	}

	void drawLineForeground(GuiGraphicsExtractor graphics, TerminalGlyphAtlas glyphAtlas, TerminalLine line,
			int bufferRow, int visibleRow, int terminalX, int terminalY, int columns, int charWidth, int charHeight) {
		CachedLine cachedLine = cachedLine(line, bufferRow, columns, charWidth, charHeight);
		int y = terminalY + visibleRow * charHeight;
		for (UnderlineRun run : cachedLine.underlineRuns) {
			int x = terminalX + run.column * charWidth;
			graphics.fill(x, y + charHeight - 2, x + run.cells * charWidth, y + charHeight - 1, run.color);
		}
		for (GlyphRun run : cachedLine.glyphRuns) {
			glyphAtlas.draw(graphics, run.text, run.color, terminalX + run.column * charWidth, y, run.cells,
					charWidth, charHeight);
		}
	}

	void clear() {
		lines.clear();
	}

	long cacheHits() {
		return cacheHits;
	}

	long cacheMisses() {
		return cacheMisses;
	}

	@Override
	public void linesChanged(int fromIndex) {
		lines.entrySet().removeIf(entry -> fromIndex < 0 ? entry.getKey() <= fromIndex : entry.getKey() >= fromIndex);
	}

	@Override
	public void linesDiscardedFromHistory(List<TerminalLine> discardedLines) {
		clear();
	}

	@Override
	public void historyCleared() {
		clear();
	}

	@Override
	public void widthResized() {
		clear();
	}

	private CachedLine cachedLine(TerminalLine line, int bufferRow, int columns, int charWidth, int charHeight) {
		int modificationCount = TerminalLineUtil.INSTANCE.getModificationCount(line);
		CachedLine cachedLine = lines.get(bufferRow);
		if (cachedLine != null
				&& cachedLine.line == line
				&& cachedLine.modificationCount == modificationCount
				&& cachedLine.columns == columns
				&& cachedLine.charWidth == charWidth
				&& cachedLine.charHeight == charHeight) {
			cacheHits++;
			return cachedLine;
		}
		cacheMisses++;
		CachedLine rebuilt = buildLine(line, modificationCount, columns, charWidth, charHeight);
		lines.put(bufferRow, rebuilt);
		return rebuilt;
	}

	private static CachedLine buildLine(TerminalLine line, int modificationCount, int columns, int charWidth,
			int charHeight) {
		List<BackgroundRun> backgroundRuns = buildBackgroundRuns(line, columns);
		List<UnderlineRun> underlineRuns = buildUnderlineRuns(line, columns);
		List<GlyphRun> glyphRuns = buildGlyphRuns(line, columns);
		return new CachedLine(line, modificationCount, columns, charWidth, charHeight,
				backgroundRuns, underlineRuns, glyphRuns);
	}

	private static List<BackgroundRun> buildBackgroundRuns(TerminalLine line, int columns) {
		List<BackgroundRun> runs = new ArrayList<>();
		int start = -1;
		int currentColor = TerminalScreen.defaultBackground();
		for (int col = 0; col < columns; col++) {
			int color = TerminalScreen.background(line.getStyleAt(col));
			if (color == TerminalScreen.defaultBackground()) {
				if (start >= 0) {
					runs.add(new BackgroundRun(start, col - start, currentColor));
					start = -1;
				}
			} else if (start < 0) {
				start = col;
				currentColor = color;
			} else if (color != currentColor) {
				runs.add(new BackgroundRun(start, col - start, currentColor));
				start = col;
				currentColor = color;
			}
		}
		if (start >= 0) {
			runs.add(new BackgroundRun(start, columns - start, currentColor));
		}
		return runs;
	}

	private static List<UnderlineRun> buildUnderlineRuns(TerminalLine line, int columns) {
		List<UnderlineRun> runs = new ArrayList<>();
		int start = -1;
		int currentColor = 0;
		for (int col = 0; col < columns; col++) {
			TextStyle style = line.getStyleAt(col);
			boolean underlined = style != null && style.hasOption(TextStyle.Option.UNDERLINED);
			if (!underlined) {
				if (start >= 0) {
					runs.add(new UnderlineRun(start, col - start, currentColor));
					start = -1;
				}
				continue;
			}
			int color = TerminalScreen.foreground(style);
			if (start < 0) {
				start = col;
				currentColor = color;
			} else if (color != currentColor) {
				runs.add(new UnderlineRun(start, col - start, currentColor));
				start = col;
				currentColor = color;
			}
		}
		if (start >= 0) {
			runs.add(new UnderlineRun(start, columns - start, currentColor));
		}
		return runs;
	}

	private static List<GlyphRun> buildGlyphRuns(TerminalLine line, int columns) {
		List<GlyphRun> runs = new ArrayList<>();
		int column = 0;
		for (TerminalLine.TextEntry entry : line.getEntries()) {
			if (column >= columns || entry.isNul()) {
				break;
			}
			column = collectGlyphRuns(runs, entry.getText(), entry.getStyle(), column, columns);
		}
		return runs;
	}

	private static int collectGlyphRuns(List<GlyphRun> runs, CharBuffer buffer, TextStyle style, int column,
			int columns) {
		String text = buffer.toString();
		for (int offset = 0; offset < text.length() && column < columns; ) {
			char first = text.charAt(offset);
			if (first == CharUtils.DWC) {
				offset++;
				continue;
			}
			if (first == CharUtils.NUL_CHAR || first == CharUtils.EMPTY_CHAR) {
				offset++;
				column++;
				continue;
			}

			int nextOffset = TerminalCellWidth.nextCluster(text, offset);
			String cluster = text.substring(offset, nextOffset);
			int width = TerminalCellWidth.cells(cluster);
			String displayText = TerminalGlyphSubstitution.displayText(cluster);
			if (width > 0 && !TerminalCellWidth.isBlankCluster(cluster)) {
				runs.add(new GlyphRun(column, width, displayText, TerminalScreen.foreground(style)));
			}
			column += width;
			offset = nextOffset;
		}
		return column;
	}

	private record CachedLine(TerminalLine line, int modificationCount, int columns, int charWidth, int charHeight,
			List<BackgroundRun> backgroundRuns, List<UnderlineRun> underlineRuns, List<GlyphRun> glyphRuns) {
	}

	private record BackgroundRun(int column, int cells, int color) {
	}

	private record UnderlineRun(int column, int cells, int color) {
	}

	private record GlyphRun(int column, int cells, String text, int color) {
	}
}
