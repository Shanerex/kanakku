package dev.kanakku.ledger.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.kanakku.ledger.api.Balances;
import dev.kanakku.ledger.api.EntryKind;
import dev.kanakku.ledger.api.History;
import dev.kanakku.ledger.api.HistoryEntry;
import dev.kanakku.ledger.api.Journal;
import dev.kanakku.ledger.api.JournalEntry;
import dev.kanakku.ledger.api.JournalLine;
import dev.kanakku.support.PostgresIntegrationTest;
import dev.kanakku.support.Seed;
import java.util.LinkedHashMap;
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
class JournalHistoryTest extends PostgresIntegrationTest {

  @Autowired Journal journal;
  @Autowired History history;
  @Autowired Balances balances;
  @Autowired DSLContext dsl;
  @Autowired TransactionTemplate transactions;

  @Test
  void afterAnEditHistoryShowsTheOriginalItsReversalAndTheNewEntryInOrder() {
    List<UUID> ids = Seed.groupWithGuests(dsl, 2);
    UUID g = ids.get(0), a = ids.get(1), b = ids.get(2);
    UUID caller = Seed.caller(dsl, g);
    LinkedHashMap<UUID, Long> before = new LinkedHashMap<>();
    before.put(b, 100L);
    UUID original = journal.post(JournalEntry.expense(g, caller, "INR", a, before), "e").entryId();
    LinkedHashMap<UUID, Long> after = new LinkedHashMap<>();
    after.put(b, 120L);
    UUID edited =
        journal
            .replace(g, original, JournalEntry.expense(g, caller, "INR", a, after), "e:edit")
            .entryId();

    List<HistoryEntry> entries = history.ofGroup(g, caller, null, 50);

    assertThat(entries)
        .extracting(HistoryEntry::kind)
        .containsExactly(EntryKind.EXPENSE, EntryKind.REVERSAL, EntryKind.EXPENSE);
    assertThat(entries.get(0).id()).isEqualTo(original);
    assertThat(entries.get(1).reversesEntryId()).isEqualTo(original);
    assertThat(entries.get(1).lines())
        .containsExactly(new JournalLine(a, "INR", -100L), new JournalLine(b, "INR", 100L));
    assertThat(entries.get(2).id()).isEqualTo(edited);
    assertThat(entries.get(2).replacesEntryId()).isEqualTo(original);
    assertThat(entries.get(2).actorUserId()).isEqualTo(caller);
    assertThat(entries).extracting(HistoryEntry::seq).isSorted().doesNotHaveDuplicates();
  }

  @Test
  void historyPagesByTheSeqCursorAndCapsThePageAt200() {
    List<UUID> ids = Seed.groupWithGuests(dsl, 2);
    UUID g = ids.get(0), a = ids.get(1), b = ids.get(2);
    UUID caller = Seed.caller(dsl, g);
    for (int i = 1; i <= 201; i++) {
      journal.post(JournalEntry.settlement(g, Seed.caller(dsl, g), "INR", a, b, i), "s" + i);
    }

    List<HistoryEntry> first = history.ofGroup(g, caller, null, 1000);
    assertThat(first).hasSize(History.MAX_LIMIT);
    List<HistoryEntry> rest = history.ofGroup(g, caller, first.getLast().seq(), 1000);
    assertThat(rest).hasSize(1);
    assertThat(rest.getFirst().lines())
        .containsExactly(new JournalLine(a, "INR", 201L), new JournalLine(b, "INR", -201L));
    assertThat(history.ofGroup(g, caller, rest.getFirst().seq(), 10)).isEmpty();
    assertThatThrownBy(() -> history.ofGroup(g, caller, null, 0))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void aNonMemberGetsNoHistoryAndNoBalances() {
    List<UUID> ids = Seed.groupWithGuests(dsl, 2);
    UUID g = ids.get(0), a = ids.get(1), b = ids.get(2);
    journal.post(JournalEntry.settlement(g, Seed.caller(dsl, g), "INR", a, b, 5L), "s");
    UUID outsider = Seed.user(dsl, "Outsider");
    UUID memberElsewhere = Seed.caller(dsl, Seed.groupWithGuests(dsl, 0).get(0));

    assertThat(history.ofGroup(g, Seed.caller(dsl, g), null, 10)).hasSize(1);
    for (UUID stranger : List.of(outsider, memberElsewhere)) {
      assertThat(history.ofGroup(g, stranger, null, 10)).isEmpty();
      assertThat(balances.ofGroup(g, stranger)).isEmpty();
      assertThat(balances.ofMember(g, a, stranger)).isEmpty();
    }
  }

  @Test
  void aLaterPostWaitsForAnEarlierOneSoSeqOrderIsCommitOrder() throws Exception {
    List<UUID> ids = Seed.groupWithGuests(dsl, 4);
    UUID g = ids.get(0), a = ids.get(1), b = ids.get(2), c = ids.get(3), d = ids.get(4);
    UUID caller = Seed.caller(dsl, g);
    // Disjoint members, so only the group ordering lock can make the second post wait.
    CountDownLatch inserted = new CountDownLatch(1);
    CountDownLatch release = new CountDownLatch(1);
    CompletableFuture<UUID> first =
        CompletableFuture.supplyAsync(
            () ->
                transactions.execute(
                    status -> {
                      UUID id =
                          journal
                              .post(JournalEntry.settlement(g, caller, "INR", a, b, 1L), "first")
                              .entryId();
                      inserted.countDown();
                      try {
                        release.await(10, TimeUnit.SECONDS);
                      } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                      }
                      return id;
                    }));
    assertThat(inserted.await(10, TimeUnit.SECONDS)).isTrue();

    CompletableFuture<UUID> second =
        CompletableFuture.supplyAsync(
            () ->
                journal
                    .post(JournalEntry.settlement(g, caller, "INR", c, d, 2L), "second")
                    .entryId());
    // A history reader that saw the second entry first would page past the first one for good.
    assertThatThrownBy(() -> second.get(500, TimeUnit.MILLISECONDS))
        .isInstanceOf(TimeoutException.class);
    release.countDown();
    UUID firstId = first.get(10, TimeUnit.SECONDS);
    UUID secondId = second.get(10, TimeUnit.SECONDS);

    assertThat(history.ofGroup(g, caller, null, 10))
        .extracting(HistoryEntry::id)
        .containsExactly(firstId, secondId);
  }

  @Test
  void historyPagesUseAGroupAndSeqIndex() {
    assertThat(
            dsl.fetchValues("select indexdef from pg_indexes where tablename = 'journal_entries'"))
        .anyMatch(def -> def.toString().contains("(group_id, seq)"));
  }
}
