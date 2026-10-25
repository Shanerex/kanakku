package dev.kanakku.groups.service;

import dev.kanakku.groups.domain.Role;
import dev.kanakku.groups.exception.GroupProblems;
import dev.kanakku.groups.repository.MemberRepository;
import dev.kanakku.groups.repository.MemberRepository.Membership;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * Looks up the caller's place in a group and refuses what their role or the group's state does not
 * allow. The lookup itself is scoped to the caller in SQL, so a non-member always gets a 404.
 */
@Component
class GroupGuard {

  private final MemberRepository members;

  GroupGuard(MemberRepository members) {
    this.members = members;
  }

  /**
   * @param writable true to refuse an archived group (409)
   * @throws dev.kanakku.platform.problem.ProblemException 404 for a non-member
   */
  Membership member(UUID groupId, UUID callerId, boolean writable) {
    Membership membership =
        members.findMembership(groupId, callerId).orElseThrow(GroupProblems::groupNotFound);
    if (writable && membership.archivedAt() != null) {
      throw GroupProblems.groupArchived();
    }
    return membership;
  }

  /**
   * @throws dev.kanakku.platform.problem.ProblemException 404 for a non-member, 403 for a Member
   */
  Membership owner(UUID groupId, UUID callerId, boolean writable) {
    Membership membership = member(groupId, callerId, false);
    if (membership.role() != Role.OWNER) {
      throw GroupProblems.ownerOnly();
    }
    if (writable && membership.archivedAt() != null) {
      throw GroupProblems.groupArchived();
    }
    return membership;
  }
}
