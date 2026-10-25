package dev.kanakku.groups.service;

import dev.kanakku.groups.domain.Member;
import dev.kanakku.groups.domain.Role;
import dev.kanakku.groups.domain.WriteOffResult;
import dev.kanakku.groups.exception.GroupProblems;
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
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

/** Member rules: adding guests and users, removing, leaving and writing off. */
@Service
public class MemberService {

  private final MemberRepository members;
  private final GroupGuard guard;
  private final UserDirectory directory;
  private final Balances balances;
  private final WriteOffs writeOffs;
  private final TransactionTemplate transactions;

  MemberService(
      MemberRepository members,
      GroupGuard guard,
      UserDirectory directory,
      Balances balances,
      WriteOffs writeOffs,
      TransactionTemplate transactions) {
    this.members = members;
    this.guard = guard;
    this.directory = directory;
    this.balances = balances;
    this.writeOffs = writeOffs;
    this.transactions = transactions;
  }

  /** The group's current members with their balances. A non-member gets a 404. */
  public List<Member> list(UUID groupId, UUID callerId) {
    List<MemberRow> rows = members.listActive(groupId, callerId);
    if (rows.isEmpty()) {
      throw GroupProblems.groupNotFound();
    }
    return view(groupId, callerId, rows);
  }

  /** Adds a guest. Any member may; the group must not be archived. */
  public Member addGuest(UUID groupId, UUID callerId, String name, String email) {
    UUID id = UUID.randomUUID();
    String cleanEmail = email == null || email.isBlank() ? null : email.strip();
    return transactions.execute(
        status -> {
          guard.member(groupId, callerId, true);
          if (!members.insertGuest(id, groupId, callerId, name.strip(), cleanEmail)) {
            throw GroupProblems.groupNotFound();
          }
          return one(groupId, callerId, id);
        });
  }

  /** Searches registered users the Owner may add directly (see {@code UserDirectory.search}). */
  public List<UserMatch> search(UUID groupId, UUID callerId, String query) {
    guard.owner(groupId, callerId, false);
    Set<UUID> inGroup = members.activeUserIds(groupId, callerId);
    return directory.search(query, inGroup, UserDirectory.MAX_RESULTS);
  }

  /** Adds a registered user as a Member. Owner only; refused for an invite-only user. */
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
          return one(groupId, callerId, id);
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
          requireSettled(groupId, memberId);
          if (!members.markLeft(groupId, memberId, callerId, version)) {
            throw GroupProblems.staleVersion();
          }
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
          requireSettled(groupId, self.id());
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
          members
              .findActive(groupId, memberId, callerId, true)
              .orElseThrow(GroupProblems::memberNotFound);
          Set<UUID> distinct = new HashSet<>(absorberMemberIds);
          if (distinct.size() != absorberMemberIds.size() || distinct.contains(memberId)) {
            throw GroupProblems.invalidAbsorbers();
          }
          List<UUID> ordered = members.activeIdsInOrderLocked(groupId, distinct, callerId);
          if (ordered.size() != distinct.size()) {
            throw GroupProblems.invalidAbsorbers();
          }
          return new WriteOffResult(writeOffs.writeOff(groupId, memberId, ordered, callerId));
        });
  }

  private void requireSettled(UUID groupId, UUID memberId) {
    List<MoneyBalance> owed =
        balances.ofMember(groupId, memberId).stream().filter(b -> b.amountMinor() != 0L).toList();
    if (!owed.isEmpty()) {
      throw GroupProblems.memberHasBalance(owed);
    }
  }

  private Member one(UUID groupId, UUID callerId, UUID memberId) {
    return view(groupId, callerId, members.listActive(groupId, callerId)).stream()
        .filter(m -> m.id().equals(memberId))
        .findFirst()
        .orElseThrow(GroupProblems::memberNotFound);
  }

  private List<Member> view(UUID groupId, UUID callerId, List<MemberRow> rows) {
    Map<UUID, UserSummary> users =
        directory.summaries(rows.stream().map(MemberRow::userId).filter(Objects::nonNull).toList());
    Map<UUID, List<MoneyBalance>> byMember =
        balances.ofGroup(groupId).stream()
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
      boolean guest = row.userId() == null;
      UserSummary user = guest ? null : users.get(row.userId());
      String name = user != null ? user.displayName() : row.guestName();
      result.add(
          new Member(
              row.id(),
              row.userId(),
              name == null ? "Member" : name,
              row.role(),
              guest,
              callerIsOwner && guest ? row.guestEmail() : null,
              row.joinedAt(),
              byMember.getOrDefault(row.id(), List.of()),
              row.version()));
    }
    return result;
  }
}
