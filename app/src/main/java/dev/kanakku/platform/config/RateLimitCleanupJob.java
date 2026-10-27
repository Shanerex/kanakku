package dev.kanakku.platform.config;

import com.github.kagkarlsson.scheduler.task.helper.RecurringTask;
import com.github.kagkarlsson.scheduler.task.helper.Tasks;
import com.github.kagkarlsson.scheduler.task.schedule.Schedules;
import dev.kanakku.platform.ratelimit.RateLimiter;
import java.time.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Deletes rate limit hits past retention once an hour, so no request pays for the DELETE. */
@Configuration
public class RateLimitCleanupJob {

  static final String NAME = "rate-limit-cleanup";

  private static final Logger log = LoggerFactory.getLogger(RateLimitCleanupJob.class);

  @Bean
  RecurringTask<Void> rateLimitCleanup(RateLimiter limiter) {
    return Tasks.recurring(NAME, Schedules.fixedDelay(Duration.ofHours(1)))
        .execute(
            (instance, context) -> {
              int deleted = limiter.purgeExpired();
              log.info("Deleted {} expired rate limit hits", deleted);
            });
  }
}
