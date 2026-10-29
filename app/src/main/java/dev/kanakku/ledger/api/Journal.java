package dev.kanakku.ledger.api;

import java.util.UUID;

/** The append-only journal. */
public interface Journal {

  /**
   * Appends an unkeyed system entry (WRITE_OFF or MERGE) and updates the member balances in one
   * transaction (it joins the caller's transaction when there is one).
   *
   * @return the new entry's id
   * @throws IllegalArgumentException when there are no lines, a line is zero or has no ISO 4217
   *     currency, the lines do not sum to zero in every currency, or the kind needs a key
   */
  UUID post(JournalEntry entry);

  /**
   * Appends the entry once per {@code idempotencyKey} within its group. A repeat with the same
   * content returns the stored entry and changes nothing; the key never expires.
   *
   * @throws LedgerConflict KEY_REUSED when the key already posted different content (or another
   *     member posted under it), NOT_A_MEMBER when the actor is not a current member of the group
   * @throws IllegalArgumentException as {@link #post(JournalEntry)}, for a REVERSAL entry, a null
   *     actor, or a blank key or one longer than 255 characters
   */
  Posted post(JournalEntry entry, String idempotencyKey);

  /**
   * Appends a REVERSAL of an EXPENSE or SETTLEMENT entry: the same lines negated, linked to it.
   * Every member's balance returns to what it was without the original.
   *
   * @throws LedgerConflict NOT_A_MEMBER, NOT_IN_GROUP, NOT_REVERSIBLE, ALREADY_REVERSED or
   *     KEY_REUSED
   */
  Posted reverse(UUID groupId, UUID entryId, UUID actorUserId, String idempotencyKey);

  /**
   * An edit: reverses the entry and appends {@code next} linked to it, in one transaction. The
   * reversal uses the key {@code <key>:rev} and the new entry {@code <key>}, so a replay is whole.
   *
   * @return the new entry
   * @throws LedgerConflict as {@link #reverse}, or NOT_REVERSIBLE when {@code next} is not an
   *     EXPENSE or SETTLEMENT
   */
  Posted replace(UUID groupId, UUID entryId, JournalEntry next, String idempotencyKey);
}
