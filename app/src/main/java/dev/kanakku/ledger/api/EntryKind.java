package dev.kanakku.ledger.api;

/** Why a journal entry was posted. */
public enum EntryKind {
  EXPENSE,
  SETTLEMENT,
  WRITE_OFF,
  REVERSAL
}
