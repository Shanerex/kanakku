package dev.kanakku.identity.service;

import java.time.Duration;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

/**
 * A bounded set of Argon2 hash slots. Each hash costs memory and CPU, so a caller past the cap
 * waits for a slot up to a timeout instead of piling more hashes on the server.
 */
abstract class HashSlots {

  private final Semaphore permits;
  private final Duration wait;

  HashSlots(int maxConcurrent, Duration wait) {
    this.permits = new Semaphore(Math.max(0, maxConcurrent), true);
    this.wait = wait;
  }

  /**
   * Waits up to the configured timeout for a slot. The caller must {@link #release()} it when this
   * returns true. An interrupted wait returns false and keeps the thread's interrupt flag.
   */
  boolean acquire() {
    try {
      return permits.tryAcquire(wait.toNanos(), TimeUnit.NANOSECONDS);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      return false;
    }
  }

  void release() {
    permits.release();
  }
}
