package dev.kanakku.ledger.api;

import java.util.List;
import java.util.UUID;

/**
 * Journal history reads, scoped to the caller inside the SQL: a user who is not a current member of
 * the group gets an empty list.
 */
public interface History {

  /** Most entries one page returns. */
  int MAX_LIMIT = 200;

  /**
   * The group's entries in journal order (an edit shows as the original, its reversal, then the new
   * entry), starting after {@code afterSeq} when it is not null.
   *
   * @param limit page size; values above {@link #MAX_LIMIT} are capped
   * @throws IllegalArgumentException when {@code limit} is less than 1
   */
  List<HistoryEntry> ofGroup(UUID groupId, UUID callerId, Long afterSeq, int limit);
}
