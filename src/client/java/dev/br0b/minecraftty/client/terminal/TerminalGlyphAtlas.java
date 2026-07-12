package dev.br0b.minecraftty.client.terminal;

import com.mojang.blaze3d.platform.NativeImage;
import dev.br0b.minecraftty.Minecraftty;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import org.lwjgl.PointerBuffer;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.util.freetype.FT_Bitmap;
import org.lwjgl.util.freetype.FT_Bitmap_Size;
import org.lwjgl.util.freetype.FT_Face;
import org.lwjgl.util.freetype.FT_GlyphSlot;
import org.lwjgl.util.freetype.FreeType;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Polygon;
import java.awt.FontFormatException;
import java.awt.FontMetrics;
import java.awt.Font;
import java.awt.font.FontRenderContext;
import java.awt.font.GlyphVector;
import java.awt.geom.Ellipse2D;
import java.awt.geom.QuadCurve2D;
import java.awt.geom.Rectangle2D;
import java.awt.BasicStroke;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

final class TerminalGlyphAtlas implements AutoCloseable {
	private static final int ATLAS_SIZE = 2048;
	private static final int PADDING = 1;
	private static final float FONT_SIZE = 11.0F;
	private static final float RASTER_SCALE = 2.0F;
	private static final String FONT_RESOURCE = "/assets/minecraftty/font/jetbrains_mono_nl_nerd_font_mono_regular.ttf";
	private static final String SYSTEM_EMOJI_FONT = "/usr/share/fonts/noto/NotoColorEmoji.ttf";
	private static final int EMOJI_PIXEL_SIZE = 109;

	private static TerminalGlyphAtlas instance;

	private final java.awt.Font primaryFont;
	private final List<java.awt.Font> fonts;
	private final EmojiRenderer emojiRenderer;
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
		this.emojiRenderer = EmojiRenderer.create(rasterFontSize);

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

	void draw(net.minecraft.client.gui.GuiGraphicsExtractor graphics, String text, int color, int x, int y,
			int cells, int targetCellWidth, int targetCellHeight) {
		Glyph glyph = glyph(text, color, Math.max(1, cells), targetCellWidth, targetCellHeight);
		graphics.blit(texture.getTextureView(), texture.getSampler(),
				x, y, x + glyph.displayWidth, y + glyph.displayHeight,
				glyph.u0, glyph.u1, glyph.v0, glyph.v1);
	}

