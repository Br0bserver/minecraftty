package dev.br0b.minecraftty.client.terminal;

import com.jediterm.core.util.TermSize;
import com.jediterm.terminal.Questioner;
import com.jediterm.terminal.TtyConnector;
import com.pty4j.PtyProcess;
import com.pty4j.WinSize;

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;

final class PtyTtyConnector implements TtyConnector {
	private final PtyProcess process;
	private final InputStreamReader reader;
	private final OutputStream writer;

	PtyTtyConnector(PtyProcess process) {
		this.process = process;
		this.reader = new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8);
		this.writer = process.getOutputStream();
	}

	@Override
	public int read(char[] buf, int offset, int length) throws IOException {
		return reader.read(buf, offset, length);
	}

	@Override
	public void write(byte[] bytes) throws IOException {
		writer.write(bytes);
		writer.flush();
	}

	@Override
	public void write(String string) throws IOException {
		write(string.getBytes(StandardCharsets.UTF_8));
	}

	@Override
	public boolean isConnected() {
		return process.isRunning();
	}

	@Override
	public void resize(TermSize termSize) {
		process.setWinSize(new WinSize(termSize.getColumns(), termSize.getRows()));
	}

	@Override
	public int waitFor() throws InterruptedException {
		return process.waitFor();
	}

	@Override
	public boolean ready() throws IOException {
		return reader.ready();
	}

	@Override
	public String getName() {
		return "minecraftty";
	}

	@Override
	public void close() {
		process.destroy();
	}

	@Override
	public boolean init(Questioner questioner) {
		return true;
	}
}
