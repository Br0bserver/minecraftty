package dev.br0b.minecraftty.client.terminal;

import com.mojang.blaze3d.platform.NativeImage;
import dev.br0b.minecraftty.Minecraftty;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;

import java.awt.Color;
import java.awt.FontFormatException;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

final class TerminalGlyphAtlas implements AutoCloseable {
	private static final int ATLAS_SIZE = 2048;
	private static final int PADDING = 1;
	private static final float FONT_SIZE = 11.0F;
	private static final float RASTER_SCALE = 2.0F;
	private static final String FONT_RESOURCE = "/assets/minecraftty/font/jetbrains_mono_nl_nerd_font_mono_regular.ttf";

	private static TerminalGlyphAtlas instance;

	private final java.awt.Font primaryFont;
	private final List<java.awt.Font> fonts;
	private final int cellWidth;
	private final int cellHeight;
	private final int ascent;
	private final NativeImage image;
	private final DynamicTexture texture;
	private final Map<GlyphKey, Glyph> glyphs = new HashMap<>();
	private int cursorX = PADDING;
	private int cursorY = PADDING;
	private int rowHeight = 0;

	private TerminalGlyphAtlas() {
		float rasterFontSize = FONT_SIZE * RASTER_SCALE;
		this.primaryFont = loadFont().deriveFont(java.awt.Font.PLAIN, rasterFontSize);
		this.fonts = List.of(
				primaryFont,
				new java.awt.Font(java.awt.Font.MONOSPACED, java.awt.Font.PLAIN, 1).deriveFont(rasterFontSize),
				new java.awt.Font(java.awt.Font.SANS_SERIF, java.awt.Font.PLAIN, 1).deriveFont(rasterFontSize),
				new java.awt.Font(java.awt.Font.DIALOG, java.awt.Font.PLAIN, 1).deriveFont(rasterFontSize));

		FontMetrics primaryMetrics = metrics(primaryFont);
		this.cellWidth = Math.max(6, Math.round(primaryMetrics.charWidth('W') / RASTER_SCALE));
		this.cellHeight = Math.max(12, Math.round(maxHeight(fonts) / RASTER_SCALE));
		this.ascent = maxAscent(fonts);
		this.image = new NativeImage(ATLAS_SIZE, ATLAS_SIZE, true);
		this.image.fillRect(0, 0, ATLAS_SIZE, ATLAS_SIZE, 0);
		this.texture = new DynamicTexture(() -> "minecraftty-terminal-glyphs", image);
		Minecraft.getInstance().getTextureManager().register(Minecraftty.id("terminal_glyphs"), texture);
	}

	static TerminalGlyphAtlas get() {
		if (instance == null) {
			instance = new TerminalGlyphAtlas();
		}
		return instance;
	}

	int cellWidth() {
		return cellWidth;
	}

	int cellHeight() {
		return cellHeight;
	}

	void draw(net.minecraft.client.gui.GuiGraphicsExtractor graphics, String text, int color, int x, int y, int cells) {
		Glyph glyph = glyph(text, color, Math.max(1, cells));
		graphics.blit(texture.getTextureView(), texture.getSampler(),
				x, y, x + glyph.displayWidth, y + glyph.displayHeight,
				glyph.u0, glyph.u1, glyph.v0, glyph.v1);
	}

	private Glyph glyph(String text, int color, int cells) {
		GlyphKey key = new GlyphKey(text, color, cells);
		Glyph cached = glyphs.get(key);
		if (cached != null) {
			return cached;
		}

		int displayWidth = cellWidth * cells;
		int displayHeight = cellHeight;
		int width = Math.max(1, Math.round(displayWidth * RASTER_SCALE));
		int height = Math.max(1, Math.round(displayHeight * RASTER_SCALE));
		place(width, height);

		BufferedImage glyphImage = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
		Graphics2D graphics = glyphImage.createGraphics();
		graphics.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
		graphics.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS, RenderingHints.VALUE_FRACTIONALMETRICS_OFF);
		graphics.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
		graphics.setFont(fontFor(text));
		graphics.setColor(new Color(color, true));
		graphics.setClip(0, 0, width, height);
		graphics.drawString(text, 0, ascent);
		graphics.dispose();

		for (int py = 0; py < height; py++) {
			for (int px = 0; px < width; px++) {
				int argb = glyphImage.getRGB(px, py);
				if ((argb >>> 24) != 0) {
					image.setPixel(cursorX + px, cursorY + py, argb);
				}
			}
		}
		texture.upload();

		Glyph glyph = new Glyph(displayWidth, displayHeight,
				cursorX / (float) ATLAS_SIZE,
				(cursorX + width) / (float) ATLAS_SIZE,
				cursorY / (float) ATLAS_SIZE,
				(cursorY + height) / (float) ATLAS_SIZE);
		glyphs.put(key, glyph);
		cursorX += width + PADDING;
		rowHeight = Math.max(rowHeight, height);
		return glyph;
	}

	private void place(int width, int height) {
		if (width > ATLAS_SIZE - PADDING * 2 || height > ATLAS_SIZE - PADDING * 2) {
			throw new IllegalStateException("Glyph is too large for atlas");
		}
		if (cursorX + width + PADDING > ATLAS_SIZE) {
			cursorX = PADDING;
			cursorY += rowHeight + PADDING;
			rowHeight = 0;
		}
		if (cursorY + height + PADDING > ATLAS_SIZE) {
			glyphs.clear();
			image.fillRect(0, 0, ATLAS_SIZE, ATLAS_SIZE, 0);
			texture.upload();
			cursorX = PADDING;
			cursorY = PADDING;
			rowHeight = 0;
		}
	}

	private java.awt.Font fontFor(String text) {
		for (java.awt.Font candidate : fonts) {
			if (candidate.canDisplayUpTo(text) == -1) {
				return candidate;
			}
		}
		int firstCodePoint = text.codePointAt(0);
		for (java.awt.Font candidate : fonts) {
			if (candidate.canDisplay(firstCodePoint)) {
				return candidate;
			}
		}
		return fonts.getLast();
	}

	private static FontMetrics metrics(java.awt.Font font) {
		BufferedImage image = new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB);
		Graphics2D graphics = image.createGraphics();
		graphics.setFont(font);
		FontMetrics metrics = graphics.getFontMetrics();
		graphics.dispose();
		return metrics;
	}

	private static int maxAscent(List<java.awt.Font> fonts) {
		int ascent = 0;
		for (java.awt.Font font : fonts) {
			ascent = Math.max(ascent, metrics(font).getAscent());
		}
		return ascent;
	}

	private static int maxHeight(List<java.awt.Font> fonts) {
		int height = 0;
		for (java.awt.Font font : fonts) {
			height = Math.max(height, metrics(font).getHeight());
		}
		return height;
	}

	private static java.awt.Font loadFont() {
		try (InputStream stream = TerminalGlyphAtlas.class.getResourceAsStream(FONT_RESOURCE)) {
			if (stream == null) {
				throw new IllegalStateException("Missing bundled terminal font: " + FONT_RESOURCE);
			}
			return java.awt.Font.createFont(java.awt.Font.TRUETYPE_FONT, stream);
		} catch (FontFormatException | IOException e) {
			throw new IllegalStateException("Failed to load bundled terminal font", e);
		}
	}

	@Override
	public void close() {
		texture.close();
		if (instance == this) {
			instance = null;
		}
	}

	private record GlyphKey(String text, int color, int cells) {
	}

	private record Glyph(int displayWidth, int displayHeight, float u0, float u1, float v0, float v1) {
	}
}
