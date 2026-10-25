package dev.kanakku.identity.repository;

import static dev.kanakku.jooq.Tables.PASSWORD_CREDENTIALS;
import static dev.kanakku.jooq.Tables.USERS;

import dev.kanakku.identity.domain.DirectAdd;
import dev.kanakku.identity.domain.Profile;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.jooq.Condition;
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
            USERS.DIRECT_ADD,
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
                    DirectAdd.valueOf(r.get(USERS.DIRECT_ADD)),
                    r.get(USERS.VERSION)));
  }

  /**
   * Updates the caller's own row if its version still matches, and returns the row it wrote. One
   * statement ({@code update ... returning}), so a concurrent edit can never be read back instead.
   *
   * @return the new profile, or empty when the version is stale
   */
  public Optional<Profile> updateProfile(
      UUID callerId,
      String displayName,
      String currency,
      String upiId,
      DirectAdd directAdd,
      int version) {
    return dsl.update(USERS)
        .set(USERS.DISPLAY_NAME, displayName)
        .set(USERS.DEFAULT_CURRENCY, currency)
        .set(USERS.UPI_ID, upiId)
        .set(USERS.DIRECT_ADD, directAdd == null ? USERS.DIRECT_ADD : DSL.val(directAdd.name()))
        .set(USERS.VERSION, USERS.VERSION.plus(1))
        .set(USERS.UPDATED_AT, DSL.currentOffsetDateTime())
        .where(USERS.ID.eq(callerId))
        .and(USERS.VERSION.eq(version))
        .returning(
            USERS.ID,
            USERS.EMAIL,
            USERS.EMAIL_VERIFIED,
            USERS.DISPLAY_NAME,
            USERS.DEFAULT_CURRENCY,
            USERS.UPI_ID,
            USERS.DIRECT_ADD,
            USERS.VERSION)
        .fetchOptional()
        .map(
            r ->
                new Profile(
                    r.get(USERS.ID),
                    r.get(USERS.EMAIL),
                    r.get(USERS.EMAIL_VERIFIED),
                    r.get(USERS.DISPLAY_NAME),
                    r.get(USERS.DEFAULT_CURRENCY),
                    r.get(USERS.UPI_ID),
                    DirectAdd.valueOf(r.get(USERS.DIRECT_ADD)),
                    r.get(USERS.VERSION)));
  }

  /** A directory hit: raw email, which the caller must mask before it leaves identity. */
  public record Hit(UUID id, String displayName, String email) {}

  /**
   * Users who allow direct add whose name or email contains {@code needle} (already escaped for
   * LIKE with a backslash), ordered by name then id.
   */
  public List<Hit> searchDirectAddable(String likePattern, Collection<UUID> exclude, int limit) {
    Condition match =
        USERS
            .DISPLAY_NAME
            .likeIgnoreCase(likePattern, '\\')
            .or(USERS.EMAIL.likeIgnoreCase(likePattern, '\\'));
    Condition where = USERS.DIRECT_ADD.eq(DirectAdd.ALLOW.name()).and(match);
    if (!exclude.isEmpty()) {
      where = where.and(USERS.ID.notIn(exclude));
    }
    return dsl.select(USERS.ID, USERS.DISPLAY_NAME, USERS.EMAIL)
        .from(USERS)
        .where(where)
        .orderBy(USERS.DISPLAY_NAME, USERS.ID)
        .limit(limit)
        .fetch(r -> new Hit(r.value1(), r.value2(), r.value3()));
  }

  /** Display names for the given ids; ids that do not exist are absent. */
  public Map<UUID, String> findDisplayNames(Collection<UUID> ids) {
    if (ids.isEmpty()) {
      return Map.of();
    }
    return dsl.select(USERS.ID, USERS.DISPLAY_NAME)
        .from(USERS)
        .where(USERS.ID.in(ids))
        .fetchMap(USERS.ID, USERS.DISPLAY_NAME);
  }

  /** The user's direct-add choice, or empty when there is no such user. */
  public Optional<DirectAdd> findDirectAdd(UUID id) {
    return dsl.select(USERS.DIRECT_ADD)
        .from(USERS)
        .where(USERS.ID.eq(id))
        .fetchOptional(r -> DirectAdd.valueOf(r.value1()));
  }

  /** What sign-in needs to check a password. */
  public record Credentials(UUID userId, String passwordHash) {}
}