	private Glyph glyph(String text, int color, int cells, int targetCellWidth, int targetCellHeight) {
		int displayWidth = Math.max(1, targetCellWidth) * cells;
		int displayHeight = Math.max(1, targetCellHeight);
		GlyphKind kind = glyphKind(text);
		GlyphKey key = new GlyphKey(text, color, cells, displayWidth, displayHeight, kind, emojiRenderer.canRender(text));
		Glyph cached = glyphs.get(key);
		if (cached != null) {
			return cached;
		}

		int width = Math.max(1, Math.round(displayWidth * RASTER_SCALE));
		int height = Math.max(1, Math.round(displayHeight * RASTER_SCALE));
		place(width, height);

		if (kind == GlyphKind.POWERLINE) {
			renderPowerlineGlyph(text.codePointAt(0), color, cursorX, cursorY, width, height);
		} else if (kind == GlyphKind.BUILTIN) {
			renderBuiltinGlyph(text.codePointAt(0), color, cursorX, cursorY, width, height);
		} else if (!emojiRenderer.render(text, cursorX, cursorY, width, height, image)) {
			renderTextGlyph(text, color, cursorX, cursorY, width, height);
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

	private void renderTextGlyph(String text, int color, int atlasX, int atlasY, int width, int height) {
		BufferedImage glyphImage = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
		Graphics2D graphics = glyphImage.createGraphics();
		graphics.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
		graphics.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS, RenderingHints.VALUE_FRACTIONALMETRICS_ON);
		graphics.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
		Font font = fontFor(text);
		graphics.setFont(font);
		graphics.setColor(new Color(color, true));
		graphics.setClip(0, 0, width, height);

		Rectangle2D visualBounds = font.createGlyphVector(graphics.getFontRenderContext(), text).getVisualBounds();
		int textX = isCellFillFriendly(text) ? (int) Math.floor(-visualBounds.getX()) : 0;
		int textY = Math.max(0, Math.min(height - 1, ascent));
		graphics.drawString(text, textX, textY);
		graphics.dispose();

		for (int py = 0; py < height; py++) {
			for (int px = 0; px < width; px++) {
				int argb = glyphImage.getRGB(px, py);
				if ((argb >>> 24) != 0) {
					image.setPixel(atlasX + px, atlasY + py, argb);
				}
			}
		}
	}

	private void renderPowerlineGlyph(int codePoint, int color, int atlasX, int atlasY, int width, int height) {
		BufferedImage glyphImage = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
		Graphics2D graphics = glyphImage.createGraphics();
		graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
		graphics.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
		graphics.setColor(new Color(color, true));

		switch (codePoint) {
			case 0xE0B0 -> graphics.fillPolygon(new Polygon(
					new int[] {0, width, 0}, new int[] {0, height / 2, height}, 3));
			case 0xE0B2 -> graphics.fillPolygon(new Polygon(
					new int[] {width, 0, width}, new int[] {0, height / 2, height}, 3));
			case 0xE0B4 -> graphics.fill(new Ellipse2D.Float(-width, 0, width * 2.0F, height));
			case 0xE0B6 -> graphics.fill(new Ellipse2D.Float(0, 0, width * 2.0F, height));
			default -> {
				graphics.dispose();
				renderTextGlyph(new String(Character.toChars(codePoint)), color, atlasX, atlasY, width, height);
				return;
			}
		}
		graphics.dispose();

		for (int py = 0; py < height; py++) {
			for (int px = 0; px < width; px++) {
				int argb = glyphImage.getRGB(px, py);
				if ((argb >>> 24) != 0) {
					image.setPixel(atlasX + px, atlasY + py, argb);
				}
			}
		}
	}

	private void renderBuiltinGlyph(int codePoint, int color, int atlasX, int atlasY, int width, int height) {
		BufferedImage glyphImage = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
		Graphics2D graphics = glyphImage.createGraphics();
		graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_OFF);
		graphics.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
		graphics.setColor(new Color(color, true));

		int stroke = Math.max(1, Math.round(width / 8.0F));
		int heavyStroke = Math.max(stroke + 1, stroke * 2);
		int midX = width / 2;
		int midY = height / 2;

		switch (codePoint) {
			case 0x2500 -> fillHorizontal(graphics, 0, midY - stroke / 2, width, stroke);
			case 0x2501 -> fillHorizontal(graphics, 0, midY - stroke, width, stroke * 2);
			case 0x2502 -> fillVertical(graphics, midX - stroke / 2, 0, stroke, height);
			case 0x2503 -> fillVertical(graphics, midX - stroke, 0, stroke * 2, height);
			case 0x250c -> {
				fillHorizontal(graphics, midX, midY - stroke / 2, width - midX, stroke);
				fillVertical(graphics, midX - stroke / 2, midY, stroke, height - midY);
			}
			case 0x2510 -> {
				fillHorizontal(graphics, 0, midY - stroke / 2, midX, stroke);
				fillVertical(graphics, midX - stroke / 2, midY, stroke, height - midY);
			}
			case 0x2514 -> {
				fillHorizontal(graphics, midX, midY - stroke / 2, width - midX, stroke);
				fillVertical(graphics, midX - stroke / 2, 0, stroke, midY);
			}
			case 0x2518 -> {
				fillHorizontal(graphics, 0, midY - stroke / 2, midX, stroke);
				fillVertical(graphics, midX - stroke / 2, 0, stroke, midY);
			}
			case 0x251c -> {
				fillHorizontal(graphics, midX, midY - stroke / 2, width - midX, stroke);
				fillVertical(graphics, midX - stroke / 2, 0, stroke, height);
			}
			case 0x2524 -> {
				fillHorizontal(graphics, 0, midY - stroke / 2, midX, stroke);
				fillVertical(graphics, midX - stroke / 2, 0, stroke, height);
			}
			case 0x252c -> {
				fillHorizontal(graphics, 0, midY - stroke / 2, width, stroke);
				fillVertical(graphics, midX - stroke / 2, midY, stroke, height - midY);
			}
			case 0x2534 -> {
				fillHorizontal(graphics, 0, midY - stroke / 2, width, stroke);
				fillVertical(graphics, midX - stroke / 2, 0, stroke, midY);
			}
			case 0x253c -> {
				fillHorizontal(graphics, 0, midY - stroke / 2, width, stroke);
				fillVertical(graphics, midX - stroke / 2, 0, stroke, height);
			}
			case 0x2574 -> fillHorizontal(graphics, 0, midY - stroke / 2, midX, stroke);
			case 0x2576 -> fillHorizontal(graphics, midX, midY - stroke / 2, width - midX, stroke);
			case 0x256D -> drawRoundedCorner(graphics, RoundedCorner.TOP_LEFT, width, height, stroke);
			case 0x256E -> drawRoundedCorner(graphics, RoundedCorner.TOP_RIGHT, width, height, stroke);
			case 0x256F -> drawRoundedCorner(graphics, RoundedCorner.BOTTOM_RIGHT, width, height, stroke);
			case 0x2570 -> drawRoundedCorner(graphics, RoundedCorner.BOTTOM_LEFT, width, height, stroke);
			case 0x2575 -> fillVertical(graphics, midX - stroke / 2, 0, stroke, midY);
			case 0x2577 -> fillVertical(graphics, midX - stroke / 2, midY, stroke, height - midY);
			case 0x2578 -> fillHorizontal(graphics, 0, midY - heavyStroke / 2, midX, heavyStroke);
			case 0x2579 -> fillVertical(graphics, midX - heavyStroke / 2, 0, heavyStroke, midY);
			case 0x257A -> fillHorizontal(graphics, midX, midY - heavyStroke / 2, width - midX, heavyStroke);
			case 0x257B -> fillVertical(graphics, midX - heavyStroke / 2, midY, heavyStroke, height - midY);
			case 0x257C -> {
				fillHorizontal(graphics, 0, midY - stroke / 2, midX, stroke);
				fillHorizontal(graphics, midX, midY - heavyStroke / 2, width - midX, heavyStroke);
			}
			case 0x257D -> {
				fillVertical(graphics, midX - stroke / 2, 0, stroke, midY);
				fillVertical(graphics, midX - heavyStroke / 2, midY, heavyStroke, height - midY);
			}
			case 0x257E -> {
				fillHorizontal(graphics, 0, midY - heavyStroke / 2, midX, heavyStroke);
				fillHorizontal(graphics, midX, midY - stroke / 2, width - midX, stroke);
			}
			case 0x257F -> {
				fillVertical(graphics, midX - heavyStroke / 2, 0, heavyStroke, midY);
				fillVertical(graphics, midX - stroke / 2, midY, stroke, height - midY);
			}
			case 0x2571 -> drawDiagonal(graphics, width, height, true, stroke);
			case 0x2572 -> drawDiagonal(graphics, width, height, false, stroke);
			case 0x2573 -> {
				drawDiagonal(graphics, width, height, true, stroke);
				drawDiagonal(graphics, width, height, false, stroke);
			}
			case 0x2580 -> fillRect(graphics, 0, 0, width, height / 2);
			case 0x2581 -> fillRect(graphics, 0, height - Math.max(1, height / 8), width, Math.max(1, height / 8));
			case 0x2582 -> fillRect(graphics, 0, height - Math.max(1, height / 4), width, Math.max(1, height / 4));
			case 0x2583 -> fillRect(graphics, 0, height - Math.max(1, (height * 3) / 8), width, Math.max(1, (height * 3) / 8));
			case 0x2584 -> fillRect(graphics, 0, height / 2, width, height - height / 2);
			case 0x2585 -> fillRect(graphics, 0, height - Math.max(1, (height * 5) / 8), width, Math.max(1, (height * 5) / 8));
			case 0x2586 -> fillRect(graphics, 0, height - Math.max(1, (height * 6) / 8), width, Math.max(1, (height * 6) / 8));
			case 0x2587 -> fillRect(graphics, 0, height - Math.max(1, (height * 7) / 8), width, Math.max(1, (height * 7) / 8));
			case 0x2588 -> fillRect(graphics, 0, 0, width, height);
			case 0x2589 -> fillRect(graphics, 0, 0, Math.max(1, (width * 7) / 8), height);
			case 0x258A -> fillRect(graphics, 0, 0, Math.max(1, (width * 6) / 8), height);
			case 0x258B -> fillRect(graphics, 0, 0, Math.max(1, (width * 5) / 8), height);
			case 0x258C -> fillRect(graphics, 0, 0, Math.max(1, (width * 4) / 8), height);
			case 0x258D -> fillRect(graphics, 0, 0, Math.max(1, (width * 3) / 8), height);
			case 0x258E -> fillRect(graphics, 0, 0, Math.max(1, (width * 2) / 8), height);
			case 0x258F -> fillRect(graphics, 0, 0, Math.max(1, width / 8), height);
			case 0x2590 -> fillRect(graphics, width / 2, 0, width - width / 2, height);
			case 0x2591 -> fillRect(graphics, 0, 0, width, height, 0x40);
			case 0x2592 -> fillRect(graphics, 0, 0, width, height, 0x80);
			case 0x2593 -> fillRect(graphics, 0, 0, width, height, 0xC0);
			case 0x2594 -> fillRect(graphics, 0, 0, width, Math.max(1, height / 8));
			case 0x2595 -> fillRect(graphics, width - Math.max(1, width / 8), 0, Math.max(1, width / 8), height);
			case 0x2596 -> fillRect(graphics, 0, height / 2, width / 2, height / 2);
			case 0x2597 -> fillRect(graphics, width / 2, height / 2, width - width / 2, height - height / 2);
			case 0x2598 -> fillRect(graphics, width / 2, 0, width - width / 2, height / 2);
			case 0x2599 -> {
				fillRect(graphics, 0, height / 2, width / 2, height / 2);
				fillRect(graphics, width / 2, 0, width - width / 2, height / 2);
				fillRect(graphics, 0, 0, width / 2, height / 2);
			}
			case 0x259A -> {
				fillRect(graphics, width / 2, 0, width - width / 2, height / 2);
				fillRect(graphics, 0, height / 2, width / 2, height / 2);
			}
			case 0x259B -> {
				fillRect(graphics, 0, 0, width / 2, height / 2);
				fillRect(graphics, width / 2, 0, width - width / 2, height / 2);
				fillRect(graphics, 0, height / 2, width / 2, height / 2);
			}
			case 0x259C -> {
				fillRect(graphics, width / 2, 0, width - width / 2, height / 2);
				fillRect(graphics, width / 2, height / 2, width - width / 2, height / 2);
				fillRect(graphics, 0, 0, width / 2, height / 2);
			}
			case 0x259D -> fillRect(graphics, 0, 0, width / 2, height / 2);
			case 0x259E -> {
				fillRect(graphics, 0, 0, width / 2, height / 2);
				fillRect(graphics, width / 2, height / 2, width - width / 2, height - height / 2);
			}
			case 0x259F -> {
				fillRect(graphics, 0, 0, width / 2, height / 2);
				fillRect(graphics, 0, height / 2, width / 2, height - height / 2);
				fillRect(graphics, width / 2, height / 2, width - width / 2, height - height / 2);
			}
			case 0x2504, 0x2505, 0x2506, 0x2507, 0x2508, 0x2509, 0x250A, 0x250B, 0x254C, 0x254D, 0x254E, 0x254F ->
				renderDottedLine(graphics, codePoint, width, height, stroke);
			default -> {
				graphics.dispose();
				renderTextGlyph(new String(Character.toChars(codePoint)), color, atlasX, atlasY, width, height);
				return;
			}
		}

		graphics.dispose();
		copyImageToAtlas(glyphImage, atlasX, atlasY, width, height);
	}

