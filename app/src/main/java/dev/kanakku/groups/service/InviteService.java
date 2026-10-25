package dev.kanakku.groups.service;

import dev.kanakku.groups.domain.CreatedInvite;
import dev.kanakku.groups.domain.Group;
import dev.kanakku.groups.domain.Invite;
import dev.kanakku.groups.domain.InvitePreview;
import dev.kanakku.groups.domain.InviteStatus;
import dev.kanakku.groups.exception.GroupProblems;
import dev.kanakku.groups.repository.GroupRepository;
import dev.kanakku.groups.repository.InviteRepository;
import dev.kanakku.groups.repository.InviteRepository.InviteRow;
import dev.kanakku.groups.repository.MemberRepository;
import dev.kanakku.groups.repository.MemberRepository.MemberRow;
import dev.kanakku.groups.util.InviteTokens;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

/** Invite rules: single-use links bound to one guest, claimed by a signed-in user. */
@Service
public class InviteService {

  /** Lifetime when the request names none. */
  public static final int DEFAULT_EXPIRY_DAYS = 7;

  private static final int MIN_SECRET_LENGTH = 32;

  private final InviteRepository invites;
  private final MemberRepository members;
  private final GroupRepository groups;
  private final GroupGuard guard;
  private final TransactionTemplate transactions;
  private final Clock clock;
  private final byte[] tokenSecret;

  InviteService(
      InviteRepository invites,
      MemberRepository members,
      GroupRepository groups,
      GroupGuard guard,
      TransactionTemplate transactions,
      Clock clock,
      @Value("${kanakku.invites.token-secret}") String tokenSecret) {
    if (tokenSecret == null || tokenSecret.length() < MIN_SECRET_LENGTH) {
      throw new IllegalStateException(
          "kanakku.invites.token-secret (KANAKKU_INVITE_TOKEN_SECRET) must be at least "
              + MIN_SECRET_LENGTH
              + " characters");
    }
    this.tokenSecret = tokenSecret.getBytes(StandardCharsets.UTF_8);
    this.invites = invites;
    this.members = members;
    this.groups = groups;
    this.guard = guard;
    this.transactions = transactions;
    this.clock = clock;
  }

  /**
   * Creates an invite for a guest. Owner only. The token is derived from the invite id, so the
   * caller can show it again on an idempotent replay without any stored copy.
   */
  public CreatedInvite create(UUID groupId, UUID callerId, UUID memberId, Integer expiryDays) {
    int days = expiryDays == null ? DEFAULT_EXPIRY_DAYS : expiryDays;
    UUID id = UUID.randomUUID();
    String token = tokenFor(id);
    return transactions.execute(
        status -> {
          guard.owner(groupId, callerId, true);
          MemberRow guest =
              members
                  .findActive(groupId, memberId, callerId, false)
                  .orElseThrow(GroupProblems::memberNotFound);
          if (guest.userId() != null) {
            throw GroupProblems.notAGuest();
          }
          OffsetDateTime expiresAt = now().plusDays(days);
          if (!invites.insert(
              id, groupId, memberId, InviteTokens.hash(token), expiresAt, callerId)) {
            throw GroupProblems.notAGuest();
          }
          Invite invite =
              toInvite(invites.findForCaller(groupId, id, callerId).orElseThrow(), now());
          return new CreatedInvite(invite, token);
        });
  }

  /** The link token of an invite, recomputed from its id and the server secret. */
  public String tokenFor(UUID inviteId) {
    return InviteTokens.derive(tokenSecret, inviteId);
  }

  /** The group's invites, newest first. Any member may look; no token is ever included. */
  public List<Invite> list(UUID groupId, UUID callerId) {
    guard.member(groupId, callerId, false);
    OffsetDateTime now = now();
    return invites.listForCaller(groupId, callerId).stream().map(r -> toInvite(r, now)).toList();
  }

  /** Revokes a live invite. Owner only; a stale version is a 409. */
  public Invite revoke(UUID groupId, UUID inviteId, UUID callerId, int version) {
    return transactions.execute(
        status -> {
          guard.owner(groupId, callerId, true);
          invites
              .findForCaller(groupId, inviteId, callerId)
              .orElseThrow(GroupProblems::inviteNotFound);
          if (!invites.revoke(groupId, inviteId, callerId, version)) {
            throw GroupProblems.staleVersion();
          }
          return toInvite(invites.findForCaller(groupId, inviteId, callerId).orElseThrow(), now());
        });
  }

  /** What a token holder may see before claiming. A bad token is the same 410 as a dead one. */
  public InvitePreview preview(String token) {
    InviteRow row =
        invites
            .findByTokenHash(InviteTokens.hash(token))
            .orElseThrow(GroupProblems::inviteUnusable);
    requireUsable(row);
    return new InvitePreview(row.groupName(), row.guestName());
  }

  /** The caller takes the guest's place; history and balance stay on the same member row. */
  public Group claim(String token, UUID callerId) {
    return transactions.execute(
        status -> {
          InviteRow row =
              invites
                  .lockByTokenHash(InviteTokens.hash(token))
                  .orElseThrow(GroupProblems::inviteUnusable);
          requireUsable(row);
          if (row.groupArchivedAt() != null) {
            throw GroupProblems.groupArchived();
          }
          if (members.findMembership(row.groupId(), callerId).isPresent()) {
            throw GroupProblems.alreadyMember();
          }
          try {
            if (!members.claimGuest(row.memberId(), callerId)) {
              throw GroupProblems.inviteUnusable();
            }
          } catch (DuplicateKeyException e) {
            throw GroupProblems.alreadyMember();
          }
          invites.markUsed(row.id(), callerId);
          invites.revokeLiveForMember(row.memberId());
          return groups.findForCaller(row.groupId(), callerId).orElseThrow();
        });
  }

  private void requireUsable(InviteRow row) {
    if (statusOf(row, now()) != InviteStatus.ACTIVE
        || row.memberUserId() != null
        || row.memberLeftAt() != null) {
      throw GroupProblems.inviteUnusable();
    }
  }

  private OffsetDateTime now() {
    return OffsetDateTime.ofInstant(clock.instant(), ZoneOffset.UTC);
  }

  private static InviteStatus statusOf(InviteRow row, OffsetDateTime now) {
    if (row.usedAt() != null) {
      return InviteStatus.USED;
    }
    if (row.revokedAt() != null) {
      return InviteStatus.REVOKED;
    }
    if (!row.expiresAt().isAfter(now)) {
      return InviteStatus.EXPIRED;
    }
    return InviteStatus.ACTIVE;
  }

  private static Invite toInvite(InviteRow row, OffsetDateTime now) {
    return new Invite(
        row.id(),
        row.memberId(),
        row.guestName(),
        statusOf(row, now),
        row.expiresAt(),
        row.createdAt(),
        row.version());
  }
}
