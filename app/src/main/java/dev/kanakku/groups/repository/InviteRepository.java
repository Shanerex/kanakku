package dev.kanakku.groups.repository;

import static dev.kanakku.jooq.Tables.GROUPS;
import static dev.kanakku.jooq.Tables.GROUP_INVITES;
import static dev.kanakku.jooq.Tables.GROUP_MEMBERS;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.jooq.DSLContext;
import org.jooq.Record;
import org.jooq.impl.DSL;
import org.springframework.stereotype.Repository;

/**
 * Invites. Group-side calls carry the caller's membership guard. The two token-bearer calls are
 * scoped by the token's hash instead: whoever holds the token may see or claim that one invite.
 */
@Repository
public class InviteRepository {

  /** An invite row with the names a screen needs. */
  public record InviteRow(
      UUID id,
      UUID groupId,
      UUID memberId,
      String groupName,
      OffsetDateTime groupArchivedAt,
      String guestName,
      UUID memberUserId,
      OffsetDateTime memberLeftAt,
      OffsetDateTime expiresAt,
      OffsetDateTime revokedAt,
      OffsetDateTime usedAt,
      OffsetDateTime createdAt,
      int version) {}

  private final DSLContext dsl;

  InviteRepository(DSLContext dsl) {
    this.dsl = dsl;
  }

  /**
   * Stores a new invite if the caller is the group's Owner and the member is a current guest.
   *
   * @return true when stored
   */
  public boolean insert(
      UUID id,
      UUID groupId,
      UUID memberId,
      byte[] tokenHash,
      OffsetDateTime expiresAt,
      UUID callerId) {
    var row =
        DSL.select(
                DSL.val(id),
                DSL.val(groupId),
                DSL.val(memberId),
                DSL.val(tokenHash),
                DSL.val(expiresAt),
                DSL.val(callerId))
            .where(Scope.owner(DSL.val(groupId), callerId))
            .and(
                DSL.exists(
                    DSL.selectOne()
                        .from(GROUP_MEMBERS)
                        .where(GROUP_MEMBERS.ID.eq(memberId))
                        .and(GROUP_MEMBERS.GROUP_ID.eq(groupId))
                        .and(GROUP_MEMBERS.USER_ID.isNull())
                        .and(GROUP_MEMBERS.LEFT_AT.isNull())));
    return dsl.insertInto(
                GROUP_INVITES,
                GROUP_INVITES.ID,
                GROUP_INVITES.GROUP_ID,
                GROUP_INVITES.MEMBER_ID,
                GROUP_INVITES.TOKEN_HASH,
                GROUP_INVITES.EXPIRES_AT,
                GROUP_INVITES.CREATED_BY)
            .select(row)
            .execute()
        > 0;
  }

  /** The group's invites, newest first; empty for a non-member. */
  public List<InviteRow> listForCaller(UUID groupId, UUID callerId) {
    return baseQuery()
        .where(GROUP_INVITES.GROUP_ID.eq(groupId))
        .and(Scope.member(GROUP_INVITES.GROUP_ID, callerId))
        .orderBy(GROUP_INVITES.CREATED_AT.desc(), GROUP_INVITES.ID)
        .fetch(InviteRepository::toRow);
  }

  public Optional<InviteRow> findForCaller(UUID groupId, UUID inviteId, UUID callerId) {
    return baseQuery()
        .where(GROUP_INVITES.GROUP_ID.eq(groupId))
        .and(GROUP_INVITES.ID.eq(inviteId))
        .and(Scope.member(GROUP_INVITES.GROUP_ID, callerId))
        .fetchOptional(InviteRepository::toRow);
  }

  /**
   * Revokes a live invite if the caller is the group's Owner and the version is current.
   *
   * @return true when revoked
   */
  public boolean revoke(UUID groupId, UUID inviteId, UUID callerId, int version) {
    return dsl.update(GROUP_INVITES)
            .set(GROUP_INVITES.REVOKED_AT, DSL.currentOffsetDateTime())
            .set(GROUP_INVITES.VERSION, GROUP_INVITES.VERSION.plus(1))
            .where(GROUP_INVITES.GROUP_ID.eq(groupId))
            .and(GROUP_INVITES.ID.eq(inviteId))
            .and(GROUP_INVITES.VERSION.eq(version))
            .and(GROUP_INVITES.REVOKED_AT.isNull())
            .and(GROUP_INVITES.USED_AT.isNull())
            .and(Scope.owner(GROUP_INVITES.GROUP_ID, callerId))
            .execute()
        > 0;
  }

  /** Token-bearer read: the invite whose token hashes to {@code tokenHash}. */
  public Optional<InviteRow> findByTokenHash(byte[] tokenHash) {
    return baseQuery()
        .where(GROUP_INVITES.TOKEN_HASH.eq(tokenHash))
        .fetchOptional(InviteRepository::toRow);
  }

  /** Token-bearer read that locks the invite and its guest row until the transaction ends. */
  public Optional<InviteRow> lockByTokenHash(byte[] tokenHash) {
    return baseQuery()
        .where(GROUP_INVITES.TOKEN_HASH.eq(tokenHash))
        .forUpdate()
        .of(GROUP_INVITES, GROUP_MEMBERS)
        .fetchOptional(InviteRepository::toRow);
  }

  public void markUsed(UUID inviteId, UUID userId) {
    dsl.update(GROUP_INVITES)
        .set(GROUP_INVITES.USED_AT, DSL.currentOffsetDateTime())
        .set(GROUP_INVITES.USED_BY, userId)
        .set(GROUP_INVITES.VERSION, GROUP_INVITES.VERSION.plus(1))
        .where(GROUP_INVITES.ID.eq(inviteId))
        .and(GROUP_INVITES.USED_AT.isNull())
        .execute();
  }

  private org.jooq.SelectOnConditionStep<? extends Record> baseQuery() {
    return dsl.select(
            GROUP_INVITES.ID,
            GROUP_INVITES.GROUP_ID,
            GROUP_INVITES.MEMBER_ID,
            GROUPS.NAME,
            GROUPS.ARCHIVED_AT,
            GROUP_MEMBERS.GUEST_NAME,
            GROUP_MEMBERS.USER_ID,
            GROUP_MEMBERS.LEFT_AT,
            GROUP_INVITES.EXPIRES_AT,
            GROUP_INVITES.REVOKED_AT,
            GROUP_INVITES.USED_AT,
            GROUP_INVITES.CREATED_AT,
            GROUP_INVITES.VERSION)
        .from(GROUP_INVITES)
        .join(GROUPS)
        .on(GROUPS.ID.eq(GROUP_INVITES.GROUP_ID))
        .join(GROUP_MEMBERS)
        .on(GROUP_MEMBERS.ID.eq(GROUP_INVITES.MEMBER_ID));
  }

  private static InviteRow toRow(Record r) {
    return new InviteRow(
        r.get(GROUP_INVITES.ID),
        r.get(GROUP_INVITES.GROUP_ID),
        r.get(GROUP_INVITES.MEMBER_ID),
        r.get(GROUPS.NAME),
        r.get(GROUPS.ARCHIVED_AT),
        r.get(GROUP_MEMBERS.GUEST_NAME),
        r.get(GROUP_MEMBERS.USER_ID),
        r.get(GROUP_MEMBERS.LEFT_AT),
        r.get(GROUP_INVITES.EXPIRES_AT),
        r.get(GROUP_INVITES.REVOKED_AT),
        r.get(GROUP_INVITES.USED_AT),
        r.get(GROUP_INVITES.CREATED_AT),
        r.get(GROUP_INVITES.VERSION));
  }
}
