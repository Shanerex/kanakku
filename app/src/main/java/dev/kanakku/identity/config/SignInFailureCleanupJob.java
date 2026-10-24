package dev.kanakku.identity.config;

import com.github.kagkarlsson.scheduler.task.helper.RecurringTask;
import com.github.kagkarlsson.scheduler.task.helper.Tasks;
import com.github.kagkarlsson.scheduler.task.schedule.Schedules;
import dev.kanakku.identity.service.SignInThrottle;
import java.time.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Deletes sign-in failures past retention once an hour, so no sign-in pays for the DELETE. */
@Configuration
public class SignInFailureCleanupJob {

  static final String NAME = "sign-in-failure-cleanup";

  private static final Logger log = LoggerFactory.getLogger(SignInFailureCleanupJob.class);

  @Bean
  RecurringTask<Void> signInFailureCleanup(SignInThrottle throttle) {
    return Tasks.recurring(NAME, Schedules.fixedDelay(Duration.ofHours(1)))
        .execute(
            (instance, context) -> {
              int deleted = throttle.purgeExpired();
              log.info("Deleted {} expired sign-in failures", deleted);
            });
  }
}
