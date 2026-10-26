package dev.kanakku.ledger.api;

/** Why a journal entry was posted. */
public enum EntryKind {
  EXPENSE,
  SETTLEMENT,
  WRITE_OFF,
  REVERSAL,
  /** A guest's balance moved onto the member row of the user who claimed them. */
  MERGE
}
