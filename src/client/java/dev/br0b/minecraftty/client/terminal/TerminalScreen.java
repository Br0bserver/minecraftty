package dev.br0b.minecraftty.client.terminal;

import com.jediterm.terminal.TerminalColor;
import com.jediterm.terminal.TextStyle;
import com.jediterm.terminal.model.CharBuffer;
import com.jediterm.terminal.model.TerminalLine;
import com.jediterm.terminal.model.TerminalTextBuffer;
import com.jediterm.terminal.util.CharUtils;
import dev.br0b.minecraftty.Minecraftty;
import dev.br0b.minecraftty.client.TerminalConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import org.lwjgl.glfw.GLFW;

import java.io.IOException;
import java.text.Normalizer;
import java.nio.charset.StandardCharsets;

public final class TerminalScreen extends Screen {
	private static final int SCREEN_DIM = 0xB0000000;
	private static final int PANEL_BACKGROUND = 0xE80D1017;
	private static final int PANEL_BORDER = 0x446E7681;
	private static final int TERMINAL_BACKGROUND = 0xF20A0D12;
	private static final int STATUS_BACKGROUND = 0x70000000;
	private static final int DEFAULT_FOREGROUND = 0xFFE6EDF3;
	private static final int DEFAULT_BACKGROUND = TERMINAL_BACKGROUND;
	private static final int OUTER_MARGIN = 14;
	private static final int PANEL_PADDING = 12;
	private static final int STATUS_GAP = 8;
	private static final int[] ANSI = {
			0xFF1C2128, 0xFFFF6B6B, 0xFF8BD17C, 0xFFF0B45B,
			0xFF6CB6FF, 0xFFD2A8FF, 0xFF56D4DD, 0xFFD0D7DE,
			0xFF6E7681, 0xFFFF8787, 0xFFA6E3A1, 0xFFFFD166,
			0xFF8CCBFF, 0xFFE0B3FF, 0xFF7EE7F2, 0xFFFFFFFF
	};

	private final Screen parent;
	private TerminalSession session;
	private TerminalGlyphAtlas glyphAtlas;
	private int columns = 80;
	private int rows = 24;
	private int charWidth = 8;
	private int charHeight = 14;
	private int panelX;
	private int panelY;
	private int panelWidth;
	private int panelHeight;
	private int terminalX;
	private int terminalY;
	private int terminalWidth;
	private int terminalHeight;
	private int statusY;
	private String startupError;

	public TerminalScreen(Screen parent) {
		super(Component.literal("minecraftty"));
		this.parent = parent;
	}

	@Override
	protected void init() {
		glyphAtlas = TerminalGlyphAtlas.get();
		recalculateTerminalSize();
		try {
			session = TerminalManager.getOrStart(columns, rows);
			startupError = null;
		} catch (IOException e) {
			Minecraftty.LOGGER.error("Failed to start terminal", e);
			startupError = e.getMessage();
		}
	}

	@Override
	public void resize(int width, int height) {
		super.resize(width, height);
		recalculateTerminalSize();
		if (session != null) {
			session.resize(columns, rows);
		}
	}

	private void recalculateTerminalSize() {
		float fontScale = Math.max(0.75F, Math.min(1.6F, TerminalConfig.fontScale()));
		TerminalGlyphAtlas atlas = TerminalGlyphAtlas.get();
		charWidth = Math.max(6, Math.round(atlas.cellWidth() * fontScale));
		charHeight = Math.max(12, Math.round(atlas.cellHeight() * fontScale));
		panelX = OUTER_MARGIN;
		panelY = OUTER_MARGIN;
		panelWidth = Math.max(1, this.width - OUTER_MARGIN * 2);
		panelHeight = Math.max(1, this.height - OUTER_MARGIN * 2);
		statusY = panelY + panelHeight - PANEL_PADDING - this.font.lineHeight;
		terminalX = panelX + PANEL_PADDING;
		terminalY = panelY + PANEL_PADDING;
		terminalWidth = Math.max(1, panelWidth - PANEL_PADDING * 2);
		terminalHeight = Math.max(1, statusY - STATUS_GAP - terminalY);
		columns = Math.max(20, terminalWidth / charWidth);
		rows = Math.max(5, terminalHeight / charHeight);
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
		graphics.fill(0, 0, width, height, SCREEN_DIM);
		graphics.fill(panelX, panelY, panelX + panelWidth, panelY + panelHeight, PANEL_BACKGROUND);
		graphics.outline(panelX, panelY, panelWidth, panelHeight, PANEL_BORDER);
		graphics.fill(terminalX - 1, terminalY - 1,
				terminalX + columns * charWidth + 1,
				terminalY + rows * charHeight + 1,
				TERMINAL_BACKGROUND);

		if (startupError != null) {
			graphics.text(font, "Failed to start shell: " + startupError, terminalX, terminalY, 0xFFFF7777);
			return;
		}

		if (session == null) {
			glyphAtlas.draw(graphics, "Starting terminal...", DEFAULT_FOREGROUND, terminalX, terminalY, 20,
					charWidth, charHeight);
			return;
		}

		drawTerminal(graphics);
		graphics.fill(panelX + 1, statusY - 4, panelX + panelWidth - 1, panelY + panelHeight - 1, STATUS_BACKGROUND);
		graphics.text(font, statusComponent(), terminalX, statusY, 0xFF87909B);
		if (session.isClosed()) {
			glyphAtlas.draw(graphics, Component.translatable("screen.minecraftty.terminal.closed").getString(), 0xFFFFCC66,
					terminalX, Math.max(terminalY, statusY - charHeight - 8), columns, charWidth, charHeight);
		}
	}

