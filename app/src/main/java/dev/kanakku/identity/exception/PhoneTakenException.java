package dev.kanakku.identity.exception;

/** The number is already verified on another account, so it cannot be claimed or verified here. */
public class PhoneTakenException extends RuntimeException {

  public PhoneTakenException() {
    super("phone already verified on another account");
  }
}
