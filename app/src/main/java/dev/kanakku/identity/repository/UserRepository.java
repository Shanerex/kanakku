package dev.kanakku.identity.repository;

import static dev.kanakku.jooq.Tables.PASSWORD_CREDENTIALS;
import static dev.kanakku.jooq.Tables.USERS;

import dev.kanakku.identity.domain.DirectAdd;
import dev.kanakku.identity.domain.Profile;
import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.jooq.Condition;
import org.jooq.DSLContext;
import org.jooq.Field;
import org.jooq.Record;
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

  /**
   * Inserts a user with a phone number, verified at {@code phoneVerifiedAt} or unverified when that
   * is null.
   *
   * @throws org.springframework.dao.DuplicateKeyException when the email is taken in any case, or
   *     the number is verified on another account and is being verified here
   */
  public void insertUser(
      UUID id, String email, String displayName, String phoneE164, OffsetDateTime phoneVerifiedAt) {
    dsl.insertInto(USERS)
        .set(USERS.ID, id)
        .set(USERS.EMAIL, email)
        .set(USERS.DISPLAY_NAME, displayName)
        .set(USERS.PHONE_E164, phoneE164)
        .set(USERS.PHONE_VERIFIED_AT, phoneVerifiedAt)
        .execute();
  }

  /** True when an account uses this email in any letter case. */
  public boolean emailExists(String email) {
    return dsl.fetchExists(USERS, DSL.lower(USERS.EMAIL).eq(DSL.lower(DSL.val(email.strip()))));
  }

  /** True when any account has verified this number. */
  public boolean isPhoneVerified(String phoneE164) {
    return dsl.fetchExists(
        USERS, USERS.PHONE_E164.eq(phoneE164).and(USERS.PHONE_VERIFIED_AT.isNotNull()));
  }

  public void insertCredentials(UUID userId, String passwordHash) {
    dsl.insertInto(PASSWORD_CREDENTIALS)
        .set(PASSWORD_CREDENTIALS.USER_ID, userId)
        .set(PASSWORD_CREDENTIALS.PASSWORD_HASH, passwordHash)
        .execute();
  }

  private static final List<Field<?>> PROFILE_FIELDS =
      List.of(
          USERS.ID,
          USERS.EMAIL,
          USERS.EMAIL_VERIFIED,
          USERS.DISPLAY_NAME,
          USERS.DEFAULT_CURRENCY,
          USERS.UPI_ID,
          USERS.DIRECT_ADD,
          USERS.PHONE_E164,
          USERS.PHONE_VERIFIED_AT,
          USERS.VERSION);

  private static Profile toProfile(Record r) {
    return new Profile(
        r.get(USERS.ID),
        r.get(USERS.EMAIL),
        r.get(USERS.EMAIL_VERIFIED),
        r.get(USERS.DISPLAY_NAME),
        r.get(USERS.DEFAULT_CURRENCY),
        r.get(USERS.UPI_ID),
        DirectAdd.valueOf(r.get(USERS.DIRECT_ADD)),
        r.get(USERS.PHONE_E164),
        r.get(USERS.PHONE_VERIFIED_AT) != null,
        r.get(USERS.VERSION));
  }

  public Optional<Profile> findProfile(UUID id) {
    return dsl.select(PROFILE_FIELDS)
        .from(USERS)
        .where(USERS.ID.eq(id))
        .fetchOptional(UserRepository::toProfile);
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
        .returning(PROFILE_FIELDS)
        .fetchOptional()
        .map(UserRepository::toProfile);
  }

  /**
   * Sets the caller's number if the version is current. A different number starts unverified; the
   * same number keeps its state. One statement, so the row returned is the row written.
   *
   * @return the new profile, or empty when the version is stale
   */
  public Optional<Profile> updatePhone(UUID callerId, String phoneE164, int version) {
    return dsl.update(USERS)
        .set(
            USERS.PHONE_VERIFIED_AT,
            DSL.when(USERS.PHONE_E164.eq(phoneE164), USERS.PHONE_VERIFIED_AT)
                .otherwise(DSL.val(null, USERS.PHONE_VERIFIED_AT.getDataType())))
        .set(USERS.PHONE_E164, phoneE164)
        .set(USERS.VERSION, USERS.VERSION.plus(1))
        .set(USERS.UPDATED_AT, DSL.currentOffsetDateTime())
        .where(USERS.ID.eq(callerId))
        .and(USERS.VERSION.eq(version))
        .returning(PROFILE_FIELDS)
        .fetchOptional()
        .map(UserRepository::toProfile);
  }

  /**
   * Removes the caller's number, verified or not, if the version is current.
   *
   * @return the new profile, or empty when the version is stale
   */
  public Optional<Profile> clearPhone(UUID callerId, int version) {
    return dsl.update(USERS)
        .setNull(USERS.PHONE_E164)
        .setNull(USERS.PHONE_VERIFIED_AT)
        .set(USERS.VERSION, USERS.VERSION.plus(1))
        .set(USERS.UPDATED_AT, DSL.currentOffsetDateTime())
        .where(USERS.ID.eq(callerId))
        .and(USERS.VERSION.eq(version))
        .returning(PROFILE_FIELDS)
        .fetchOptional()
        .map(UserRepository::toProfile);
  }

  /**
   * Marks the caller's current number verified at {@code at}.
   *
   * @return the new profile, or empty when the caller's number is no longer {@code phoneE164}
   * @throws org.springframework.dao.DuplicateKeyException when another account verified it first
   */
  public Optional<Profile> markPhoneVerified(UUID userId, String phoneE164, OffsetDateTime at) {
    return dsl.update(USERS)
        .set(USERS.PHONE_VERIFIED_AT, at)
        .set(USERS.VERSION, USERS.VERSION.plus(1))
        .set(USERS.UPDATED_AT, DSL.currentOffsetDateTime())
        .where(USERS.ID.eq(userId))
        .and(USERS.PHONE_E164.eq(phoneE164))
        .returning(PROFILE_FIELDS)
        .fetchOptional()
        .map(UserRepository::toProfile);
  }

  /** True when an account other than {@code userId} has verified this number. */
  public boolean isPhoneVerifiedByOther(String phoneE164, UUID userId) {
    return dsl.fetchExists(
        USERS,
        USERS
            .PHONE_E164
            .eq(phoneE164)
            .and(USERS.PHONE_VERIFIED_AT.isNotNull())
            .and(USERS.ID.ne(userId)));
  }

  /**
   * A directory hit with raw contact data, which the caller must mask before it leaves identity.
   */
  public record Hit(
      UUID id,
      String displayName,
      String email,
      String phoneE164,
      boolean phoneVerified,
      boolean directAdd,
      boolean nameMatched) {

    @Override
    public String toString() {
      return "Hit[id=" + id + "]";
    }
  }

  /**
   * Users whose name or email contains the text (already escaped for LIKE with a backslash and
   * wrapped in percent signs), ordered by name then id. Invite-only users are included.
   */
  public List<Hit> searchByText(String likePattern, Collection<UUID> exclude, int limit) {
    Condition nameMatch = USERS.DISPLAY_NAME.likeIgnoreCase(likePattern, '\\');
    Condition emailMatch = USERS.EMAIL.likeIgnoreCase(likePattern, '\\');
    Condition where = nameMatch.or(emailMatch);
    if (!exclude.isEmpty()) {
      where = where.and(USERS.ID.notIn(exclude));
    }
    return dsl.select(
            USERS.ID,
            USERS.DISPLAY_NAME,
            USERS.EMAIL,
            USERS.PHONE_E164,
            USERS.PHONE_VERIFIED_AT,
            USERS.DIRECT_ADD,
            DSL.field(nameMatch))
        .from(USERS)
        .where(where)
        .orderBy(USERS.DISPLAY_NAME, USERS.ID)
        .limit(limit)
        .fetch(
            r ->
                new Hit(
                    r.value1(),
                    r.value2(),
                    r.value3(),
                    r.value4(),
                    r.value5() != null,
                    DirectAdd.ALLOW.name().equals(r.value6()),
                    Boolean.TRUE.equals(r.value7())));
  }

  /**
   * Users whose stored number contains {@code digits} (digits only, country code included in what
   * is matched), verified or not, ordered by name then id. Invite-only users are included.
   */
  public List<Hit> searchByPhone(String digits, Collection<UUID> exclude, int limit) {
    Condition where =
        DSL.replace(USERS.PHONE_E164, DSL.inline("+"), DSL.inline("")).like("%" + digits + "%");
    if (!exclude.isEmpty()) {
      where = where.and(USERS.ID.notIn(exclude));
    }
    return dsl.select(
            USERS.ID,
            USERS.DISPLAY_NAME,
            USERS.EMAIL,
            USERS.PHONE_E164,
            USERS.PHONE_VERIFIED_AT,
            USERS.DIRECT_ADD)
        .from(USERS)
        .where(where)
        .orderBy(USERS.DISPLAY_NAME, USERS.ID)
        .limit(limit)
        .fetch(
            r ->
                new Hit(
                    r.value1(),
                    r.value2(),
                    r.value3(),
                    r.value4(),
                    r.value5() != null,
                    DirectAdd.ALLOW.name().equals(r.value6()),
                    false));
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

  /** The user's number when it is verified. */
  public Optional<String> findVerifiedPhone(UUID id) {
    return dsl.select(USERS.PHONE_E164)
        .from(USERS)
        .where(USERS.ID.eq(id))
        .and(USERS.PHONE_VERIFIED_AT.isNotNull())
        .fetchOptional(USERS.PHONE_E164);
  }

  /** The account that has verified this number; the unique index allows at most one. */
  public Optional<UUID> findVerifiedPhoneHolder(String phoneE164) {
    return dsl.select(USERS.ID)
        .from(USERS)
        .where(USERS.PHONE_E164.eq(phoneE164))
        .and(USERS.PHONE_VERIFIED_AT.isNotNull())
        .fetchOptional(USERS.ID);
  }

  /** What sign-in needs to check a password. */
  public record Credentials(UUID userId, String passwordHash) {}
}
