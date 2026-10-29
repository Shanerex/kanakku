package dev.kanakku.ledger.api;

import java.util.UUID;

/**
 * The outcome of a keyed post.
 *
 * @param entryId the entry the key names: the new one, or the stored one on a replay
 * @param replayed true when the key had already posted this same content, so nothing changed
 */
public record Posted(UUID entryId, boolean replayed) {}
