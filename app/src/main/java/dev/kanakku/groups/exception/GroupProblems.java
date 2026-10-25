package dev.kanakku.groups.exception;

import dev.kanakku.ledger.api.MoneyBalance;
import dev.kanakku.platform.problem.ProblemException;
import dev.kanakku.platform.problem.Problems;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;

/** Builds the problem details groups answers with; each is thrown as a {@link ProblemException}. */
public final class GroupProblems {

  private GroupProblems() {}

  /** Also the answer for a group that exists but the caller does not belong to. */
  public static ProblemException groupNotFound() {
    return new ProblemException(
        Problems.of(
            HttpStatus.NOT_FOUND,
            "group-not-found",
            "Group not found",
            "There is no such group, or you are not in it."));
  }

  public static ProblemException memberNotFound() {
    return new ProblemException(
        Problems.of(
            HttpStatus.NOT_FOUND,
            "member-not-found",
            "Member not found",
            "There is no such member in this group."));
  }

  public static ProblemException inviteNotFound() {
    return new ProblemException(
        Problems.of(
            HttpStatus.NOT_FOUND,
            "invite-not-found",
            "Invite not found",
            "There is no such invite in this group."));
  }

  public static ProblemException ownerOnly() {
    return new ProblemException(
        Problems.of(
            HttpStatus.FORBIDDEN,
            "owner-only",
            "Only the Owner can do this",
            "Ask the group's Owner to do this."));
  }

  public static ProblemException staleVersion() {
    return new ProblemException(
        Problems.of(
            HttpStatus.CONFLICT,
            "stale-version",
            "Changed elsewhere",
            "This was changed somewhere else. Reload it and try again."));
  }

  public static ProblemException groupArchived() {
    return new ProblemException(
        Problems.of(
            HttpStatus.CONFLICT,
            "group-archived",
            "Group is archived",
            "This group is archived and read-only. Unarchive it to make changes."));
  }

  public static ProblemException lastOwner() {
    return new ProblemException(
        Problems.of(
            HttpStatus.CONFLICT,
            "last-owner",
            "The Owner cannot leave",
            "A group always keeps its Owner, so the Owner cannot leave or be removed."));
  }

  public static ProblemException alreadyMember() {
    return new ProblemException(
        Problems.of(
            HttpStatus.CONFLICT,
            "already-member",
            "Already in the group",
            "This person is already a member of the group."));
  }

  public static ProblemException inviteOnly() {
    return new ProblemException(
        Problems.of(
            HttpStatus.CONFLICT,
            "invite-only",
            "Cannot be added directly",
            "This person can only join with an invite link."));
  }

  public static ProblemException notAGuest() {
    return new ProblemException(
        Problems.of(
            HttpStatus.CONFLICT,
            "not-a-guest",
            "Not a guest",
            "Invites are only for guests who have not joined with an account."));
  }

  public static ProblemException invalidAbsorbers() {
    return new ProblemException(
        Problems.of(
            HttpStatus.BAD_REQUEST,
            "invalid-absorbers",
            "Pick who absorbs the balance",
            "Pick at least one other current member, each only once."));
  }

  /** 410 for an invite that is expired, revoked, used or unknown; all look the same. */
  public static ProblemException inviteUnusable() {
    return new ProblemException(
        Problems.of(
            HttpStatus.GONE,
            "invite-unusable",
            "Invite no longer works",
            "This invite has expired, was revoked or was already used. Ask for a new one."));
  }

  /** 409 naming what the member still owes or is owed, per currency. */
  public static ProblemException memberHasBalance(List<MoneyBalance> balances) {
    ProblemDetail problem =
        Problems.of(
            HttpStatus.CONFLICT,
            "member-has-balance",
            "Balance is not settled",
            "This member still has a balance. Settle it or write it off first.");
    problem.setProperty(
        "balances",
        balances.stream()
            .map(
                b ->
                    Map.<String, Object>of(
                        "currency", b.currency(), "amountMinor", b.amountMinor()))
            .toList());
    return new ProblemException(problem);
  }
}
