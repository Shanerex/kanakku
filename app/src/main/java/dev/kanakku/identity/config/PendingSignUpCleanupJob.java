package dev.kanakku.identity.config;

import com.github.kagkarlsson.scheduler.task.helper.RecurringTask;
import com.github.kagkarlsson.scheduler.task.helper.Tasks;
import com.github.kagkarlsson.scheduler.task.schedule.Schedules;
import dev.kanakku.identity.service.PendingSignUpService;
import java.time.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Deletes expired pending sign-ups (with their password hashes) every 15 minutes. */
@Configuration
public class PendingSignUpCleanupJob {

  static final String NAME = "pending-sign-up-cleanup";

  private static final Logger log = LoggerFactory.getLogger(PendingSignUpCleanupJob.class);

  @Bean
  RecurringTask<Void> pendingSignUpCleanup(PendingSignUpService signUps) {
    return Tasks.recurring(NAME, Schedules.fixedDelay(Duration.ofMinutes(15)))
        .execute(
            (instance, context) -> {
              int deleted = signUps.purgeExpired();
              log.info("Deleted {} expired pending sign-ups", deleted);
            });
  }
}
