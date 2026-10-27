package dev.kanakku.identity.service;

import dev.kanakku.identity.domain.DirectAdd;
import dev.kanakku.identity.domain.Profile;
import dev.kanakku.identity.exception.PhoneProblems;
import dev.kanakku.identity.exception.PhoneTakenException;
import dev.kanakku.identity.exception.VersionConflictException;
import dev.kanakku.identity.repository.PhoneChallengeRepository;
import dev.kanakku.identity.repository.UserRepository;
import dev.kanakku.identity.util.ThrottleIp;
import dev.kanakku.platform.phone.PhoneNumbers;
import dev.kanakku.platform.ratelimit.RateLimiter;
import java.time.Duration;
import java.util.OptionalLong;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

/** The caller's own profile. Every call takes the caller's id from the session, never a request. */
@Service
public class ProfileService {

  /** Saves of a number per user per hour; also bounds how often "is it taken" can be asked. */
  static final String USER_PHONE_BUCKET = "phone-update-user";

  static final int MAX_PHONE_UPDATES_PER_USER = 10;

  /** Saves of a number per client address per hour, across all accounts. */
  static final String IP_PHONE_BUCKET = "phone-update-ip";

  static final int MAX_PHONE_UPDATES_PER_IP = 30;
  static final Duration PHONE_UPDATE_WINDOW = Duration.ofHours(1);

  private final UserRepository users;
  private final PhoneChallengeRepository challenges;
  private final TransactionTemplate transactions;
  private final RateLimiter limiter;

  ProfileService(
      UserRepository users,
      PhoneChallengeRepository challenges,
      TransactionTemplate transactions,
      RateLimiter limiter) {
    this.users = users;
    this.challenges = challenges;
    this.transactions = transactions;
    this.limiter = limiter;
  }

  public Profile get(UUID callerId) {
    return users.findProfile(callerId).orElseThrow(() -> new IllegalStateException("no such user"));
  }

  /**
   * @param upiId blank means none
   * @throws VersionConflictException when {@code version} is not the stored version
   */
  public Profile update(
      UUID callerId, String displayName, String currency, String upiId, int version) {
    return update(callerId, displayName, currency, upiId, null, version);
  }

  /**
   * @param upiId blank means none
   * @param directAdd null keeps the stored choice
   * @throws VersionConflictException when {@code version} is not the stored version
   */
  public Profile update(
      UUID callerId,
      String displayName,
      String currency,
      String upiId,
      DirectAdd directAdd,
      int version) {
    String upi = upiId == null || upiId.isBlank() ? null : upiId.strip();
    return users
        .updateProfile(callerId, displayName.strip(), currency, upi, directAdd, version)
        .orElseThrow(VersionConflictException::new);
  }

  /**
   * Sets the caller's one mobile number, replacing any earlier one and starting it unverified (the
   * same number keeps its state). A code still pending for the old number is dropped.
   *
   * <p>Answering "taken" tells a signed-in caller that someone verified the number, so every call
   * counts against a per-user and a per-address hourly limit. The count is recorded before the
   * lookup and in its own transaction, so a refused or "taken" answer still counts.
   *
   * @param ip the client address, for the per-address limit
   * @throws dev.kanakku.platform.problem.ProblemException 400 {@code invalid-phone}, 429 {@code
   *     too-many-codes} past a limit
   * @throws PhoneTakenException when another account has verified this number
   * @throws VersionConflictException when {@code version} is not the stored version
   */
  public Profile updatePhone(UUID callerId, String rawPhone, int version, String ip) {
    String e164 = PhoneNumbers.parse(rawPhone);
    refuseIfWaiting(
        limiter.tryHit(
            USER_PHONE_BUCKET,
            "user:" + callerId,
            MAX_PHONE_UPDATES_PER_USER,
            PHONE_UPDATE_WINDOW));
    refuseIfWaiting(
        limiter.tryHit(
            IP_PHONE_BUCKET,
            "ip:" + ThrottleIp.of(ip),
            MAX_PHONE_UPDATES_PER_IP,
            PHONE_UPDATE_WINDOW));
    return transactions.execute(
        status -> {
          if (users.isPhoneVerifiedByOther(e164, callerId)) {
            throw new PhoneTakenException();
          }
          Profile saved =
              users.updatePhone(callerId, e164, version).orElseThrow(VersionConflictException::new);
          if (!e164.equals(challenges.findNumber(callerId).orElse(e164))) {
            challenges.delete(callerId);
          }
          return saved;
        });
  }

  /**
   * Removes the caller's number, verified or not, and drops any code still pending for it. A
   * removed verified number frees it for another account.
   *
   * @throws VersionConflictException when {@code version} is not the stored version
   */
  public Profile removePhone(UUID callerId, int version) {
    return transactions.execute(
        status -> {
          Profile saved =
              users.clearPhone(callerId, version).orElseThrow(VersionConflictException::new);
          challenges.delete(callerId);
          return saved;
        });
  }

  private static void refuseIfWaiting(OptionalLong wait) {
    if (wait.isPresent()) {
      throw PhoneProblems.tooManyCodes(wait.getAsLong());
    }
  }
}
