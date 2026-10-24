package dev.kanakku.identity.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;

class UnknownEmailHashLimiterTest {

  @Test
  void waitsForAReleasedSlotInsteadOfRefusing() throws Exception {
    UnknownEmailHashLimiter limiter = new UnknownEmailHashLimiter(1, Duration.ofSeconds(10));
    assertThat(limiter.acquire()).isTrue();

    CompletableFuture<Boolean> waiter = CompletableFuture.supplyAsync(limiter::acquire);
    Thread.sleep(100);
    assertThat(waiter).isNotDone();
    limiter.release();

    assertThat(waiter.get(5, TimeUnit.SECONDS)).isTrue();
  }

  @Test
  void givesUpAfterTheTimeoutWhenNoSlotFrees() {
    UnknownEmailHashLimiter limiter = new UnknownEmailHashLimiter(1, Duration.ofMillis(50));
    assertThat(limiter.acquire()).isTrue();

    assertThat(limiter.acquire()).isFalse();
  }

  @Test
  void aZeroCapNeverGrantsASlot() {
    UnknownEmailHashLimiter limiter = new UnknownEmailHashLimiter(0, Duration.ofMillis(20));

    assertThat(limiter.acquire()).isFalse();
  }

  @Test
  void aReleasedSlotCanBeTakenAgain() {
    UnknownEmailHashLimiter limiter = new UnknownEmailHashLimiter(1, Duration.ofMillis(20));
    assertThat(limiter.acquire()).isTrue();
    limiter.release();

    assertThat(limiter.acquire()).isTrue();
  }

  @Test
  void anInterruptedWaitIsRefusedAndKeepsTheInterruptFlag() {
    UnknownEmailHashLimiter limiter = new UnknownEmailHashLimiter(0, Duration.ofSeconds(10));
    Thread.currentThread().interrupt();
    try {
      assertThat(limiter.acquire()).isFalse();
      assertThat(Thread.currentThread().isInterrupted()).isTrue();
    } finally {
      Thread.interrupted();
    }
  }
}
