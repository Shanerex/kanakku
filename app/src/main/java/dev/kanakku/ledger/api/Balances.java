package dev.kanakku.ledger.api;

import java.util.List;
import java.util.UUID;

/**
 * Balance reads. Each is scoped to the caller inside the SQL: a user who is not a current member of
 * the group gets an empty list, whatever the ids.
 */
public interface Balances {

  /** The member's balance per currency, including zero rows left by earlier posts. */
  List<MoneyBalance> ofMember(UUID groupId, UUID memberId, UUID callerId);

  /** Every stored balance in the group; per currency they sum to zero. */
  List<MemberBalance> ofGroup(UUID groupId, UUID callerId);
}
