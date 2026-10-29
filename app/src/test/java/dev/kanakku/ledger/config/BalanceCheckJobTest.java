package dev.kanakku.ledger.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.github.kagkarlsson.scheduler.task.helper.RecurringTask;
import com.github.kagkarlsson.scheduler.task.schedule.Schedules;
import dev.kanakku.support.PostgresIntegrationTest;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Map;
import org.awaitility.Awaitility;
import org.jooq.DSLContext;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.MapConfigurationPropertySource;
import org.springframework.boot.test.context.SpringBootTest;

/** The nightly balance check is a db-scheduler task on a UTC cron, with repair on by default. */
@SpringBootTest(properties = "db-scheduler.enabled=true")
class BalanceCheckJobTest extends PostgresIntegrationTest {

  @Autowired DSLContext dsl;
  @Autowired BalanceCheckProperties properties;
  @Autowired Map<String, RecurringTask<?>> tasks;

  static BalanceCheckProperties bind(Map<String, String> values) {
    return new Binder(new MapConfigurationPropertySource(values))
        .bindOrCreate("kanakku.ledger.balance-check", BalanceCheckProperties.class);
  }

  @Test
  void theTaskIsRegisteredAndScheduledForTheNext0230Utc() {
    assertThat(tasks.values()).extracting(RecurringTask::getName).contains(BalanceCheckJob.NAME);
    assertThat(properties.cron()).isEqualTo("0 30 2 * * *");

    Awaitility.await()
        .atMost(Duration.ofSeconds(30))
        .untilAsserted(
            () -> {
              OffsetDateTime next =
                  dsl.fetchOne(
                          "select execution_time from scheduled_tasks where task_name = ?",
                          BalanceCheckJob.NAME)
                      .get(0, OffsetDateTime.class)
                      .withOffsetSameInstant(ZoneOffset.UTC);
              assertThat(next.getHour()).isEqualTo(2);
              assertThat(next.getMinute()).isEqualTo(30);
              assertThat(next.getSecond()).isZero();
              assertThat(next).isBefore(OffsetDateTime.now(ZoneOffset.UTC).plusDays(1));
            });
  }

  @Test
  void repairDefaultsToTrueAndHonoursFalse() {
    assertThat(properties.repair()).isTrue();
    assertThat(bind(Map.of())).isEqualTo(new BalanceCheckProperties("0 30 2 * * *", true));
    assertThat(bind(Map.of("kanakku.ledger.balance-check.repair", "false")).repair()).isFalse();
    assertThat(bind(Map.of("kanakku.ledger.balance-check.cron", "0 0 4 * * *")).cron())
        .isEqualTo("0 0 4 * * *");
    // The default is a valid six-field cron.
    assertThat(Schedules.cron(bind(Map.of()).cron(), ZoneOffset.UTC).getPattern())
        .isEqualTo("0 30 2 * * *");
  }
}
