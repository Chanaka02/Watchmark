package app.watchmark.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.time.Instant;

import org.junit.jupiter.api.Test;

import app.watchmark.MutableClock;
import app.watchmark.auth.RateLimitProperties.Rule;
import app.watchmark.common.RateLimitExceededException;

class RateLimiterTests {

	private final MutableClock clock = new MutableClock(Instant.parse("2026-10-27T10:00:00Z"));

	private final RateLimiter limiter = new RateLimiter(clock);

	private final Rule threePerMinute = new Rule(3, Duration.ofMinutes(1));

	@Test
	void allowsUpToTheLimitWithinAWindow() {
		for (int i = 0; i < 3; i++) {
			limiter.acquire("k", threePerMinute);
		}
		assertThatThrownBy(() -> limiter.acquire("k", threePerMinute))
			.isInstanceOfSatisfying(RateLimitExceededException.class,
					ex -> assertThat(ex.retryAfter()).isEqualTo(Duration.ofMinutes(1)));
	}

	@Test
	void reportsRemainingTimeUntilTheWindowResets() {
		for (int i = 0; i < 3; i++) {
			limiter.acquire("k", threePerMinute);
		}
		clock.advance(Duration.ofSeconds(45));
		assertThatThrownBy(() -> limiter.acquire("k", threePerMinute))
			.isInstanceOfSatisfying(RateLimitExceededException.class,
					ex -> assertThat(ex.retryAfter()).isEqualTo(Duration.ofSeconds(15)));
	}

	@Test
	void resetsWhenTheWindowEnds() {
		for (int i = 0; i < 4; i++) {
			try {
				limiter.acquire("k", threePerMinute);
			}
			catch (RateLimitExceededException expected) {
				// fourth call
			}
		}
		clock.advance(Duration.ofMinutes(1));
		assertThatNoException().isThrownBy(() -> limiter.acquire("k", threePerMinute));
	}

	@Test
	void keysAreIndependent() {
		for (int i = 0; i < 3; i++) {
			limiter.acquire("a", threePerMinute);
		}
		assertThatNoException().isThrownBy(() -> limiter.acquire("b", threePerMinute));
	}

	@Test
	void sweepsExpiredWindowsOnceManyKeysAreTracked() {
		for (int i = 0; i <= RateLimiter.SWEEP_THRESHOLD; i++) {
			limiter.acquire("key-" + i, threePerMinute);
		}
		clock.advance(Duration.ofMinutes(2));
		limiter.acquire("fresh", threePerMinute);
		assertThat(limiter.trackedKeys()).isEqualTo(1);
	}

}
