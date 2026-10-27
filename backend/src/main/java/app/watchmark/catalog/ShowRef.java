package app.watchmark.catalog;

import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Provider-qualified show identifier, written {@code provider:externalId}, for
 * example {@code tmdb:1399}. This is the only show id the API exposes, so user
 * data never depends on one provider's numbering.
 */
public record ShowRef(String provider, String externalId) {

	private static final Pattern FORMAT = Pattern.compile("([a-z]{2,16}):([1-9][0-9]{0,11})");

	public static Optional<ShowRef> parse(String value) {
		if (value == null) {
			return Optional.empty();
		}
		Matcher matcher = FORMAT.matcher(value);
		return matcher.matches() ? Optional.of(new ShowRef(matcher.group(1), matcher.group(2))) : Optional.empty();
	}

	@Override
	public String toString() {
		return provider + ":" + externalId;
	}

}
