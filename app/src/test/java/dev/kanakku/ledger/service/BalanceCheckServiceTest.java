package dev.kanakku.ledger.service;

import static dev.kanakku.jooq.Tables.MEMBER_BALANCES;
import static org.assertj.core.api.Assertions.assertThat;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import dev.kanakku.ledger.api.Journal;
import dev.kanakku.ledger.api.JournalEntry;
import dev.kanakku.ledger.config.BalanceCheckProperties;
import dev.kanakku.ledger.repository.JournalRepository;
import dev.kanakku.support.PostgresIntegrationTest;
import dev.kanakku.support.Seed;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import org.jooq.DSLContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.support.TransactionTemplate;

@SpringBootTest
class BalanceCheckServiceTest extends PostgresIntegrationTest {

  @Autowired Journal journal;
  @Autowired JournalRepository repository;
  @Autowired TransactionTemplate transactions;
  @Autowired DSLContext dsl;

  final SimpleMeterRegistry meters = new SimpleMeterRegistry();
  final ListAppender<ILoggingEvent> logs = new ListAppender<>();
  final Logger logger = (Logger) LoggerFactory.getLogger(BalanceCheckService.class);

  @BeforeEach
  void captureLogs() {
    logs.start();
    logger.addAppender(logs);
  }

  @AfterEach
  void releaseLogs() {
    logger.detachAppender(logs);
  }

  BalanceCheckService check(boolean repair) {
    return new BalanceCheckService(
        repository, transactions, meters, new BalanceCheckProperties("0 30 2 * * *", repair));
  }

  double count() {
    return meters.counter(BalanceCheckService.METRIC).count();
  }

  List<ILoggingEvent> mismatchLines() {
    return logs.list.stream()
        .filter(e -> e.getLevel() == Level.ERROR)
        .filter(e -> e.getMessage().equals(BalanceCheckService.MESSAGE))
        .toList();
  }

  static Map<String, Object> keyValues(ILoggingEvent event) {
    return event.getKeyValuePairs().stream()
        .collect(Collectors.toMap(kv -> kv.key, kv -> kv.value));
  }

  /** A group with a three-way INR expense and a JPY settlement; returns [group, a, b, c]. */
  List<UUID> postedGroup() {
    List<UUID> ids = Seed.groupWithGuests(dsl, 3);
    UUID g = ids.get(0), a = ids.get(1), b = ids.get(2), c = ids.get(3);
    LinkedHashMap<UUID, Long> shares = new LinkedHashMap<>();
    shares.put(b, 400L);
    shares.put(c, 600L);
    journal.post(JournalEntry.expense(g, Seed.caller(dsl, g), "INR", a, shares), "e");
    journal.post(JournalEntry.settlement(g, Seed.caller(dsl, g), "JPY", b, c, 30L), "s");
    return ids;
  }

  long stored(UUID g, UUID member, String currency) {
    return dsl.fetchValue(
        MEMBER_BALANCES.BALANCE_MINOR,
        MEMBER_BALANCES
            .GROUP_ID
            .eq(g)
            .and(MEMBER_BALANCES.MEMBER_ID.eq(member))
            .and(MEMBER_BALANCES.CURRENCY.eq(currency)));
  }

  void corrupt(UUID g, UUID member, String currency, long value) {
    dsl.update(MEMBER_BALANCES)
        .set(MEMBER_BALANCES.BALANCE_MINOR, value)
        .where(MEMBER_BALANCES.GROUP_ID.eq(g))
        .and(MEMBER_BALANCES.MEMBER_ID.eq(member))
        .and(MEMBER_BALANCES.CURRENCY.eq(currency))
        .execute();
  }

  /** A fingerprint of every stored balance row version; any write changes it. */
  String balanceRowVersions() {
    return dsl.fetchOne(
            "select coalesce(md5(string_agg(xmin::text || ':' || balance_minor, ','"
                + " order by group_id, member_id, currency)), '') from member_balances")
        .get(0, String.class);
  }

  @Test
  void withRepairOnACorruptRowIsReportedOnceAndSetToTheJournalSum() {
    List<UUID> ids = postedGroup();
    UUID g = ids.get(0), b = ids.get(2);
    corrupt(g, b, "INR", -999L);

    check(true).run();

    List<ILoggingEvent> lines =
        mismatchLines().stream().filter(e -> keyValues(e).get("groupId").equals(g)).toList();
    assertThat(lines).hasSize(1);
    assertThat(keyValues(lines.getFirst()))
        .containsEntry("currency", "INR")
        .containsEntry("members", 1)
        .containsEntry("repaired", true);
    assertThat(lines.getFirst().getFormattedMessage()).doesNotContain("999");
    assertThat(count()).isEqualTo(1.0);
    assertThat(stored(g, b, "INR")).isEqualTo(-400L);
    assertThat(repository.mismatchesOf(g)).isEmpty();
  }

  @Test
  void withRepairOffTheRowIsReportedButLeftUntouched() {
    List<UUID> ids = postedGroup();
    UUID g = ids.get(0), c = ids.get(3);
    corrupt(g, c, "JPY", 1L);
    try {
      check(false).run();

      List<ILoggingEvent> lines =
          mismatchLines().stream().filter(e -> keyValues(e).get("groupId").equals(g)).toList();
      assertThat(lines).hasSize(1);
      assertThat(keyValues(lines.getFirst()))
          .containsEntry("currency", "JPY")
          .containsEntry("repaired", false);
      assertThat(count()).isEqualTo(1.0);
      assertThat(stored(g, c, "JPY")).isEqualTo(1L);
    } finally {
      corrupt(g, c, "JPY", -30L);
    }
  }

  @Test
  void aCleanLedgerLogsNothingCountsNothingAndWritesNothing() {
    postedGroup();
    // Any mismatch left in the shared database by other tests is repaired first.
    new BalanceCheckService(
            repository,
            transactions,
            new SimpleMeterRegistry(),
            new BalanceCheckProperties("0 30 2 * * *", true))
        .run();
    logs.list.clear();
    String before = balanceRowVersions();

    check(true).run();

    assertThat(mismatchLines()).isEmpty();
    assertThat(logs.list).noneMatch(e -> e.getLevel() == Level.ERROR);
    assertThat(count()).isZero();
    assertThat(balanceRowVersions()).isEqualTo(before);
  }
}
