package dev.br0b.minecraftty.client.terminal;

import com.jediterm.core.Color;
import com.jediterm.core.util.TermSize;
import com.jediterm.terminal.CursorShape;
import com.jediterm.terminal.RequestOrigin;
import com.jediterm.terminal.TerminalDisplay;
import com.jediterm.terminal.emulator.mouse.MouseFormat;
import com.jediterm.terminal.emulator.mouse.MouseMode;
import com.jediterm.terminal.model.TerminalSelection;

final class MinecrafttyTerminalDisplay implements TerminalDisplay {
	private volatile int cursorX = 1;
	private volatile int cursorY = 1;
	private volatile boolean cursorVisible = true;
	private volatile String windowTitle = "minecraftty";

	@Override
	public void setCursor(int x, int y) {
		cursorX = x;
		cursorY = y;
	}

	@Override
	public void setCursorShape(CursorShape cursorShape) {
	}

	@Override
	public void beep() {
	}

	@Override
	public void onResize(TermSize termSize, RequestOrigin origin) {
	}

	@Override
	public void scrollArea(int scrollRegionTop, int scrollRegionBottom, int dy) {
	}

	@Override
	public void setCursorVisible(boolean visible) {
		cursorVisible = visible;
	}

	@Override
	public void useAlternateScreenBuffer(boolean enabled) {
	}

	@Override
	public String getWindowTitle() {
		return windowTitle;
	}

	@Override
	public void setWindowTitle(String title) {
		windowTitle = title == null || title.isBlank() ? "minecraftty" : title;
	}

	@Override
	public TerminalSelection getSelection() {
		return null;
	}

	@Override
	public void terminalMouseModeSet(MouseMode mouseMode) {
	}

	@Override
	public void setMouseFormat(MouseFormat mouseFormat) {
	}

	@Override
	public boolean ambiguousCharsAreDoubleWidth() {
		return false;
	}

	@Override
	public Color getWindowForeground() {
		return new Color(0xD0, 0xD0, 0xD0);
	}

	@Override
	public Color getWindowBackground() {
		return new Color(0x0B, 0x0D, 0x10);
	}

	int cursorX() {
		return cursorX;
	}

	int cursorY() {
		return cursorY;
	}

	boolean cursorVisible() {
		return cursorVisible;
	}
}