	private static void fillRect(Graphics2D graphics, int x, int y, int width, int height) {
		if (width > 0 && height > 0) {
			graphics.fillRect(x, y, width, height);
		}
	}

	private static void fillRect(Graphics2D graphics, int x, int y, int width, int height, int alpha) {
		if (width <= 0 || height <= 0) {
			return;
		}
		Color color = graphics.getColor();
		graphics.setColor(new Color(color.getRed(), color.getGreen(), color.getBlue(), alpha));
		graphics.fillRect(x, y, width, height);
		graphics.setColor(color);
	}

	private static void fillHorizontal(Graphics2D graphics, int x, int y, int width, int thickness) {
		fillRect(graphics, x, y, width, thickness);
	}

	private static void fillVertical(Graphics2D graphics, int x, int y, int thickness, int height) {
		fillRect(graphics, x, y, thickness, height);
	}

	private static void drawDiagonal(Graphics2D graphics, int width, int height, boolean slash, int stroke) {
		for (int i = 0; i < stroke; i++) {
			if (slash) {
				graphics.drawLine(i, height - 1, width - 1, i);
			} else {
				graphics.drawLine(i, 0, width - 1, height - 1 - i);
			}
		}
	}

	private static void drawRoundedCorner(Graphics2D graphics, RoundedCorner corner, int width, int height, int stroke) {
		graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
		graphics.setStroke(new BasicStroke(stroke, BasicStroke.CAP_BUTT, BasicStroke.JOIN_ROUND));

		float midX = width / 2.0F;
		float midY = height / 2.0F;
		QuadCurve2D.Float curve = switch (corner) {
			case TOP_LEFT -> new QuadCurve2D.Float(midX, height, midX, midY, width, midY);
			case TOP_RIGHT -> new QuadCurve2D.Float(0, midY, midX, midY, midX, height);
			case BOTTOM_RIGHT -> new QuadCurve2D.Float(0, midY, midX, midY, midX, 0);
			case BOTTOM_LEFT -> new QuadCurve2D.Float(midX, 0, midX, midY, width, midY);
		};
		graphics.draw(curve);
	}

