package dev.kanakku.identity.internal;

import static dev.kanakku.jooq.Tables.PASSWORD_CREDENTIALS;
import static dev.kanakku.jooq.Tables.USERS;

import java.util.UUID;
import org.jooq.DSLContext;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Creates password accounts: the user row and its credentials commit together or not at all. */
@Service
public class SignUpService {

  private final DSLContext dsl;
  private final PasswordEncoder passwordEncoder;

  SignUpService(DSLContext dsl, PasswordEncoder passwordEncoder) {
    this.dsl = dsl;
    this.passwordEncoder = passwordEncoder;
  }

  /**
   * Creates an unverified account with the default currency INR.
   *
   * @throws EmailTakenException when another account already uses the email in any letter case
   */
  @Transactional
  public Profile signUp(String email, String password, String displayName) {
    if (!PasswordPolicy.isValid(password)) {
      throw new IllegalArgumentException("password length");
    }
    String trimmedEmail = email.strip();
    UUID id = UUID.randomUUID();
    try {
      dsl.insertInto(USERS)
          .set(USERS.ID, id)
          .set(USERS.EMAIL, trimmedEmail)
          .set(USERS.DISPLAY_NAME, displayName.strip())
          .execute();
    } catch (DuplicateKeyException e) {
      throw new EmailTakenException();
    }
    dsl.insertInto(PASSWORD_CREDENTIALS)
        .set(PASSWORD_CREDENTIALS.USER_ID, id)
        .set(PASSWORD_CREDENTIALS.PASSWORD_HASH, passwordEncoder.encode(password))
        .execute();
    return new Profile(id, trimmedEmail, false, displayName.strip(), "INR", null, 0);
  }
}
