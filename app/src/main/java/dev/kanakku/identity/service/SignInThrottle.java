package dev.kanakku.identity.service;

import dev.kanakku.identity.repository.SignInFailureRepository;
import dev.kanakku.identity.util.ThrottleIp;
import java.time.Clock;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.OptionalLong;
import org.springframework.stereotype.Component;

/**
 * Limits sign-in guesses per (email, client) pair, where the client is the full IPv4 address or the
 * IPv6 /64 prefix (see {@link ThrottleIp}): after {@value #MAX_FAILURES} failures in the last 10
 * minutes every try is refused until the oldest failure leaves the window. A refused try records
 * nothing and a success does not reset the count. Time comes from the injected clock.
 */
@Component
public class SignInThrottle {

  static final int MAX_FAILURES = 5;
  static final Duration WINDOW = Duration.ofMinutes(10);
  static final Duration RETENTION = Duration.ofHours(1);

  private final SignInFailureRepository failures;
  private final Clock clock;

  SignInThrottle(SignInFailureRepository failures, Clock clock) {
    this.failures = failures;
    this.clock = clock;
  }

  /**
   * Serialises tries for one (email, IP) pair until the surrounding transaction ends, so the count
   * in {@link #retryAfterSeconds} and the insert in {@link #reserveFailure} cannot interleave with
   * a parallel try. Must be called inside the sign-in transaction, before the first count.
   */
  public void lock(String email, String ip) {
    failures.lockPair(email, ThrottleIp.of(ip));
  }

  /** Seconds until the next try is allowed, or empty when the pair is not limited. */
  public OptionalLong retryAfterSeconds(String email, String ip) {
    OffsetDateTime now = now();
    List<OffsetDateTime> recent =
        failures.failureTimesAfter(email, ThrottleIp.of(ip), now.minus(WINDOW));
    if (recent.size() < MAX_FAILURES) {
      return OptionalLong.empty();
    }
    OffsetDateTime unlocks = recent.get(recent.size() - MAX_FAILURES).plus(WINDOW);
    long millis = Duration.between(now, unlocks).toMillis();
    return OptionalLong.of(Math.max(1, (millis + 999) / 1000));
  }

  /**
   * Counts one try against the pair and returns its row id. Called before the password is hashed,
   * inside the lock, so parallel tries cannot all slip under the limit; {@link #forgive} removes
   * the row again when the password turns out right.
   */
  public long reserveFailure(String email, String ip) {
    return failures.insert(email, ThrottleIp.of(ip), now());
  }

  /** Drops a reserved try that turned out to be a correct password. */
  public void forgive(long failureId) {
    failures.delete(failureId);
  }

  /**
   * Deletes failures past retention and returns how many went. Housekeeping for the scheduled
   * cleanup job: sign-in never runs it, and the count only reads rows inside the window.
   */
  public int purgeExpired() {
    return failures.deleteBefore(now().minus(RETENTION));
  }

  private OffsetDateTime now() {
    return OffsetDateTime.ofInstant(clock.instant(), ZoneOffset.UTC);
  }
}
