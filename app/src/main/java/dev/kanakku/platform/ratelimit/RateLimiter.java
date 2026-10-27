package dev.kanakku.platform.ratelimit;

import java.time.Clock;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.OptionalLong;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Counts events per (bucket, key) in a sliding window. {@code bucket} names the rule (for example
 * {@code phone-wrong-code}), {@code key} names who it applies to. Every call takes a
 * transaction-scoped advisory lock on the pair, so a parallel caller cannot slip past the limit
 * between a count and its insert; when the caller has no transaction, each call is its own. A
 * caller that checks, works and then records a hit must do all three in one transaction. Time comes
 * from the injected clock.
 */
@Service
public class RateLimiter {

  /** How long hits are kept; longer than any window in use. */
  static final Duration RETENTION = Duration.ofDays(1);

  private final RateLimitRepository hits;
  private final TransactionTemplate transaction;
  private final Clock clock;

  RateLimiter(RateLimitRepository hits, PlatformTransactionManager transactions, Clock clock) {
    this.hits = hits;
    this.transaction = new TransactionTemplate(transactions);
    this.clock = clock;
  }

  /**
   * Whether the pair is at its limit: {@code max} hits inside the last {@code window}. Records
   * nothing.
   *
   * @return seconds until the oldest counted hit leaves the window, or empty when under the limit
   */
  public OptionalLong check(String bucket, String key, int max, Duration window) {
    return transaction.execute(
        status -> {
          hits.lock(bucket, key);
          return retryAfter(bucket, key, max, window);
        });
  }

  /** Records one hit now. */
  public void hit(String bucket, String key) {
    transaction.executeWithoutResult(
        status -> {
          hits.lock(bucket, key);
          hits.insert(bucket, key, now());
        });
  }

  /**
   * Counts one event unless the pair is at its limit; a refused call records nothing.
   *
   * @return seconds to wait when refused, or empty when the event was counted
   */
  public OptionalLong tryHit(String bucket, String key, int max, Duration window) {
    return transaction.execute(
        status -> {
          hits.lock(bucket, key);
          OptionalLong retry = retryAfter(bucket, key, max, window);
          if (retry.isEmpty()) {
            hits.insert(bucket, key, now());
          }
          return retry;
        });
  }

  /** Forgets every hit of the pair. */
  public void reset(String bucket, String key) {
    transaction.executeWithoutResult(
        status -> {
          hits.lock(bucket, key);
          hits.deleteAll(bucket, key);
        });
  }

  /** Deletes hits past retention and returns how many went; housekeeping for the cleanup job. */
  public int purgeExpired() {
    return hits.deleteBefore(now().minus(RETENTION));
  }

  private OptionalLong retryAfter(String bucket, String key, int max, Duration window) {
    OffsetDateTime now = now();
    List<OffsetDateTime> recent = hits.timesAfter(bucket, key, now.minus(window));
    if (recent.size() < max) {
      return OptionalLong.empty();
    }
    OffsetDateTime unlocks = recent.get(recent.size() - max).plus(window);
    long millis = Duration.between(now, unlocks).toMillis();
    return OptionalLong.of(Math.max(1, (millis + 999) / 1000));
  }

  private OffsetDateTime now() {
    return OffsetDateTime.ofInstant(clock.instant(), ZoneOffset.UTC);
  }
}
