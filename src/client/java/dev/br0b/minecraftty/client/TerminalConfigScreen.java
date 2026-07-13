package dev.br0b.minecraftty.client;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

import java.util.Locale;

public final class TerminalConfigScreen extends Screen {
	private static final int MAX_CONTENT_WIDTH = 420;
	private static final int CONTROL_HEIGHT = 20;
	private static final int ROW_GAP = 8;
	private static final int PANEL_BACKGROUND = 0xE812151C;
	private static final int PANEL_BORDER = 0xFF39414C;

	private final Screen parent;
	private TerminalConfig.Settings draft;
	private EditBox shellBox;

	public TerminalConfigScreen(Screen parent) {
		super(Component.translatable("screen.minecraftty.config.title"));
		this.parent = parent;
		this.draft = TerminalConfig.settings();
	}

	@Override
	protected void init() {
		int contentWidth = Math.min(MAX_CONTENT_WIDTH, Math.max(240, width - 40));
		int left = (width - contentWidth) / 2;
		boolean singleColumn = width < 360;
		int controlWidth = singleColumn ? contentWidth : (contentWidth - 12) / 2;
		int right = singleColumn ? left : left + controlWidth + 12;
		int panelY = (height - panelHeight()) / 2;
		int y = Math.max(panelY + 26, (height - (singleColumn ? 212 : 190)) / 2);

		shellBox = new EditBox(font, left, y + 14, contentWidth, CONTROL_HEIGHT,
				Component.translatable("screen.minecraftty.config.shell"));
		shellBox.setMaxLength(512);
		shellBox.setValue(draft.shell());
		shellBox.setResponder(value -> draft = new TerminalConfig.Settings(value, draft.fontScale(),
				draft.terminalScale(), draft.backgroundOpacity(), draft.inputPrediction()));
		addRenderableWidget(shellBox);

		int controlsY = y + 48;
		addRenderableWidget(new ConfigSlider(left, controlsY, controlWidth,
				"screen.minecraftty.config.font_scale", draft.fontScale(),
				TerminalConfig.MIN_FONT_SCALE, TerminalConfig.MAX_FONT_SCALE,
				value -> draft = new TerminalConfig.Settings(draft.shell(), value, draft.terminalScale(),
						draft.backgroundOpacity(), draft.inputPrediction())));

		int terminalScaleX = singleColumn ? left : right;
		int terminalScaleY = singleColumn ? controlsY + CONTROL_HEIGHT + ROW_GAP : controlsY;
		addRenderableWidget(new ConfigSlider(terminalScaleX, terminalScaleY, controlWidth,
				"screen.minecraftty.config.terminal_size", draft.terminalScale(),
				TerminalConfig.MIN_TERMINAL_SCALE, TerminalConfig.MAX_TERMINAL_SCALE,
				value -> draft = new TerminalConfig.Settings(draft.shell(), draft.fontScale(), value,
						draft.backgroundOpacity(), draft.inputPrediction())));

		int secondRowY = terminalScaleY + CONTROL_HEIGHT + ROW_GAP;
		addRenderableWidget(new ConfigSlider(left, secondRowY, controlWidth,
				"screen.minecraftty.config.opacity", draft.backgroundOpacity(),
				TerminalConfig.MIN_BACKGROUND_OPACITY, TerminalConfig.MAX_BACKGROUND_OPACITY,
				value -> draft = new TerminalConfig.Settings(draft.shell(), draft.fontScale(),
						draft.terminalScale(), value, draft.inputPrediction())));

		int predictionX = singleColumn ? left : right;
		int predictionY = singleColumn ? secondRowY + CONTROL_HEIGHT + ROW_GAP : secondRowY;
		addRenderableWidget(CycleButton.onOffBuilder(draft.inputPrediction()).create(
				predictionX, predictionY, controlWidth, CONTROL_HEIGHT,
				Component.translatable("screen.minecraftty.config.input_prediction"),
				(button, value) -> draft = new TerminalConfig.Settings(draft.shell(), draft.fontScale(),
						draft.terminalScale(), draft.backgroundOpacity(), value)));

		int actionsY = predictionY + CONTROL_HEIGHT + 18;
		int actionWidth = (contentWidth - 16) / 3;
		addRenderableWidget(Button.builder(Component.translatable("screen.minecraftty.config.defaults"),
				button -> resetDefaults()).bounds(left, actionsY, actionWidth, CONTROL_HEIGHT).build());
		addRenderableWidget(Button.builder(CommonComponents.GUI_CANCEL, button -> onClose())
				.bounds(left + actionWidth + 8, actionsY, actionWidth, CONTROL_HEIGHT).build());
		addRenderableWidget(Button.builder(CommonComponents.GUI_DONE, button -> saveAndClose())
				.bounds(left + (actionWidth + 8) * 2, actionsY, actionWidth, CONTROL_HEIGHT).build());
	}

