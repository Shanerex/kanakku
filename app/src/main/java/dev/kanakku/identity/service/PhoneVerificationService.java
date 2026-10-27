package dev.kanakku.identity.service;

import dev.kanakku.identity.api.PhoneVerified;
import dev.kanakku.identity.domain.CodeSent;
import dev.kanakku.identity.domain.Profile;
import dev.kanakku.identity.exception.PhoneProblems;
import dev.kanakku.identity.exception.PhoneTakenException;
import dev.kanakku.identity.repository.PhoneChallengeRepository;
import dev.kanakku.identity.repository.PhoneChallengeRepository.Challenge;
import dev.kanakku.identity.repository.UserRepository;
import dev.kanakku.identity.util.ThrottleIp;
import dev.kanakku.platform.ratelimit.RateLimiter;
import java.time.Clock;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.OptionalLong;
import java.util.UUID;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Verifies a signed-in user's number by SMS code. Sends are limited per user (four an hour: the
 * first plus three resends), per number and per client address, and the whole instance, through
 * {@link SmsPolicy}. Wrong codes are counted per user, per number (shared with sign-up, so many
 * accounts cannot each take guesses at one number) and per client address; five wrong codes on a
 * user or a number lock confirming for 15 minutes. A wrong try is recorded and committed before the
 * refusal is thrown. The provider call never runs inside a transaction.
 */
@Service
public class PhoneVerificationService {

  static final String WRONG_BUCKET = "phone-wrong-code";
  static final String NUMBER_WRONG_BUCKET = "number-wrong-code";
  static final String IP_WRONG_BUCKET = "phone-ip-wrong-code";
  static final String SEND_BUCKET = "phone-send";
  static final int MAX_WRONG = 5;
  static final int MAX_WRONG_PER_IP = 20;
  static final Duration LOCK_WINDOW = Duration.ofMinutes(15);
  static final int MAX_SENDS = 4;
  static final Duration SEND_WINDOW = Duration.ofHours(1);

  private final UserRepository users;
  private final PhoneChallengeRepository challenges;
  private final PhoneCodeService codes;
  private final SmsPolicy policy;
  private final RateLimiter limiter;
  private final TransactionTemplate transactions;
  private final Clock clock;
  private final ApplicationEventPublisher events;

  PhoneVerificationService(
      UserRepository users,
      PhoneChallengeRepository challenges,
      PhoneCodeService codes,
      SmsPolicy policy,
      RateLimiter limiter,
      TransactionTemplate transactions,
      Clock clock,
      ApplicationEventPublisher events) {
    this.events = events;
    this.users = users;
    this.challenges = challenges;
    this.codes = codes;
    this.policy = policy;
    this.limiter = limiter;
    this.transactions = transactions;
    this.clock = clock;
  }

  /**
   * Makes a fresh code for the caller's number, replacing any pending one, and counts the send
   * against every limit. Joins the caller's transaction when there is one. The text goes out when
   * {@link Deferred#finish} runs, after that transaction has ended.
   *
   * @param ip the client address, for the per-address limits
   * @throws dev.kanakku.platform.problem.ProblemException 409 when no provider is configured, the
   *     caller has no number or it is verified already; 422 for a country that is not texted; 429
   *     past a send limit
   * @throws PhoneTakenException when another account verified this number
   */
  public Deferred<CodeSent> send(UUID userId, String ip) {
    if (!codes.enabled()) {
      throw PhoneProblems.smsNotConfigured();
    }
    return transactions.execute(
        status -> {
          Profile profile = users.findProfile(userId).orElseThrow();
          if (profile.phone() == null) {
            throw PhoneProblems.phoneMissing();
          }
          if (profile.phoneVerified()) {
            throw PhoneProblems.alreadyVerified();
          }
          if (users.isPhoneVerifiedByOther(profile.phone(), userId)) {
            throw new PhoneTakenException();
          }
          OptionalLong wait = limiter.tryHit(SEND_BUCKET, key(userId), MAX_SENDS, SEND_WINDOW);
          if (wait.isPresent()) {
            throw PhoneProblems.tooManyCodes(wait.getAsLong());
          }
          policy.requireSendAllowed(profile.phone(), ip, SmsPolicy.Origin.PROFILE);
          String phone = profile.phone();
          String code = codes.newCode();
          OffsetDateTime expires = now().plus(PhoneCodeService.TTL);
          challenges.upsert(userId, phone, codes.hash(phone, code), expires);
          return new Deferred<>(
              new CodeSent(expires, true), () -> new CodeSent(expires, codes.send(phone, code)));
        });
  }

