package dev.kanakku.identity.domain;

/** An Argon2 hash ready to store. {@code toString} masks it so it can never reach a log line. */
public record HashedPassword(String value) {

  @Override
  public String toString() {
    return "HashedPassword[****]";
  }
}
