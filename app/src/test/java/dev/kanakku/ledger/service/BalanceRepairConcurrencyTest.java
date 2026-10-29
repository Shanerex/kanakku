package dev.kanakku.ledger.service;

import static dev.kanakku.jooq.Tables.MEMBER_BALANCES;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;

import dev.kanakku.ledger.api.Balances;
import dev.kanakku.ledger.api.Journal;
import dev.kanakku.ledger.api.JournalEntry;
import dev.kanakku.ledger.api.MemberBalance;
import dev.kanakku.ledger.api.Posted;
import dev.kanakku.ledger.repository.JournalRepository;
import dev.kanakku.support.PostgresIntegrationTest;
import dev.kanakku.support.Seed;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.jooq.DSLContext;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.transaction.support.TransactionTemplate;

/** A repair and a post to the same group, in both orders, end consistent and never deadlock. */
@SpringBootTest
class BalanceRepairConcurrencyTest extends PostgresIntegrationTest {

  @Autowired Journal journal;
  @Autowired Balances balances;
  @Autowired BalanceCheckService check;
  @Autowired TransactionTemplate transactions;
  @Autowired DSLContext dsl;
  @MockitoSpyBean JournalRepository repository;

  List<UUID> group() {
    List<UUID> ids = Seed.groupWithGuests(dsl, 3);
    UUID g = ids.get(0), a = ids.get(1), b = ids.get(2), c = ids.get(3);
    LinkedHashMap<UUID, Long> shares = new LinkedHashMap<>();
    shares.put(b, 500L);
    shares.put(c, 250L);
    journal.post(JournalEntry.expense(g, Seed.caller(dsl, g), "INR", a, shares), "e");
    journal.post(JournalEntry.settlement(g, Seed.caller(dsl, g), "KWD", c, a, 1234L), "s");
    // A corrupt row puts the group on the repair list.
    dsl.update(MEMBER_BALANCES)
        .set(MEMBER_BALANCES.BALANCE_MINOR, 1L)
        .where(MEMBER_BALANCES.GROUP_ID.eq(g))
        .and(MEMBER_BALANCES.MEMBER_ID.eq(b))
        .and(MEMBER_BALANCES.CURRENCY.eq("INR"))
        .execute();
    return ids;
  }

  void assertConsistent(UUID g) {
    assertThat(repository.mismatchesOf(g)).isEmpty();
    Map<String, Long> sums = new TreeMap<>();
    for (MemberBalance balance : balances.ofGroup(g, Seed.caller(dsl, g))) {
      sums.merge(balance.currency(), balance.amountMinor(), Math::addExact);
    }
    assertThat(sums).containsOnlyKeys("INR", "KWD");
    assertThat(sums.values()).containsOnly(0L);
  }

  static void notDoneYet(CompletableFuture<?> future) {
    assertThatThrownBy(() -> future.get(500, TimeUnit.MILLISECONDS))
        .isInstanceOf(TimeoutException.class);
  }

  @Test
  void aPostWaitsWhileTheRepairHoldsTheGroupLock() throws Exception {
    List<UUID> ids = group();
    UUID g = ids.get(0), a = ids.get(1), b = ids.get(2);
    CountDownLatch locked = new CountDownLatch(1);
    CountDownLatch go = new CountDownLatch(1);
    doAnswer(
            invocation -> {
              invocation.callRealMethod();
              if (g.equals(invocation.getArgument(0))) {
                locked.countDown();
                JournalIdempotencyTest.await(go);
              }
              return null;
            })
        .when(repository)
        .lockGroupExclusive(any());

    CompletableFuture<Void> repair = CompletableFuture.runAsync(check::run);
    assertThat(locked.await(20, TimeUnit.SECONDS)).isTrue();
    CompletableFuture<Posted> post =
        CompletableFuture.supplyAsync(
            () ->
                journal.post(
                    JournalEntry.settlement(g, Seed.caller(dsl, g), "INR", b, a, 77L), "during"));
    notDoneYet(post);
    go.countDown();

    repair.get(20, TimeUnit.SECONDS);
    assertThat(post.get(20, TimeUnit.SECONDS).replayed()).isFalse();
    assertConsistent(g);
  }

  @Test
  void theRepairWaitsWhileAPostHoldsTheSharedLock() throws Exception {
    List<UUID> ids = group();
    UUID g = ids.get(0), a = ids.get(1), b = ids.get(2);
    CountDownLatch posted = new CountDownLatch(1);
    CountDownLatch commit = new CountDownLatch(1);

    CompletableFuture<Posted> post =
        CompletableFuture.supplyAsync(
            () ->
                transactions.execute(
                    status -> {
                      Posted p =
                          journal.post(
                              JournalEntry.settlement(g, Seed.caller(dsl, g), "INR", b, a, 55L),
                              "open");
                      posted.countDown();
                      JournalIdempotencyTest.await(commit);
                      return p;
                    }));
    assertThat(posted.await(20, TimeUnit.SECONDS)).isTrue();
    CompletableFuture<Void> repair = CompletableFuture.runAsync(check::run);
    notDoneYet(repair);
    commit.countDown();

    assertThat(post.get(20, TimeUnit.SECONDS).replayed()).isFalse();
    repair.get(20, TimeUnit.SECONDS);
    assertConsistent(g);
  }
}
