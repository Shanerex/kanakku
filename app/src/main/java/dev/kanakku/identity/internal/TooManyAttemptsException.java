package dev.kanakku.identity.internal;

/** Sign-in refused by the throttle; the caller may retry after {@link #retryAfterSeconds()}. */
public class TooManyAttemptsException extends RuntimeException {

  private final long retryAfterSeconds;

  public TooManyAttemptsException(long retryAfterSeconds) {
    super("too many sign-in attempts");
    this.retryAfterSeconds = retryAfterSeconds;
  }

  public long retryAfterSeconds() {
    return retryAfterSeconds;
  }
}
