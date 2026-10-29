package dev.kanakku.ledger.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * The nightly ledger balance check.
 *
 * @param cron when it runs, in UTC (db-scheduler cron with seconds)
 * @param repair whether mismatched balances are overwritten with their journal sums after they are
 *     reported; false only reports them
 */
@ConfigurationProperties("kanakku.ledger.balance-check")
public record BalanceCheckProperties(
    @DefaultValue("0 30 2 * * *") String cron, @DefaultValue("true") boolean repair) {}
