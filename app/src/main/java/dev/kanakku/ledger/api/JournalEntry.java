package dev.kanakku.ledger.api;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * An entry to append to a group's journal. Its lines must sum to zero in each currency.
 *
 * @param actorUserId the signed-in user who caused it, or null for a system entry
 */
public record JournalEntry(
    UUID groupId, EntryKind kind, UUID actorUserId, List<JournalLine> lines) {

  /**
   * An expense: the payer is credited the total (+) and each participant is debited their share
   * (-), in the map's order. A payer who also takes a share gets both lines. Shares of zero post no
   * line.
   *
   * @param sharesMinor participant member id to share in minor units, in stable member order
   * @throws IllegalArgumentException when there is no positive share or a share is negative
   */
  public static JournalEntry expense(
      UUID groupId,
      UUID actorUserId,
      String currency,
      UUID payerMemberId,
      LinkedHashMap<UUID, Long> sharesMinor) {
    long total = 0L;
    List<JournalLine> debits = new ArrayList<>();
    for (Map.Entry<UUID, Long> share : sharesMinor.entrySet()) {
      long amount = share.getValue();
      if (amount < 0L) {
        throw new IllegalArgumentException("a share cannot be negative");
      }
      if (amount == 0L) {
        continue;
      }
      total = Math.addExact(total, amount);
      debits.add(new JournalLine(share.getKey(), currency, Math.negateExact(amount)));
    }
    if (total == 0L) {
      throw new IllegalArgumentException("an expense needs a positive share");
    }
    List<JournalLine> lines = new ArrayList<>();
    lines.add(new JournalLine(payerMemberId, currency, total));
    lines.addAll(debits);
    return new JournalEntry(groupId, EntryKind.EXPENSE, actorUserId, List.copyOf(lines));
  }

  /**
   * A settlement: {@code fromMemberId} paid {@code toMemberId}, so the payer's balance rises by the
   * amount and the payee's falls by it.
   *
   * @throws IllegalArgumentException when the amount is not positive or both are the same member
   */
  public static JournalEntry settlement(
      UUID groupId,
      UUID actorUserId,
      String currency,
      UUID fromMemberId,
      UUID toMemberId,
      long amountMinor) {
    if (amountMinor <= 0L) {
      throw new IllegalArgumentException("a settlement amount must be positive");
    }
    if (fromMemberId.equals(toMemberId)) {
      throw new IllegalArgumentException("a member cannot settle with themself");
    }
    return new JournalEntry(
        groupId,
        EntryKind.SETTLEMENT,
        actorUserId,
        List.of(
            new JournalLine(fromMemberId, currency, amountMinor),
            new JournalLine(toMemberId, currency, Math.negateExact(amountMinor))));
  }
}
