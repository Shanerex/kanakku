package dev.kanakku.ledger.api;

import java.util.List;
import java.util.UUID;

/**
 * Moves a guest's whole balance onto a member's own row when a user claims the guest. It never
 * divides an amount, so nothing is rounded.
 */
public interface MemberMerges {

  /**
   * For each currency in which {@code fromMemberId} has a balance B other than zero, posts one
   * MERGE entry taking B from that member (leaving them at zero) and giving exactly B to {@code
   * intoMemberId}. The entries and the balance updates commit in one transaction, which joins the
   * caller's when there is one. A member with no balance posts nothing.
   *
   * @return the amounts moved, one per currency that had a non-zero balance
   * @throws IllegalArgumentException when both ids are the same member
   */
  List<MoneyBalance> merge(UUID groupId, UUID fromMemberId, UUID intoMemberId, UUID actorUserId);
}
