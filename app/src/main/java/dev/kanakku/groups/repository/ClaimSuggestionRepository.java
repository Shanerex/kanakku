package dev.kanakku.groups.repository;

import static dev.kanakku.jooq.Tables.CLAIM_SUGGESTIONS;
import static dev.kanakku.jooq.Tables.GROUPS;
import static dev.kanakku.jooq.Tables.GROUP_MEMBERS;

import dev.kanakku.groups.domain.ClaimSuggestion;
import dev.kanakku.groups.domain.ClaimSuggestionStatus;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.jooq.DSLContext;
import org.jooq.Record;
import org.jooq.SelectConditionStep;
import org.jooq.impl.DSL;
import org.springframework.stereotype.Repository;

/** Suggestions to claim a guest's seat. Every statement is scoped to the user it is for. */
@Repository
public class ClaimSuggestionRepository {

  private final DSLContext dsl;

  ClaimSuggestionRepository(DSLContext dsl) {
    this.dsl = dsl;
  }

  /**
   * Suggests to the user every current, unclaimed guest whose number equals {@code phoneE164}, but
   * only in groups the user is a current member of. A suggestion that already exists, in any state,
   * is left as it is.
   *
   * @return how many suggestions were created
   */
  public int createForVerifiedPhone(UUID userId, String phoneE164) {
    var guests =
        DSL.select(
                DSL.field("gen_random_uuid()", UUID.class),
                GROUP_MEMBERS.GROUP_ID,
                GROUP_MEMBERS.ID,
                DSL.val(userId))
            .from(GROUP_MEMBERS)
            .where(GROUP_MEMBERS.GUEST_PHONE_E164.eq(phoneE164))
            .and(GROUP_MEMBERS.USER_ID.isNull())
            .and(GROUP_MEMBERS.LEFT_AT.isNull())
            .and(Scope.member(GROUP_MEMBERS.GROUP_ID, userId));
    return dsl.insertInto(
            CLAIM_SUGGESTIONS,
            CLAIM_SUGGESTIONS.ID,
            CLAIM_SUGGESTIONS.GROUP_ID,
            CLAIM_SUGGESTIONS.MEMBER_ID,
            CLAIM_SUGGESTIONS.USER_ID)
        .select(guests)
        .onConflict(CLAIM_SUGGESTIONS.MEMBER_ID, CLAIM_SUGGESTIONS.USER_ID)
        .doNothing()
        .execute();
  }

  /**
   * Suggests one guest to the user who verified its number, if the guest is still a current,
   * unclaimed guest carrying {@code phoneE164} and the user is a current member of its group. A
   * suggestion that already exists, in any state, is left as it is.
   *
   * @return true when a suggestion was created
   */
  public boolean createForGuest(UUID groupId, UUID guestMemberId, UUID userId, String phoneE164) {
    var guest =
        DSL.select(
                DSL.field("gen_random_uuid()", UUID.class),
                GROUP_MEMBERS.GROUP_ID,
                GROUP_MEMBERS.ID,
                DSL.val(userId))
            .from(GROUP_MEMBERS)
            .where(GROUP_MEMBERS.ID.eq(guestMemberId))
            .and(GROUP_MEMBERS.GROUP_ID.eq(groupId))
            .and(GROUP_MEMBERS.GUEST_PHONE_E164.eq(phoneE164))
            .and(GROUP_MEMBERS.USER_ID.isNull())
            .and(GROUP_MEMBERS.LEFT_AT.isNull())
            .and(Scope.member(GROUP_MEMBERS.GROUP_ID, userId));
    return dsl.insertInto(
                CLAIM_SUGGESTIONS,
                CLAIM_SUGGESTIONS.ID,
                CLAIM_SUGGESTIONS.GROUP_ID,
                CLAIM_SUGGESTIONS.MEMBER_ID,
                CLAIM_SUGGESTIONS.USER_ID)
            .select(guest)
            .onConflict(CLAIM_SUGGESTIONS.MEMBER_ID, CLAIM_SUGGESTIONS.USER_ID)
            .doNothing()
            .execute()
        > 0;
  }

  /**
   * Marks every pending suggestion of a guest {@code STALE}, for when the Owner changes the guest's
   * number and the old match no longer holds.
   *
   * @return how many were withdrawn
   */
  public int withdrawPendingForGuest(UUID groupId, UUID guestMemberId, UUID callerId) {
    return dsl.update(CLAIM_SUGGESTIONS)
        .set(CLAIM_SUGGESTIONS.STATUS, ClaimSuggestionStatus.STALE.name())
        .set(CLAIM_SUGGESTIONS.VERSION, CLAIM_SUGGESTIONS.VERSION.plus(1))
        .where(CLAIM_SUGGESTIONS.GROUP_ID.eq(groupId))
        .and(CLAIM_SUGGESTIONS.MEMBER_ID.eq(guestMemberId))
        .and(CLAIM_SUGGESTIONS.STATUS.eq(ClaimSuggestionStatus.PENDING.name()))
        .and(Scope.owner(CLAIM_SUGGESTIONS.GROUP_ID, callerId))
        .execute();
  }