	private enum RoundedCorner {
		TOP_LEFT,
		TOP_RIGHT,
		BOTTOM_RIGHT,
		BOTTOM_LEFT
	}

	private static void renderDottedLine(Graphics2D graphics, int codePoint, int width, int height, int stroke) {
		boolean horizontal = codePoint == 0x2504 || codePoint == 0x2505 || codePoint == 0x2508 || codePoint == 0x2509 || codePoint == 0x254C || codePoint == 0x254D;
		int count = switch (codePoint) {
			case 0x2504, 0x2505 -> 2;
			case 0x2508, 0x2509 -> 3;
			case 0x254C, 0x254D, 0x254E, 0x254F -> 1;
			default -> 2;
		};
		int line = horizontal ? Math.max(1, height / 2 - stroke / 2) : Math.max(1, width / 2 - stroke / 2);
		int gap = horizontal ? Math.max(1, width / (count * 2 + 1)) : Math.max(1, height / (count * 2 + 1));
		for (int i = 0; i <= count; i++) {
			if (horizontal) {
				int x = Math.min(width, i * gap * 2);
				fillRect(graphics, x, line, Math.max(1, gap), stroke);
			} else {
				int y = Math.min(height, i * gap * 2);
				fillRect(graphics, line, y, stroke, Math.max(1, gap));
			}
		}
	}

