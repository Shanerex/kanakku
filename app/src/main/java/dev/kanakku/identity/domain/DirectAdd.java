package dev.kanakku.identity.domain;

/** Who may add a user to a group directly, without an invite. */
public enum DirectAdd {
  /** Any group Owner can add this user (the default). */
  ALLOW,
  /** The user joins only by claiming an invite. */
  INVITE_ONLY
}
