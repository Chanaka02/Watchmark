package app.watchmark.common;

import java.time.Clock;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** A single injectable clock so time-dependent rules can be tested deterministically. */
@Configuration(proxyBeanMethods = false)
class ClockConfig {

	@Bean
	Clock clock() {
		return Clock.systemUTC();
	}

}
