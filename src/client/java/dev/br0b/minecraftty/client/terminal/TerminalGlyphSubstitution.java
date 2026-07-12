package dev.br0b.minecraftty.client.terminal;

import com.jediterm.terminal.util.CharUtils;

import java.util.LinkedHashMap;
import java.util.Map;

final class TerminalGlyphSubstitution {
	private static final int PLACEHOLDER_START = 0xFDD0;
	private static final int PLACEHOLDER_END = 0xFDEF;
	private static final Map<Integer, Character> PLACEHOLDER_BY_CODE_POINT = new LinkedHashMap<>();
	private static final Map<Character, String> TEXT_BY_PLACEHOLDER = new LinkedHashMap<>();

	private TerminalGlyphSubstitution() {
	}

	static synchronized String modelText(String text) {
		StringBuilder builder = null;
		for (int offset = 0; offset < text.length(); ) {
			int codePoint = text.codePointAt(offset);
			int charCount = Character.charCount(codePoint);
			if (shouldUsePlaceholder(codePoint)) {
				if (builder == null) {
					builder = new StringBuilder(text.length());
					builder.append(text, 0, offset);
				}
				builder.append(placeholderFor(codePoint));
			} else if (builder != null) {
				builder.appendCodePoint(codePoint);
			}
			offset += charCount;
		}
		return builder == null ? text : builder.toString();
	}

	static synchronized String displayText(String text) {
		StringBuilder builder = null;
		for (int offset = 0; offset < text.length(); offset++) {
			char ch = text.charAt(offset);
			String replacement = TEXT_BY_PLACEHOLDER.get(ch);
			if (replacement != null) {
				if (builder == null) {
					builder = new StringBuilder(text.length());
					builder.append(text, 0, offset);
				}
				builder.append(replacement);
			} else if (builder != null) {
				builder.append(ch);
			}
		}
		return builder == null ? text : builder.toString();
	}

	private static boolean shouldUsePlaceholder(int codePoint) {
		return Character.charCount(codePoint) == 2
				&& !CharUtils.isDoubleWidthCharacter(codePoint, false);
	}

	private static char placeholderFor(int codePoint) {
		Character cached = PLACEHOLDER_BY_CODE_POINT.get(codePoint);
		if (cached != null) {
			return cached;
		}
		int next = PLACEHOLDER_START + PLACEHOLDER_BY_CODE_POINT.size();
		if (next > PLACEHOLDER_END) {
			return '?';
		}
		char placeholder = (char) next;
		PLACEHOLDER_BY_CODE_POINT.put(codePoint, placeholder);
		TEXT_BY_PLACEHOLDER.put(placeholder, new String(Character.toChars(codePoint)));
		return placeholder;
	}
}
