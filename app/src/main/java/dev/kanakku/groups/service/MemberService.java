package dev.kanakku.groups.service;

import dev.kanakku.groups.domain.Member;
import dev.kanakku.groups.domain.Role;
import dev.kanakku.groups.domain.WriteOffResult;
import dev.kanakku.groups.exception.GroupProblems;
import dev.kanakku.groups.repository.InviteRepository;
import dev.kanakku.groups.repository.MemberRepository;
import dev.kanakku.groups.repository.MemberRepository.MemberRow;
import dev.kanakku.groups.repository.MemberRepository.Membership;
import dev.kanakku.identity.api.UserDirectory;
import dev.kanakku.identity.api.UserMatch;
import dev.kanakku.identity.api.UserSummary;
import dev.kanakku.ledger.api.Balances;
import dev.kanakku.ledger.api.MemberBalance;
import dev.kanakku.ledger.api.MoneyBalance;
import dev.kanakku.ledger.api.WriteOffs;
import dev.kanakku.platform.phone.PhoneNumbers;
import dev.kanakku.platform.ratelimit.RateLimiter;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.OptionalLong;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

/** Member rules: adding guests and users, removing, leaving and writing off. */
@Service
public class MemberService {

  static final String SEARCH_BUCKET = "user-search";
  static final int SEARCHES_PER_MINUTE = 20;

  private final MemberRepository members;
  private final InviteRepository invites;
  private final GroupGuard guard;
  private final UserDirectory directory;
  private final Balances balances;
  private final WriteOffs writeOffs;
  private final TransactionTemplate transactions;
  private final RateLimiter limiter;
  private final ClaimSuggestionService claims;

  MemberService(
      MemberRepository members,
      InviteRepository invites,
      GroupGuard guard,
      UserDirectory directory,
      Balances balances,
      WriteOffs writeOffs,
      TransactionTemplate transactions,
      RateLimiter limiter,
      ClaimSuggestionService claims) {
    this.members = members;
    this.invites = invites;
    this.guard = guard;
    this.directory = directory;
    this.balances = balances;
    this.writeOffs = writeOffs;
    this.transactions = transactions;
    this.limiter = limiter;
    this.claims = claims;
  }

  /** The group's current members with their balances. A non-member gets a 404. */
  public List<Member> list(UUID groupId, UUID callerId) {
    List<MemberRow> rows = members.listActive(groupId, callerId);
    if (rows.isEmpty()) {
      throw GroupProblems.groupNotFound();
    }
    return view(groupId, callerId, rows);
  }

  /**
   * Adds a guest. Any member may; the group must not be archived. A number that a member of the
   * group has verified becomes a claim suggestion for them.
   */
  public Member addGuest(UUID groupId, UUID callerId, String name, String email, String phone) {
    UUID id = UUID.randomUUID();
    String cleanEmail = email == null || email.isBlank() ? null : email.strip();
    String e164 = phone == null || phone.isBlank() ? null : PhoneNumbers.parse(phone);
    return transactions.execute(
        status -> {
          Membership caller = guard.member(groupId, callerId, true);
          if (!members.insertGuest(id, groupId, callerId, name.strip(), cleanEmail, e164)) {
            throw GroupProblems.groupNotFound();
          }
          if (e164 != null) {
            claims.suggestForGuest(groupId, id, e164);
          }
          return one(groupId, callerId, id, caller.role() == Role.OWNER);
        });
  }

