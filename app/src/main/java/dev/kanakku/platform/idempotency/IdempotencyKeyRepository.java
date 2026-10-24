package dev.kanakku.platform.idempotency;

import static dev.kanakku.jooq.Tables.IDEMPOTENCY_KEYS;

import java.time.OffsetDateTime;
import java.util.Optional;
import org.jooq.DSLContext;
import org.jooq.JSONB;
import org.springframework.stereotype.Repository;

/** Reads and writes idempotency key rows. The replay rules live in IdempotencyService. */
@Repository
class IdempotencyKeyRepository {

  /** A stored key row; {@code status} is 0 and {@code body} null until the action finishes. */
  record Stored(byte[] requestHash, int status, String body) {}

  private final DSLContext dsl;

  IdempotencyKeyRepository(DSLContext dsl) {
    this.dsl = dsl;
  }

  /** Deletes keys created before the cutoff and returns how many went. */
  int deleteCreatedBefore(OffsetDateTime cutoff) {
    return dsl.deleteFrom(IDEMPOTENCY_KEYS).where(IDEMPOTENCY_KEYS.CREATED_AT.lt(cutoff)).execute();
  }

  /**
   * Claims the key; false when a live row already exists (waits on the pk if another tx holds it).
   * A row created before {@code expiredBefore} is dead whether or not the cleanup job has removed
   * it yet, so it is replaced in the same statement.
   */
  boolean claim(
      String scope,
      String key,
      byte[] requestHash,
      OffsetDateTime createdAt,
      OffsetDateTime expiredBefore) {
    return dsl.insertInto(IDEMPOTENCY_KEYS)
            .set(IDEMPOTENCY_KEYS.SCOPE, scope)
            .set(IDEMPOTENCY_KEYS.KEY, key)
            .set(IDEMPOTENCY_KEYS.REQUEST_HASH, requestHash)
            .set(IDEMPOTENCY_KEYS.STATUS, 0)
            .set(IDEMPOTENCY_KEYS.CREATED_AT, createdAt)
            .onConflict(IDEMPOTENCY_KEYS.SCOPE, IDEMPOTENCY_KEYS.KEY)
            .doUpdate()
            .set(IDEMPOTENCY_KEYS.REQUEST_HASH, requestHash)
            .set(IDEMPOTENCY_KEYS.STATUS, 0)
            .setNull(IDEMPOTENCY_KEYS.RESPONSE)
            .set(IDEMPOTENCY_KEYS.CREATED_AT, createdAt)
            .where(IDEMPOTENCY_KEYS.CREATED_AT.lt(expiredBefore))
            .execute()
        > 0;
  }

  Optional<Stored> find(String scope, String key) {
    return dsl.select(
            IDEMPOTENCY_KEYS.REQUEST_HASH, IDEMPOTENCY_KEYS.STATUS, IDEMPOTENCY_KEYS.RESPONSE)
        .from(IDEMPOTENCY_KEYS)
        .where(IDEMPOTENCY_KEYS.SCOPE.eq(scope).and(IDEMPOTENCY_KEYS.KEY.eq(key)))
        .fetchOptional(
            r -> {
              JSONB body = r.value3();
              return new Stored(r.value1(), r.value2(), body == null ? null : body.data());
            });
  }

  void storeResponse(String scope, String key, int status, String body) {
    dsl.update(IDEMPOTENCY_KEYS)
        .set(IDEMPOTENCY_KEYS.STATUS, status)
        .set(IDEMPOTENCY_KEYS.RESPONSE, body == null ? null : JSONB.valueOf(body))
        .where(IDEMPOTENCY_KEYS.SCOPE.eq(scope).and(IDEMPOTENCY_KEYS.KEY.eq(key)))
        .execute();
  }
}
