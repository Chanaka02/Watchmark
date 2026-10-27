package app.watchmark.catalog.tmdb;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import org.junit.jupiter.api.Test;

class RequestPacerTests {

	private final AtomicLong now = new AtomicLong(1_000_000_000L);

	private final List<Long> sleeps = new ArrayList<>();

	private final RequestPacer pacer = new RequestPacer(10, now::get, sleeps::add);

	@Test
	void burstIsSpreadEvenly() throws InterruptedException {
		pacer.acquire();
		pacer.acquire();
		pacer.acquire();

		assertThat(sleeps).containsExactly(TimeUnit.MILLISECONDS.toNanos(100), TimeUnit.MILLISECONDS.toNanos(200));
	}

	@Test
	void spacedOutRequestsDoNotWait() throws InterruptedException {
		pacer.acquire();
		now.addAndGet(TimeUnit.MILLISECONDS.toNanos(150));
		pacer.acquire();

		assertThat(sleeps).isEmpty();
	}

}