  /**
   * Sets, changes or clears a guest's number. Owner only, with the member's version. A changed
   * number withdraws the guest's pending claim suggestions, and a new one may make another.
   *
   * @param phone blank clears the number
   */
  public Member setGuestPhone(
      UUID groupId, UUID memberId, UUID callerId, String phone, int version) {
    String e164 = phone == null || phone.isBlank() ? null : PhoneNumbers.parse(phone);
    return transactions.execute(
        status -> {
          guard.owner(groupId, callerId, true);
          MemberRow guest =
              members
                  .findActive(groupId, memberId, callerId, true)
                  .orElseThrow(GroupProblems::memberNotFound);
          if (guest.userId() != null) {
            throw GroupProblems.notAGuest();
          }
          if (guest.version() != version
              || !members.updateGuestPhone(groupId, memberId, callerId, e164, version)) {
            throw GroupProblems.staleVersion();
          }
          if (!Objects.equals(e164, guest.guestPhoneE164())) {
            claims.withdrawForGuest(groupId, memberId, callerId);
            if (e164 != null) {
              claims.suggestForGuest(groupId, memberId, e164);
            }
          }
          return one(groupId, callerId, memberId, true);
        });
  }

  /**
   * Searches registered users by name, email or phone (see {@code UserDirectory.search}). Owner
   * only, at most {@value #SEARCHES_PER_MINUTE} searches a minute per user.
   */
  public List<UserMatch> search(UUID groupId, UUID callerId, String query) {
    guard.owner(groupId, callerId, false);
    OptionalLong wait =
        limiter.tryHit(
            SEARCH_BUCKET, "user:" + callerId, SEARCHES_PER_MINUTE, Duration.ofMinutes(1));
    if (wait.isPresent()) {
      throw GroupProblems.searchRateLimited(wait.getAsLong());
    }
    Set<UUID> inGroup = members.activeUserIds(groupId, callerId);
    return directory.search(query, inGroup, UserDirectory.MAX_RESULTS);
  }

  /**
   * Adds a registered user as a Member. Owner only; refused for an invite-only user. A phone number
   * on file, verified or not, plays no part.
   */
  public Member addUser(UUID groupId, UUID callerId, UUID userId) {
    UUID id = UUID.randomUUID();
    return transactions.execute(
        status -> {
          guard.owner(groupId, callerId, true);
          if (members.activeUserIds(groupId, callerId).contains(userId)) {
            throw GroupProblems.alreadyMember();
          }
          if (!directory.allowsDirectAdd(userId)) {
            throw GroupProblems.inviteOnly();
          }
          try {
            if (!members.insertUserMember(id, groupId, callerId, userId)) {
              throw GroupProblems.ownerOnly();
            }
          } catch (DuplicateKeyException e) {
            throw GroupProblems.alreadyMember();
          }
          claims.suggestForNewMember(userId);
          return one(groupId, callerId, id, true);
        });
  }

  /**
   * Removes a member who has no balance. Owner only. The member row is locked first, so a post that
   * lands during the check cannot slip past it.
   */
  public void remove(UUID groupId, UUID memberId, UUID callerId, int version) {
    transactions.executeWithoutResult(
        status -> {
          guard.owner(groupId, callerId, true);
          MemberRow target =
              members
                  .findActive(groupId, memberId, callerId, true)
                  .orElseThrow(GroupProblems::memberNotFound);
          if (target.role() == Role.OWNER) {
            throw GroupProblems.lastOwner();
          }
          if (target.version() != version) {
            throw GroupProblems.staleVersion();
          }
          requireSettled(groupId, memberId, callerId);
          if (!members.markLeft(groupId, memberId, callerId, version)) {
            throw GroupProblems.staleVersion();
          }
          invites.revokeLiveForMember(memberId);
        });
  }

  /** The caller leaves. The Owner never can; a balance must be settled first. */
  public void leave(UUID groupId, UUID callerId) {
    transactions.executeWithoutResult(
        status -> {
          Membership membership = guard.member(groupId, callerId, true);
          MemberRow self =
              members
                  .findActive(groupId, membership.memberId(), callerId, true)
                  .orElseThrow(GroupProblems::groupNotFound);
          if (self.role() == Role.OWNER) {
            throw GroupProblems.lastOwner();
          }
          requireSettled(groupId, self.id(), callerId);
          if (!members.markLeft(groupId, self.id(), callerId, self.version())) {
            throw GroupProblems.staleVersion();
          }
        });
  }

