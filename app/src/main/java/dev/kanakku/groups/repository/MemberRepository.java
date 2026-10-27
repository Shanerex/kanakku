package dev.kanakku.groups.repository;

import static dev.kanakku.jooq.Tables.GROUPS;
import static dev.kanakku.jooq.Tables.GROUP_MEMBERS;

import dev.kanakku.groups.domain.Role;
import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.jooq.DSLContext;
import org.jooq.Record;
import org.jooq.SelectConditionStep;
import org.jooq.impl.DSL;
import org.springframework.stereotype.Repository;

/** Reads and writes group members. Every statement carries the caller's membership guard. */
@Repository
public class MemberRepository {

  /** The caller's own place in a group, with whether the group is archived. */
  public record Membership(UUID memberId, Role role, OffsetDateTime archivedAt) {}

  /** A current or former member row. {@code userId} is null for a guest. */
  public record MemberRow(
      UUID id,
      UUID userId,
      String guestName,
      String guestEmail,
      String guestPhoneE164,
      Role role,
      OffsetDateTime joinedAt,
      int version) {}

  private final DSLContext dsl;

  MemberRepository(DSLContext dsl) {
    this.dsl = dsl;
  }

  /** The caller's current membership, or empty for a non-member or an unknown group. */
  public Optional<Membership> findMembership(UUID groupId, UUID callerId) {
    return dsl.select(GROUP_MEMBERS.ID, GROUP_MEMBERS.ROLE, GROUPS.ARCHIVED_AT)
        .from(GROUP_MEMBERS)
        .join(GROUPS)
        .on(GROUPS.ID.eq(GROUP_MEMBERS.GROUP_ID))
        .where(GROUP_MEMBERS.GROUP_ID.eq(groupId))
        .and(GROUP_MEMBERS.USER_ID.eq(callerId))
        .and(GROUP_MEMBERS.LEFT_AT.isNull())
        .fetchOptional(r -> new Membership(r.value1(), Role.valueOf(r.value2()), r.value3()));
  }

  /** Inserts the group's first member. The group row was created in the same transaction. */
  public void insertCreator(UUID id, UUID groupId, UUID userId) {
    dsl.insertInto(GROUP_MEMBERS)
        .set(GROUP_MEMBERS.ID, id)
        .set(GROUP_MEMBERS.GROUP_ID, groupId)
        .set(GROUP_MEMBERS.USER_ID, userId)
        .set(GROUP_MEMBERS.ROLE, Role.OWNER.name())
        .execute();
  }

  /**
   * Adds a guest if the caller is a current member.
   *
   * @return true when added
   */
  public boolean insertGuest(
      UUID id, UUID groupId, UUID callerId, String name, String email, String phoneE164) {
    var row =
        DSL.select(
                DSL.val(id),
                DSL.val(groupId),
                DSL.val(name),
                DSL.val(email, GROUP_MEMBERS.GUEST_EMAIL),
                DSL.val(phoneE164, GROUP_MEMBERS.GUEST_PHONE_E164),
                DSL.val(Role.MEMBER.name()))
            .where(Scope.member(DSL.val(groupId), callerId));
    return dsl.insertInto(
                GROUP_MEMBERS,
                GROUP_MEMBERS.ID,
                GROUP_MEMBERS.GROUP_ID,
                GROUP_MEMBERS.GUEST_NAME,
                GROUP_MEMBERS.GUEST_EMAIL,
                GROUP_MEMBERS.GUEST_PHONE_E164,
                GROUP_MEMBERS.ROLE)
            .select(row)
            .execute()
        > 0;
  }

  /**
   * Sets or clears a current guest's number if the caller is the group's Owner and the version is
   * current.
   *
   * @return true when the row changed
   */
  public boolean updateGuestPhone(
      UUID groupId, UUID memberId, UUID callerId, String phoneE164, int version) {
    return dsl.update(GROUP_MEMBERS)
            .set(GROUP_MEMBERS.GUEST_PHONE_E164, phoneE164)
            .set(GROUP_MEMBERS.VERSION, GROUP_MEMBERS.VERSION.plus(1))
            .where(GROUP_MEMBERS.GROUP_ID.eq(groupId))
            .and(GROUP_MEMBERS.ID.eq(memberId))
            .and(GROUP_MEMBERS.USER_ID.isNull())
            .and(GROUP_MEMBERS.LEFT_AT.isNull())
            .and(GROUP_MEMBERS.VERSION.eq(version))
            .and(Scope.owner(GROUP_MEMBERS.GROUP_ID, callerId))
            .execute()
        > 0;
  }

  /**
   * Adds a registered user as a Member if the caller is the group's Owner.
   *
   * @return true when added
   * @throws org.springframework.dao.DuplicateKeyException when the user is already a member
   */
  public boolean insertUserMember(UUID id, UUID groupId, UUID callerId, UUID userId) {
    var row =
        DSL.select(DSL.val(id), DSL.val(groupId), DSL.val(userId), DSL.val(Role.MEMBER.name()))
            .where(Scope.owner(DSL.val(groupId), callerId));
    return dsl.insertInto(
                GROUP_MEMBERS,
                GROUP_MEMBERS.ID,
                GROUP_MEMBERS.GROUP_ID,
                GROUP_MEMBERS.USER_ID,
                GROUP_MEMBERS.ROLE)
            .select(row)
            .execute()
        > 0;
  }

  /** The group's current members, oldest first; empty for a non-member. */
  public List<MemberRow> listActive(UUID groupId, UUID callerId) {
    return activeQuery(groupId, callerId)
        .orderBy(GROUP_MEMBERS.JOINED_AT, GROUP_MEMBERS.ID)
        .fetch(MemberRepository::toRow);
  }

