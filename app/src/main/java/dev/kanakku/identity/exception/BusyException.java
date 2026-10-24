package dev.kanakku.identity.exception;

/**
 * Too many password hashes are being processed, or one client address already has its share. The
 * caller should retry in a moment. It never depends on whether an account exists.
 */
public class BusyException extends RuntimeException {

  public BusyException() {
    super("busy");
  }
}
