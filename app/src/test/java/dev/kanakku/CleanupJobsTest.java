package dev.kanakku;

import static org.assertj.core.api.Assertions.assertThat;

import dev.kanakku.support.PostgresIntegrationTest;
import java.time.Duration;
import java.util.List;
import org.awaitility.Awaitility;
import org.jooq.DSLContext;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * Expired idempotency keys, sign-in failures and rate limit hits are cleaned by recurring
 * db-scheduler jobs.
 */
@SpringBootTest(properties = "db-scheduler.enabled=true")
class CleanupJobsTest extends PostgresIntegrationTest {

  @Autowired DSLContext dsl;

  @Test
  void everyCleanupJobIsScheduledOnStartup() {
    Awaitility.await()
        .atMost(Duration.ofSeconds(30))
        .untilAsserted(
            () -> {
              List<String> names =
                  dsl.fetch("select task_name from scheduled_tasks")
                      .getValues("task_name", String.class);
              assertThat(names)
                  .contains(
                      "idempotency-key-cleanup",
                      "sign-in-failure-cleanup",
                      "rate-limit-cleanup",
                      "pending-sign-up-cleanup");
            });
  }
}
