package dev.kanakku.ledger.service;

import static dev.kanakku.jooq.Tables.GROUP_MEMBERS;
import static dev.kanakku.jooq.Tables.JOURNAL_ENTRIES;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.kanakku.ledger.api.Balances;
import dev.kanakku.ledger.api.EntryKind;
import dev.kanakku.ledger.api.Journal;
import dev.kanakku.ledger.api.JournalEntry;
import dev.kanakku.ledger.api.JournalLine;
import dev.kanakku.ledger.api.LedgerConflict;
import dev.kanakku.ledger.api.LedgerConflict.Reason;
import dev.kanakku.ledger.api.MemberBalance;
import dev.kanakku.ledger.api.MemberMerges;
import dev.kanakku.support.PostgresIntegrationTest;
import dev.kanakku.support.Seed;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.jooq.DSLContext;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.support.TransactionTemplate;

/** Every member a keyed entry names must be current, and only member kinds take a key. */
@SpringBootTest
class JournalLineMembersTest extends PostgresIntegrationTest {

  @Autowired Journal journal;
  @Autowired Balances balances;
  @Autowired MemberMerges merges;
  @Autowired DSLContext dsl;
  @Autowired TransactionTemplate transactions;

  static void assertConflict(ThrowingCallable call, Reason reason) {
    assertThatThrownBy(call)
        .isInstanceOfSatisfying(
            LedgerConflict.class, e -> assertThat(e.reason()).isEqualTo(reason));
  }

  static void notDoneYet(CompletableFuture<?> future) {
    assertThatThrownBy(() -> future.get(500, TimeUnit.MILLISECONDS))
        .isInstanceOf(TimeoutException.class);
  }

  static void await(CountDownLatch latch) {
    try {
      latch.await(10, TimeUnit.SECONDS);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
    }
  }

  int entries(UUID g) {
    return dsl.fetchCount(JOURNAL_ENTRIES, JOURNAL_ENTRIES.GROUP_ID.eq(g));
  }

  void leave(UUID memberId) {
    dsl.update(GROUP_MEMBERS)
        .set(GROUP_MEMBERS.LEFT_AT, OffsetDateTime.now())
        .where(GROUP_MEMBERS.ID.eq(memberId))
        .execute();
  }

  @Test
  void aKeyedPostOrEditCannotNameAMemberWhoLeft() {
    List<UUID> ids = Seed.groupWithGuests(dsl, 3);
    UUID g = ids.get(0), a = ids.get(1), b = ids.get(2), c = ids.get(3);
    UUID caller = Seed.caller(dsl, g);
    UUID s = journal.post(JournalEntry.settlement(g, caller, "INR", a, c, 9L), "s").entryId();
    List<MemberBalance> before = balances.ofGroup(g, caller);
    leave(b);

    assertConflict(
        () -> journal.post(JournalEntry.settlement(g, caller, "INR", a, b, 5L), "x"),
        Reason.MEMBER_NOT_CURRENT);
    assertConflict(
        () -> journal.replace(g, s, JournalEntry.settlement(g, caller, "INR", a, b, 7L), "edit"),
        Reason.MEMBER_NOT_CURRENT);

    assertThat(entries(g)).isEqualTo(1);
    assertThat(balances.ofGroup(g, caller)).isEqualTo(before);
  }

  @Test
  void aLeaveWaitsForAPostThatNamesTheLeavingMember() throws Exception {
    List<UUID> ids = Seed.groupWithGuests(dsl, 2);
    UUID g = ids.get(0), a = ids.get(1), b = ids.get(2);
    UUID caller = Seed.caller(dsl, g);
    CountDownLatch posted = new CountDownLatch(1);
    CountDownLatch release = new CountDownLatch(1);
    CompletableFuture<Void> post =
        CompletableFuture.runAsync(
            () ->
                transactions.executeWithoutResult(
                    status -> {
                      journal.post(JournalEntry.settlement(g, caller, "INR", a, b, 5L), "k");
                      posted.countDown();
                      await(release);
                    }));
    assertThat(posted.await(10, TimeUnit.SECONDS)).isTrue();

    CompletableFuture<Void> leave =
        CompletableFuture.runAsync(() -> transactions.executeWithoutResult(s -> leave(b)));
    notDoneYet(leave);
    release.countDown();
    post.get(10, TimeUnit.SECONDS);
    leave.get(10, TimeUnit.SECONDS);

    assertThat(entries(g)).isEqualTo(1);
  }

  @Test
  void aPostWaitsForALeaveInFlightThenSeesThatTheMemberLeft() throws Exception {
    List<UUID> ids = Seed.groupWithGuests(dsl, 2);
    UUID g = ids.get(0), a = ids.get(1), b = ids.get(2);
    UUID caller = Seed.caller(dsl, g);
    CountDownLatch leaving = new CountDownLatch(1);
    CountDownLatch release = new CountDownLatch(1);
    CompletableFuture<Void> leave =
        CompletableFuture.runAsync(
            () ->
                transactions.executeWithoutResult(
                    status -> {
                      leave(b);
                      leaving.countDown();
                      await(release);
                    }));
    assertThat(leaving.await(10, TimeUnit.SECONDS)).isTrue();

    CompletableFuture<?> post =
        CompletableFuture.supplyAsync(
            () -> journal.post(JournalEntry.settlement(g, caller, "INR", a, b, 5L), "k"));
    notDoneYet(post);
    release.countDown();
    leave.get(10, TimeUnit.SECONDS);

    assertThatThrownBy(() -> post.get(10, TimeUnit.SECONDS))
        .hasCauseInstanceOf(LedgerConflict.class);
    assertThat(entries(g)).isZero();
  }

  @Test
  void aReversalIsRefusedWhenALineMemberWasMergedAway() {
    List<UUID> ids = Seed.groupWithGuests(dsl, 3);
    UUID g = ids.get(0), guest = ids.get(1), member = ids.get(2), payer = ids.get(3);
    UUID caller = Seed.caller(dsl, g);
    UUID expense =
        journal
            .post(
                JournalEntry.expense(
                    g, caller, "INR", payer, JournalReversalTest.shares(guest, 500L, member, 100L)),
                "e")
            .entryId();
    merges.merge(g, guest, member, caller);
    dsl.update(GROUP_MEMBERS)
        .set(GROUP_MEMBERS.LEFT_AT, OffsetDateTime.now())
        .set(GROUP_MEMBERS.MERGED_INTO_MEMBER_ID, member)
        .where(GROUP_MEMBERS.ID.eq(guest))
        .execute();
    List<MemberBalance> before = balances.ofGroup(g, caller);

    assertConflict(() -> journal.reverse(g, expense, caller, "del"), Reason.MEMBER_NOT_CURRENT);

    assertThat(entries(g)).isEqualTo(2);
    assertThat(balances.ofGroup(g, caller)).isEqualTo(before);
  }

  @Test
  void aKeyedPostTakesOnlyMemberKinds() {
    List<UUID> ids = Seed.groupWithGuests(dsl, 2);
    UUID g = ids.get(0), a = ids.get(1), b = ids.get(2);
    UUID caller = Seed.caller(dsl, g);
    List<JournalLine> lines =
        List.of(new JournalLine(a, "INR", -9L), new JournalLine(b, "INR", 9L));

    for (EntryKind kind : List.of(EntryKind.WRITE_OFF, EntryKind.MERGE)) {
      assertThatThrownBy(() -> journal.post(new JournalEntry(g, kind, caller, lines), "k"))
          .isInstanceOf(IllegalArgumentException.class);
    }

    assertThat(entries(g)).isZero();
  }
}