	private void copyImageToAtlas(BufferedImage glyphImage, int atlasX, int atlasY, int width, int height) {
		for (int py = 0; py < height; py++) {
			for (int px = 0; px < width; px++) {
				int argb = glyphImage.getRGB(px, py);
				if ((argb >>> 24) != 0) {
					image.setPixel(atlasX + px, atlasY + py, argb);
				}
			}
		}
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
		emojiRenderer.close();
		texture.close();
		if (instance == this) {
			instance = null;
		}
	}

	private static boolean isCellFillFriendly(String text) {
		for (int offset = 0; offset < text.length(); ) {
			int codePoint = text.codePointAt(offset);
			if ((codePoint >= 0xE000 && codePoint <= 0xF8FF)
					|| (codePoint >= 0x2500 && codePoint <= 0x259F)
					|| (codePoint >= 0x1FB00 && codePoint <= 0x1FBFF)) {
				return true;
			}
			offset += Character.charCount(codePoint);
		}
		return false;
	}

	private static boolean isEmojiText(String text) {
		for (int offset = 0; offset < text.length(); ) {
			int codePoint = text.codePointAt(offset);
			if (isEmojiPresentationCodePoint(codePoint)) {
				return true;
			}
			offset += Character.charCount(codePoint);
		}
		return false;
	}

