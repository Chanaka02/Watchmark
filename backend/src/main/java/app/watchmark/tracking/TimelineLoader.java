package app.watchmark.tracking;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;

import org.springframework.stereotype.Component;

import app.watchmark.catalog.store.Episode;
import app.watchmark.tracking.ShowTimeline.TimelineEpisode;
import app.watchmark.user.User;
import app.watchmark.user.UserRepository;

/** Builds a show's {@link ShowTimeline} as of today in the user's time zone (R5). */
@Component
class TimelineLoader {

	private final EpisodeWatchRepository watches;

	private final UserRepository users;

	private final Clock clock;

	TimelineLoader(EpisodeWatchRepository watches, UserRepository users, Clock clock) {
		this.watches = watches;
		this.users = users;
		this.clock = clock;
	}

	ShowTimeline load(long userId, long showId) {
		return of(watches.findEpisodesOfShow(showId), today(userId));
	}

	static ShowTimeline of(List<Episode> episodes, LocalDate today) {
		return new ShowTimeline(episodes.stream()
			.map(e -> new TimelineEpisode(e.getId(), e.getSeasonNumber(), e.getEpisodeNumber(), e.getAirDate(),
					e.isActive()))
			.toList(), today);
	}

	LocalDate today(long userId) {
		ZoneId zone = users.findById(userId).map(User::getTimeZone).map(ZoneId::of).orElse(ZoneOffset.UTC);
		return LocalDate.ofInstant(clock.instant(), zone);
	}

}
