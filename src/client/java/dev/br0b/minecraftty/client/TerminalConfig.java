package dev.br0b.minecraftty.client;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParseException;
import dev.br0b.minecraftty.Minecraftty;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;

public final class TerminalConfig {
	public static final float MIN_FONT_SCALE = 0.75F;
	public static final float MAX_FONT_SCALE = 1.60F;
	public static final float MIN_TERMINAL_SCALE = 0.60F;
	public static final float MAX_TERMINAL_SCALE = 1.00F;
	public static final float MIN_BACKGROUND_OPACITY = 0.35F;
	public static final float MAX_BACKGROUND_OPACITY = 1.00F;

	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
	private static final Path PATH = FabricLoader.getInstance().getConfigDir().resolve("minecraftty.json");
	private static Data data = new Data();

	private TerminalConfig() {
	}

	public static void load() {
		if (!Files.exists(PATH)) {
			save();
			return;
		}

		try (Reader reader = Files.newBufferedReader(PATH)) {
			Data loaded = GSON.fromJson(reader, Data.class);
			data = loaded == null ? new Data() : loaded;
			normalize();
		} catch (IOException | JsonParseException e) {
			Minecraftty.LOGGER.warn("Failed to read config {}, using defaults", PATH, e);
			data = new Data();
		}
	}

	public static boolean isAcknowledged() {
		return data.enabledAcknowledgement;
	}

	public static void setAcknowledged(boolean acknowledged) {
		data.enabledAcknowledgement = acknowledged;
		save();
	}

	public static String shell() {
		if (data.shell != null && !data.shell.isBlank()) {
			return data.shell;
		}
		String envShell = System.getenv("SHELL");
		return envShell == null || envShell.isBlank() ? "/bin/bash" : envShell;
	}

	public static float fontScale() {
		return data.fontScale;
	}

	public static float terminalScale() {
		return data.terminalScale;
	}

	public static float backgroundOpacity() {
		return data.backgroundOpacity;
	}

	public static boolean inputPrediction() {
		return data.inputPrediction;
	}

	public static Settings settings() {
		return new Settings(shell(), data.fontScale, data.terminalScale, data.backgroundOpacity, data.inputPrediction);
	}

	public static void apply(Settings settings) {
		Settings normalized = settings.normalized();
		data.shell = normalized.shell();
		data.fontScale = normalized.fontScale();
		data.terminalScale = normalized.terminalScale();
		data.backgroundOpacity = normalized.backgroundOpacity();
		data.inputPrediction = normalized.inputPrediction();
		save();
	}

	public static Settings defaults() {
		String envShell = System.getenv("SHELL");
		String shell = envShell == null || envShell.isBlank() ? "/bin/bash" : envShell;
		return new Settings(shell, 1.0F, 1.0F, 0.95F, true);
	}

	private static void normalize() {
		Settings normalized = new Settings(shell(), data.fontScale, data.terminalScale,
				data.backgroundOpacity, data.inputPrediction).normalized();
		data.shell = normalized.shell();
		data.fontScale = normalized.fontScale();
		data.terminalScale = normalized.terminalScale();
		data.backgroundOpacity = normalized.backgroundOpacity();
		data.inputPrediction = normalized.inputPrediction();
	}

	private static void save() {
		try {
			Files.createDirectories(PATH.getParent());
			try (Writer writer = Files.newBufferedWriter(PATH)) {
				GSON.toJson(data, writer);
			}
		} catch (IOException e) {
			Minecraftty.LOGGER.warn("Failed to write config {}", PATH, e);
		}
	}

	public record Settings(String shell, float fontScale, float terminalScale, float backgroundOpacity,
			boolean inputPrediction) {
		public Settings normalized() {
			String normalizedShell = shell == null || shell.isBlank() ? defaults().shell() : shell.trim();
			return new Settings(normalizedShell,
					clamp(fontScale, MIN_FONT_SCALE, MAX_FONT_SCALE, 1.0F),
					clamp(terminalScale, MIN_TERMINAL_SCALE, MAX_TERMINAL_SCALE, 1.0F),
					clamp(backgroundOpacity, MIN_BACKGROUND_OPACITY, MAX_BACKGROUND_OPACITY, 0.95F),
					inputPrediction);
		}

		private static float clamp(float value, float min, float max, float fallback) {
			return Float.isFinite(value) ? Math.max(min, Math.min(max, value)) : fallback;
		}
	}

	private static final class Data {
		boolean enabledAcknowledgement = false;
		String shell = "";
		float fontScale = 1.0F;
		float terminalScale = 1.0F;
		float backgroundOpacity = 0.95F;
		boolean inputPrediction = true;
		String closeBehavior = "detach";
	}
}
