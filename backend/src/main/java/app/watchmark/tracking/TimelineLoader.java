package app.watchmark.tracking;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;

import org.springframework.stereotype.Component;

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
		var episodes = watches.findEpisodesOfShow(showId)
			.stream()
			.map(e -> new TimelineEpisode(e.getId(), e.getSeasonNumber(), e.getEpisodeNumber(), e.getAirDate(),
					e.isActive()))
			.toList();
		return new ShowTimeline(episodes, today(userId));
	}

	LocalDate today(long userId) {
		ZoneId zone = users.findById(userId).map(User::getTimeZone).map(ZoneId::of).orElse(ZoneOffset.UTC);
		return LocalDate.ofInstant(clock.instant(), zone);
	}

}
