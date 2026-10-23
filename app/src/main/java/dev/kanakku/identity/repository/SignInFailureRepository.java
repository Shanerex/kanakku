package dev.kanakku.identity.repository;

import static dev.kanakku.jooq.Tables.SIGN_IN_FAILURES;

import java.net.InetAddress;
import java.time.OffsetDateTime;
import java.util.List;
import org.jooq.DSLContext;
import org.jooq.Field;
import org.jooq.impl.DSL;
import org.jooq.postgres.extensions.types.Inet;
import org.springframework.stereotype.Repository;

/** Reads and writes sign-in failures. Holds no throttle rules; those live in SignInThrottle. */
@Repository
public class SignInFailureRepository {

  private final DSLContext dsl;

  SignInFailureRepository(DSLContext dsl) {
    this.dsl = dsl;
  }

  /**
   * The throttle key for an email: stripped here, lower-cased by Postgres {@code lower()} so it
   * matches the lookup on {@code users} exactly (Java and Postgres differ on some Unicode).
   */
  private static Field<String> key(String email) {
    return DSL.lower(DSL.val(email.strip()));
  }

  /** Takes a transaction-scoped advisory lock for one (email, IP) pair. */
  public void lockPair(String email, String ip) {
    dsl.fetch(
        "select pg_advisory_xact_lock(hashtextextended(lower(?) || '|' || ?, 0))",
        email.strip(),
        inet(ip).address().getHostAddress());
  }

  /** Failure times for the pair after {@code since}, oldest first. */
  public List<OffsetDateTime> failureTimesAfter(String email, String ip, OffsetDateTime since) {
    return dsl.select(SIGN_IN_FAILURES.FAILED_AT)
        .from(SIGN_IN_FAILURES)
        .where(SIGN_IN_FAILURES.EMAIL_KEY.eq(key(email)))
        .and(SIGN_IN_FAILURES.IP.eq(inet(ip)))
        .and(SIGN_IN_FAILURES.FAILED_AT.gt(since))
        .orderBy(SIGN_IN_FAILURES.FAILED_AT.asc())
        .fetch(SIGN_IN_FAILURES.FAILED_AT);
  }

  /** Inserts one failure and returns its row id. */
  public long insert(String email, String ip, OffsetDateTime failedAt) {
    return dsl.insertInto(SIGN_IN_FAILURES)
        .set(SIGN_IN_FAILURES.EMAIL_KEY, key(email))
        .set(SIGN_IN_FAILURES.IP, inet(ip))
        .set(SIGN_IN_FAILURES.FAILED_AT, failedAt)
        .returningResult(SIGN_IN_FAILURES.ID)
        .fetchSingle()
        .value1();
  }

  public void delete(long failureId) {
    dsl.deleteFrom(SIGN_IN_FAILURES).where(SIGN_IN_FAILURES.ID.eq(failureId)).execute();
  }

  public void deleteBefore(OffsetDateTime cutoff) {
    dsl.deleteFrom(SIGN_IN_FAILURES).where(SIGN_IN_FAILURES.FAILED_AT.lt(cutoff)).execute();
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
