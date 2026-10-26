package dev.kanakku.identity.service;

import dev.kanakku.identity.domain.DirectAdd;
import dev.kanakku.identity.domain.HashedPassword;
import dev.kanakku.identity.domain.Profile;
import dev.kanakku.identity.exception.BusyException;
import dev.kanakku.identity.exception.EmailTakenException;
import dev.kanakku.identity.exception.PhoneTakenException;
import dev.kanakku.identity.repository.PendingSignUpRepository;
import dev.kanakku.identity.repository.UserRepository;
import dev.kanakku.identity.util.PasswordPolicy;
import dev.kanakku.platform.problem.ProblemException;
import dev.kanakku.platform.problem.Problems;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Creates password accounts: the user row and its credentials commit together or not at all. The
 * Argon2 hash is made before any transaction opens, and at most a bounded number run at once, so
 * hashing never holds a pooled database connection.
 */
@Service
public class SignUpService {

  private final UserRepository users;
  private final PasswordEncoder passwordEncoder;
  private final SignUpHashLimiter hashSlots;
  private final IpHashGuard ipGuard;
  private final TransactionTemplate transactions;
  private final PendingSignUpRepository pendingSignUps;
  private final Clock clock;

  SignUpService(
      UserRepository users,
      PasswordEncoder passwordEncoder,
      SignUpHashLimiter hashSlots,
      IpHashGuard ipGuard,
      TransactionTemplate transactions,
      PendingSignUpRepository pendingSignUps,
      Clock clock) {
    this.users = users;
    this.passwordEncoder = passwordEncoder;
    this.hashSlots = hashSlots;
    this.ipGuard = ipGuard;
    this.transactions = transactions;
    this.pendingSignUps = pendingSignUps;
    this.clock = clock;
  }

  /**
   * Hashes a password with Argon2id for a request from {@code ip}. Call it before opening a
   * transaction.
   *
   * @throws IllegalArgumentException when the password breaks the length policy
   * @throws BusyException when the IP is at its cap, or no hash slot freed up within the short wait
   */
  public HashedPassword hashPassword(String password, String ip) {
    try (IpHashGuard.Permit permit = ipGuard.tryEnter(ip).orElseThrow(BusyException::new)) {
      return hash(password);
    }
  }

  private HashedPassword hash(String password) {
    if (!PasswordPolicy.isValid(password)) {
      throw new IllegalArgumentException("password length");
    }
    acquireSlot();
    try {
      return new HashedPassword(passwordEncoder.encode(password));
    } finally {
      hashSlots.release();
    }
  }

  /**
   * Creates an unverified account with the default currency INR. Joins the caller's transaction
   * when there is one.
   *
   * @throws EmailTakenException when another account already uses the email in any letter case
   */
  public Profile signUp(String email, HashedPassword hash, String displayName) {
    return signUp(email, hash, displayName, null);
  }

  /**
   * As above, with an optional mobile number kept unverified. Used when no SMS provider is set.
   *
   * @param phoneE164 the E.164 number, or null for none
   */
  public Profile signUp(String email, HashedPassword hash, String displayName, String phoneE164) {
    return transactions.execute(
        status -> {
          if (phoneE164 != null && users.isPhoneVerified(phoneE164)) {
            throw new PhoneTakenException();
          }
          return create(email, hash, displayName, phoneE164, null);
        });
  }

  /**
   * Creates the account for a sign-up whose code was confirmed ({@code verifiedAt} set) or that
   * finished without one (null). Joins the caller's transaction.
   *
   * @throws EmailTakenException when another account already uses the email
   */
  Profile createAccount(
      String email,
      HashedPassword hash,
      String displayName,
      String phoneE164,
      OffsetDateTime verifiedAt) {
    return transactions.execute(status -> create(email, hash, displayName, phoneE164, verifiedAt));
  }

  /**
   * Hashes first, then creates the account in its own transaction. For callers that are not a
   * client request, so there is no per-IP cap; a request goes through {@link #hashPassword}.
   */
  public Profile signUp(String email, String password, String displayName) {
    return signUp(email, hash(password), displayName, null);
  }

  /**
   * Checks a replayed sign-up key against the account it created. The request fingerprint leaves
   * the password out, so this is where a replay with another password is caught. Runs outside any
   * transaction.
   *
   * @throws ProblemException 422 when the password is not the created account's password
   * @throws BusyException when the IP is at its cap, or no hash slot freed up within the wait
   */
  public void verifyReplayedPassword(String email, String password, String ip) {
    verifyReplayedPassword(email, password, ip, null);
  }

  /**
   * As above for a sign-up that may still be waiting for its code: when {@code pendingId} names a
   * live pending sign-up the password is checked against that, otherwise against the account.
   */
  public void verifyReplayedPassword(String email, String password, String ip, UUID pendingId) {
    boolean matches = false;
    Optional<String> hash =
        pendingId == null ? Optional.empty() : pendingSignUps.findPasswordHash(pendingId, now());
    if (hash.isEmpty()) {
      hash = users.findCredentials(email).map(UserRepository.Credentials::passwordHash);
    }
    if (hash.isPresent()) {
      try (IpHashGuard.Permit permit = ipGuard.tryEnter(ip).orElseThrow(BusyException::new)) {
        acquireSlot();
        try {
          matches = passwordEncoder.matches(password, hash.get());
        } finally {
          hashSlots.release();
        }
      }
    }
    if (!matches) {
      throw new ProblemException(
          Problems.of(
              HttpStatus.UNPROCESSABLE_ENTITY,
              "idempotency-key-reuse",
              "Idempotency key already used",
              "This Idempotency-Key was already used with a different request."));
    }
  }

  private Profile create(
      String email,
      HashedPassword hash,
      String displayName,
      String phoneE164,
      OffsetDateTime verifiedAt) {
    String trimmedEmail = email.strip();
    UUID id = UUID.randomUUID();
    try {
      if (phoneE164 == null) {
        users.insertUser(id, trimmedEmail, displayName.strip());
      } else {
        users.insertUser(id, trimmedEmail, displayName.strip(), phoneE164, verifiedAt);
      }
    } catch (DuplicateKeyException e) {
      if (e.getMessage() != null && e.getMessage().contains("users_phone_verified_key")) {
        throw new PhoneTakenException();
      }
      throw new EmailTakenException();
    }
    users.insertCredentials(id, hash.value());
    return new Profile(
        id,
        trimmedEmail,
        false,
        displayName.strip(),
        "INR",
        null,
        DirectAdd.ALLOW,
        phoneE164,
        phoneE164 != null && verifiedAt != null,
        0);
  }

  private OffsetDateTime now() {
    return OffsetDateTime.ofInstant(clock.instant(), ZoneOffset.UTC);
  }

  private void acquireSlot() {
    if (!hashSlots.acquire()) {
      throw new BusyException();
    }
  }
}
