package dev.kanakku.ledger.service;

import static dev.kanakku.jooq.Tables.JOURNAL_ENTRIES;
import static dev.kanakku.jooq.Tables.JOURNAL_LINES;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.kanakku.ledger.api.Balances;
import dev.kanakku.ledger.api.EntryKind;
import dev.kanakku.ledger.api.Journal;
import dev.kanakku.ledger.api.JournalEntry;
import dev.kanakku.ledger.api.JournalLine;
import dev.kanakku.ledger.api.MemberBalance;
import dev.kanakku.ledger.api.MoneyBalance;
import dev.kanakku.support.PostgresIntegrationTest;
import dev.kanakku.support.Seed;
import java.util.List;
import java.util.UUID;
import org.jooq.DSLContext;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataAccessException;

@SpringBootTest
class JournalServiceTest extends PostgresIntegrationTest {

  @Autowired Journal journal;
  @Autowired Balances balances;
  @Autowired DSLContext dsl;

  JournalEntry entry(UUID group, JournalLine... lines) {
    return new JournalEntry(group, EntryKind.EXPENSE, null, List.of(lines));
  }

  @Test
  void aPostWritesTheLinesAndMovesTheBalancesTogether() {
    List<UUID> ids = Seed.groupWithGuests(dsl, 3);
    UUID g = ids.get(0), a = ids.get(1), b = ids.get(2), c = ids.get(3);

    UUID entryId =
        journal.post(
            entry(
                g,
                new JournalLine(a, "INR", 3000),
                new JournalLine(b, "INR", -1500),
                new JournalLine(c, "INR", -1500)));

    assertThat(dsl.fetchCount(JOURNAL_LINES, JOURNAL_LINES.ENTRY_ID.eq(entryId))).isEqualTo(3);
    assertThat(balances.ofMember(g, a)).containsExactly(new MoneyBalance("INR", 3000));
    assertThat(balances.ofMember(g, b)).containsExactly(new MoneyBalance("INR", -1500));
    assertThat(balances.ofGroup(g))
        .extracting(MemberBalance::amountMinor)
        .containsExactlyInAnyOrder(3000L, -1500L, -1500L);
  }

  @Test
  void laterPostsAddToTheRunningBalanceAndCurrenciesStaySeparate() {
    List<UUID> ids = Seed.groupWithGuests(dsl, 2);
    UUID g = ids.get(0), a = ids.get(1), b = ids.get(2);
    journal.post(entry(g, new JournalLine(a, "INR", 100), new JournalLine(b, "INR", -100)));
    journal.post(entry(g, new JournalLine(a, "INR", 50), new JournalLine(b, "INR", -50)));
    journal.post(entry(g, new JournalLine(a, "USD", -7), new JournalLine(b, "USD", 7)));

    assertThat(balances.ofMember(g, a))
        .containsExactlyInAnyOrder(new MoneyBalance("INR", 150), new MoneyBalance("USD", -7));
  }

  @Test
  void anEntryThatDoesNotSumToZeroPerCurrencyIsRefusedAndWritesNothing() {
    List<UUID> ids = Seed.groupWithGuests(dsl, 2);
    UUID g = ids.get(0), a = ids.get(1), b = ids.get(2);

    assertThatThrownBy(
            () ->
                journal.post(
                    entry(g, new JournalLine(a, "INR", 100), new JournalLine(b, "INR", -99))))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(
            () ->
                journal.post(
                    entry(g, new JournalLine(a, "INR", 100), new JournalLine(b, "USD", -100))))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> journal.post(entry(g))).isInstanceOf(IllegalArgumentException.class);

    assertThat(dsl.fetchCount(JOURNAL_ENTRIES, JOURNAL_ENTRIES.GROUP_ID.eq(g))).isZero();
    assertThat(balances.ofGroup(g)).isEmpty();
  }

  @Test
  void aFailureHalfwayRollsBackTheJournalAndTheBalances() {
    List<UUID> ids = Seed.groupWithGuests(dsl, 1);
    UUID g = ids.get(0), a = ids.get(1);
    UUID stranger = UUID.randomUUID();

    assertThatThrownBy(
            () ->
                journal.post(
                    entry(
                        g, new JournalLine(a, "INR", 100), new JournalLine(stranger, "INR", -100))))
        .isInstanceOf(DataAccessException.class);

    assertThat(dsl.fetchCount(JOURNAL_ENTRIES, JOURNAL_ENTRIES.GROUP_ID.eq(g))).isZero();
    assertThat(balances.ofMember(g, a)).isEmpty();
  }

  @Test
  void aLineCannotNameAMemberOfAnotherGroup() {
    List<UUID> one = Seed.groupWithGuests(dsl, 1);
    List<UUID> two = Seed.groupWithGuests(dsl, 1);

    assertThatThrownBy(
            () ->
                journal.post(
                    entry(
                        one.get(0),
                        new JournalLine(one.get(1), "INR", 5),
                        new JournalLine(two.get(1), "INR", -5))))
        .isInstanceOf(DataAccessException.class);
  }

  @Test
  void journalRowsCannotBeUpdatedOrDeleted() {
    List<UUID> ids = Seed.groupWithGuests(dsl, 2);
    UUID g = ids.get(0);
    UUID entryId =
        journal.post(
            entry(
                g,
                new JournalLine(ids.get(1), "INR", 10),
                new JournalLine(ids.get(2), "INR", -10)));

    assertThatThrownBy(
            () ->
                dsl.update(JOURNAL_LINES)
                    .set(JOURNAL_LINES.AMOUNT_MINOR, 1L)
                    .where(JOURNAL_LINES.ENTRY_ID.eq(entryId))
                    .execute())
        .hasMessageContaining("append-only");
    assertThatThrownBy(
            () -> dsl.deleteFrom(JOURNAL_LINES).where(JOURNAL_LINES.ENTRY_ID.eq(entryId)).execute())
        .hasMessageContaining("append-only");
    assertThatThrownBy(
            () -> dsl.deleteFrom(JOURNAL_ENTRIES).where(JOURNAL_ENTRIES.ID.eq(entryId)).execute())
        .hasMessageContaining("append-only");
  }
}
