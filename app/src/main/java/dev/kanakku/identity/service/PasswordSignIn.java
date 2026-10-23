package dev.kanakku.identity.service;

import dev.kanakku.identity.domain.Profile;
import dev.kanakku.identity.exception.InvalidCredentialsException;
import dev.kanakku.identity.exception.SignInBusyException;
import dev.kanakku.identity.exception.TooManyAttemptsException;
import dev.kanakku.identity.repository.UserRepository;
import java.util.Optional;
import java.util.UUID;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Checks an email and password. Gets credentials only through {@link
 * UserRepository#findCredentials}. An unknown email still runs one hash comparison, so it costs the
 * same as a wrong password.
 *
 * <p>Two steps, so Argon2id never runs while a transaction holds a pooled connection: a short
 * transaction takes the per-pair lock, checks the throttle, looks the user up and counts this try
 * as a failure up front; the hash is then checked with no transaction open, and a correct password
 * removes its own counted try.
 */
@Service
public class PasswordSignIn {

  private final PasswordEncoder passwordEncoder;
  private final UserRepository users;
  private final SignInThrottle throttle;
  private final UnknownEmailHashLimiter unknownEmailLimiter;
  private final TransactionTemplate transactions;
  private final String dummyHash;

  PasswordSignIn(
      PasswordEncoder passwordEncoder,
      UserRepository users,
      SignInThrottle throttle,
      UnknownEmailHashLimiter unknownEmailLimiter,
      TransactionTemplate transactions) {
    this.throttle = throttle;
    this.passwordEncoder = passwordEncoder;
    this.users = users;
    this.unknownEmailLimiter = unknownEmailLimiter;
    this.transactions = transactions;
    this.dummyHash = passwordEncoder.encode(UUID.randomUUID().toString());
  }

  /** What step one decided: who to check against, and the try that was counted. */
  private record Reserved(UUID userId, String hash, long failureId, boolean holdsSlot) {}

  /**
   * @return the signed-in user's profile
   * @throws TooManyAttemptsException when the (email, IP) pair is throttled, even for a right
   *     password
   * @throws SignInBusyException when too many unknown-email hashes are already running
   * @throws InvalidCredentialsException for an unknown email or a wrong password
   */
  public Profile signIn(String email, String password, String ip) {
    Reserved reserved = transactions.execute(status -> reserve(email, ip));
    // The try is committed; clean up old rows outside the lock, where an error costs nothing.
    throttle.prune();

    boolean matches;
    try {
      // An unknown email gets the same hashing cost as a wrong password, so timing is equal.
      matches = passwordEncoder.matches(password, reserved.hash());
    } finally {
      if (reserved.holdsSlot()) {
        unknownEmailLimiter.release();
      }
    }
    if (reserved.userId() == null || !matches) {
      throw new InvalidCredentialsException();
    }
    throttle.forgive(reserved.failureId());
    return users.findProfile(reserved.userId()).orElseThrow(InvalidCredentialsException::new);
  }

  private Reserved reserve(String email, String ip) {
    // Held until commit: the count and the reserved try are one step per pair.
    throttle.lock(email, ip);
    throttle
        .retryAfterSeconds(email, ip)
        .ifPresent(
            seconds -> {
              throw new TooManyAttemptsException(seconds);
            });
    Optional<UserRepository.Credentials> credentials = users.findCredentials(email);
    boolean unknown = credentials.isEmpty();
    if (unknown && !unknownEmailLimiter.tryAcquire()) {
      throw new SignInBusyException();
    }
    try {
      long failureId = throttle.reserveFailure(email, ip);
      return unknown
          ? new Reserved(null, dummyHash, failureId, true)
          : new Reserved(
              credentials.get().userId(), credentials.get().passwordHash(), failureId, false);
    } catch (RuntimeException e) {
      if (unknown) {
        unknownEmailLimiter.release();
      }
      throw e;
    }
  }
}
