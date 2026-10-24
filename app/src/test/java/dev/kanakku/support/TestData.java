package dev.kanakku.support;

import java.util.UUID;

/** Unique test data so tests can share one database. */
public final class TestData {

  private TestData() {}

  public static String email() {
    return "user-" + UUID.randomUUID() + "@example.com";
  }
}
