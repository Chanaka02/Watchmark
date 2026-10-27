package app.watchmark.catalog;

import java.util.Arrays;
import java.util.Optional;

/** The curated lists on the Discover page, addressed by URL slug. */
public enum DiscoverList {

	TRENDING("trending"),

	POPULAR("popular"),

	TOP_RATED("top-rated"),

	/** Shows with an episode airing within the next seven days. */
	ON_THE_AIR("on-the-air");

	private final String slug;

	DiscoverList(String slug) {
		this.slug = slug;
	}

	public String slug() {
		return slug;
	}

	public static Optional<DiscoverList> fromSlug(String slug) {
		return Arrays.stream(values()).filter(list -> list.slug.equals(slug)).findFirst();
	}

}
