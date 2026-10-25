package dev.kanakku.identity.api;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Lets other modules look users up without touching identity's tables. */
public interface UserDirectory {

  /** The most hits one search returns. */
  int MAX_RESULTS = 10;

  /** The fewest characters a search query may have, after trimming. */
  int MIN_QUERY_LENGTH = 3;

  /**
   * Finds users whose name or email contains {@code query}, ignoring case. Users who chose
   * invite-only and users in {@code excludeUserIds} are left out.
   *
   * @param limit capped at {@link #MAX_RESULTS}
   * @throws dev.kanakku.platform.problem.ProblemException 400 when the query is under {@link
   *     #MIN_QUERY_LENGTH} characters
   */
  List<UserMatch> search(String query, Collection<UUID> excludeUserIds, int limit);

  /** Display names for the given user ids; unknown ids are absent from the result. */
  Map<UUID, UserSummary> summaries(Collection<UUID> ids);

  /** True when the user exists and has not chosen invite-only. */
  boolean allowsDirectAdd(UUID userId);
}
