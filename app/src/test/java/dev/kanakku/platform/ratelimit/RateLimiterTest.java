package dev.kanakku.platform.ratelimit;

import static org.assertj.core.api.Assertions.assertThat;

import dev.kanakku.support.PostgresIntegrationTest;
import dev.kanakku.support.TestClockConfig;
import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.IntStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

@SpringBootTest
@Import(TestClockConfig.class)
class RateLimiterTest extends PostgresIntegrationTest {

  static final Duration WINDOW = Duration.ofMinutes(15);

  @Autowired RateLimiter limiter;

  String key = "";

  @BeforeEach
  void freshKey() {
    TestClockConfig.reset();
    key = UUID.randomUUID().toString();
  }

  @Test
  void allowsUpToTheLimitAndThenRefusesWithARetryAfter() {
    for (int i = 0; i < 5; i++) {
      assertThat(limiter.tryHit("test-bucket", key, 5, WINDOW)).isEmpty();
    }
    var retry = limiter.tryHit("test-bucket", key, 5, WINDOW);
    assertThat(retry).isPresent();
    assertThat(retry.getAsLong()).isEqualTo(WINDOW.toSeconds());
  }

  @Test
  void aRefusedTryRecordsNothing() {
    limiter.tryHit("test-bucket", key, 1, WINDOW);
    limiter.tryHit("test-bucket", key, 1, WINDOW);
    limiter.tryHit("test-bucket", key, 1, WINDOW);

    TestClockConfig.CLOCK.advance(WINDOW.plusSeconds(1));
    assertThat(limiter.tryHit("test-bucket", key, 1, WINDOW)).isEmpty();
  }

  @Test
  void retryAfterShrinksAsTimePassesAndTheWindowSlides() {
    limiter.tryHit("test-bucket", key, 2, WINDOW);
    TestClockConfig.CLOCK.advance(Duration.ofMinutes(5));
    limiter.tryHit("test-bucket", key, 2, WINDOW);
    TestClockConfig.CLOCK.advance(Duration.ofMinutes(5));

    assertThat(limiter.check("test-bucket", key, 2, WINDOW).getAsLong())
        .isEqualTo(Duration.ofMinutes(5).toSeconds());

    TestClockConfig.CLOCK.advance(Duration.ofMinutes(5));
    assertThat(limiter.check("test-bucket", key, 2, WINDOW)).isEmpty();
  }

  @Test
  void checkRecordsNothingAndHitCountsWithoutChecking() {
    for (int i = 0; i < 5; i++) {
      assertThat(limiter.check("test-bucket", key, 1, WINDOW)).isEmpty();
    }
    limiter.hit("test-bucket", key);
    assertThat(limiter.check("test-bucket", key, 1, WINDOW)).isPresent();
  }

  @Test
  void bucketsAndKeysAreIndependent() {
    limiter.tryHit("bucket-a", key, 1, WINDOW);

    assertThat(limiter.tryHit("bucket-b", key, 1, WINDOW)).isEmpty();
    assertThat(limiter.tryHit("bucket-a", key + "x", 1, WINDOW)).isEmpty();
    assertThat(limiter.tryHit("bucket-a", key, 1, WINDOW)).isPresent();
  }

  @Test
  void resetForgetsThePair() {
    limiter.tryHit("test-bucket", key, 1, WINDOW);
    limiter.reset("test-bucket", key);

    assertThat(limiter.tryHit("test-bucket", key, 1, WINDOW)).isEmpty();
  }

  @Test
  void parallelTriesNeverExceedTheLimit() {
    AtomicInteger allowed = new AtomicInteger();
    try (var pool = Executors.newFixedThreadPool(12)) {
      IntStream.range(0, 30)
          .mapToObj(
              i ->
                  CompletableFuture.runAsync(
                      () -> {
                        if (limiter.tryHit("race-bucket", key, 4, WINDOW).isEmpty()) {
                          allowed.incrementAndGet();
                        }
                      },
                      pool))
          .toList()
          .forEach(CompletableFuture::join);
    }
    assertThat(allowed.get()).isEqualTo(4);
  }

  @Test
  void purgeDeletesHitsPastRetentionOnly() {
    limiter.hit("purge-bucket", key);
    TestClockConfig.CLOCK.advance(RateLimiter.RETENTION.plusHours(1));
    limiter.hit("purge-bucket", key + "-new");

    assertThat(limiter.purgeExpired()).isGreaterThanOrEqualTo(1);
    // The recent hit survives: its pair is still at a limit of one.
    assertThat(limiter.check("purge-bucket", key + "-new", 1, WINDOW)).isPresent();
  }
}
