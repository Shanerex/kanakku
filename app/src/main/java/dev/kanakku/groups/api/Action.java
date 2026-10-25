package dev.kanakku.groups.api;

/** What a caller wants to do in a group; an archived group allows only some of these. */
public enum Action {
  /** Add or change an expense; refused in an archived group. */
  ADD_EXPENSE,
  /** Record a payment between members; allowed in an archived group to clear leftover balances. */
  RECORD_SETTLEMENT
}
