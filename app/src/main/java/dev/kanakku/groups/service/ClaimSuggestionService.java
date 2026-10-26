package dev.kanakku.groups.service;

import dev.kanakku.groups.domain.ClaimSuggestion;
import dev.kanakku.groups.domain.ClaimSuggestionStatus;
import dev.kanakku.groups.exception.GroupProblems;
import dev.kanakku.groups.repository.ClaimSuggestionRepository;
import dev.kanakku.groups.repository.InviteRepository;
import dev.kanakku.groups.repository.MemberRepository;
import dev.kanakku.groups.repository.MemberRepository.Membership;
import dev.kanakku.identity.api.PhoneVerified;
import dev.kanakku.identity.api.UserDirectory;
import dev.kanakku.ledger.api.MemberMerges;
import java.util.List;
import java.util.UUID;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Claim suggestions: a verified number that equals a guest's number in one of the user's groups
 * becomes a suggestion, whichever came first, the verification or the guest's number. Nothing
 * changes until the user confirms it. Confirming moves the guest's balance onto the user's own
 * member row with a ledger merge and retires the guest.
 */
@Service
public class ClaimSuggestionService {

  private final ClaimSuggestionRepository suggestions;
  private final MemberRepository members;
  private final InviteRepository invites;
  private final GroupGuard guard;
  private final UserDirectory directory;
  private final MemberMerges merges;
  private final TransactionTemplate transactions;

  ClaimSuggestionService(
      ClaimSuggestionRepository suggestions,
      MemberRepository members,
      InviteRepository invites,
      GroupGuard guard,
      UserDirectory directory,
      MemberMerges merges,
      TransactionTemplate transactions) {
    this.suggestions = suggestions;
    this.members = members;
    this.invites = invites;
    this.guard = guard;
    this.directory = directory;
    this.merges = merges;
    this.transactions = transactions;
  }

  /**
   * Runs synchronously in the transaction that verified the number, so a suggestion commits or
   * rolls back with the verification itself.
   */
  @EventListener
  void onPhoneVerified(PhoneVerified event) {
    directory
        .verifiedPhone(event.userId())
        .ifPresent(phone -> suggestions.createForVerifiedPhone(event.userId(), phone));
  }

  /**
   * A guest just got {@code phoneE164}: if a member of its group has verified that number, suggest
   * the guest to them. Runs in the caller's transaction.
   */
  void suggestForGuest(UUID groupId, UUID guestMemberId, String phoneE164) {
    directory
        .verifiedPhoneHolder(phoneE164)
        .ifPresent(userId -> suggestions.createForGuest(groupId, guestMemberId, userId, phoneE164));
  }

  /**
   * The user just became a member of a group, by direct add or by claiming an invite: any guest
   * there with the number they hold verified is suggested to them. Runs in the caller's
   * transaction, after the member row exists.
   */
  void suggestForNewMember(UUID userId) {
    directory
        .verifiedPhone(userId)
        .ifPresent(phone -> suggestions.createForVerifiedPhone(userId, phone));
  }

  /**
   * The Owner changed a guest's number: its pending suggestions no longer hold and are withdrawn.
   * Runs in the caller's transaction, which checked the Owner guard.
   */
  void withdrawForGuest(UUID groupId, UUID guestMemberId, UUID callerId) {
    suggestions.withdrawPendingForGuest(groupId, guestMemberId, callerId);
  }

  /** The caller's own pending suggestions, oldest first. */
  public List<ClaimSuggestion> listPending(UUID callerId) {
    return suggestions.listPending(callerId);
  }

  /**
   * The caller says it is them: the guest's balance moves onto the caller's own member row and the
   * guest is retired, in one transaction. Both member rows are locked first, in the one stable
   * order every member lock uses.
   *
   * @throws dev.kanakku.platform.problem.ProblemException 404 for a suggestion that is not the
   *     caller's, 409 when it was answered, the guest is gone, the group is archived, or the caller
   *     no longer holds the verified number or the guest's number changed (then the suggestion is
   *     marked {@link ClaimSuggestionStatus#STALE} and nothing merges)
   */
  public ClaimSuggestion confirm(UUID suggestionId, UUID callerId, int version) {
    ClaimSuggestion result =
        transactions.execute(status -> confirmInTransaction(suggestionId, callerId, version));
    if (result.status() == ClaimSuggestionStatus.STALE) {
      // Thrown after the commit, so the stale mark is kept.
      throw GroupProblems.suggestionResolved();
    }
    return result;
  }

  private ClaimSuggestion confirmInTransaction(UUID suggestionId, UUID callerId, int version) {
    // Member rows first, then the suggestion row: setGuestPhone locks the guest and then withdraws
    // its suggestions, so the other order could deadlock. The first read only finds the ids.
    ClaimSuggestion peek =
        suggestions.find(suggestionId, callerId).orElseThrow(GroupProblems::suggestionNotFound);
    Membership mine = guard.member(peek.groupId(), callerId, true);
    List<UUID> locked =
        members.activeIdsInOrderLocked(
            peek.groupId(), List.of(peek.guestMemberId(), mine.memberId()), callerId);
    ClaimSuggestion suggestion = pendingLocked(suggestionId, callerId, version);
    if (!locked.contains(suggestion.guestMemberId())) {
      throw GroupProblems.suggestionResolved();
    }
    // The suggestion was made when the number matched; it must still match, and the caller must
    // still hold it verified, or a number released and reused could claim someone else's seat.
    String held = directory.verifiedPhone(callerId).orElse(null);
    String guestPhone =
        suggestions
            .currentGuestPhone(suggestion.groupId(), suggestion.guestMemberId(), callerId)
            .orElse(null);
    if (held == null || !held.equals(guestPhone)) {
      if (!suggestions.resolve(suggestionId, callerId, version, ClaimSuggestionStatus.STALE)) {
        throw GroupProblems.staleVersion();
      }
      return suggestions.findLocked(suggestionId, callerId).orElseThrow();
    }
    if (!members.retireMerged(
        suggestion.groupId(), suggestion.guestMemberId(), mine.memberId(), callerId)) {
      throw GroupProblems.suggestionResolved();
    }
    merges.merge(suggestion.groupId(), suggestion.guestMemberId(), mine.memberId(), callerId);
    invites.revokeLiveForMember(suggestion.guestMemberId());
    if (!suggestions.resolve(suggestionId, callerId, version, ClaimSuggestionStatus.CONFIRMED)) {
      throw GroupProblems.staleVersion();
    }
    return suggestions.findLocked(suggestionId, callerId).orElseThrow();
  }

  /** The caller says it is not them. The guest stays as it is. */
  public ClaimSuggestion decline(UUID suggestionId, UUID callerId, int version) {
    return transactions.execute(
        status -> {
          pendingLocked(suggestionId, callerId, version);
          if (!suggestions.resolve(
              suggestionId, callerId, version, ClaimSuggestionStatus.DECLINED)) {
            throw GroupProblems.staleVersion();
          }
          return suggestions.findLocked(suggestionId, callerId).orElseThrow();
        });
  }

  private ClaimSuggestion pendingLocked(UUID suggestionId, UUID callerId, int version) {
    ClaimSuggestion suggestion =
        suggestions
            .findLocked(suggestionId, callerId)
            .orElseThrow(GroupProblems::suggestionNotFound);
    if (suggestion.status() != ClaimSuggestionStatus.PENDING) {
      throw GroupProblems.suggestionResolved();
    }
    if (suggestion.version() != version) {
      throw GroupProblems.staleVersion();
    }
    return suggestion;
  }
}
