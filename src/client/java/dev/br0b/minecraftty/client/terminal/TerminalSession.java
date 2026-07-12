package dev.br0b.minecraftty.client.terminal;

import com.jediterm.core.util.TermSize;
import com.jediterm.terminal.RequestOrigin;
import com.jediterm.terminal.TerminalStarter;
import com.jediterm.terminal.TtyBasedArrayDataStream;
import com.jediterm.terminal.model.JediTerminal;
import com.jediterm.terminal.model.StyleState;
import com.jediterm.terminal.model.TerminalTextBuffer;
import com.pty4j.PtyProcess;
import com.pty4j.PtyProcessBuilder;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

public final class TerminalSession implements AutoCloseable {
	private static final int HISTORY_LINES = 2_000;

	private final PtyProcess process;
	private final PtyTtyConnector connector;
	private final MinecrafttyTerminalDisplay display;
	private final TerminalTextBuffer textBuffer;
	private final JediTerminal terminal;
	private final TerminalStarter starter;
	private final MinecrafttyExecutorServiceManager executorServiceManager;
	private final Thread emulatorThread;
	private volatile boolean closed;

	private TerminalSession(PtyProcess process, PtyTtyConnector connector, MinecrafttyTerminalDisplay display,
							TerminalTextBuffer textBuffer, JediTerminal terminal, TerminalStarter starter,
							MinecrafttyExecutorServiceManager executorServiceManager) {
		this.process = process;
		this.connector = connector;
		this.display = display;
		this.textBuffer = textBuffer;
		this.terminal = terminal;
		this.starter = starter;
		this.executorServiceManager = executorServiceManager;
		this.emulatorThread = new Thread(() -> {
			try {
				starter.start();
			} finally {
				closed = true;
			}
		}, "minecraftty-terminal-emulator");
		this.emulatorThread.setDaemon(true);
		this.emulatorThread.start();
	}

	static TerminalSession start(String shell, int columns, int rows) throws IOException {
		Map<String, String> env = new HashMap<>(System.getenv());
		env.put("TERM", "xterm-256color");
		env.put("COLORTERM", "truecolor");
		String locale = preferredUtf8Locale(env);
		env.put("LANG", locale);
		env.put("LC_CTYPE", locale);
		if (!locale.equalsIgnoreCase(env.get("LC_ALL"))) {
			env.remove("LC_ALL");
		}

		PtyProcess process = new PtyProcessBuilder(new String[]{shell, "-l"})
				.setEnvironment(env)
				.setDirectory(System.getProperty("user.home"))
				.setRedirectErrorStream(true)
				.setInitialColumns(columns)
				.setInitialRows(rows)
				.start();

		PtyTtyConnector connector = new PtyTtyConnector(process);
		MinecrafttyTerminalDisplay display = new MinecrafttyTerminalDisplay();
		StyleState styleState = new StyleState();
		TerminalTextBuffer textBuffer = new TerminalTextBuffer(columns, rows, styleState, HISTORY_LINES);
		JediTerminal terminal = new JediTerminal(display, textBuffer, styleState);
		MinecrafttyExecutorServiceManager executorServiceManager = new MinecrafttyExecutorServiceManager();
		TerminalStarter starter = new TerminalStarter(
				terminal,
				connector,
				new TtyBasedArrayDataStream(connector),
				null,
				executorServiceManager
		);
		terminal.setTerminalOutput(starter);
		return new TerminalSession(process, connector, display, textBuffer, terminal, starter, executorServiceManager);
	}

	private static String preferredUtf8Locale(Map<String, String> env) {
		String locale = firstUtf8Locale(false, env.get("LANG"), env.get("LC_CTYPE"), env.get("LC_ALL"));
		if (locale != null) {
			return locale;
		}
		locale = firstUtf8Locale(true, env.get("LC_ALL"), env.get("LC_CTYPE"), env.get("LANG"));
		return locale == null ? "C.UTF-8" : locale;
	}

	private static String firstUtf8Locale(boolean allowCLocale, String... locales) {
		for (String locale : locales) {
			if (isUtf8Locale(locale) && (allowCLocale || !isCLocale(locale))) {
				return locale;
			}
		}
		return null;
	}

	private static boolean isUtf8Locale(String locale) {
		if (locale == null) {
			return false;
		}
		String normalized = locale.toLowerCase(java.util.Locale.ROOT).replace("-", "");
		return normalized.contains("utf8");
	}

	private static boolean isCLocale(String locale) {
		if (locale == null) {
			return false;
		}
		String normalized = locale.toLowerCase(java.util.Locale.ROOT);
		return normalized.equals("c.utf-8") || normalized.equals("c.utf8");
	}

	public TerminalTextBuffer textBuffer() {
		return textBuffer;
	}

	public JediTerminal terminal() {
		return terminal;
	}

	public MinecrafttyTerminalDisplay display() {
		return display;
	}

	public boolean isClosed() {
		return closed || !process.isRunning();
	}

	public void resize(int columns, int rows) {
		if (isClosed()) {
			return;
		}
		TermSize size = new TermSize(columns, rows);
		starter.postResize(size, RequestOrigin.User);
		connector.resize(size);
	}

	public void write(String text) {
		write(text.getBytes(StandardCharsets.UTF_8));
	}

	public void write(byte[] bytes) {
		if (isClosed()) {
			return;
		}
		starter.sendBytes(bytes, false);
	}

	@Override
	public void close() {
		closed = true;
		starter.close();
		connector.close();
		executorServiceManager.shutdownWhenAllExecuted();
		process.destroy();
	}
}
