package dev.br0b.minecraftty.client;

import dev.br0b.minecraftty.Minecraftty;
import dev.br0b.minecraftty.client.terminal.TerminalManager;
import dev.br0b.minecraftty.client.terminal.TerminalScreen;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.minecraft.client.Minecraft;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.gui.screens.AlertScreen;
import net.minecraft.client.gui.screens.ConfirmScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

public final class MinecrafttyClient implements ClientModInitializer {
	private static final KeyMapping.Category CATEGORY = KeyMapping.Category.register(Minecraftty.id("terminal"));
	private static KeyMapping openTerminalKey;
	private static KeyMapping openSettingsKey;

	@Override
	public void onInitializeClient() {
		TerminalConfig.load();
		openTerminalKey = KeyMappingHelper.registerKeyMapping(new KeyMapping(
				"key.minecraftty.open_terminal",
				GLFW.GLFW_KEY_F12,
				CATEGORY
		));
		openSettingsKey = KeyMappingHelper.registerKeyMapping(new KeyMapping(
				"key.minecraftty.open_settings",
				GLFW.GLFW_KEY_F10,
				CATEGORY
		));

		ClientTickEvents.END_CLIENT_TICK.register(client -> {
			while (openTerminalKey.consumeClick()) {
				openTerminal(client);
			}
		});
	}

	public static boolean matchesTerminalKey(KeyEvent event) {
		return openTerminalKey != null && openTerminalKey.matches(event);
	}

	public static boolean matchesSettingsKey(KeyEvent event) {
		return openSettingsKey != null && openSettingsKey.matches(event);
	}

	public static Component terminalKeyName() {
		return openTerminalKey == null ? Component.literal("F12") : openTerminalKey.getTranslatedKeyMessage();
	}

	private static void openTerminal(Minecraft client) {
		if (!TerminalManager.isLinux()) {
			client.setScreen(new AlertScreen(
					() -> client.setScreen(null),
					Component.translatable("screen.minecraftty.unsupported.title"),
					Component.translatable("screen.minecraftty.unsupported.body")
			));
			return;
		}

		if (!TerminalConfig.isAcknowledged()) {
			Screen parent = client.screen;
			client.setScreen(new ConfirmScreen(confirmed -> {
				if (confirmed) {
					TerminalConfig.setAcknowledged(true);
					client.setScreen(new TerminalScreen(parent));
				} else {
					client.setScreen(parent);
				}
			}, Component.translatable("screen.minecraftty.security.title"), Component.translatable("screen.minecraftty.security.body")));
			return;
		}

		Minecraftty.LOGGER.debug("Opening terminal screen");
		client.setScreen(new TerminalScreen(client.screen));
	}
}
