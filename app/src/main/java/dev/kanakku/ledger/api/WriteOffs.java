package dev.kanakku.ledger.api;

import java.util.List;
import java.util.UUID;

/** Clears a member's balance by moving it onto other members. */
public interface WriteOffs {

  /**
   * For each currency in which the member has a balance B, posts one WRITE_OFF entry that takes -B
   * from the member and splits +B across the absorbers: equal parts, remainder units by largest
   * remainder in the order of {@code absorberIds}. A single absorber takes all of B.
   *
   * @param absorberIds distinct members other than {@code memberId}, in stable member order
   * @return the amounts written off, one per currency that had a non-zero balance
   * @throws IllegalArgumentException when there are no absorbers, the member is among them, or an
   *     id repeats
   */
  List<MoneyBalance> writeOff(
      UUID groupId, UUID memberId, List<UUID> absorberIds, UUID actorUserId);
}
