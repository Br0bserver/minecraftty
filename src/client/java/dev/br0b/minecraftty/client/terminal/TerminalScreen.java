package dev.br0b.minecraftty.client.terminal;

import com.jediterm.core.compatibility.Point;
import com.jediterm.terminal.TerminalColor;
import com.jediterm.terminal.TextStyle;
import com.jediterm.terminal.emulator.mouse.MouseButtonCodes;
import com.jediterm.terminal.emulator.mouse.MouseButtonModifierFlags;
import com.jediterm.terminal.emulator.mouse.MouseFormat;
import com.jediterm.terminal.emulator.mouse.MouseMode;
import com.jediterm.terminal.model.TerminalLine;
import com.jediterm.terminal.model.TerminalSelection;
import com.jediterm.terminal.model.TerminalTextBuffer;
import dev.br0b.minecraftty.Minecraftty;
import dev.br0b.minecraftty.client.TerminalConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import org.lwjgl.glfw.GLFW;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.text.Normalizer;

public final class TerminalScreen extends Screen {
	private static final int SCREEN_DIM = 0xB0000000;
	private static final int PANEL_BACKGROUND = 0xE80D1017;
	private static final int PANEL_BORDER = 0x446E7681;
	private static final int TERMINAL_BACKGROUND = 0xF20A0D12;
	private static final int STATUS_BACKGROUND = 0x70000000;
	private static final int DEFAULT_FOREGROUND = 0xFFE6EDF3;
	private static final int DEFAULT_BACKGROUND = TERMINAL_BACKGROUND;
	private static final int PREDICTION_FOREGROUND = 0xFF6E7681;
	private static final int SELECTION_BACKGROUND = 0x663B82F6;
	private static final int OUTER_MARGIN = 14;
	private static final int PANEL_PADDING = 12;
	private static final int STATUS_GAP = 8;
	private static final int SCROLLBACK_WHEEL_LINES = 3;
	private static final int SELECTION_SCROLL_EDGE_ROWS = 1;
	private static final long CLICK_CHAIN_MS = 500L;
	private static final long COPY_STATUS_MS = 1_500L;
	private static final int[] ANSI = {
			0xFF1C2128, 0xFFFF6B6B, 0xFF8BD17C, 0xFFF0B45B,
			0xFF6CB6FF, 0xFFD2A8FF, 0xFF56D4DD, 0xFFD0D7DE,
			0xFF6E7681, 0xFFFF8787, 0xFFA6E3A1, 0xFFFFD166,
			0xFF8CCBFF, 0xFFE0B3FF, 0xFF7EE7F2, 0xFFFFFFFF
	};

