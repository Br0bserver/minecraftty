package dev.br0b.minecraftty.client.terminal;

final class TerminalImageProtocolFilter {
	private boolean pendingKittyImage;
	private int suppressedImages;

	String consumeApplicationProgramCommand(String text) {
		if (!isKittyGraphicsCommand(text)) {
			return "";
		}
		boolean moreChunks = kittyHasMoreChunks(text);
		if (moreChunks) {
			pendingKittyImage = true;
			return "";
		}
		if (pendingKittyImage) {
			pendingKittyImage = false;
		}
		suppressedImages++;
		return "";
	}

	int suppressedImages() {
		return suppressedImages;
	}

	private static boolean isKittyGraphicsCommand(String text) {
		return !text.isEmpty() && text.charAt(0) == 'G';
	}

	private static boolean kittyHasMoreChunks(String text) {
		int optionsEnd = text.indexOf(';');
		String options = optionsEnd >= 0 ? text.substring(1, optionsEnd) : text.substring(1);
		for (String option : options.split(",")) {
			String trimmed = option.trim();
			if (trimmed.equals("m=1")) {
				return true;
			}
			if (trimmed.equals("m=0")) {
				return false;
			}
		}
		return false;
	}
}
