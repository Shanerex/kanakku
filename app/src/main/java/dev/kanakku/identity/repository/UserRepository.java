package dev.kanakku.identity.repository;

import static dev.kanakku.jooq.Tables.PASSWORD_CREDENTIALS;
import static dev.kanakku.jooq.Tables.USERS;

import dev.kanakku.identity.domain.Profile;
import java.util.Optional;
import java.util.UUID;
import org.jooq.DSLContext;
import org.jooq.impl.DSL;
import org.springframework.stereotype.Repository;

/**
 * Reads and writes users and their password credentials. Profile queries are scoped to one user id
 * and never select the hash; only {@link #findCredentials} does.
 */
@Repository
public class UserRepository {

  private final DSLContext dsl;

  UserRepository(DSLContext dsl) {
    this.dsl = dsl;
  }

  /** The user id and password hash for an email in any letter case, or empty when unknown. */
  public Optional<Credentials> findCredentials(String email) {
    return dsl.select(USERS.ID, PASSWORD_CREDENTIALS.PASSWORD_HASH)
        .from(USERS)
        .join(PASSWORD_CREDENTIALS)
        .on(PASSWORD_CREDENTIALS.USER_ID.eq(USERS.ID))
        .where(DSL.lower(USERS.EMAIL).eq(DSL.lower(DSL.val(email.strip()))))
        .fetchOptional(r -> new Credentials(r.value1(), r.value2()));
  }

  /**
   * Inserts an unverified user with the column defaults.
   *
   * @throws org.springframework.dao.DuplicateKeyException when the email is taken in any case
   */
  public void insertUser(UUID id, String email, String displayName) {
    dsl.insertInto(USERS)
        .set(USERS.ID, id)
        .set(USERS.EMAIL, email)
        .set(USERS.DISPLAY_NAME, displayName)
        .execute();
  }

  public void insertCredentials(UUID userId, String passwordHash) {
    dsl.insertInto(PASSWORD_CREDENTIALS)
        .set(PASSWORD_CREDENTIALS.USER_ID, userId)
        .set(PASSWORD_CREDENTIALS.PASSWORD_HASH, passwordHash)
        .execute();
  }

  public Optional<Profile> findProfile(UUID id) {
    return dsl.select(
            USERS.ID,
            USERS.EMAIL,
            USERS.EMAIL_VERIFIED,
            USERS.DISPLAY_NAME,
            USERS.DEFAULT_CURRENCY,
            USERS.UPI_ID,
            USERS.VERSION)
        .from(USERS)
        .where(USERS.ID.eq(id))
        .fetchOptional(
            r ->
                new Profile(
                    r.get(USERS.ID),
                    r.get(USERS.EMAIL),
                    r.get(USERS.EMAIL_VERIFIED),
                    r.get(USERS.DISPLAY_NAME),
                    r.get(USERS.DEFAULT_CURRENCY),
                    r.get(USERS.UPI_ID),
                    r.get(USERS.VERSION)));
  }

  /**
   * Updates the caller's own row if its version still matches.
   *
   * @return the new profile, or empty when the version is stale
   */
  public Optional<Profile> updateProfile(
      UUID callerId, String displayName, String currency, String upiId, int version) {
    int changed =
        dsl.update(USERS)
            .set(USERS.DISPLAY_NAME, displayName)
            .set(USERS.DEFAULT_CURRENCY, currency)
            .set(USERS.UPI_ID, upiId)
            .set(USERS.VERSION, USERS.VERSION.plus(1))
            .set(USERS.UPDATED_AT, DSL.currentOffsetDateTime())
            .where(USERS.ID.eq(callerId))
            .and(USERS.VERSION.eq(version))
            .execute();
    return changed == 0 ? Optional.empty() : findProfile(callerId);
  }

  /** What sign-in needs to check a password. */
  public record Credentials(UUID userId, String passwordHash) {}
}
