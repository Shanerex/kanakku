package dev.kanakku.identity.api;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/** Lets other modules look users up without touching identity's tables. */
public interface UserDirectory {

  /** The most hits one search returns. */
  int MAX_RESULTS = 10;

  /** The fewest characters a name or email search query may have, after trimming. */
  int MIN_QUERY_LENGTH = 3;

  /** The fewest digits a phone search query may have. */
  int MIN_PHONE_DIGITS = 4;

  /**
   * Finds users by a query. A query of only digits (spaces, dashes, a leading plus allowed) is a
   * phone search: it matches any part of a stored number, verified or not. Anything else matches
   * the name or the email, ignoring case. Invite-only users are included (flagged by {@link
   * UserMatch#directAdd()}); users in {@code excludeUserIds} are left out. Each hit carries only
   * the masked field it matched, and a phone hit says whether that number is verified.
   *
   * @param limit capped at {@link #MAX_RESULTS}
   * @throws dev.kanakku.platform.problem.ProblemException 400 when a text query is under {@link
   *     #MIN_QUERY_LENGTH} characters or a phone query under {@link #MIN_PHONE_DIGITS} digits
   */
  List<UserMatch> search(String query, Collection<UUID> excludeUserIds, int limit);

  /** Display names for the given user ids; unknown ids are absent from the result. */
  Map<UUID, UserSummary> summaries(Collection<UUID> ids);

  /**
   * True when the user exists and has not chosen invite-only. A phone number, verified or not,
   * never changes the answer.
   */
  boolean allowsDirectAdd(UUID userId);

  /** The user's phone number in E.164 when it is verified; empty otherwise. Never log it. */
  Optional<String> verifiedPhone(UUID userId);

  /** The one account that has verified this E.164 number, if any. */
  Optional<UUID> verifiedPhoneHolder(String phoneE164);
}