	private final Screen parent;
	private final TerminalRenderCache renderCache = new TerminalRenderCache();
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
	private int scrollbackOffset;
	private boolean selecting;
	private Point selectionAnchor;
	private Point lastSelectionClickPoint;
	private long lastSelectionClickTimeMs;
	private int selectionClickCount;
	private long copyStatusUntilMs;
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
			session.textBuffer().addChangesListener(renderCache);
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
		clampScrollbackOffset();
		renderCache.clear();
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
			glyphAtlas.flushUploads();
			return;
		}

		clampScrollbackOffset();
		drawTerminal(graphics);
		graphics.fill(panelX + 1, statusY - 4, panelX + panelWidth - 1, panelY + panelHeight - 1, STATUS_BACKGROUND);
		graphics.text(font, statusComponent(), terminalX, statusY, 0xFF87909B);
		if (session.isClosed()) {
			glyphAtlas.draw(graphics, Component.translatable("screen.minecraftty.terminal.closed").getString(), 0xFFFFCC66,
					terminalX, Math.max(terminalY, statusY - charHeight - 8), columns, charWidth, charHeight);
		}
		glyphAtlas.flushUploads();
	}

	private void drawTerminal(GuiGraphicsExtractor graphics) {
		TerminalTextBuffer buffer = session.textBuffer();
		buffer.lock();
		try {
			for (int row = 0; row < rows; row++) {
				int bufferRow = bufferRow(row);
				TerminalLine line = buffer.getLine(bufferRow);
				renderCache.drawLineBackground(graphics, line, bufferRow, row, terminalX, terminalY, columns,
						charWidth, charHeight);
			}
			drawSelection(graphics);
			for (int row = 0; row < rows; row++) {
				int bufferRow = bufferRow(row);
				TerminalLine line = buffer.getLine(bufferRow);
				renderCache.drawLineForeground(graphics, glyphAtlas, line, bufferRow, row, terminalX, terminalY,
						columns, charWidth, charHeight);
			}

			if (scrollbackOffset == 0 && session.display().cursorVisible() && !session.isClosed()) {
				TerminalInputPrediction.State actual = session.inputPrediction().actualState();
				TerminalInputPrediction.Overlay overlay = session.inputPrediction().overlay(actual);
				drawPrediction(graphics, overlay);
				drawCursor(graphics, actual, overlay);
			}
		} finally {
			buffer.unlock();
		}
	}

	private void drawSelection(GuiGraphicsExtractor graphics) {
		TerminalSelection selection = session.display().getSelection();
		if (selection == null || selection.getEnd() == null) {
			return;
		}

		Point start = new Point(selection.getStart());
		Point end = new Point(selection.getEnd());
		if (TerminalSelectionUtil.compare(start, end) == 0) {
			return;
		}
		if (TerminalSelectionUtil.compare(start, end) > 0) {
			Point swap = start;
			start = end;
			end = swap;
		}

		for (int visibleRow = 0; visibleRow < rows; visibleRow++) {
			int row = bufferRow(visibleRow);
			if (row < start.y || row > end.y) {
				continue;
			}
			int firstColumn = row == start.y ? start.x : 0;
			int endColumn = row == end.y ? end.x : columns;
			firstColumn = Math.max(0, Math.min(columns, firstColumn));
			endColumn = Math.max(0, Math.min(columns, endColumn));
			if (endColumn <= firstColumn) {
				continue;
			}
			int x = terminalX + firstColumn * charWidth;
			int y = terminalY + visibleRow * charHeight;
			graphics.fill(x, y, terminalX + endColumn * charWidth, y + charHeight, SELECTION_BACKGROUND);
		}
	}

	private void drawPrediction(GuiGraphicsExtractor graphics, TerminalInputPrediction.Overlay overlay) {
		if (!overlay.visible() || overlay.text().isEmpty() || overlay.row() < 0 || overlay.row() >= rows) {
			return;
		}
		int column = Math.max(0, Math.min(columns - 1, overlay.textColumn()));
		String text = overlay.text();
		int cells = Math.min(text.length(), columns - column);
		if (cells <= 0) {
			return;
		}
		if (cells < text.length()) {
			text = text.substring(0, cells);
		}
		glyphAtlas.draw(graphics, text, PREDICTION_FOREGROUND, terminalX + column * charWidth,
				terminalY + overlay.row() * charHeight, cells, charWidth, charHeight);
	}

	private void drawCursor(GuiGraphicsExtractor graphics, TerminalInputPrediction.State actual,
			TerminalInputPrediction.Overlay overlay) {
		int cursorCol = overlay.visible() ? overlay.cursorX() : actual.cursorX();
		int cursorRow = actual.cursorY() - 1;
		cursorCol = Math.max(0, Math.min(columns - 1, cursorCol));
		cursorRow = Math.max(0, Math.min(rows - 1, cursorRow));
		int x = terminalX + cursorCol * charWidth;
		int y = terminalY + cursorRow * charHeight;
		graphics.fill(x, y, x + charWidth, y + charHeight, 0x55FFFFFF);
		graphics.outline(x, y, charWidth, charHeight, 0xDDE6EDF3);
	}

	private int bufferRow(int visibleRow) {
		return visibleRow - scrollbackOffset;
	}

	static int foreground(TextStyle style) {
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

	static int background(TextStyle style) {
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

	static int defaultBackground() {
		return DEFAULT_BACKGROUND;
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

		if (isCopyShortcut(keyCode, modifiers)) {
			copySelection();
			return true;
		}

		if (isPasteShortcut(keyCode, modifiers)) {
			clearSelection();
			scrollbackOffset = 0;
			pasteClipboard();
			return true;
		}

		byte[] encoded = encodeKey(keyCode, modifiers);
		if (encoded != null) {
			if (isScrollbackShortcut(keyCode, modifiers)) {
				scrollScrollback(keyCode == GLFW.GLFW_KEY_PAGE_UP ? rows - 1 : -(rows - 1));
				return true;
			}
			clearSelection();
			scrollbackOffset = 0;
			session.writeUserInput(encoded);
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
			session.writeRaw("\u001B[200~" + text + "\u001B[201~");
		} else {
			session.writeRaw(text.replace('\n', '\r'));
		}
	}

	private static boolean isPasteShortcut(int keyCode, int modifiers) {
		boolean ctrl = (modifiers & GLFW.GLFW_MOD_CONTROL) != 0;
		boolean shift = (modifiers & GLFW.GLFW_MOD_SHIFT) != 0;
		boolean superKey = (modifiers & GLFW.GLFW_MOD_SUPER) != 0;
		return (keyCode == GLFW.GLFW_KEY_V && (superKey || (ctrl && shift)))
				|| (keyCode == GLFW.GLFW_KEY_INSERT && shift);
	}

	private static boolean isCopyShortcut(int keyCode, int modifiers) {
		boolean ctrl = (modifiers & GLFW.GLFW_MOD_CONTROL) != 0;
		boolean shift = (modifiers & GLFW.GLFW_MOD_SHIFT) != 0;
		return ctrl && ((shift && keyCode == GLFW.GLFW_KEY_C) || keyCode == GLFW.GLFW_KEY_INSERT);
	}

	private void copySelection() {
		TerminalTextBuffer buffer = session.textBuffer();
		buffer.lock();
		try {
			String text = TerminalSelectionUtil.selectionText(session.display().getSelection(), buffer);
			if (!text.isEmpty()) {
				Minecraft.getInstance().keyboardHandler.setClipboard(text);
				showCopyStatus();
			}
		} finally {
			buffer.unlock();
		}
	}

	private void showCopyStatus() {
		copyStatusUntilMs = System.currentTimeMillis() + COPY_STATUS_MS;
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
		clearSelection();
		scrollbackOffset = 0;
		session.writeUserInput(bytes(typed));
		return true;
	}

	@Override
	public void mouseMoved(double mouseX, double mouseY) {
		if (!selecting && session != null && !session.isClosed()
				&& session.display().sendsMouseReports()
				&& isInsideTerminal(mouseX, mouseY)) {
			sendMouseMotion(terminalColumn(mouseX), terminalRow(mouseY), MouseButtonCodes.RELEASE,
					terminalMouseModifiers(currentGlfwModifiers()));
		}
		super.mouseMoved(mouseX, mouseY);
	}

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
		if (canStartSelection(event)) {
			Point point = selectionPoint(event.x(), event.y(), false);
			int clickCount = selectionClickCount(point, doubleClick);
			if (clickCount >= 3) {
				selectLineAt(point);
			} else if (clickCount == 2) {
				selectWordAt(point);
			} else {
				startSelection(point);
			}
			return true;
		}
		if (!canSendMouseEvent(event.x(), event.y())) {
			clearSelection();
			return super.mouseClicked(event, doubleClick);
		}
		int button = terminalMouseButton(event.button());
		if (button == MouseButtonCodes.NONE) {
			return super.mouseClicked(event, doubleClick);
		}
		setDragging(true);
		sendMouseButton(terminalColumn(event.x()), terminalRow(event.y()), button,
				terminalMouseModifiers(event.modifiers()), true);
		return true;
	}

	@Override
	public boolean mouseReleased(MouseButtonEvent event) {
		if (selecting) {
			updateSelection(event.x(), event.y());
			selecting = false;
			if (isEmptySelection()) {
				clearSelection();
			}
			setDragging(false);
			return true;
		}
		if (!canSendMouseEvent(event.x(), event.y())) {
			setDragging(false);
			return super.mouseReleased(event);
		}
		int button = terminalMouseButton(event.button());
		if (button == MouseButtonCodes.NONE) {
			setDragging(false);
			return super.mouseReleased(event);
		}
		sendMouseButton(terminalColumn(event.x()), terminalRow(event.y()), button,
				terminalMouseModifiers(event.modifiers()), false);
		setDragging(false);
		return true;
	}

	@Override
	public boolean mouseDragged(MouseButtonEvent event, double dragX, double dragY) {
		if (selecting) {
			updateSelection(event.x(), event.y());
			return true;
		}
		if (!canSendMouseEvent(event.x(), event.y())) {
			return super.mouseDragged(event, dragX, dragY);
		}
		int button = terminalMouseButton(event.button());
		if (button == MouseButtonCodes.NONE) {
			return super.mouseDragged(event, dragX, dragY);
		}
		sendMouseMotion(terminalColumn(event.x()), terminalRow(event.y()), button, terminalMouseModifiers(event.modifiers()));
		return true;
	}

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
		if (verticalAmount == 0.0D) {
			return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
		}
		if (canSendMouseEvent(mouseX, mouseY)) {
			int button = verticalAmount > 0.0D ? MouseButtonCodes.SCROLLUP : MouseButtonCodes.SCROLLDOWN;
			sendMouseWheel(terminalColumn(mouseX), terminalRow(mouseY), button, terminalMouseModifiers(currentGlfwModifiers()));
			return true;
		}
		if (canScrollBack(mouseX, mouseY)) {
			scrollScrollback(verticalAmount > 0.0D ? SCROLLBACK_WHEEL_LINES : -SCROLLBACK_WHEEL_LINES);
			return true;
		}
		return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
	}

	private boolean canScrollBack(double mouseX, double mouseY) {
		return session != null && !session.isClosed()
				&& !session.display().alternateScreenBuffer()
				&& isInsideTerminal(mouseX, mouseY);
	}

	private boolean canStartSelection(MouseButtonEvent event) {
		return session != null && !session.isClosed()
				&& event.button() == GLFW.GLFW_MOUSE_BUTTON_LEFT
				&& isInsideTerminal(event.x(), event.y())
				&& (!session.display().sendsMouseReports() || (event.modifiers() & GLFW.GLFW_MOD_SHIFT) != 0);
	}

	private void startSelection(Point point) {
		selectionAnchor = point;
		session.display().setSelection(new TerminalSelection(selectionAnchor, new Point(selectionAnchor)));
		selecting = true;
		setDragging(true);
	}

	private int selectionClickCount(Point point, boolean doubleClick) {
		long now = System.currentTimeMillis();
		boolean sameCell = lastSelectionClickPoint != null && lastSelectionClickPoint.equals(point);
		if (!sameCell || now - lastSelectionClickTimeMs > CLICK_CHAIN_MS) {
			selectionClickCount = 1;
		} else {
			selectionClickCount++;
		}
		if (doubleClick && selectionClickCount < 2) {
			selectionClickCount = 2;
		}
		selectionClickCount = Math.min(selectionClickCount, 3);
		lastSelectionClickPoint = new Point(point);
		lastSelectionClickTimeMs = now;
		return selectionClickCount;
	}

	private void selectWordAt(Point point) {
		TerminalTextBuffer buffer = session.textBuffer();
		buffer.lock();
		try {
			TerminalSelection selection = TerminalSelectionUtil.wordSelection(point, buffer);
			if (selection == null) {
				clearSelection();
			} else {
				session.display().setSelection(selection);
			}
		} finally {
			buffer.unlock();
		}
		selecting = false;
		setDragging(false);
	}

	private void selectLineAt(Point point) {
		TerminalTextBuffer buffer = session.textBuffer();
		buffer.lock();
		try {
			TerminalSelection selection = TerminalSelectionUtil.lineSelection(point, buffer);
			if (selection == null) {
				clearSelection();
			} else {
				session.display().setSelection(selection);
			}
		} finally {
			buffer.unlock();
		}
		selecting = false;
		setDragging(false);
	}

	private void updateSelection(double mouseX, double mouseY) {
		if (selectionAnchor == null) {
			return;
		}
		scrollSelectionAtEdge(mouseY);
		Point end = selectionPoint(mouseX, mouseY, true);
		session.display().setSelection(new TerminalSelection(selectionAnchor, end));
	}

	private boolean isEmptySelection() {
		return TerminalSelectionUtil.isEmpty(session.display().getSelection());
	}

	private void clearSelection() {
		selecting = false;
		selectionAnchor = null;
		if (session != null) {
			session.display().clearSelection();
		}
	}

	private Point selectionPoint(double mouseX, double mouseY, boolean endpoint) {
		int visibleRow = terminalRow(mouseY);
		int row = bufferRow(visibleRow);
		int column = terminalColumn(mouseX);
		if (!endpoint || selectionAnchor == null) {
			return new Point(column, row);
		}
		boolean beforeAnchor = row < selectionAnchor.y || (row == selectionAnchor.y && column < selectionAnchor.x);
		int boundaryColumn = beforeAnchor ? column : Math.min(columns, column + 1);
		return new Point(boundaryColumn, row);
	}

	private void scrollSelectionAtEdge(double mouseY) {
		if (session == null || session.display().alternateScreenBuffer()) {
			return;
		}
		int topEdge = terminalY + SELECTION_SCROLL_EDGE_ROWS * charHeight;
		int bottomEdge = terminalY + (rows - SELECTION_SCROLL_EDGE_ROWS) * charHeight;
		if (mouseY < topEdge) {
			scrollScrollback(1);
		} else if (mouseY >= bottomEdge) {
			scrollScrollback(-1);
		}
	}

	private void scrollScrollback(int lines) {
		scrollbackOffset += lines;
		clampScrollbackOffset();
	}

	private void clampScrollbackOffset() {
		if (session == null || session.display().alternateScreenBuffer()) {
			scrollbackOffset = 0;
			return;
		}
		int maxOffset = Math.max(0, session.textBuffer().getHistoryLinesCount());
		scrollbackOffset = Math.max(0, Math.min(maxOffset, scrollbackOffset));
	}

	private static boolean isScrollbackShortcut(int keyCode, int modifiers) {
		return (modifiers & GLFW.GLFW_MOD_SHIFT) != 0
				&& (keyCode == GLFW.GLFW_KEY_PAGE_UP || keyCode == GLFW.GLFW_KEY_PAGE_DOWN);
	}

	private boolean canSendMouseEvent(double mouseX, double mouseY) {
		return session != null && !session.isClosed()
				&& session.display().sendsMouseReports()
				&& isInsideTerminal(mouseX, mouseY);
	}

	private void sendMouseButton(int column, int row, int button, int modifiers, boolean pressed) {
		MinecrafttyTerminalDisplay display = session.display();
		if (pressed) {
			sendMouseReport(button | modifiers, column, row, false);
			return;
		}
		if (display.mouseFormat() == MouseFormat.MOUSE_FORMAT_SGR) {
			sendMouseReport(button | modifiers, column, row, true);
		} else {
			sendMouseReport(MouseButtonCodes.RELEASE | modifiers, column, row, false);
		}
	}

	private void sendMouseMotion(int column, int row, int button, int modifiers) {
		MouseMode mode = session.display().mouseMode();
		if (mode == MouseMode.MOUSE_REPORTING_ALL_MOTION) {
			sendMouseReport((button == MouseButtonCodes.RELEASE ? MouseButtonCodes.RELEASE : button)
					| MouseButtonModifierFlags.MOUSE_BUTTON_MOTION_FLAG | modifiers, column, row, false);
		} else if (mode == MouseMode.MOUSE_REPORTING_BUTTON_MOTION && button != MouseButtonCodes.RELEASE) {
			sendMouseReport(button | MouseButtonModifierFlags.MOUSE_BUTTON_MOTION_FLAG | modifiers, column, row, false);
		}
	}

	private void sendMouseWheel(int column, int row, int button, int modifiers) {
		int wheelButton = (button - MouseButtonCodes.SCROLLDOWN)
				| MouseButtonModifierFlags.MOUSE_BUTTON_SCROLL_FLAG
				| modifiers;
		sendMouseReport(wheelButton, column, row, false);
	}

	private void sendMouseReport(int button, int column, int row, boolean sgrRelease) {
		int x = column + 1;
		int y = row + 1;
		MouseFormat format = session.display().mouseFormat();
		byte[] report = switch (format) {
			case MOUSE_FORMAT_SGR -> bytes("\u001B[<" + button + ";" + x + ";" + y + (sgrRelease ? "m" : "M"));
			case MOUSE_FORMAT_URXVT -> bytes("\u001B[" + (32 + button) + ";" + x + ";" + y + "M");
			case MOUSE_FORMAT_XTERM_EXT -> mouseReportBytes("\u001B[M", button, x, y, StandardCharsets.UTF_8);
			case MOUSE_FORMAT_XTERM -> mouseReportBytes("\u001B[M", button, x, y, StandardCharsets.ISO_8859_1);
		};
		session.writeRaw(report);
	}

	private static byte[] mouseReportBytes(String prefix, int button, int x, int y, java.nio.charset.Charset charset) {
		return (prefix + (char) (32 + button) + (char) (32 + x) + (char) (32 + y)).getBytes(charset);
	}

	private boolean isInsideTerminal(double mouseX, double mouseY) {
		return mouseX >= terminalX && mouseX < terminalX + columns * charWidth
				&& mouseY >= terminalY && mouseY < terminalY + rows * charHeight;
	}

	private int terminalColumn(double mouseX) {
		return Math.max(0, Math.min(columns - 1, (int) ((mouseX - terminalX) / charWidth)));
	}

	private int terminalRow(double mouseY) {
		return Math.max(0, Math.min(rows - 1, (int) ((mouseY - terminalY) / charHeight)));
	}

	private static int terminalMouseButton(int button) {
		return switch (button) {
			case GLFW.GLFW_MOUSE_BUTTON_LEFT -> MouseButtonCodes.LEFT;
			case GLFW.GLFW_MOUSE_BUTTON_RIGHT -> MouseButtonCodes.RIGHT;
			case GLFW.GLFW_MOUSE_BUTTON_MIDDLE -> MouseButtonCodes.MIDDLE;
			default -> MouseButtonCodes.NONE;
		};
	}

	private static int terminalMouseModifiers(int modifiers) {
		int terminalModifiers = 0;
		if ((modifiers & GLFW.GLFW_MOD_SHIFT) != 0) {
			terminalModifiers |= MouseButtonModifierFlags.MOUSE_BUTTON_SHIFT_FLAG;
		}
		if ((modifiers & GLFW.GLFW_MOD_SUPER) != 0 || (modifiers & GLFW.GLFW_MOD_ALT) != 0) {
			terminalModifiers |= MouseButtonModifierFlags.MOUSE_BUTTON_META_FLAG;
		}
		if ((modifiers & GLFW.GLFW_MOD_CONTROL) != 0) {
			terminalModifiers |= MouseButtonModifierFlags.MOUSE_BUTTON_CTRL_FLAG;
		}
		return terminalModifiers;
	}

	private static int currentGlfwModifiers() {
		long window = Minecraft.getInstance().getWindow().handle();
		int modifiers = 0;
		if (isKeyPressed(window, GLFW.GLFW_KEY_LEFT_SHIFT) || isKeyPressed(window, GLFW.GLFW_KEY_RIGHT_SHIFT)) {
			modifiers |= GLFW.GLFW_MOD_SHIFT;
		}
		if (isKeyPressed(window, GLFW.GLFW_KEY_LEFT_ALT) || isKeyPressed(window, GLFW.GLFW_KEY_RIGHT_ALT)) {
			modifiers |= GLFW.GLFW_MOD_ALT;
		}
		if (isKeyPressed(window, GLFW.GLFW_KEY_LEFT_SUPER) || isKeyPressed(window, GLFW.GLFW_KEY_RIGHT_SUPER)) {
			modifiers |= GLFW.GLFW_MOD_SUPER;
		}
		if (isKeyPressed(window, GLFW.GLFW_KEY_LEFT_CONTROL) || isKeyPressed(window, GLFW.GLFW_KEY_RIGHT_CONTROL)) {
			modifiers |= GLFW.GLFW_MOD_CONTROL;
		}
		return modifiers;
	}

	private static boolean isKeyPressed(long window, int key) {
		return GLFW.glfwGetKey(window, key) == GLFW.GLFW_PRESS;
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

	private MutableComponent statusComponent() {
		if (session == null) {
			return Component.translatable("screen.minecraftty.terminal.status").withStyle(Style.EMPTY.withoutShadow());
		}
		String status = Component.translatable("screen.minecraftty.terminal.status").getString();
		if (scrollbackOffset > 0) {
			status += " | history " + scrollbackOffset + "/" + session.textBuffer().getHistoryLinesCount();
		}
		if (System.currentTimeMillis() < copyStatusUntilMs) {
			status += " | Copied";
		}
		return Component.literal(status).withStyle(Style.EMPTY.withoutShadow());
	}

	@Override
	public void removed() {
		if (session != null) {
			session.textBuffer().removeChangesListener(renderCache);
		}
		super.removed();
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}
}
