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
	private String pendingText = "";
	private char pendingHighSurrogate = 0;

	PtyTtyConnector(PtyProcess process) {
		this.process = process;
		this.reader = new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8);
		this.writer = process.getOutputStream();
	}

	@Override
	public int read(char[] buf, int offset, int length) throws IOException {
		if (pendingText.isEmpty()) {
			char[] readBuffer = new char[Math.max(1, length)];
			int read = reader.read(readBuffer, 0, readBuffer.length);
			if (read < 0) {
				return read;
			}
			pendingText = TerminalGlyphSubstitution.modelText(completeSurrogatePairs(readBuffer, read));
		}
		int copied = Math.min(length, pendingText.length());
		pendingText.getChars(0, copied, buf, offset);
		pendingText = pendingText.substring(copied);
		return copied;
	}

	private String completeSurrogatePairs(char[] readBuffer, int read) {
		StringBuilder builder = new StringBuilder(read + (pendingHighSurrogate == 0 ? 0 : 1));
		if (pendingHighSurrogate != 0) {
			builder.append(pendingHighSurrogate);
			pendingHighSurrogate = 0;
		}
		if (read > 0 && Character.isHighSurrogate(readBuffer[read - 1])) {
			pendingHighSurrogate = readBuffer[read - 1];
			read--;
		}
		builder.append(readBuffer, 0, read);
		return builder.toString();
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