	private void drawTerminal(GuiGraphicsExtractor graphics) {
		TerminalTextBuffer buffer = session.textBuffer();
		buffer.lock();
		try {
			for (int row = 0; row < rows; row++) {
				drawCellStyles(graphics, buffer, row);
				drawLineText(graphics, buffer.getLine(row), row);
			}

			if (session.display().cursorVisible() && !session.isClosed()) {
				int cursorCol = Math.max(0, Math.min(columns - 1, session.terminal().getCursorX() - 1));
				int cursorRow = Math.max(0, Math.min(rows - 1, session.terminal().getCursorY() - 1));
				int x = terminalX + cursorCol * charWidth;
				int y = terminalY + cursorRow * charHeight;
				graphics.fill(x, y, x + charWidth, y + charHeight, 0x55FFFFFF);
				graphics.outline(x, y, charWidth, charHeight, 0xDDE6EDF3);
			}
		} finally {
			buffer.unlock();
		}
	}

	private void drawCellStyles(GuiGraphicsExtractor graphics, TerminalTextBuffer buffer, int row) {
		int y = terminalY + row * charHeight;
		for (int col = 0; col < columns; col++) {
			TextStyle style = buffer.getStyleAt(col, row);
			int fg = foreground(style);
			int bg = background(style);
			int x = terminalX + col * charWidth;
			if (bg != DEFAULT_BACKGROUND) {
				graphics.fill(x, y, x + charWidth, y + charHeight, bg);
			}
			if (style != null && style.hasOption(TextStyle.Option.UNDERLINED)) {
				graphics.fill(x, y + charHeight - 2, x + charWidth, y + charHeight - 1, fg);
			}
		}
	}

	private void drawLineText(GuiGraphicsExtractor graphics, TerminalLine line, int row) {
		int column = 0;
		for (TerminalLine.TextEntry entry : line.getEntries()) {
			if (column >= columns || entry.isNul()) {
				break;
			}
			column = drawTextEntry(graphics, entry.getText(), entry.getStyle(), row, column);
		}
	}

