package dev.kanakku.identity.service;

import dev.kanakku.identity.api.UserDirectory;
import dev.kanakku.identity.api.UserMatch;
import dev.kanakku.identity.api.UserSummary;
import dev.kanakku.identity.domain.DirectAdd;
import dev.kanakku.identity.repository.UserRepository;
import dev.kanakku.identity.util.EmailMask;
import dev.kanakku.platform.problem.ProblemException;
import dev.kanakku.platform.problem.Problems;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

/** The one door other modules use to look users up. Emails leave here masked. */
@Service
class UserDirectoryService implements UserDirectory {

  private final UserRepository users;

  UserDirectoryService(UserRepository users) {
    this.users = users;
  }

  @Override
  public List<UserMatch> search(String query, Collection<UUID> excludeUserIds, int limit) {
    String q = query == null ? "" : query.strip();
    if (q.codePointCount(0, q.length()) < MIN_QUERY_LENGTH) {
      throw new ProblemException(
          Problems.of(
              HttpStatus.BAD_REQUEST,
              "search-too-short",
              "Search needs more letters",
              "Type at least " + MIN_QUERY_LENGTH + " characters to search."));
    }
    int capped = Math.max(1, Math.min(limit, MAX_RESULTS));
    return users.searchDirectAddable("%" + escapeLike(q) + "%", excludeUserIds, capped).stream()
        .map(h -> new UserMatch(h.id(), h.displayName(), EmailMask.mask(h.email())))
        .toList();
  }

  @Override
  public Map<UUID, UserSummary> summaries(Collection<UUID> ids) {
    return users.findDisplayNames(ids).entrySet().stream()
        .collect(
            Collectors.toUnmodifiableMap(
                Map.Entry::getKey, e -> new UserSummary(e.getKey(), e.getValue())));
  }

  @Override
  public boolean allowsDirectAdd(UUID userId) {
    return users.findDirectAdd(userId).map(d -> d == DirectAdd.ALLOW).orElse(false);
  }

  private static String escapeLike(String q) {
    return q.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
  }
}
