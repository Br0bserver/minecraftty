package dev.br0b.minecraftty.client.terminal;

import com.jediterm.terminal.TerminalExecutorServiceManager;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;

final class MinecrafttyExecutorServiceManager implements TerminalExecutorServiceManager {
	private final ScheduledExecutorService scheduled = Executors.newSingleThreadScheduledExecutor(r -> {
		Thread thread = new Thread(r, "minecraftty-terminal-scheduled");
		thread.setDaemon(true);
		return thread;
	});
	private final ExecutorService unbounded = Executors.newCachedThreadPool(r -> {
		Thread thread = new Thread(r, "minecraftty-terminal-worker");
		thread.setDaemon(true);
		return thread;
	});

	@Override
	public ScheduledExecutorService getSingleThreadScheduledExecutor() {
		return scheduled;
	}

	@Override
	public ExecutorService getUnboundedExecutorService() {
		return unbounded;
	}

	@Override
	public void shutdownWhenAllExecuted() {
		scheduled.shutdown();
		unbounded.shutdown();
	}
}