	private int drawTextEntry(GuiGraphicsExtractor graphics, CharBuffer buffer, TextStyle style, int row, int column) {
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

			int nextOffset = nextTextCluster(text, offset);
			String cluster = text.substring(offset, nextOffset);
			int width = terminalCellWidth(cluster);
			if (width > 0 && !isBlankCluster(cluster)) {
				glyphAtlas.draw(graphics, TerminalGlyphSubstitution.displayText(cluster), foreground(style),
						terminalX + column * charWidth, terminalY + row * charHeight, width,
						charWidth, charHeight);
			}
			column += width;
			offset = nextOffset;
		}
		return column;
	}

	private static int nextTextCluster(String text, int offset) {
		int next = offset + Character.charCount(text.codePointAt(offset));
		while (next < text.length()) {
			int codePoint = text.codePointAt(next);
			if (isCombiningOrVariation(codePoint)) {
				next += Character.charCount(codePoint);
				continue;
			}
			if (codePoint == 0x200D) {
				next += Character.charCount(codePoint);
				if (next < text.length()) {
					next += Character.charCount(text.codePointAt(next));
				}
				continue;
			}
			if (isRegionalIndicator(text.codePointAt(offset)) && isRegionalIndicator(codePoint)) {
				next += Character.charCount(codePoint);
			}
			break;
		}
		return next;
	}

	private static int terminalCellWidth(String text) {
		if (isEmojiPresentationCluster(text)) {
			return 2;
		}
		int width = 0;
		boolean hasJoinerSequence = false;
		boolean hasRegionalIndicatorPair = false;
		int regionalIndicators = 0;
		for (int offset = 0; offset < text.length(); ) {
			int codePoint = text.codePointAt(offset);
			if (codePoint == 0x200D) {
				hasJoinerSequence = true;
			} else if (isRegionalIndicator(codePoint)) {
				regionalIndicators++;
				hasRegionalIndicatorPair = regionalIndicators >= 2;
				width += 1;
			} else {
				width += terminalCellWidth(codePoint);
			}
			offset += Character.charCount(codePoint);
		}
		if ((hasJoinerSequence || hasRegionalIndicatorPair) && width > 0) {
			return 2;
		}
		return width;
	}

	private static boolean isEmojiPresentationCluster(String text) {
		for (int offset = 0; offset < text.length(); ) {
			int codePoint = text.codePointAt(offset);
			if ((codePoint >= 0x1F000 && codePoint <= 0x1FAFF)
					|| (codePoint >= 0xFE00 && codePoint <= 0xFE0F)
					|| codePoint == 0x200D) {
				return true;
			}
			offset += Character.charCount(codePoint);
		}
		return false;
	}

	private static int terminalCellWidth(int codePoint) {
		if (isCombiningOrVariation(codePoint)) {
			return 0;
		}
		return CharUtils.isDoubleWidthCharacter(codePoint, false) ? 2 : 1;
	}

	private static boolean isBlankCluster(String text) {
		for (int offset = 0; offset < text.length(); ) {
			int codePoint = text.codePointAt(offset);
			if (!Character.isWhitespace(codePoint) && !isCombiningOrVariation(codePoint)) {
				return false;
			}
			offset += Character.charCount(codePoint);
		}
		return true;
	}

	private static boolean isCombiningOrVariation(int codePoint) {
		int type = Character.getType(codePoint);
		return type == Character.NON_SPACING_MARK
				|| type == Character.COMBINING_SPACING_MARK
				|| type == Character.ENCLOSING_MARK
				|| (codePoint >= 0xFE00 && codePoint <= 0xFE0F)
				|| (codePoint >= 0xE0100 && codePoint <= 0xE01EF);
	}

	private static boolean isRegionalIndicator(int codePoint) {
		return codePoint >= 0x1F1E6 && codePoint <= 0x1F1FF;
	}

	private static int foreground(TextStyle style) {
		if (style == null) {
			return DEFAULT_FOREGROUND;
		}
		if (style.hasOption(TextStyle.Option.INVERSE)) {
			return backgroundColor(style.getBackground(), DEFAULT_BACKGROUND);
		}
		int color = color(style.getForeground(), DEFAULT_FOREGROUND);
		if (style.hasOption(TextStyle.Option.DIM)) {
			return dim(color);
		}
		if (style.hasOption(TextStyle.Option.BOLD)) {
			return brighten(color);
		}
		return color;
	}

	private static int background(TextStyle style) {
		if (style == null) {
			return DEFAULT_BACKGROUND;
		}
		if (style.hasOption(TextStyle.Option.INVERSE)) {
			return color(style.getForeground(), DEFAULT_FOREGROUND);
		}
		return backgroundColor(style.getBackground(), DEFAULT_BACKGROUND);
	}

	private static int backgroundColor(TerminalColor terminalColor, int fallback) {
		return color(terminalColor, fallback);
	}

	private static int color(TerminalColor terminalColor, int fallback) {
		if (terminalColor == null) {
			return fallback;
		}
		if (terminalColor.isIndexed()) {
			int index = terminalColor.getColorIndex();
			if (index >= 0 && index < ANSI.length) {
				return ANSI[index];
			}
		}
		com.jediterm.core.Color color = terminalColor.toColor();
		return 0xFF000000 | (color.getRed() << 16) | (color.getGreen() << 8) | color.getBlue();
	}

	private static int dim(int color) {
		int r = ((color >> 16) & 0xFF) / 2;
		int g = ((color >> 8) & 0xFF) / 2;
		int b = (color & 0xFF) / 2;
		return 0xFF000000 | (r << 16) | (g << 8) | b;
	}

	private static int brighten(int color) {
		int r = Math.min(255, (int) (((color >> 16) & 0xFF) * 1.2F));
		int g = Math.min(255, (int) (((color >> 8) & 0xFF) * 1.2F));
		int b = Math.min(255, (int) ((color & 0xFF) * 1.2F));
		return 0xFF000000 | (r << 16) | (g << 8) | b;
	}

	@Override
	public boolean keyPressed(KeyEvent event) {
		int keyCode = event.key();
		int modifiers = event.modifiers();
		if (keyCode == GLFW.GLFW_KEY_F12) {
			Minecraft.getInstance().setScreen(parent);
			return true;
		}
		if (session == null || session.isClosed()) {
			return super.keyPressed(event);
		}

		if (isPasteShortcut(keyCode, modifiers)) {
			pasteClipboard();
			return true;
		}

		byte[] encoded = encodeKey(keyCode, modifiers);
		if (encoded != null) {
			session.write(encoded);
			return true;
		}
		return super.keyPressed(event);
	}

	private void pasteClipboard() {
		String clipboard = Minecraft.getInstance().keyboardHandler.getClipboard();
		if (clipboard == null || clipboard.isEmpty()) {
			return;
		}
		String text = normalizePastedText(clipboard);
		if (session.display().bracketedPasteMode()) {
			session.write("\u001B[200~" + text + "\u001B[201~");
		} else {
			session.write(text.replace('\n', '\r'));
		}
	}

	private static boolean isPasteShortcut(int keyCode, int modifiers) {
		boolean ctrl = (modifiers & GLFW.GLFW_MOD_CONTROL) != 0;
		boolean shift = (modifiers & GLFW.GLFW_MOD_SHIFT) != 0;
		boolean superKey = (modifiers & GLFW.GLFW_MOD_SUPER) != 0;
		return (keyCode == GLFW.GLFW_KEY_V && (superKey || (ctrl && shift)))
				|| (keyCode == GLFW.GLFW_KEY_INSERT && shift);
	}

	private static String normalizePastedText(String text) {
		return Normalizer.normalize(text.replace("\r\n", "\n").replace('\r', '\n'), Normalizer.Form.NFC);
	}

	@Override
	public boolean charTyped(CharacterEvent event) {
		if (session == null || session.isClosed()) {
			return super.charTyped(event);
		}
		String typed = event.codepointAsString();
		if (typed.isEmpty()) {
			return true;
		}
		int chr = typed.codePointAt(0);
		long window = Minecraft.getInstance().getWindow().handle();
		int modifiers = GLFW.glfwGetKey(window, GLFW.GLFW_KEY_LEFT_CONTROL) == GLFW.GLFW_PRESS
				|| GLFW.glfwGetKey(window, GLFW.GLFW_KEY_RIGHT_CONTROL) == GLFW.GLFW_PRESS
				? GLFW.GLFW_MOD_CONTROL : 0;
		if ((modifiers & GLFW.GLFW_MOD_CONTROL) != 0) {
			return true;
		}
		session.write(typed);
		return true;
	}

	private byte[] encodeKey(int keyCode, int modifiers) {
		boolean ctrl = (modifiers & GLFW.GLFW_MOD_CONTROL) != 0;
		if (ctrl) {
			if (keyCode >= GLFW.GLFW_KEY_A && keyCode <= GLFW.GLFW_KEY_Z) {
				return new byte[]{(byte) (keyCode - GLFW.GLFW_KEY_A + 1)};
			}
			if (keyCode == GLFW.GLFW_KEY_LEFT_BRACKET) {
				return bytes("\u001B");
			}
		}

		return switch (keyCode) {
			case GLFW.GLFW_KEY_ESCAPE -> bytes("\u001B");
			case GLFW.GLFW_KEY_ENTER, GLFW.GLFW_KEY_KP_ENTER -> bytes("\r");
			case GLFW.GLFW_KEY_BACKSPACE -> new byte[]{0x7F};
			case GLFW.GLFW_KEY_TAB -> bytes("\t");
			case GLFW.GLFW_KEY_UP -> bytes("\u001B[A");
			case GLFW.GLFW_KEY_DOWN -> bytes("\u001B[B");
			case GLFW.GLFW_KEY_RIGHT -> bytes("\u001B[C");
			case GLFW.GLFW_KEY_LEFT -> bytes("\u001B[D");
			case GLFW.GLFW_KEY_HOME -> bytes("\u001B[H");
			case GLFW.GLFW_KEY_END -> bytes("\u001B[F");
			case GLFW.GLFW_KEY_PAGE_UP -> bytes("\u001B[5~");
			case GLFW.GLFW_KEY_PAGE_DOWN -> bytes("\u001B[6~");
			case GLFW.GLFW_KEY_INSERT -> bytes("\u001B[2~");
			case GLFW.GLFW_KEY_DELETE -> bytes("\u001B[3~");
			default -> null;
		};
	}

	private static byte[] bytes(String text) {
		return text.getBytes(StandardCharsets.UTF_8);
	}

	private static MutableComponent statusComponent() {
		return Component.translatable("screen.minecraftty.terminal.status").withStyle(Style.EMPTY.withoutShadow());
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}
}
