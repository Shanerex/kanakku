package dev.kanakku.groups.api;

import java.util.UUID;

/** The check expenses and settlements run before they post to a group. */
public interface GroupAccess {

  /**
   * Confirms the caller is a current member and the group accepts the action.
   *
   * @return the caller's member id in the group, which is what ledger lines name
   * @throws dev.kanakku.platform.problem.ProblemException 404 for a non-member (also for an unknown
   *     group), 409 {@code group-archived} for {@link Action#ADD_EXPENSE} in an archived group
   */
  UUID requireWritable(UUID groupId, UUID callerUserId, Action action);
}
