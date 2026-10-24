package dev.kanakku.platform.idempotency;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.kanakku.platform.problem.ProblemException;
import dev.kanakku.support.PostgresIntegrationTest;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;
import org.jooq.DSLContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.PlatformTransactionManager;

@SpringBootTest
class IdempotencyServiceTest extends PostgresIntegrationTest {

  @Autowired DSLContext dsl;
  @Autowired PlatformTransactionManager transactions;

  Instant now = Instant.parse("2026-10-23T10:00:00Z");
  Clock clock =
      new Clock() {
        @Override
        public java.time.ZoneId getZone() {
          return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(java.time.ZoneId zone) {
          return this;
        }

        @Override
        public Instant instant() {
          return now;
        }
      };
  IdempotencyService service;
  String key;

  @BeforeEach
  void setUp() {
    service = new IdempotencyService(new IdempotencyKeyRepository(dsl), transactions, clock);
    key = UUID.randomUUID().toString();
  }

  @Test
  void firstCallRunsTheActionAndStoresTheResponse() {
    AtomicInteger runs = new AtomicInteger();

    IdempotencyService.Outcome outcome =
        service.execute(
            "test", key, IdempotencyService.hash("a"), () -> run(runs, 201, "{\"id\":1}"));

    assertThat(outcome.replayed()).isFalse();
    assertThat(outcome.response().status()).isEqualTo(201);
    assertThat(outcome.response().body()).contains("\"id\"");
    assertThat(runs).hasValue(1);
  }

  @Test
  void repeatWithTheSameBodyReplaysTheStoredResponseWithoutRunningAgain() {
    AtomicInteger runs = new AtomicInteger();
    service.execute("test", key, IdempotencyService.hash("a"), () -> run(runs, 201, "{\"id\":1}"));

    IdempotencyService.Outcome second =
        service.execute(
            "test", key, IdempotencyService.hash("a"), () -> run(runs, 201, "{\"id\":2}"));

    assertThat(second.replayed()).isTrue();
    assertThat(second.response().status()).isEqualTo(201);
    assertThat(second.response().body()).contains("1").doesNotContain("2");
    assertThat(runs).hasValue(1);
  }

  @Test
  void sameKeyWithADifferentBodyIsRefusedWith422() {
    AtomicInteger runs = new AtomicInteger();
    service.execute("test", key, IdempotencyService.hash("a"), () -> run(runs, 201, "{}"));

    assertThatThrownBy(
            () ->
                service.execute(
                    "test", key, IdempotencyService.hash("b"), () -> run(runs, 201, "{}")))
        .isInstanceOfSatisfying(
            ProblemException.class, e -> assertThat(e.problem().getStatus()).isEqualTo(422));
    assertThat(runs).hasValue(1);
  }

  @Test
  void theSameKeyInAnotherScopeIsIndependent() {
    AtomicInteger runs = new AtomicInteger();
    service.execute("one", key, IdempotencyService.hash("a"), () -> run(runs, 201, "{}"));
    service.execute("two", key, IdempotencyService.hash("b"), () -> run(runs, 201, "{}"));

    assertThat(runs).hasValue(2);
  }

  @Test
  void aFailedActionStoresNothingSoTheRetryRuns() {
    AtomicInteger runs = new AtomicInteger();
    assertThatThrownBy(
            () ->
                service.execute(
                    "test",
                    key,
                    IdempotencyService.hash("a"),
                    () -> {
                      runs.incrementAndGet();
                      throw new IllegalStateException("boom");
                    }))
        .isInstanceOf(IllegalStateException.class);

    IdempotencyService.Outcome retry =
        service.execute("test", key, IdempotencyService.hash("a"), () -> run(runs, 201, "{}"));

    assertThat(retry.replayed()).isFalse();
    assertThat(runs).hasValue(2);
  }

  @Test
  void aKeyOlderThan24HoursCanBeUsedAgain() {
    AtomicInteger runs = new AtomicInteger();
    service.execute("test", key, IdempotencyService.hash("a"), () -> run(runs, 201, "{}"));

    now = now.plus(Duration.ofHours(23));
    assertThat(
            service
                .execute("test", key, IdempotencyService.hash("a"), () -> run(runs, 201, "{}"))
                .replayed())
        .isTrue();

    now = now.plus(Duration.ofHours(1).plusMinutes(1));
    IdempotencyService.Outcome later =
        service.execute("test", key, IdempotencyService.hash("b"), () -> run(runs, 201, "{}"));

    assertThat(later.replayed()).isFalse();
    assertThat(runs).hasValue(2);
  }

  @Test
  void aRequestNeverDeletesOtherExpiredKeys() {
    AtomicInteger runs = new AtomicInteger();
    String oldKey = UUID.randomUUID().toString();
    service.execute("test", oldKey, IdempotencyService.hash("a"), () -> run(runs, 201, "{}"));
    now = now.plus(Duration.ofHours(25));

    service.execute("test", key, IdempotencyService.hash("b"), () -> run(runs, 201, "{}"));

    assertThat(keyRows(oldKey)).isOne();
  }

  @Test
  void purgeExpiredDeletesKeysPastTheTtlAndKeepsTheRest() {
    AtomicInteger runs = new AtomicInteger();
    String oldKey = UUID.randomUUID().toString();
    service.execute("test", oldKey, IdempotencyService.hash("a"), () -> run(runs, 201, "{}"));
    now = now.plus(Duration.ofHours(25));
    service.execute("test", key, IdempotencyService.hash("b"), () -> run(runs, 201, "{}"));

    int deleted = service.purgeExpired();

    assertThat(deleted).isPositive();
    assertThat(keyRows(oldKey)).isZero();
    assertThat(keyRows(key)).isOne();
  }

  @Test
  void anExpiredKeyThatWasNotPurgedYetIsReplacedNotReplayed() {
    AtomicInteger runs = new AtomicInteger();
    service.execute("test", key, IdempotencyService.hash("a"), () -> run(runs, 201, "{\"n\":1}"));
    now = now.plus(Duration.ofHours(25));

    IdempotencyService.Outcome again =
        service.execute(
            "test", key, IdempotencyService.hash("b"), () -> run(runs, 201, "{\"n\":2}"));

    assertThat(again.replayed()).isFalse();
    assertThat(again.response().body()).contains("2");
    assertThat(keyRows(key)).isOne();
    assertThat(
            service
                .execute("test", key, IdempotencyService.hash("b"), () -> run(runs, 201, "{}"))
                .replayed())
        .isTrue();
  }

  private int keyRows(String k) {
    return dsl.fetchCount(
        dev.kanakku.jooq.Tables.IDEMPOTENCY_KEYS,
        dev.kanakku.jooq.Tables.IDEMPOTENCY_KEYS.KEY.eq(k));
  }

  @Test
  void concurrentRepeatsRunTheActionOnceAndAllGetTheSameResponse() throws Exception {
    AtomicInteger runs = new AtomicInteger();
    int callers = 5;
    CountDownLatch start = new CountDownLatch(1);
    ExecutorService pool = Executors.newFixedThreadPool(callers);
    try {
      List<Future<IdempotencyService.Outcome>> results = new ArrayList<>();
      for (int i = 0; i < callers; i++) {
        results.add(
            pool.submit(
                () -> {
                  start.await();
                  return service.execute(
                      "test",
                      key,
                      IdempotencyService.hash("a"),
                      () -> {
                        sleep(150);
                        return run(runs, 201, "{\"id\":7}");
                      });
                }));
      }
      start.countDown();
      int fresh = 0;
      for (Future<IdempotencyService.Outcome> result : results) {
        IdempotencyService.Outcome outcome = result.get();
        assertThat(outcome.response().status()).isEqualTo(201);
        assertThat(outcome.response().body()).contains("7");
        if (!outcome.replayed()) {
          fresh++;
        }
      }
      assertThat(fresh).isEqualTo(1);
      assertThat(runs).hasValue(1);
    } finally {
      pool.shutdownNow();
    }
  }

  private static IdempotencyService.StoredResponse run(
      AtomicInteger runs, int status, String body) {
    runs.incrementAndGet();
    return new IdempotencyService.StoredResponse(status, body);
  }

  private static void sleep(long millis) {
    try {
      Thread.sleep(millis);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
    }
  }
}
