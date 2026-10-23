package dev.kanakku.identity.exception;

/** Too many unknown-email hashes are running at once; the caller may retry shortly. */
public class SignInBusyException extends RuntimeException {

  public SignInBusyException() {
    super("sign-in busy");
  }
}
