package app.watchmark.catalog.tmdb;

import java.util.concurrent.TimeUnit;
import java.util.function.LongSupplier;

/**
 * Spaces outgoing requests evenly so Watchmark never exceeds the configured
 * rate, even when many users load pages at once. Callers block briefly instead
 * of being rejected.
 */
class RequestPacer {

	@FunctionalInterface
	interface Sleeper {

		void sleepNanos(long nanos) throws InterruptedException;

	}

	private final long intervalNanos;

	private final LongSupplier nanoTime;

	private final Sleeper sleeper;

	private long nextSlot;

	RequestPacer(int requestsPerSecond) {
		this(requestsPerSecond, System::nanoTime, TimeUnit.NANOSECONDS::sleep);
	}

	RequestPacer(int requestsPerSecond, LongSupplier nanoTime, Sleeper sleeper) {
		this.intervalNanos = TimeUnit.SECONDS.toNanos(1) / requestsPerSecond;
		this.nanoTime = nanoTime;
		this.sleeper = sleeper;
		this.nextSlot = nanoTime.getAsLong();
	}

	/** Blocks until this caller's slot; throws if interrupted while waiting. */
	void acquire() throws InterruptedException {
		long wait;
		synchronized (this) {
			long now = nanoTime.getAsLong();
			long slot = Math.max(now, nextSlot);
			nextSlot = slot + intervalNanos;
			wait = slot - now;
		}
		if (wait > 0) {
			sleeper.sleepNanos(wait);
		}
	}

}
