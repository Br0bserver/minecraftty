package dev.br0b.minecraftty.client.terminal;

import com.jediterm.terminal.util.CharUtils;

final class TerminalCellWidth {
	private TerminalCellWidth() {
	}

	static int nextCluster(String text, int offset) {
		int next = offset + Character.charCount(text.codePointAt(offset));
		while (next < text.length()) {
			int codePoint = text.codePointAt(next);
			if (isCombiningOrVariation(codePoint)) {
				next += Character.charCount(codePoint);
				continue;
			}
			if (codePoint == 0x200D) {
				next += Character.charCount(codePoint);
				if (next < text.length()) {
					next += Character.charCount(text.codePointAt(next));
				}
				continue;
			}
			if (isRegionalIndicator(text.codePointAt(offset)) && isRegionalIndicator(codePoint)) {
				next += Character.charCount(codePoint);
			}
			break;
		}
		return next;
	}

	static int cells(String text) {
		if (isEmojiPresentationCluster(text)) {
			return 2;
		}
		int width = 0;
		boolean hasJoinerSequence = false;
		boolean hasRegionalIndicatorPair = false;
		int regionalIndicators = 0;
		for (int offset = 0; offset < text.length(); ) {
			int codePoint = text.codePointAt(offset);
			if (codePoint == 0x200D) {
				hasJoinerSequence = true;
			} else if (isRegionalIndicator(codePoint)) {
				regionalIndicators++;
				hasRegionalIndicatorPair = regionalIndicators >= 2;
				width += 1;
			} else {
				width += cells(codePoint);
			}
			offset += Character.charCount(codePoint);
		}
		if ((hasJoinerSequence || hasRegionalIndicatorPair) && width > 0) {
			return 2;
		}
		return width;
	}

	static boolean isBlankCluster(String text) {
		for (int offset = 0; offset < text.length(); ) {
			int codePoint = text.codePointAt(offset);
			if (!Character.isWhitespace(codePoint) && !isCombiningOrVariation(codePoint)) {
				return false;
			}
			offset += Character.charCount(codePoint);
		}
		return true;
	}

	private static boolean isEmojiPresentationCluster(String text) {
		for (int offset = 0; offset < text.length(); ) {
			int codePoint = text.codePointAt(offset);
			if ((codePoint >= 0x1F000 && codePoint <= 0x1FAFF)
					|| (codePoint >= 0xFE00 && codePoint <= 0xFE0F)
					|| codePoint == 0x200D) {
				return true;
			}
			offset += Character.charCount(codePoint);
		}
		return false;
	}

	private static int cells(int codePoint) {
		if (isCombiningOrVariation(codePoint)) {
			return 0;
		}
		if (isPrivateUse(codePoint)) {
			return 1;
		}
		return CharUtils.isDoubleWidthCharacter(codePoint, false) ? 2 : 1;
	}

	private static boolean isPrivateUse(int codePoint) {
		return (codePoint >= 0xE000 && codePoint <= 0xF8FF)
				|| (codePoint >= 0xF0000 && codePoint <= 0xFFFFD)
				|| (codePoint >= 0x100000 && codePoint <= 0x10FFFD);
	}

	private static boolean isCombiningOrVariation(int codePoint) {
		int type = Character.getType(codePoint);
		return type == Character.NON_SPACING_MARK
				|| type == Character.COMBINING_SPACING_MARK
				|| type == Character.ENCLOSING_MARK
				|| (codePoint >= 0xFE00 && codePoint <= 0xFE0F)
				|| (codePoint >= 0xE0100 && codePoint <= 0xE01EF);
	}

	private static boolean isRegionalIndicator(int codePoint) {
		return codePoint >= 0x1F1E6 && codePoint <= 0x1F1FF;
	}
}
