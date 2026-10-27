package app.watchmark.user;

import java.util.Locale;

public final class EmailAddresses {

	private EmailAddresses() {
	}

	/**
	 * Canonical stored form: trimmed and lowercased with {@link Locale#ROOT} so the
	 * result never depends on the server's default locale (e.g. Turkish "I").
	 */
	public static String normalize(String email) {
		return email.strip().toLowerCase(Locale.ROOT);
	}

}
