package dev.kanakku.identity.internal;

/** Sign-in failed. Deliberately says nothing about whether the email exists. */
public class InvalidCredentialsException extends RuntimeException {

  public InvalidCredentialsException() {
    super("invalid credentials");
  }
}
