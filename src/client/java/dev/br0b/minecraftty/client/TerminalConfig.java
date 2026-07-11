package dev.br0b.minecraftty.client;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import dev.br0b.minecraftty.Minecraftty;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;

public final class TerminalConfig {
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
		} catch (IOException e) {
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
		return data.fontScale <= 0.0F ? 1.0F : data.fontScale;
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

	private static final class Data {
		boolean enabledAcknowledgement = false;
		String shell = "";
		float fontScale = 1.0F;
		String closeBehavior = "detach";
	}
}
