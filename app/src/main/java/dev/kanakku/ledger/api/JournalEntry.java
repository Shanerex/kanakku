package dev.kanakku.ledger.api;

import java.util.List;
import java.util.UUID;

/**
 * An entry to append to a group's journal. Its lines must sum to zero in each currency.
 *
 * @param actorUserId the signed-in user who caused it, or null for a system entry
 */
public record JournalEntry(
    UUID groupId, EntryKind kind, UUID actorUserId, List<JournalLine> lines) {}
