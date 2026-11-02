package app.watchmark.catalog.store;

/** Fits provider strings into column limits without splitting a surrogate pair. */
final class Text {

	private Text() {
	}

	static String limit(String value, int maxChars) {
		if (value == null || value.length() <= maxChars) {
			return value;
		}
		int end = maxChars;
		if (Character.isHighSurrogate(value.charAt(end - 1))) {
			end--;
		}
		return value.substring(0, end);
	}

}
