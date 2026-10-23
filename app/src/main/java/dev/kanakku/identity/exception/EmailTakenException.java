package dev.kanakku.identity.exception;

/** Sign-up refused because the email already belongs to an account (compared ignoring case). */
public class EmailTakenException extends RuntimeException {

  public EmailTakenException() {
    super("email already registered");
  }
}
