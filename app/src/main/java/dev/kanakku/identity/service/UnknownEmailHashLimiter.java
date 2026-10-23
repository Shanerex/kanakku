package dev.kanakku.identity.service;

import java.util.concurrent.Semaphore;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Caps how many dummy Argon2 hashes (sign-ins for an unknown email) run at once. The per-(email,
 * IP) throttle cannot stop an attacker who rotates random emails, and each hash costs memory and
 * CPU; past the cap the caller gets a 503 instead of queuing. Known emails are never limited here,
 * so real members can still sign in during such a flood.
 */
@Component
public class UnknownEmailHashLimiter {

  private final Semaphore permits;

  UnknownEmailHashLimiter(
      @Value("${kanakku.auth.max-concurrent-unknown-hashes:4}") int maxConcurrent) {
    this.permits = new Semaphore(Math.max(0, maxConcurrent));
  }

  /** Takes a slot without waiting; the caller must {@link #release()} it when true. */
  boolean tryAcquire() {
    return permits.tryAcquire();
  }

  void release() {
    permits.release();
  }
}
