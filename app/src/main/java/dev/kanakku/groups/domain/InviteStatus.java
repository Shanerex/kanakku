package dev.kanakku.groups.domain;

/** Where an invite stands; only ACTIVE can be claimed. */
public enum InviteStatus {
  ACTIVE,
  EXPIRED,
  REVOKED,
  USED
}
