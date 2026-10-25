package dev.kanakku.identity.util;

/** Hides most of an email address before it is shown to someone who is not its owner. */
public final class EmailMask {

  private static final String HIDDEN = "***";

  private EmailMask() {}

  /** {@code asha.k@example.com} becomes {@code a***@example.com}; a one-letter name is hidden. */
  public static String mask(String email) {
    if (email == null) {
      return HIDDEN;
    }
    int at = email.lastIndexOf('@');
    if (at < 1 || at == email.length() - 1) {
      return HIDDEN;
    }
    String domain = email.substring(at);
    String local = email.substring(0, at);
    return (local.length() == 1 ? "*" : local.substring(0, 1) + HIDDEN) + domain;
  }
}