	private static boolean isEmojiPresentationCodePoint(int codePoint) {
		return (codePoint >= 0x1F000 && codePoint <= 0x1FAFF)
				|| (codePoint >= 0xFE00 && codePoint <= 0xFE0F)
				|| codePoint == 0x200D;
	}

	private static GlyphKind glyphKind(String text) {
		if (text.codePointCount(0, text.length()) != 1) {
			return GlyphKind.FONT;
		}
		int codePoint = text.codePointAt(0);
		if (isPowerlineCodePoint(codePoint)) {
			return GlyphKind.POWERLINE;
		}
		return isBuiltinCodePoint(codePoint) ? GlyphKind.BUILTIN : GlyphKind.FONT;
	}

	private static boolean isBuiltinCodePoint(int codePoint) {
		return (codePoint >= 0x2500 && codePoint <= 0x257F)
				|| (codePoint >= 0x2580 && codePoint <= 0x259F)
				|| (codePoint >= 0x1FB00 && codePoint <= 0x1FB3B)
				|| (codePoint >= 0x1FB82 && codePoint <= 0x1FB8B);
	}

	private static boolean isPowerlineCodePoint(int codePoint) {
		return codePoint == 0xE0B0 || codePoint == 0xE0B2 || codePoint == 0xE0B4 || codePoint == 0xE0B6;
	}

	private enum GlyphKind {
		FONT,
		BUILTIN,
		POWERLINE
	}

	private record GlyphKey(String text, int color, int cells, int displayWidth, int displayHeight,
			GlyphKind kind, boolean emoji) {
	}

	private record Glyph(int displayWidth, int displayHeight, float u0, float u1, float v0, float v1) {
	}

	private static final class EmojiRenderer implements AutoCloseable {
		private static final EmojiRenderer EMPTY = new EmojiRenderer(null, null, 0);

		private final FT_Face face;
		private final java.awt.Font shapeFont;
		private final long library;

		private EmojiRenderer(FT_Face face, java.awt.Font shapeFont, long library) {
			this.face = face;
			this.shapeFont = shapeFont;
			this.library = library;
		}

		static EmojiRenderer create(float rasterFontSize) {
			Path path = Path.of(SYSTEM_EMOJI_FONT);
			if (!Files.isRegularFile(path)) {
				return EMPTY;
			}
			try (MemoryStack stack = MemoryStack.stackPush()) {
				PointerBuffer libraryPointer = stack.mallocPointer(1);
				if (FreeType.FT_Init_FreeType(libraryPointer) != 0) {
					return EMPTY;
				}
				long library = libraryPointer.get(0);
				PointerBuffer facePointer = stack.mallocPointer(1);
				if (FreeType.FT_New_Face(library, path.toString(), 0, facePointer) != 0) {
					FreeType.FT_Done_FreeType(library);
					return EMPTY;
				}
				FT_Face face = FT_Face.create(facePointer.get(0));
				selectBestEmojiStrike(face);
				java.awt.Font shapeFont = java.awt.Font.createFont(java.awt.Font.TRUETYPE_FONT, path.toFile())
						.deriveFont(java.awt.Font.PLAIN, rasterFontSize);
				return new EmojiRenderer(face, shapeFont, library);
			} catch (Throwable ignored) {
				return EMPTY;
			}
		}

		boolean canRender(String text) {
			if (face == null || shapeFont == null || !isEmojiText(text)) {
				return false;
			}
			return glyphCode(text) > 0;
		}

