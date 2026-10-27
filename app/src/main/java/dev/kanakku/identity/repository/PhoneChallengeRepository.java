package dev.kanakku.identity.repository;

import static dev.kanakku.jooq.Tables.PHONE_CHALLENGES;

import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;
import org.jooq.DSLContext;
import org.jooq.impl.DSL;
import org.springframework.stereotype.Repository;

/** The SMS code waiting for a signed-in user's number: one live row per user. */
@Repository
public class PhoneChallengeRepository {

  /** A stored challenge; the code itself is only ever a hash. */
  public record Challenge(String phoneE164, byte[] codeHash, OffsetDateTime expiresAt) {}

  private final DSLContext dsl;

  PhoneChallengeRepository(DSLContext dsl) {
    this.dsl = dsl;
  }

  /** Stores the challenge for the user, replacing any earlier one. */
  public void upsert(UUID userId, String phoneE164, byte[] codeHash, OffsetDateTime expiresAt) {
    dsl.insertInto(PHONE_CHALLENGES)
        .set(PHONE_CHALLENGES.USER_ID, userId)
        .set(PHONE_CHALLENGES.PHONE_E164, phoneE164)
        .set(PHONE_CHALLENGES.CODE_HASH, codeHash)
        .set(PHONE_CHALLENGES.EXPIRES_AT, expiresAt)
        .onConflict(PHONE_CHALLENGES.USER_ID)
        .doUpdate()
        .set(PHONE_CHALLENGES.PHONE_E164, phoneE164)
        .set(PHONE_CHALLENGES.CODE_HASH, codeHash)
        .set(PHONE_CHALLENGES.EXPIRES_AT, expiresAt)
        .set(PHONE_CHALLENGES.CREATED_AT, DSL.currentOffsetDateTime())
        .execute();
  }

  /** The user's challenge, locked until the transaction ends. */
  public Optional<Challenge> findLocked(UUID userId) {
    return dsl.select(
            PHONE_CHALLENGES.PHONE_E164, PHONE_CHALLENGES.CODE_HASH, PHONE_CHALLENGES.EXPIRES_AT)
        .from(PHONE_CHALLENGES)
        .where(PHONE_CHALLENGES.USER_ID.eq(userId))
        .forUpdate()
        .fetchOptional(r -> new Challenge(r.value1(), r.value2(), r.value3()));
  }

  /** The number the user's pending challenge was sent to. */
  public Optional<String> findNumber(UUID userId) {
    return dsl.select(PHONE_CHALLENGES.PHONE_E164)
        .from(PHONE_CHALLENGES)
        .where(PHONE_CHALLENGES.USER_ID.eq(userId))
        .fetchOptional(PHONE_CHALLENGES.PHONE_E164);
  }

  public void delete(UUID userId) {
    dsl.deleteFrom(PHONE_CHALLENGES).where(PHONE_CHALLENGES.USER_ID.eq(userId)).execute();
  }
}
