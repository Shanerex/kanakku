package dev.kanakku.ledger.config;

import com.github.kagkarlsson.scheduler.task.helper.RecurringTask;
import com.github.kagkarlsson.scheduler.task.helper.Tasks;
import com.github.kagkarlsson.scheduler.task.schedule.Schedules;
import dev.kanakku.ledger.service.BalanceCheckService;
import java.time.ZoneOffset;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Runs the ledger balance check nightly (02:30 UTC by default). db-scheduler runs it on one
 * instance at a time.
 */
@Configuration
@EnableConfigurationProperties(BalanceCheckProperties.class)
public class BalanceCheckJob {

  static final String NAME = "ledger-balance-check";

  @Bean
  RecurringTask<Void> ledgerBalanceCheck(
      BalanceCheckService check, BalanceCheckProperties properties) {
    return Tasks.recurring(NAME, Schedules.cron(properties.cron(), ZoneOffset.UTC))
        .execute((instance, context) -> check.run());
  }
}
