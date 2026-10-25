package dev.kanakku.groups.service;

import dev.kanakku.groups.domain.CoverPreset;
import dev.kanakku.groups.domain.Group;
import dev.kanakku.groups.domain.GroupType;
import dev.kanakku.groups.exception.GroupProblems;
import dev.kanakku.groups.repository.GroupRepository;
import dev.kanakku.groups.repository.MemberRepository;
import dev.kanakku.groups.repository.MemberRepository.Membership;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

/** Group rules: who may see and change a group. */
@Service
public class GroupService {

  private final GroupRepository groups;
  private final MemberRepository members;
  private final GroupGuard guard;
  private final TransactionTemplate transactions;

  GroupService(
      GroupRepository groups,
      MemberRepository members,
      GroupGuard guard,
      TransactionTemplate transactions) {
    this.groups = groups;
    this.members = members;
    this.guard = guard;
    this.transactions = transactions;
  }

  /** Creates the group and makes the caller its Owner, together or not at all. */
  public Group create(UUID callerId, String name, GroupType type, String currency, String cover) {
    UUID groupId = UUID.randomUUID();
    String storedCover = cover == null ? null : CoverPreset.normalize(cover).orElseThrow();
    return transactions.execute(
        status -> {
          groups.insert(groupId, name.strip(), type, currency, storedCover);
          members.insertCreator(UUID.randomUUID(), groupId, callerId);
          return groups.findForCaller(groupId, callerId).orElseThrow();
        });
  }

  public List<Group> list(UUID callerId) {
    return groups.listForCaller(callerId);
  }

  public Group get(UUID groupId, UUID callerId) {
    return groups.findForCaller(groupId, callerId).orElseThrow(GroupProblems::groupNotFound);
  }

  /** Changes name, type or cover. Owner only; a stale version is a 409. */
  public Group update(
      UUID groupId, UUID callerId, int version, String name, GroupType type, String cover) {
    guard.owner(groupId, callerId, true);
    String storedCover = cover == null ? null : CoverPreset.normalize(cover).orElseThrow();
    return groups
        .update(groupId, callerId, version, name == null ? null : name.strip(), type, storedCover)
        .orElseThrow(GroupProblems::staleVersion);
  }

  /** Makes the group read-only except for settlements. Owner only. */
  public Group archive(UUID groupId, UUID callerId, int version) {
    guard.owner(groupId, callerId, true);
    return groups
        .setArchived(groupId, callerId, version, true)
        .orElseThrow(GroupProblems::staleVersion);
  }

  /** Reopens an archived group. Owner only; the one write an archived group allows. */
  public Group unarchive(UUID groupId, UUID callerId, int version) {
    Membership membership = guard.owner(groupId, callerId, false);
    if (membership.archivedAt() == null) {
      throw GroupProblems.staleVersion();
    }
    return groups
        .setArchived(groupId, callerId, version, false)
        .orElseThrow(GroupProblems::staleVersion);
  }
}
