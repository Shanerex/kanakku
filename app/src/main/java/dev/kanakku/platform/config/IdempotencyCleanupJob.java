package dev.kanakku.platform.config;

import com.github.kagkarlsson.scheduler.task.helper.RecurringTask;
import com.github.kagkarlsson.scheduler.task.helper.Tasks;
import com.github.kagkarlsson.scheduler.task.schedule.Schedules;
import dev.kanakku.platform.idempotency.IdempotencyService;
import java.time.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Deletes expired idempotency keys once an hour, so no request pays for the DELETE. */
@Configuration
public class IdempotencyCleanupJob {

  static final String NAME = "idempotency-key-cleanup";

  private static final Logger log = LoggerFactory.getLogger(IdempotencyCleanupJob.class);

  @Bean
  RecurringTask<Void> idempotencyKeyCleanup(IdempotencyService idempotency) {
    return Tasks.recurring(NAME, Schedules.fixedDelay(Duration.ofHours(1)))
        .execute(
            (instance, context) -> {
              int deleted = idempotency.purgeExpired();
              log.info("Deleted {} expired idempotency keys", deleted);
            });
  }
}
