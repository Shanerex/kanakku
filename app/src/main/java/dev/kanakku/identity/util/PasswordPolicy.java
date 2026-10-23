package dev.kanakku.identity.util;

/** Password rule: 8 to 128 characters (code points), no composition rules. */
public final class PasswordPolicy {

  public static final int MIN_LENGTH = 8;
  public static final int MAX_LENGTH = 128;

  private PasswordPolicy() {}

  public static boolean isValid(String password) {
    if (password == null) {
      return false;
    }
    int length = password.codePointCount(0, password.length());
    return length >= MIN_LENGTH && length <= MAX_LENGTH;
  }
}