	private void resetDefaults() {
		draft = TerminalConfig.defaults();
		rebuildWidgets();
	}

	private void saveAndClose() {
		TerminalConfig.apply(draft);
		minecraft.setScreen(parent);
	}

	@Override
	public boolean keyPressed(KeyEvent event) {
		if (event.key() == GLFW.GLFW_KEY_F10) {
			saveAndClose();
			return true;
		}
		return super.keyPressed(event);
	}

	@Override
	public void onClose() {
		minecraft.setScreen(parent);
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
		extractTransparentBackground(graphics);
		int panelWidth = Math.min(MAX_CONTENT_WIDTH + 32, width - 16);
		int panelHeight = panelHeight();
		int panelX = (width - panelWidth) / 2;
		int panelY = (height - panelHeight) / 2;
		graphics.fill(panelX, panelY, panelX + panelWidth, panelY + panelHeight, PANEL_BACKGROUND);
		graphics.outline(panelX, panelY, panelWidth, panelHeight, PANEL_BORDER);
		graphics.centeredText(font, title, width / 2, panelY + 14, 0xFFF0F3F6);
		int contentWidth = Math.min(MAX_CONTENT_WIDTH, Math.max(240, width - 40));
		int left = (width - contentWidth) / 2;
		boolean singleColumn = width < 360;
		int y = Math.max(panelY + 26, (height - (singleColumn ? 212 : 190)) / 2);
		graphics.text(font, Component.translatable("screen.minecraftty.config.shell"), left, y, 0xFFAEB7C2);
		if (!singleColumn || height >= 270) {
			graphics.text(font, Component.translatable("screen.minecraftty.config.shell_restart"), left,
					Math.min(height - 22, panelY + panelHeight - 18), 0xFF7D8793);
		}
		super.extractRenderState(graphics, mouseX, mouseY, delta);
	}

	private int panelHeight() {
		return Math.min(width < 360 ? 238 : 210, height - 16);
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}

	private interface FloatConsumer {
		void accept(float value);
	}

	private static final class ConfigSlider extends AbstractSliderButton {
		private final String labelKey;
		private final float min;
		private final float max;
		private final FloatConsumer consumer;

		private ConfigSlider(int x, int y, int width, String labelKey, float current, float min, float max,
				FloatConsumer consumer) {
			super(x, y, width, CONTROL_HEIGHT, Component.empty(), (current - min) / (max - min));
			this.labelKey = labelKey;
			this.min = min;
			this.max = max;
			this.consumer = consumer;
			updateMessage();
		}

		@Override
		protected void updateMessage() {
			setMessage(Component.translatable(labelKey,
					String.format(Locale.ROOT, "%.0f%%", currentValue() * 100.0F)));
		}

		@Override
		protected void applyValue() {
			consumer.accept(currentValue());
		}

		private float currentValue() {
			return min + (float) value * (max - min);
		}
	}
}
