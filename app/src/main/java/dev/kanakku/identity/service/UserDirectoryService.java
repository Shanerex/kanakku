package dev.kanakku.identity.service;

import dev.kanakku.identity.api.MatchedBy;
import dev.kanakku.identity.api.UserDirectory;
import dev.kanakku.identity.api.UserMatch;
import dev.kanakku.identity.api.UserSummary;
import dev.kanakku.identity.domain.DirectAdd;
import dev.kanakku.identity.repository.UserRepository;
import dev.kanakku.identity.util.EmailMask;
import dev.kanakku.identity.util.PhoneMask;
import dev.kanakku.platform.phone.PhoneNumbers;
import dev.kanakku.platform.problem.ProblemException;
import dev.kanakku.platform.problem.Problems;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
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
    int capped = Math.max(1, Math.min(limit, MAX_RESULTS));
    var digits = PhoneNumbers.searchDigits(q);
    if (digits.isPresent()) {
      if (digits.get().length() < MIN_PHONE_DIGITS) {
        throw tooShort(
            "Search needs more digits", "Type at least " + MIN_PHONE_DIGITS + " digits to search.");
      }
      return users.searchByPhone(digits.get(), excludeUserIds, capped).stream()
          .map(
              h ->
                  new UserMatch(
                      h.id(),
                      h.displayName(),
                      MatchedBy.PHONE,
                      null,
                      PhoneMask.mask(h.phoneE164()),
                      h.phoneVerified(),
                      h.directAdd()))
          .toList();
    }
    if (q.codePointCount(0, q.length()) < MIN_QUERY_LENGTH) {
      throw tooShort(
          "Search needs more letters",
          "Type at least " + MIN_QUERY_LENGTH + " characters to search.");
    }
    return users.searchByText("%" + escapeLike(q) + "%", excludeUserIds, capped).stream()
        .map(
            h ->
                new UserMatch(
                    h.id(),
                    h.displayName(),
                    h.nameMatched() ? MatchedBy.NAME : MatchedBy.EMAIL,
                    EmailMask.mask(h.email()),
                    null,
                    null,
                    h.directAdd()))
        .toList();
  }

  private static ProblemException tooShort(String title, String detail) {
    return new ProblemException(
        Problems.of(HttpStatus.BAD_REQUEST, "search-too-short", title, detail));
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

  @Override
  public Optional<String> verifiedPhone(UUID userId) {
    return users.findVerifiedPhone(userId);
  }

  @Override
  public Optional<UUID> verifiedPhoneHolder(String phoneE164) {
    return users.findVerifiedPhoneHolder(phoneE164);
  }

  private static String escapeLike(String q) {
    return q.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
  }
}
