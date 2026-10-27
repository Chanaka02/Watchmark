package app.watchmark.common;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.stereotype.Component;

import app.watchmark.common.RateLimitProperties.Rule;

/**
 * In-memory fixed-window rate limiter for sensitive endpoints.
 *
 * <p>
 * State is per application instance and lost on restart, which is acceptable
 * for a single-instance deployment. If Watchmark ever runs more than one
 * instance, move this to a shared store.
 */
@Component
public class RateLimiter {

	/** Above this many tracked keys, expired windows are swept on the next call. */
	static final int SWEEP_THRESHOLD = 10_000;

	private final ConcurrentHashMap<String, Window> windows = new ConcurrentHashMap<>();

	private final Clock clock;

	public RateLimiter(Clock clock) {
		this.clock = clock;
	}

	/**
	 * Counts one attempt against {@code key} and throws when the rule's limit for
	 * the current window is exceeded. Keys are namespaced by the caller, e.g.
	 * {@code "login-client:203.0.113.7"}.
	 */
	public void acquire(String key, Rule rule) {
		Instant now = clock.instant();
		if (windows.size() > SWEEP_THRESHOLD) {
			windows.values().removeIf(window -> !now.isBefore(window.resetsAt()));
		}
		Window window = windows.compute(key, (k, current) -> {
			if (current == null || !now.isBefore(current.resetsAt())) {
				return new Window(now.plus(rule.window()), 1);
			}
			return new Window(current.resetsAt(), current.count() + 1);
		});
		if (window.count() > rule.limit()) {
			throw new RateLimitExceededException(Duration.between(now, window.resetsAt()));
		}
	}

	int trackedKeys() {
		return windows.size();
	}

	private record Window(Instant resetsAt, int count) {
	}

}
