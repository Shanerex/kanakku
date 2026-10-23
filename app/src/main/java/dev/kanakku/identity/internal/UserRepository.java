package dev.kanakku.identity.internal;

import static dev.kanakku.jooq.Tables.USERS;

import java.util.Optional;
import java.util.UUID;
import org.jooq.DSLContext;
import org.springframework.stereotype.Repository;

/** Reads and writes users. Every query is scoped to one user id; the hash is never selected. */
@Repository
class UserRepository {

  private final DSLContext dsl;

  UserRepository(DSLContext dsl) {
    this.dsl = dsl;
  }

  Optional<Profile> findProfile(UUID id) {
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
  Optional<Profile> updateProfile(
      UUID callerId, String displayName, String currency, String upiId, int version) {
    int changed =
        dsl.update(USERS)
            .set(USERS.DISPLAY_NAME, displayName)
            .set(USERS.DEFAULT_CURRENCY, currency)
            .set(USERS.UPI_ID, upiId)
            .set(USERS.VERSION, USERS.VERSION.plus(1))
            .set(USERS.UPDATED_AT, org.jooq.impl.DSL.currentOffsetDateTime())
            .where(USERS.ID.eq(callerId))
            .and(USERS.VERSION.eq(version))
            .execute();
    return changed == 0 ? Optional.empty() : findProfile(callerId);
  }
}