  private sealed interface Outcome {}

  private record Verified(Profile profile) implements Outcome {}

  private record Locked(long retryAfterSeconds) implements Outcome {}

  private record Wrong() implements Outcome {}

  private record Expired() implements Outcome {}

  /**
   * Checks the code against the caller's pending one and, if right, marks the number verified.
   *
   * @param ip the client address, for the per-address guess limit
   * @throws dev.kanakku.platform.problem.ProblemException 409 when no provider is configured, 422
   *     for a wrong code or none pending, 410 when expired, 429 while locked (even for the right
   *     code)
   * @throws PhoneTakenException when another account verified this number first
   */
  public Profile confirm(UUID userId, String code, String ip) {
    if (!codes.enabled()) {
      throw PhoneProblems.smsNotConfigured();
    }
    Outcome outcome;
    try {
      outcome = transactions.execute(status -> confirmInTransaction(userId, code, ip));
    } catch (DuplicateKeyException e) {
      throw new PhoneTakenException();
    }
    return switch (outcome) {
      case Verified v -> v.profile();
      case Locked l -> throw PhoneProblems.codeLocked(l.retryAfterSeconds());
      case Expired e -> throw PhoneProblems.codeExpired();
      case Wrong w -> throw PhoneProblems.wrongCode();
    };
  }

  private Outcome confirmInTransaction(UUID userId, String code, String ip) {
    // The user row first, then the challenge row: updatePhone and removePhone take them in that
    // order, so the other order could deadlock.
    Profile profile = users.findProfileLocked(userId).orElseThrow();
    String userKey = key(userId);
    String ipKey = "ip:" + ThrottleIp.of(ip);
    String numberKey = profile.phone() == null ? null : codes.numberKey(profile.phone());
    OptionalLong locked = limiter.check(WRONG_BUCKET, userKey, MAX_WRONG, LOCK_WINDOW);
    if (locked.isEmpty() && numberKey != null) {
      locked = limiter.check(NUMBER_WRONG_BUCKET, numberKey, MAX_WRONG, LOCK_WINDOW);
    }
    if (locked.isEmpty()) {
      locked = limiter.check(IP_WRONG_BUCKET, ipKey, MAX_WRONG_PER_IP, LOCK_WINDOW);
    }
    if (locked.isPresent()) {
      return new Locked(locked.getAsLong());
    }
    Challenge challenge = challenges.findLocked(userId).orElse(null);
    if (challenge == null
        || profile.phone() == null
        || !profile.phone().equals(challenge.phoneE164())) {
      recordWrong(userKey, numberKey, ipKey);
      return new Wrong();
    }
    if (!challenge.expiresAt().isAfter(now())) {
      return new Expired();
    }
    if (!codes.matches(challenge.codeHash(), challenge.phoneE164(), code)) {
      recordWrong(userKey, numberKey, ipKey);
      return new Wrong();
    }
    Profile verified = users.markPhoneVerified(userId, challenge.phoneE164(), now()).orElseThrow();
    challenges.delete(userId);
    limiter.reset(WRONG_BUCKET, userKey);
    limiter.reset(NUMBER_WRONG_BUCKET, numberKey);
    // Synchronous: listeners run in this transaction, so they commit or roll back with the number.
    events.publishEvent(new PhoneVerified(userId));
    return new Verified(verified);
  }

  private void recordWrong(String userKey, String numberKey, String ipKey) {
    limiter.hit(WRONG_BUCKET, userKey);
    if (numberKey != null) {
      limiter.hit(NUMBER_WRONG_BUCKET, numberKey);
    }
    limiter.hit(IP_WRONG_BUCKET, ipKey);
  }

  private static String key(UUID userId) {
    return "user:" + userId;
  }

  private OffsetDateTime now() {
    return OffsetDateTime.ofInstant(clock.instant(), ZoneOffset.UTC);
  }
}
