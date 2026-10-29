package dev.kanakku.ledger.service;

import static dev.kanakku.jooq.Tables.GROUP_MEMBERS;
import static dev.kanakku.jooq.Tables.JOURNAL_ENTRIES;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.kanakku.ledger.api.Balances;
import dev.kanakku.ledger.api.Journal;
import dev.kanakku.ledger.api.JournalEntry;
import dev.kanakku.ledger.api.LedgerConflict;
import dev.kanakku.ledger.api.LedgerConflict.Reason;
import dev.kanakku.ledger.api.MemberBalance;
import dev.kanakku.ledger.api.Posted;
import dev.kanakku.ledger.repository.JournalRepository;
import dev.kanakku.support.PostgresIntegrationTest;
import dev.kanakku.support.Seed;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.jooq.DSLContext;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.support.TransactionTemplate;

/** The keyed write path only acts for a current member, and keys are per actor and bounded. */
@SpringBootTest
class JournalAccessTest extends PostgresIntegrationTest {

  @Autowired Journal journal;
  @Autowired Balances balances;
  @Autowired DSLContext dsl;
  @Autowired JournalRepository repository;
  @Autowired TransactionTemplate transactions;

  static void assertConflict(ThrowingCallable call, Reason reason) {
    assertThatThrownBy(call)
        .isInstanceOfSatisfying(
            LedgerConflict.class, e -> assertThat(e.reason()).isEqualTo(reason));
  }

  int entries(UUID g) {
    return dsl.fetchCount(JOURNAL_ENTRIES, JOURNAL_ENTRIES.GROUP_ID.eq(g));
  }

  UUID member(UUID g) {
    UUID user = Seed.user(dsl, "Member");
    dsl.insertInto(GROUP_MEMBERS)
        .set(GROUP_MEMBERS.ID, UUID.randomUUID())
        .set(GROUP_MEMBERS.GROUP_ID, g)
        .set(GROUP_MEMBERS.USER_ID, user)
        .set(GROUP_MEMBERS.ROLE, "MEMBER")
        .execute();
    return user;
  }

  @Test
  void aNonMemberCannotPostReverseOrReplace() {
    List<UUID> ids = Seed.groupWithGuests(dsl, 2);
    UUID g = ids.get(0), a = ids.get(1), b = ids.get(2);
    UUID caller = Seed.caller(dsl, g);
    UUID outsider = Seed.user(dsl, "Outsider");
    UUID s = journal.post(JournalEntry.settlement(g, caller, "INR", a, b, 9L), "s").entryId();
    List<MemberBalance> before = balances.ofGroup(g, caller);

    assertConflict(
        () -> journal.post(JournalEntry.settlement(g, outsider, "INR", a, b, 5L), "x"),
        Reason.NOT_A_MEMBER);
    assertConflict(() -> journal.reverse(g, s, outsider, "del"), Reason.NOT_A_MEMBER);
    assertConflict(
        () -> journal.replace(g, s, JournalEntry.settlement(g, outsider, "INR", a, b, 7L), "edit"),
        Reason.NOT_A_MEMBER);
    // A non-member cannot replay a member's key either.
    assertConflict(
        () -> journal.post(JournalEntry.settlement(g, outsider, "INR", a, b, 9L), "s"),
        Reason.NOT_A_MEMBER);

    assertThat(entries(g)).isEqualTo(1);
    assertThat(balances.ofGroup(g, caller)).isEqualTo(before);
  }

  @Test
  void aMemberWhoLeftCannotPostAndKeyedPostsNeedAnActor() {
    List<UUID> ids = Seed.groupWithGuests(dsl, 2);
    UUID g = ids.get(0), a = ids.get(1), b = ids.get(2);
    Seed.caller(dsl, g);
    UUID leaver = member(g);
    dsl.update(GROUP_MEMBERS)
        .set(GROUP_MEMBERS.LEFT_AT, OffsetDateTime.now())
        .where(GROUP_MEMBERS.USER_ID.eq(leaver))
        .execute();

    assertConflict(
        () -> journal.post(JournalEntry.settlement(g, leaver, "INR", a, b, 5L), "x"),
        Reason.NOT_A_MEMBER);
    assertThatThrownBy(() -> journal.post(JournalEntry.settlement(g, null, "INR", a, b, 5L), "y"))
        .isInstanceOf(IllegalArgumentException.class);
    assertThat(entries(g)).isZero();
  }

  @Test
  void aMemberFromAnotherGroupCannotReverseItsEntries() {
    List<UUID> one = Seed.groupWithGuests(dsl, 2);
    List<UUID> two = Seed.groupWithGuests(dsl, 2);
    UUID g = one.get(0);
    UUID s =
        journal
            .post(
                JournalEntry.settlement(g, Seed.caller(dsl, g), "INR", one.get(1), one.get(2), 9L),
                "s")
            .entryId();
    UUID otherCaller = Seed.caller(dsl, two.get(0));

    assertConflict(() -> journal.reverse(g, s, otherCaller, "del"), Reason.NOT_A_MEMBER);
    assertThat(entries(g)).isEqualTo(1);
  }

  @Test
  void anotherMemberReusingAKeyGetsAConflictNotTheFirstMembersEntry() {
    List<UUID> ids = Seed.groupWithGuests(dsl, 2);
    UUID g = ids.get(0), a = ids.get(1), b = ids.get(2);
    UUID first = Seed.caller(dsl, g);
    UUID second = member(g);

    Posted posted = journal.post(JournalEntry.settlement(g, first, "INR", a, b, 9L), "k");

    assertConflict(
        () -> journal.post(JournalEntry.settlement(g, second, "INR", a, b, 9L), "k"),
        Reason.KEY_REUSED);
    assertThat(journal.post(JournalEntry.settlement(g, first, "INR", a, b, 9L), "k"))
        .isEqualTo(new Posted(posted.entryId(), true));
    assertThat(entries(g)).isEqualTo(1);
  }

