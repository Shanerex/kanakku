package dev.kanakku.identity.util;

import dev.kanakku.platform.phone.PhoneNumbers;

/** Hides a phone number before it is shown to someone who is not its owner. */
public final class PhoneMask {

  private PhoneMask() {}

  /** {@code +919876543210} becomes {@code +91 ••••• ••10}; anything unreadable is fully hidden. */
  public static String mask(String e164) {
    return PhoneNumbers.mask(e164);
  }
}
