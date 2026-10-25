package dev.kanakku.ledger.api;

import java.util.UUID;

/**
 * One side of an entry: a signed amount in minor units for a group member row (never a user id) and
 * an ISO 4217 currency. Positive means the group owes the member; negative means the member owes.
 */
public record JournalLine(UUID memberId, String currency, long amountMinor) {}
