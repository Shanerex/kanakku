package dev.kanakku.identity.repository;

import static dev.kanakku.jooq.Tables.PENDING_SIGN_UPS;

import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;
import org.jooq.DSLContext;
import org.springframework.stereotype.Repository;

/**
 * Sign-ups waiting for their SMS code. A row holds an Argon2 hash and the HMAC of the code, never a
 * password or a code, and is deleted when the account is made or the row expires.
 */
@Repository
public class PendingSignUpRepository {

  /** A waiting sign-up. */
  public record Pending(
      UUID id,
      String email,
      String displayName,
      String passwordHash,
      String phoneE164,
      byte[] codeHash,
      OffsetDateTime codeExpiresAt,
      boolean smsFailed,
      OffsetDateTime expiresAt) {

    /** Hides the number and the hashes so a logged row carries nothing sensitive. */
    @Override
    public String toString() {
      return "Pending[id=" + id + "]";
    }
  }

  private final DSLContext dsl;

  PendingSignUpRepository(DSLContext dsl) {
    this.dsl = dsl;
  }

  public void insert(Pending p) {
    dsl.insertInto(PENDING_SIGN_UPS)
        .set(PENDING_SIGN_UPS.ID, p.id())
        .set(PENDING_SIGN_UPS.EMAIL, p.email())
        .set(PENDING_SIGN_UPS.DISPLAY_NAME, p.displayName())
        .set(PENDING_SIGN_UPS.PASSWORD_HASH, p.passwordHash())
        .set(PENDING_SIGN_UPS.PHONE_E164, p.phoneE164())
        .set(PENDING_SIGN_UPS.CODE_HASH, p.codeHash())
        .set(PENDING_SIGN_UPS.CODE_EXPIRES_AT, p.codeExpiresAt())
        .set(PENDING_SIGN_UPS.SMS_FAILED, p.smsFailed())
        .set(PENDING_SIGN_UPS.EXPIRES_AT, p.expiresAt())
        .execute();
  }

  /** The sign-up, locked until the transaction ends; the caller checks {@code expiresAt}. */
  public Optional<Pending> findLocked(UUID id) {
    return dsl.selectFrom(PENDING_SIGN_UPS)
        .where(PENDING_SIGN_UPS.ID.eq(id))
        .forUpdate()
        .fetchOptional(
            r ->
                new Pending(
                    r.getId(),
                    r.getEmail(),
                    r.getDisplayName(),
                    r.getPasswordHash(),
                    r.getPhoneE164(),
                    r.getCodeHash(),
                    r.getCodeExpiresAt(),
                    r.getSmsFailed(),
                    r.getExpiresAt()));
  }

  /** The password hash of a live sign-up, for checking a replayed request. */
  public Optional<String> findPasswordHash(UUID id, OffsetDateTime now) {
    return dsl.select(PENDING_SIGN_UPS.PASSWORD_HASH)
        .from(PENDING_SIGN_UPS)
        .where(PENDING_SIGN_UPS.ID.eq(id))
        .and(PENDING_SIGN_UPS.EXPIRES_AT.gt(now))
        .fetchOptional(PENDING_SIGN_UPS.PASSWORD_HASH);
  }

  public void updateCode(
      UUID id,
      byte[] codeHash,
      OffsetDateTime codeExpiresAt,
      boolean smsFailed,
      OffsetDateTime expiresAt) {
    dsl.update(PENDING_SIGN_UPS)
        .set(PENDING_SIGN_UPS.CODE_HASH, codeHash)
        .set(PENDING_SIGN_UPS.CODE_EXPIRES_AT, codeExpiresAt)
        .set(PENDING_SIGN_UPS.SMS_FAILED, smsFailed)
        .set(PENDING_SIGN_UPS.EXPIRES_AT, expiresAt)
        .where(PENDING_SIGN_UPS.ID.eq(id))
        .execute();
  }

  /** Records that the text for the current code could not be sent. */
  public void markSmsFailed(UUID id) {
    dsl.update(PENDING_SIGN_UPS)
        .set(PENDING_SIGN_UPS.SMS_FAILED, true)
        .where(PENDING_SIGN_UPS.ID.eq(id))
        .execute();
  }

  public void delete(UUID id) {
    dsl.deleteFrom(PENDING_SIGN_UPS).where(PENDING_SIGN_UPS.ID.eq(id)).execute();
  }

  /** Deletes sign-ups that expired before {@code now} and returns how many went. */
  public int deleteExpired(OffsetDateTime now) {
    return dsl.deleteFrom(PENDING_SIGN_UPS).where(PENDING_SIGN_UPS.EXPIRES_AT.le(now)).execute();
  }
}
