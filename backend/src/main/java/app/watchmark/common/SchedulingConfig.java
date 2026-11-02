package app.watchmark.common;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Enables {@code @Scheduled} jobs. Each job's cron is configurable and {@code -}
 * disables it (tests disable all of them and call the jobs directly).
 */
@Configuration(proxyBeanMethods = false)
@EnableScheduling
class SchedulingConfig {

}
