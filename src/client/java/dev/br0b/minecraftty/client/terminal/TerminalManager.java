package dev.br0b.minecraftty.client.terminal;

import dev.br0b.minecraftty.client.TerminalConfig;

import java.io.IOException;
import java.util.Locale;

public final class TerminalManager {
	private static TerminalSession session;

	private TerminalManager() {
	}

	public static boolean isLinux() {
		return System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("linux");
	}

	public static synchronized TerminalSession getOrStart(int columns, int rows) throws IOException {
		if (session == null || session.isClosed()) {
			session = TerminalSession.start(TerminalConfig.shell(), columns, rows);
		} else {
			session.resize(columns, rows);
		}
		return session;
	}

	public static synchronized void terminate() {
		if (session != null) {
			session.close();
			session = null;
		}
	}
}