  @Test
  void anOversizedKeyIsAValidationErrorAndPostsNothing() {
    List<UUID> ids = Seed.groupWithGuests(dsl, 2);
    UUID g = ids.get(0), a = ids.get(1), b = ids.get(2);
    UUID caller = Seed.caller(dsl, g);
    String longest = "k".repeat(255);
    String tooLong = "k".repeat(256);

    UUID s = journal.post(JournalEntry.settlement(g, caller, "INR", a, b, 9L), longest).entryId();

    assertThatThrownBy(
            () -> journal.post(JournalEntry.settlement(g, caller, "INR", a, b, 5L), tooLong))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> journal.reverse(g, s, caller, tooLong))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(
            () ->
                journal.replace(g, s, JournalEntry.settlement(g, caller, "INR", a, b, 5L), tooLong))
        .isInstanceOf(IllegalArgumentException.class);
    assertThat(entries(g)).isEqualTo(1);
  }

  /** The leave as the groups module writes it: a plain UPDATE of left_at, no key column. */
  int leave(UUID g, UUID user) {
    return transactions.execute(
        status ->
            dsl.update(GROUP_MEMBERS)
                .set(GROUP_MEMBERS.LEFT_AT, OffsetDateTime.now())
                .set(GROUP_MEMBERS.VERSION, GROUP_MEMBERS.VERSION.plus(1))
                .where(GROUP_MEMBERS.GROUP_ID.eq(g))
                .and(GROUP_MEMBERS.USER_ID.eq(user))
                .and(GROUP_MEMBERS.LEFT_AT.isNull())
                .execute());
  }

  @Test
  void aConcurrentLeaveWaitsForTheInFlightPostToCommit() throws Exception {
    List<UUID> ids = Seed.groupWithGuests(dsl, 2);
    UUID g = ids.get(0), a = ids.get(1), b = ids.get(2);
    UUID caller = Seed.caller(dsl, g);
    UUID leaver = member(g);
    CountDownLatch posted = new CountDownLatch(1);
    CountDownLatch commit = new CountDownLatch(1);

    CompletableFuture<Posted> post =
        CompletableFuture.supplyAsync(
            () ->
                transactions.execute(
                    status -> {
                      Posted p =
                          journal.post(
                              JournalEntry.settlement(g, leaver, "INR", a, b, 5L), "in-flight");
                      posted.countDown();
                      JournalIdempotencyTest.await(commit);
                      return p;
                    }));
    assertThat(posted.await(20, TimeUnit.SECONDS)).isTrue();
    CompletableFuture<Integer> left = CompletableFuture.supplyAsync(() -> leave(g, leaver));
    BalanceRepairConcurrencyTest.notDoneYet(left);
    commit.countDown();

    assertThat(post.get(20, TimeUnit.SECONDS).replayed()).isFalse();
    assertThat(left.get(20, TimeUnit.SECONDS)).isEqualTo(1);
    assertThat(entries(g)).isEqualTo(1);
    assertThat(balances.ofGroup(g, caller).stream().mapToLong(MemberBalance::amountMinor).sum())
        .isZero();
    assertConflict(
        () -> journal.post(JournalEntry.settlement(g, leaver, "INR", a, b, 5L), "after"),
        Reason.NOT_A_MEMBER);
  }

  @Test
  void aPostWaitsForAnInFlightLeaveAndIsRefusedOnceItCommits() throws Exception {
    List<UUID> ids = Seed.groupWithGuests(dsl, 2);
    UUID g = ids.get(0), a = ids.get(1), b = ids.get(2);
    Seed.caller(dsl, g);
    UUID leaver = member(g);
    CountDownLatch updated = new CountDownLatch(1);
    CountDownLatch commit = new CountDownLatch(1);

    CompletableFuture<Integer> left =
        CompletableFuture.supplyAsync(
            () ->
                transactions.execute(
                    status -> {
                      int rows = leave(g, leaver);
                      updated.countDown();
                      JournalIdempotencyTest.await(commit);
                      return rows;
                    }));
    assertThat(updated.await(20, TimeUnit.SECONDS)).isTrue();
    CompletableFuture<Posted> post =
        CompletableFuture.supplyAsync(
            () -> journal.post(JournalEntry.settlement(g, leaver, "INR", a, b, 5L), "late"));
    BalanceRepairConcurrencyTest.notDoneYet(post);
    commit.countDown();

    assertThat(left.get(20, TimeUnit.SECONDS)).isEqualTo(1);
    assertThatThrownBy(() -> post.get(20, TimeUnit.SECONDS))
        .hasCauseInstanceOf(LedgerConflict.class);
    assertThat(entries(g)).isZero();
  }

  @Test
  void theReversalLookupIsScopedToTheCallersGroup() {
    List<UUID> one = Seed.groupWithGuests(dsl, 2);
    List<UUID> two = Seed.groupWithGuests(dsl, 2);
    UUID g = one.get(0);
    UUID caller = Seed.caller(dsl, g);
    UUID s =
        journal
            .post(JournalEntry.settlement(g, caller, "INR", one.get(1), one.get(2), 9L), "s")
            .entryId();
    journal.reverse(g, s, caller, "del");

    assertThat(repository.hasReversal(g, s, caller)).isTrue();
    assertThat(repository.hasReversal(g, s, Seed.user(dsl, "Outsider"))).isFalse();
    assertThat(repository.hasReversal(g, s, Seed.caller(dsl, two.get(0)))).isFalse();
  }
}