  /** The user's own pending suggestions whose guest is still a current, unclaimed guest. */
  public List<ClaimSuggestion> listPending(UUID callerId) {
    return baseQuery(callerId)
        .and(CLAIM_SUGGESTIONS.STATUS.eq(ClaimSuggestionStatus.PENDING.name()))
        .and(GROUP_MEMBERS.USER_ID.isNull())
        .and(GROUP_MEMBERS.LEFT_AT.isNull())
        .orderBy(CLAIM_SUGGESTIONS.CREATED_AT, CLAIM_SUGGESTIONS.ID)
        .fetch(ClaimSuggestionRepository::toSuggestion);
  }

  /**
   * One suggestion of the caller, locked until the transaction ends. Empty for someone else's
   * suggestion and for a group the caller is no longer a member of.
   */
  public Optional<ClaimSuggestion> findLocked(UUID id, UUID callerId) {
    return baseQuery(callerId)
        .and(CLAIM_SUGGESTIONS.ID.eq(id))
        .forUpdate()
        .of(CLAIM_SUGGESTIONS)
        .fetchOptional(ClaimSuggestionRepository::toSuggestion);
  }

  /**
   * The number on a current, unclaimed guest of a group the caller belongs to; empty when the guest
   * has none, is gone, or was claimed.
   */
  public Optional<String> currentGuestPhone(UUID groupId, UUID guestMemberId, UUID callerId) {
    return dsl.select(GROUP_MEMBERS.GUEST_PHONE_E164)
        .from(GROUP_MEMBERS)
        .where(GROUP_MEMBERS.ID.eq(guestMemberId))
        .and(GROUP_MEMBERS.GROUP_ID.eq(groupId))
        .and(GROUP_MEMBERS.USER_ID.isNull())
        .and(GROUP_MEMBERS.LEFT_AT.isNull())
        .and(Scope.member(GROUP_MEMBERS.GROUP_ID, callerId))
        .fetchOptional(GROUP_MEMBERS.GUEST_PHONE_E164);
  }

  /**
   * Moves a pending suggestion of the caller to a final status if its version is current.
   *
   * @return true when it changed
   */
  public boolean resolve(UUID id, UUID callerId, int version, ClaimSuggestionStatus status) {
    return dsl.update(CLAIM_SUGGESTIONS)
            .set(CLAIM_SUGGESTIONS.STATUS, status.name())
            .set(CLAIM_SUGGESTIONS.VERSION, CLAIM_SUGGESTIONS.VERSION.plus(1))
            .where(CLAIM_SUGGESTIONS.ID.eq(id))
            .and(CLAIM_SUGGESTIONS.USER_ID.eq(callerId))
            .and(CLAIM_SUGGESTIONS.VERSION.eq(version))
            .and(CLAIM_SUGGESTIONS.STATUS.eq(ClaimSuggestionStatus.PENDING.name()))
            .and(Scope.member(CLAIM_SUGGESTIONS.GROUP_ID, callerId))
            .execute()
        > 0;
  }

  /** The caller's suggestions with the group and guest names; callers add their own filters. */
  private SelectConditionStep<? extends Record> baseQuery(UUID callerId) {
    return dsl.select(
            CLAIM_SUGGESTIONS.ID,
            CLAIM_SUGGESTIONS.GROUP_ID,
            GROUPS.NAME,
            CLAIM_SUGGESTIONS.MEMBER_ID,
            GROUP_MEMBERS.GUEST_NAME,
            CLAIM_SUGGESTIONS.STATUS,
            CLAIM_SUGGESTIONS.VERSION,
            CLAIM_SUGGESTIONS.CREATED_AT)
        .from(CLAIM_SUGGESTIONS)
        .join(GROUPS)
        .on(GROUPS.ID.eq(CLAIM_SUGGESTIONS.GROUP_ID))
        .join(GROUP_MEMBERS)
        .on(GROUP_MEMBERS.ID.eq(CLAIM_SUGGESTIONS.MEMBER_ID))
        .where(CLAIM_SUGGESTIONS.USER_ID.eq(callerId))
        .and(Scope.member(CLAIM_SUGGESTIONS.GROUP_ID, callerId));
  }

  private static ClaimSuggestion toSuggestion(Record r) {
    return new ClaimSuggestion(
        r.get(CLAIM_SUGGESTIONS.ID),
        r.get(CLAIM_SUGGESTIONS.GROUP_ID),
        r.get(GROUPS.NAME),
        r.get(CLAIM_SUGGESTIONS.MEMBER_ID),
        r.get(GROUP_MEMBERS.GUEST_NAME),
        ClaimSuggestionStatus.valueOf(r.get(CLAIM_SUGGESTIONS.STATUS)),
        r.get(CLAIM_SUGGESTIONS.VERSION),
        r.get(CLAIM_SUGGESTIONS.CREATED_AT));
  }
}
