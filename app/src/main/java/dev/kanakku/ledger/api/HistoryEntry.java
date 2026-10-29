package dev.kanakku.ledger.api;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/**
 * One journal entry as history shows it.
 *
 * @param seq the entry's place in the journal's total order
 * @param reversesEntryId for a REVERSAL, the entry it undoes; otherwise null
 * @param replacesEntryId for an edit, the entry it supersedes; otherwise null
 * @param lines the entry's lines in line order
 */
public record HistoryEntry(
    UUID id,
    long seq,
    EntryKind kind,
    UUID actorUserId,
    OffsetDateTime createdAt,
    UUID reversesEntryId,
    UUID replacesEntryId,
    List<JournalLine> lines) {}
