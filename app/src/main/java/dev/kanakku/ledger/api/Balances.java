package dev.kanakku.ledger.api;

import java.util.List;
import java.util.UUID;

/**
 * Balance reads. They take a group id the caller already checked against the caller's membership,
 * so only the {@code groups} module calls them.
 */
public interface Balances {

  /** The member's balance per currency, including zero rows left by earlier posts. */
  List<MoneyBalance> ofMember(UUID groupId, UUID memberId);

  /** Every stored balance in the group; per currency they sum to zero. */
  List<MemberBalance> ofGroup(UUID groupId);
}
