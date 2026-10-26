package dev.kanakku.groups.domain;

/** Where a claim suggestion stands. */
public enum ClaimSuggestionStatus {
  /** Waiting for the user to claim or decline. */
  PENDING,
  /** The user said it is not them. */
  DECLINED,
  /** The user claimed the guest and their balance moved onto the user's own row. */
  CONFIRMED,
  /** At confirm time the user no longer held the verified number, or the guest's number changed. */
  STALE
}