		boolean render(String text, int atlasX, int atlasY, int width, int height, NativeImage image) {
			if (face == null || shapeFont == null || !isEmojiText(text)) {
				return false;
			}
			int glyphCode = glyphCode(text);
			if (glyphCode <= 0) {
				return false;
			}
			if (FreeType.FT_Load_Glyph(face, glyphCode, FreeType.FT_LOAD_COLOR | FreeType.FT_LOAD_RENDER) != 0) {
				return false;
			}
			FT_GlyphSlot slot = face.glyph();
			FT_Bitmap bitmap = slot.bitmap();
			if (bitmap.width() <= 0 || bitmap.rows() <= 0) {
				return false;
			}
			if (bitmap.pixel_mode() != FreeType.FT_PIXEL_MODE_BGRA) {
				if (FreeType.FT_Render_Glyph(slot, FreeType.FT_RENDER_MODE_NORMAL) != 0
						|| slot.bitmap().pixel_mode() != FreeType.FT_PIXEL_MODE_BGRA) {
					return false;
				}
				bitmap = slot.bitmap();
			}

			int drawWidth = Math.max(1, Math.min(width, Math.round(bitmap.width() * emojiScale(bitmap, width, height))));
			int drawHeight = Math.max(1, Math.min(height, Math.round(bitmap.rows() * emojiScale(bitmap, width, height))));
			int drawX = atlasX + Math.max(0, (width - drawWidth) / 2);
			int drawY = atlasY + Math.max(0, (height - drawHeight) / 2);
			copyBgraBitmap(bitmap, image, drawX, drawY, drawWidth, drawHeight);
			return true;
		}

		private int glyphCode(String text) {
			GlyphVector vector = shapeFont.layoutGlyphVector(new FontRenderContext(null, true, true),
					text.toCharArray(), 0, text.length(), Font.LAYOUT_LEFT_TO_RIGHT);
			if (vector.getNumGlyphs() == 1) {
				return vector.getGlyphCode(0);
			}
			if (text.codePointCount(0, text.length()) == 1) {
				return FreeType.FT_Get_Char_Index(face, text.codePointAt(0));
			}
			return 0;
		}

		private static void selectBestEmojiStrike(FT_Face face) {
			int best = -1;
			int bestDistance = Integer.MAX_VALUE;
			FT_Bitmap_Size.Buffer sizes = face.available_sizes();
			for (int i = 0; i < face.num_fixed_sizes(); i++) {
				FT_Bitmap_Size size = sizes.get(i);
				int distance = Math.abs(size.height() - EMOJI_PIXEL_SIZE);
				if (distance < bestDistance) {
					bestDistance = distance;
					best = i;
				}
			}
			if (best >= 0) {
				FreeType.FT_Select_Size(face, best);
			} else {
				FreeType.FT_Set_Pixel_Sizes(face, 0, EMOJI_PIXEL_SIZE);
			}
		}

		private static float emojiScale(FT_Bitmap bitmap, int width, int height) {
			return Math.min(width / (float) Math.max(1, bitmap.width()),
					height / (float) Math.max(1, bitmap.rows()));
		}

		private static void copyBgraBitmap(FT_Bitmap bitmap, NativeImage image, int dstX, int dstY,
											int dstWidth, int dstHeight) {
			int pitch = Math.abs(bitmap.pitch());
			ByteBuffer buffer = bitmap.buffer(pitch * bitmap.rows());
			for (int y = 0; y < dstHeight; y++) {
				int srcY = Math.min(bitmap.rows() - 1, y * bitmap.rows() / dstHeight);
				for (int x = 0; x < dstWidth; x++) {
					int srcX = Math.min(bitmap.width() - 1, x * bitmap.width() / dstWidth);
					int offset = srcY * pitch + srcX * 4;
					int b = buffer.get(offset) & 0xFF;
					int g = buffer.get(offset + 1) & 0xFF;
					int r = buffer.get(offset + 2) & 0xFF;
					int a = buffer.get(offset + 3) & 0xFF;
					if (a != 0) {
						image.setPixel(dstX + x, dstY + y, (a << 24) | (r << 16) | (g << 8) | b);
					}
				}
			}
		}

		@Override
		public void close() {
			if (face != null) {
				FreeType.FT_Done_Face(face);
			}
			if (library != 0) {
				FreeType.FT_Done_FreeType(library);
			}
		}
	}
}
