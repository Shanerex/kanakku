package dev.kanakku.support;

import dev.kanakku.platform.phone.PhoneNumbers;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/** Unique test data so tests can share one database. */
public final class TestData {

  private TestData() {}

  public static String email() {
    return "user-" + UUID.randomUUID() + "@example.com";
  }

  /** A random valid Indian mobile number as ten national digits, for example 9876543210. */
  public static String phoneDigits() {
    while (true) {
      String digits = String.valueOf(6 + ThreadLocalRandom.current().nextInt(4));
      digits += String.format("%09d", ThreadLocalRandom.current().nextLong(1_000_000_000L));
      if (PhoneNumbers.tryParse(digits).isPresent()) {
        return digits;
      }
    }
  }

  /** The E.164 form of {@link #phoneDigits()}'s kind of number. */
  public static String phone() {
    return "+91" + phoneDigits();
  }
}