  /**
   * Clears the member's balance onto the chosen members, equally. Owner only.
   *
   * @param absorberMemberIds current members other than the target, each once
   */
  public WriteOffResult writeOff(
      UUID groupId, UUID memberId, UUID callerId, List<UUID> absorberMemberIds) {
    return transactions.execute(
        status -> {
          guard.owner(groupId, callerId, true);
          // The target and the absorbers are locked in one query, so every write-off takes its
          // member locks in the same (joined_at, id) order whichever member it targets.
          Set<UUID> distinct = new HashSet<>(absorberMemberIds);
          Set<UUID> toLock = new HashSet<>(distinct);
          toLock.add(memberId);
          List<UUID> locked = members.activeIdsInOrderLocked(groupId, toLock, callerId);
          if (!locked.contains(memberId)) {
            throw GroupProblems.memberNotFound();
          }
          if (distinct.size() != absorberMemberIds.size() || distinct.contains(memberId)) {
            throw GroupProblems.invalidAbsorbers();
          }
          List<UUID> ordered = locked.stream().filter(id -> !id.equals(memberId)).toList();
          if (ordered.size() != distinct.size()) {
            throw GroupProblems.invalidAbsorbers();
          }
          return new WriteOffResult(writeOffs.writeOff(groupId, memberId, ordered, callerId));
        });
  }

  private void requireSettled(UUID groupId, UUID memberId, UUID callerId) {
    List<MoneyBalance> owed =
        balances.ofMember(groupId, memberId, callerId).stream()
            .filter(b -> b.amountMinor() != 0L)
            .toList();
    if (!owed.isEmpty()) {
      throw GroupProblems.memberHasBalance(owed);
    }
  }

  /** One member as a screen shows them: reads that member's row and balances, not the group's. */
  private Member one(UUID groupId, UUID callerId, UUID memberId, boolean callerIsOwner) {
    MemberRow row =
        members
            .findActive(groupId, memberId, callerId, false)
            .orElseThrow(GroupProblems::memberNotFound);
    Map<UUID, UserSummary> users =
        row.userId() == null ? Map.of() : directory.summaries(List.of(row.userId()));
    List<MoneyBalance> owed =
        balances.ofMember(groupId, memberId, callerId).stream()
            .filter(b -> b.amountMinor() != 0L)
            .toList();
    return toMember(row, users, owed, callerIsOwner);
  }

  private List<Member> view(UUID groupId, UUID callerId, List<MemberRow> rows) {
    Map<UUID, UserSummary> users =
        directory.summaries(rows.stream().map(MemberRow::userId).filter(Objects::nonNull).toList());
    Map<UUID, List<MoneyBalance>> byMember =
        balances.ofGroup(groupId, callerId).stream()
            .filter(b -> b.amountMinor() != 0L)
            .collect(
                Collectors.groupingBy(
                    MemberBalance::memberId,
                    Collectors.mapping(
                        b -> new MoneyBalance(b.currency(), b.amountMinor()),
                        Collectors.toList())));
    boolean callerIsOwner =
        rows.stream().anyMatch(r -> callerId.equals(r.userId()) && r.role() == Role.OWNER);
    List<Member> result = new ArrayList<>();
    for (MemberRow row : rows) {
      result.add(toMember(row, users, byMember.getOrDefault(row.id(), List.of()), callerIsOwner));
    }
    return result;
  }

  private static Member toMember(
      MemberRow row, Map<UUID, UserSummary> users, List<MoneyBalance> owed, boolean callerIsOwner) {
    boolean guest = row.userId() == null;
    UserSummary user = guest ? null : users.get(row.userId());
    String name = user != null ? user.displayName() : row.guestName();
    return new Member(
        row.id(),
        row.userId(),
        name == null ? "Member" : name,
        row.role(),
        guest,
        callerIsOwner && guest ? row.guestEmail() : null,
        callerIsOwner && guest && row.guestPhoneE164() != null
            ? PhoneNumbers.mask(row.guestPhoneE164())
            : null,
        row.joinedAt(),
        owed,
        row.version());
  }
}