  /** One current member; with {@code lock} the row stays locked until the transaction ends. */
  public Optional<MemberRow> findActive(UUID groupId, UUID memberId, UUID callerId, boolean lock) {
    SelectConditionStep<Record> query =
        activeQuery(groupId, callerId).and(GROUP_MEMBERS.ID.eq(memberId));
    return (lock ? query.forUpdate().fetchOptional() : query.fetchOptional())
        .map(MemberRepository::toRow);
  }

  /** User ids of the group's current registered members. */
  public Set<UUID> activeUserIds(UUID groupId, UUID callerId) {
    return activeQuery(groupId, callerId).fetch(GROUP_MEMBERS.USER_ID).stream()
        .filter(java.util.Objects::nonNull)
        .collect(Collectors.toSet());
  }

  /**
   * Those of {@code ids} that are current members, oldest first (joined_at, then id). The rows stay
   * locked until the transaction ends, so none can leave before the caller finishes. The lock is
   * FOR NO KEY UPDATE: it blocks a leave (an update) and another caller locking the same rows, but
   * not the key-share lock a journal post takes on a member through its foreign key. Callers that
   * lock several members all use this one order, so two of them cannot wait on each other.
   */
  public List<UUID> activeIdsInOrderLocked(UUID groupId, Collection<UUID> ids, UUID callerId) {
    return activeQuery(groupId, callerId)
        .and(GROUP_MEMBERS.ID.in(ids))
        .orderBy(GROUP_MEMBERS.JOINED_AT, GROUP_MEMBERS.ID)
        .forNoKeyUpdate()
        .fetch(GROUP_MEMBERS.ID);
  }

  /**
   * Marks the member as left if the version is current and the caller is the Owner or the member
   * themself.
   *
   * @return true when the row changed
   */
  public boolean markLeft(UUID groupId, UUID memberId, UUID callerId, int version) {
    return dsl.update(GROUP_MEMBERS)
            .set(GROUP_MEMBERS.LEFT_AT, DSL.currentOffsetDateTime())
            .set(GROUP_MEMBERS.VERSION, GROUP_MEMBERS.VERSION.plus(1))
            .where(GROUP_MEMBERS.GROUP_ID.eq(groupId))
            .and(GROUP_MEMBERS.ID.eq(memberId))
            .and(GROUP_MEMBERS.LEFT_AT.isNull())
            .and(GROUP_MEMBERS.VERSION.eq(version))
            .and(
                Scope.owner(GROUP_MEMBERS.GROUP_ID, callerId)
                    .or(GROUP_MEMBERS.USER_ID.eq(callerId)))
            .execute()
        > 0;
  }

  /**
   * Token-bearer write: gives the guest row an account. The row id came from an invite found by its
   * token hash, so the guard here is the row itself: still a current guest.
   *
   * @return true when the row was an unclaimed, current guest
   * @throws org.springframework.dao.DuplicateKeyException when the user is already in the group
   */
  public boolean claimGuest(UUID memberId, UUID userId) {
    return dsl.update(GROUP_MEMBERS)
            .set(GROUP_MEMBERS.USER_ID, userId)
            .set(GROUP_MEMBERS.VERSION, GROUP_MEMBERS.VERSION.plus(1))
            .where(GROUP_MEMBERS.ID.eq(memberId))
            .and(GROUP_MEMBERS.USER_ID.isNull())
            .and(GROUP_MEMBERS.LEFT_AT.isNull())
            .execute()
        > 0;
  }

  /**
   * Retires a guest whose seat was claimed by a member: marks it left and points it at the member's
   * own row, so the journal lines that name it still resolve. The guard is the caller's membership.
   *
   * @return true when the guest was a current, unclaimed guest of the group and was retired
   */
  public boolean retireMerged(UUID groupId, UUID guestId, UUID intoMemberId, UUID callerId) {
    return dsl.update(GROUP_MEMBERS)
            .set(GROUP_MEMBERS.LEFT_AT, DSL.currentOffsetDateTime())
            .set(GROUP_MEMBERS.MERGED_INTO_MEMBER_ID, intoMemberId)
            .set(GROUP_MEMBERS.VERSION, GROUP_MEMBERS.VERSION.plus(1))
            .where(GROUP_MEMBERS.GROUP_ID.eq(groupId))
            .and(GROUP_MEMBERS.ID.eq(guestId))
            .and(GROUP_MEMBERS.USER_ID.isNull())
            .and(GROUP_MEMBERS.LEFT_AT.isNull())
            .and(Scope.member(GROUP_MEMBERS.GROUP_ID, callerId))
            .execute()
        > 0;
  }

  private SelectConditionStep<Record> activeQuery(UUID groupId, UUID callerId) {
    return dsl.select(GROUP_MEMBERS.fields())
        .from(GROUP_MEMBERS)
        .where(GROUP_MEMBERS.GROUP_ID.eq(groupId))
        .and(GROUP_MEMBERS.LEFT_AT.isNull())
        .and(Scope.member(GROUP_MEMBERS.GROUP_ID, callerId));
  }

  private static MemberRow toRow(Record r) {
    return new MemberRow(
        r.get(GROUP_MEMBERS.ID),
        r.get(GROUP_MEMBERS.USER_ID),
        r.get(GROUP_MEMBERS.GUEST_NAME),
        r.get(GROUP_MEMBERS.GUEST_EMAIL),
        r.get(GROUP_MEMBERS.GUEST_PHONE_E164),
        Role.valueOf(r.get(GROUP_MEMBERS.ROLE)),
        r.get(GROUP_MEMBERS.JOINED_AT),
        r.get(GROUP_MEMBERS.VERSION));
  }
}
