package dev.kanakku.identity.internal;

import static dev.kanakku.jooq.Tables.SIGN_IN_FAILURES;

import java.net.InetAddress;
import java.time.Clock;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.OptionalLong;
import org.jooq.DSLContext;
import org.jooq.Field;
import org.jooq.impl.DSL;
import org.jooq.postgres.extensions.types.Inet;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Limits sign-in guesses per (email, IP) pair: after {@value #MAX_FAILURES} failures in the last 10
 * minutes every try is refused until the oldest failure leaves the window. A refused try records
 * nothing and a success does not reset the count. Time comes from the injected clock.
 */
@Component
public class SignInThrottle {

  static final int MAX_FAILURES = 5;
  static final Duration WINDOW = Duration.ofMinutes(10);
  static final Duration RETENTION = Duration.ofHours(1);

  private final DSLContext dsl;
  private final Clock clock;

  SignInThrottle(DSLContext dsl, Clock clock) {
    this.dsl = dsl;
    this.clock = clock;
  }

  private static final Logger log = LoggerFactory.getLogger(SignInThrottle.class);

  /**
   * The throttle key for an email: stripped here, lower-cased by Postgres {@code lower()} so it
   * matches the lookup on {@code users} exactly (Java and Postgres differ on some Unicode).
   */
  private static Field<String> key(String email) {
    return DSL.lower(DSL.val(email.strip()));
  }

  /**
   * Serialises tries for one (email, IP) pair until the surrounding transaction ends, so the count
   * in {@link #retryAfterSeconds} and the insert in {@link #recordFailure} cannot interleave with a
   * parallel try. Must be called inside the sign-in transaction, before the first count.
   */
  public void lock(String email, String ip) {
    dsl.fetch(
        "select pg_advisory_xact_lock(hashtextextended(lower(?) || '|' || ?, 0))",
        email.strip(),
        inet(ip).address().getHostAddress());
  }

  /** Seconds until the next try is allowed, or empty when the pair is not limited. */
  public OptionalLong retryAfterSeconds(String email, String ip) {
    OffsetDateTime now = now();
    List<OffsetDateTime> failures =
        dsl.select(SIGN_IN_FAILURES.FAILED_AT)
            .from(SIGN_IN_FAILURES)
            .where(SIGN_IN_FAILURES.EMAIL_KEY.eq(key(email)))
            .and(SIGN_IN_FAILURES.IP.eq(inet(ip)))
            .and(SIGN_IN_FAILURES.FAILED_AT.gt(now.minus(WINDOW)))
            .orderBy(SIGN_IN_FAILURES.FAILED_AT.asc())
            .fetch(SIGN_IN_FAILURES.FAILED_AT);
    if (failures.size() < MAX_FAILURES) {
      return OptionalLong.empty();
    }
    OffsetDateTime unlocks = failures.get(failures.size() - MAX_FAILURES).plus(WINDOW);
    long millis = Duration.between(now, unlocks).toMillis();
    return OptionalLong.of(Math.max(1, (millis + 999) / 1000));
  }

  /**
   * Counts one try against the pair and returns its row id. Called before the password is hashed,
   * inside the lock, so parallel tries cannot all slip under the limit; {@link #forgive} removes
   * the row again when the password turns out right.
   */
  public long reserveFailure(String email, String ip) {
    return dsl.insertInto(SIGN_IN_FAILURES)
        .set(SIGN_IN_FAILURES.EMAIL_KEY, key(email))
        .set(SIGN_IN_FAILURES.IP, inet(ip))
        .set(SIGN_IN_FAILURES.FAILED_AT, now())
        .returningResult(SIGN_IN_FAILURES.ID)
        .fetchSingle()
        .value1();
  }

  /** Drops a reserved try that turned out to be a correct password. */
  public void forgive(long failureId) {
    dsl.deleteFrom(SIGN_IN_FAILURES).where(SIGN_IN_FAILURES.ID.eq(failureId)).execute();
  }

  /**
   * Deletes failures past retention. Best effort and run outside the sign-in transaction, so an
   * error here is logged and can never lose a count or fail a sign-in.
   */
  public void prune() {
    try {
      dsl.deleteFrom(SIGN_IN_FAILURES)
          .where(SIGN_IN_FAILURES.FAILED_AT.lt(now().minus(RETENTION)))
          .execute();
    } catch (RuntimeException e) {
      log.warn("Pruning old sign-in failures failed; will retry on a later sign-in", e);
    }
  }

  private OffsetDateTime now() {
    return OffsetDateTime.ofInstant(clock.instant(), ZoneOffset.UTC);
  }

  private static Inet inet(String ip) {
    String address = ip == null ? "" : ip;
    int zone = address.indexOf('%');
    if (zone >= 0) {
      address = address.substring(0, zone);
    }
    try {
      return Inet.valueOf(InetAddress.ofLiteral(address));
    } catch (IllegalArgumentException e) {
      return Inet.valueOf(InetAddress.ofLiteral("0.0.0.0"));
    }
  }
}
