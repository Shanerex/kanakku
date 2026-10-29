package dev.kanakku.ledger.service;

import static dev.kanakku.jooq.Tables.JOURNAL_ENTRIES;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.kanakku.ledger.api.Balances;
import dev.kanakku.ledger.api.Journal;
import dev.kanakku.ledger.api.JournalEntry;
import dev.kanakku.ledger.api.LedgerConflict;
import dev.kanakku.ledger.api.MemberBalance;
import dev.kanakku.ledger.api.Posted;
import dev.kanakku.support.PostgresIntegrationTest;
import dev.kanakku.support.Seed;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.jooq.DSLContext;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.support.TransactionTemplate;

@SpringBootTest
class JournalIdempotencyTest extends PostgresIntegrationTest {

  @Autowired Journal journal;
  @Autowired Balances balances;
  @Autowired DSLContext dsl;
  @Autowired TransactionTemplate transactions;

  @Test
  void postingTwiceWithTheSameKeyYieldsOneEntryAndUnchangedBalances() {
    List<UUID> ids = Seed.groupWithGuests(dsl, 2);
    UUID g = ids.get(0), a = ids.get(1), b = ids.get(2);
    JournalEntry settlement = JournalEntry.settlement(g, Seed.caller(dsl, g), "INR", a, b, 250L);

    Posted first = journal.post(settlement, "k-1");
    List<MemberBalance> after = balances.ofGroup(g, Seed.caller(dsl, g));
    Posted second = journal.post(settlement, "k-1");

    assertThat(first.replayed()).isFalse();
    assertThat(second).isEqualTo(new Posted(first.entryId(), true));
    assertThat(dsl.fetchCount(JOURNAL_ENTRIES, JOURNAL_ENTRIES.GROUP_ID.eq(g))).isEqualTo(1);
    assertThat(balances.ofGroup(g, Seed.caller(dsl, g))).isEqualTo(after);
  }

  @Test
  void theSameKeyWithDifferentContentIsAConflictAndChangesNothing() {
    List<UUID> ids = Seed.groupWithGuests(dsl, 2);
    UUID g = ids.get(0), a = ids.get(1), b = ids.get(2);
    journal.post(JournalEntry.settlement(g, Seed.caller(dsl, g), "INR", a, b, 250L), "k-1");
    List<MemberBalance> after = balances.ofGroup(g, Seed.caller(dsl, g));

    assertThatThrownBy(
            () ->
                journal.post(
                    JournalEntry.settlement(g, Seed.caller(dsl, g), "INR", a, b, 251L), "k-1"))
        .isInstanceOfSatisfying(
            LedgerConflict.class,
            e -> assertThat(e.reason()).isEqualTo(LedgerConflict.Reason.KEY_REUSED));
    assertThat(dsl.fetchCount(JOURNAL_ENTRIES, JOURNAL_ENTRIES.GROUP_ID.eq(g))).isEqualTo(1);
    assertThat(balances.ofGroup(g, Seed.caller(dsl, g))).isEqualTo(after);
  }

  @Test
  void keysAreScopedToTheGroup() {
    List<UUID> one = Seed.groupWithGuests(dsl, 2);
    List<UUID> two = Seed.groupWithGuests(dsl, 2);

    Posted a =
        journal.post(
            JournalEntry.settlement(
                one.get(0), Seed.caller(dsl, one.get(0)), "INR", one.get(1), one.get(2), 5),
            "same");
    Posted b =
        journal.post(
            JournalEntry.settlement(
                two.get(0), Seed.caller(dsl, two.get(0)), "INR", two.get(1), two.get(2), 5),
            "same");

    assertThat(a.replayed()).isFalse();
    assertThat(b.replayed()).isFalse();
    assertThat(a.entryId()).isNotEqualTo(b.entryId());
  }

  @Test
  void memberEntriesNeedAKeyAndSystemEntriesDoNot() {
    List<UUID> ids = Seed.groupWithGuests(dsl, 2);
    UUID g = ids.get(0), a = ids.get(1), b = ids.get(2);

    assertThatThrownBy(
            () -> journal.post(JournalEntry.settlement(g, Seed.caller(dsl, g), "INR", a, b, 5)))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(
            () ->
                journal.post(JournalEntry.settlement(g, Seed.caller(dsl, g), "INR", a, b, 5), " "))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void aConcurrentPostWithTheSameKeyWaitsAndThenReplays() throws Exception {
    List<UUID> ids = Seed.groupWithGuests(dsl, 2);
    UUID g = ids.get(0), a = ids.get(1), b = ids.get(2);
    JournalEntry settlement = JournalEntry.settlement(g, Seed.caller(dsl, g), "INR", a, b, 40L);
    CountDownLatch posted = new CountDownLatch(1);
    CountDownLatch release = new CountDownLatch(1);

    CompletableFuture<Posted> first =
        CompletableFuture.supplyAsync(
            () ->
                transactions.execute(
                    status -> {
                      Posted p = journal.post(settlement, "race");
                      posted.countDown();
                      await(release);
                      return p;
                    }));
    assertThat(posted.await(10, TimeUnit.SECONDS)).isTrue();
    CompletableFuture<Posted> second =
        CompletableFuture.supplyAsync(() -> journal.post(settlement, "race"));

    // The second waits on the first's uncommitted key until it commits.
    assertThatThrownBy(() -> second.get(500, TimeUnit.MILLISECONDS))
        .isInstanceOf(TimeoutException.class);
    release.countDown();

    Posted p1 = first.get(10, TimeUnit.SECONDS);
    Posted p2 = second.get(10, TimeUnit.SECONDS);
    assertThat(p1.replayed()).isFalse();
    assertThat(p2).isEqualTo(new Posted(p1.entryId(), true));
    assertThat(dsl.fetchCount(JOURNAL_ENTRIES, JOURNAL_ENTRIES.GROUP_ID.eq(g))).isEqualTo(1);
    assertThat(balances.ofGroup(g, Seed.caller(dsl, g)))
        .extracting(MemberBalance::amountMinor)
        .containsExactlyInAnyOrder(40L, -40L);
  }

  static void await(CountDownLatch latch) {
    try {
      if (!latch.await(10, TimeUnit.SECONDS)) {
        throw new IllegalStateException("latch timed out");
      }
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException(e);
    }
  }
}
