package dev.kanakku.groups.service;

import dev.kanakku.groups.api.Action;
import dev.kanakku.groups.api.GroupAccess;
import dev.kanakku.groups.exception.GroupProblems;
import dev.kanakku.groups.repository.MemberRepository;
import dev.kanakku.groups.repository.MemberRepository.Membership;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
class GroupAccessService implements GroupAccess {

  private final MemberRepository members;

  GroupAccessService(MemberRepository members) {
    this.members = members;
  }

  @Override
  public UUID requireWritable(UUID groupId, UUID callerUserId, Action action) {
    Membership membership =
        members.findMembership(groupId, callerUserId).orElseThrow(GroupProblems::groupNotFound);
    if (action == Action.ADD_EXPENSE && membership.archivedAt() != null) {
      throw GroupProblems.groupArchived();
    }
    return membership.memberId();
  }
}
