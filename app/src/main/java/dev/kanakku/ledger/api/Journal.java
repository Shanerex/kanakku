package dev.kanakku.ledger.api;

import java.util.UUID;

/** The append-only journal. */
public interface Journal {

  /**
   * Appends the entry and updates the member balances in one transaction (it joins the caller's
   * transaction when there is one).
   *
   * @return the new entry's id
   * @throws IllegalArgumentException when there are no lines, or the lines do not sum to zero in
   *     every currency
   */
  UUID post(JournalEntry entry);
}
