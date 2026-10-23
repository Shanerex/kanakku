package dev.kanakku.identity.service;

import dev.kanakku.identity.domain.Profile;
import dev.kanakku.identity.exception.EmailTakenException;
import dev.kanakku.identity.repository.UserRepository;
import dev.kanakku.identity.util.PasswordPolicy;
import java.util.UUID;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Creates password accounts: the user row and its credentials commit together or not at all. */
@Service
public class SignUpService {

  private final UserRepository users;
  private final PasswordEncoder passwordEncoder;

  SignUpService(UserRepository users, PasswordEncoder passwordEncoder) {
    this.users = users;
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
      users.insertUser(id, trimmedEmail, displayName.strip());
    } catch (DuplicateKeyException e) {
      throw new EmailTakenException();
    }
    users.insertCredentials(id, passwordEncoder.encode(password));
    return new Profile(id, trimmedEmail, false, displayName.strip(), "INR", null, 0);
  }
}
