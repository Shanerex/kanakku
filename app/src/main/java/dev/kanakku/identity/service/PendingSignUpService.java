package dev.kanakku.identity.service;

import dev.kanakku.identity.domain.HashedPassword;
import dev.kanakku.identity.domain.PendingSignUp;
import dev.kanakku.identity.domain.Profile;
import dev.kanakku.identity.exception.EmailTakenException;
import dev.kanakku.identity.exception.PhoneProblems;
import dev.kanakku.identity.exception.PhoneTakenException;
import dev.kanakku.identity.repository.PendingSignUpRepository;
import dev.kanakku.identity.repository.PendingSignUpRepository.Pending;
import dev.kanakku.identity.repository.UserRepository;
import dev.kanakku.identity.util.PhoneMask;
import dev.kanakku.identity.util.ThrottleIp;
import dev.kanakku.platform.problem.ProblemException;
import dev.kanakku.platform.ratelimit.RateLimiter;
import java.time.Clock;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.OptionalLong;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Sign-up when an SMS provider is set: the account row is written only once the code is confirmed,
 * so a wrong code, an abandoned code step or a number verified elsewhere leaves no user. Send
 * limits (country allow-list, per number, per client address, whole instance) live in {@link
 * SmsPolicy}; five wrong codes lock confirming that one sign-up for 15 minutes. The count is per
 * sign-up and not shared with profile verification, so someone starting a sign-up with another
 * person's number cannot lock that person out of verifying it. The provider call never runs inside
 * a transaction: the code is stored first, the text goes out in {@link Deferred#finish}, and a
 * failed text is then recorded on the pending row.
 */
@Service
public class PendingSignUpService {

  /** How long a pending sign-up lives after its latest text. */
  static final Duration LIFETIME = Duration.ofHours(1);

  /** Sign-up attempts per client address per hour; each one may be told a number is taken. */
  static final String PHONE_CHECK_BUCKET = "sign-up-phone-check-ip";

  static final int MAX_PHONE_CHECKS_PER_IP = 30;
  static final Duration PHONE_CHECK_WINDOW = Duration.ofHours(1);

  static final String WRONG_BUCKET = "sign-up-wrong-code";
  static final int MAX_WRONG = 5;
  static final Duration LOCK_WINDOW = Duration.ofMinutes(15);

  /** The outcome of a confirm: the new profile, or the refusal to answer with. */
  public record Verification(Profile profile, ProblemException refusal) {

    static Verification ok(Profile profile) {
      return new Verification(profile, null);
    }

    static Verification refused(ProblemException refusal) {
      return new Verification(null, refusal);
    }
  }

  private final PendingSignUpRepository pending;
  private final UserRepository users;
  private final SignUpService signUp;
  private final PhoneCodeService codes;
  private final SmsPolicy policy;
  private final RateLimiter limiter;
  private final TransactionTemplate transactions;
  private final Clock clock;

  PendingSignUpService(
      PendingSignUpRepository pending,
      UserRepository users,
      SignUpService signUp,
      PhoneCodeService codes,
      SmsPolicy policy,
      RateLimiter limiter,
      TransactionTemplate transactions,
      Clock clock) {
    this.pending = pending;
    this.users = users;
    this.signUp = signUp;
    this.codes = codes;
    this.policy = policy;
    this.limiter = limiter;
    this.transactions = transactions;
    this.clock = clock;
  }

  /**
   * Counts one sign-up attempt from {@code ip}, before {@link #start} may answer "number taken".
   * Run it outside the sign-up transaction: the count must survive the 409 that rolls that
   * transaction back.
   *
   * @throws ProblemException 429 past the per-address limit
   */
  public void requirePhoneCheckAllowed(String ip) {
    OptionalLong wait =
        limiter.tryHit(
            PHONE_CHECK_BUCKET,
            "ip:" + ThrottleIp.of(ip),
            MAX_PHONE_CHECKS_PER_IP,
            PHONE_CHECK_WINDOW);
    if (wait.isPresent()) {
      throw PhoneProblems.tooManyCodes(wait.getAsLong());
    }
  }

  /**
   * Starts a sign-up and stores its code. The password was hashed before this call. The text goes
   * out when {@link Deferred#finish} runs, after the caller's transaction has ended.
   *
   * @throws EmailTakenException when the email has an account
   * @throws PhoneTakenException when another account verified the number
   * @throws ProblemException 422 for a country that is not texted, 429 past a send limit
   */
  public Deferred<PendingSignUp> start(
      String email, HashedPassword hash, String displayName, String phoneE164, String ip) {
    return transactions.execute(
        status -> {
          if (users.emailExists(email)) {
            throw new EmailTakenException();
          }
          if (users.isPhoneVerified(phoneE164)) {
            throw new PhoneTakenException();
          }
          policy.requireSendAllowed(phoneE164, ip, SmsPolicy.Origin.SIGN_UP);
          UUID id = UUID.randomUUID();
          String code = codes.newCode();
          OffsetDateTime codeExpires = now().plus(PhoneCodeService.TTL);
          pending.insert(
              new Pending(
                  id,
                  email.strip(),
                  displayName.strip(),
                  hash.value(),
                  phoneE164,
                  codes.hash(phoneE164, code),
                  codeExpires,
                  false,
                  now().plus(LIFETIME)));
          return deliver(id, phoneE164, code, codeExpires);
        });
  }

  /**
   * Stores a new code and replaces the old one; the text goes out in {@link Deferred#finish}.
   *
   * @throws ProblemException 410 when the sign-up is gone, 422 for a country that is not texted,
   *     429 past a send limit
   */
  public Deferred<PendingSignUp> resend(UUID id, String ip) {
    return transactions.execute(
        status -> {
          Pending p = live(id);
          policy.requireSendAllowed(p.phoneE164(), ip, SmsPolicy.Origin.SIGN_UP);
          String code = codes.newCode();
          OffsetDateTime codeExpires = now().plus(PhoneCodeService.TTL);
          pending.updateCode(
              id, codes.hash(p.phoneE164(), code), codeExpires, false, now().plus(LIFETIME));
          return deliver(id, p.phoneE164(), code, codeExpires);
        });
  }

  /**
   * The text step, run with no transaction open. A refusal by the provider is recorded on the
   * pending row (so the sign-up may finish unverified) and reported as {@code smsSent} false; the
   * send already counted against the limits either way.
   */
  private Deferred<PendingSignUp> deliver(
      UUID id, String phoneE164, String code, OffsetDateTime codeExpires) {
    String masked = PhoneMask.mask(phoneE164);
    return new Deferred<>(
        new PendingSignUp(id, masked, codeExpires, true),
        () -> {
          if (codes.send(phoneE164, code)) {
            return new PendingSignUp(id, masked, codeExpires, true);
          }
          transactions.executeWithoutResult(status -> pending.markSmsFailed(id));
          return new PendingSignUp(id, masked, codeExpires, false);
        });
  }

  /**
   * Checks the code and, if right, creates the verified account and drops the pending row in one
   * transaction. A refusal comes back as a value, not an exception, so the wrong-code count it
   * recorded is committed with the transaction.
   *
   * @throws ProblemException 410 when the sign-up is gone
   * @throws EmailTakenException when the email got an account meanwhile
   * @throws PhoneTakenException when another account verified the number first
   */
  public Verification verify(UUID id, String code) {
    return transactions.execute(
        status -> {
          Pending p = live(id);
          String key = "sign-up:" + id;
          OptionalLong locked = limiter.check(WRONG_BUCKET, key, MAX_WRONG, LOCK_WINDOW);
          if (locked.isPresent()) {
            return Verification.refused(PhoneProblems.codeLocked(locked.getAsLong()));
          }
          if (!p.codeExpiresAt().isAfter(now())) {
            return Verification.refused(PhoneProblems.codeExpired());
          }
          if (!codes.matches(p.codeHash(), p.phoneE164(), code)) {
            limiter.hit(WRONG_BUCKET, key);
            return Verification.refused(PhoneProblems.wrongCode());
          }
          if (users.emailExists(p.email())) {
            throw new EmailTakenException();
          }
          if (users.isPhoneVerified(p.phoneE164())) {
            throw new PhoneTakenException();
          }
          Profile profile =
              signUp.createAccount(
                  p.email(),
                  new HashedPassword(p.passwordHash()),
                  p.displayName(),
                  p.phoneE164(),
                  now());
          pending.delete(id);
          limiter.reset(WRONG_BUCKET, key);
          return Verification.ok(profile);
        });
  }

  /**
   * Creates the account with the number left unverified. Allowed at any point of the code step,
   * whether or not the text went out, since a text can be accepted by the provider and still never
   * arrive.
   *
   * @throws ProblemException 410 when the sign-up is gone
   */
  public Profile finishUnverified(UUID id) {
    return transactions.execute(
        status -> {
          Pending p = live(id);
          if (users.emailExists(p.email())) {
            throw new EmailTakenException();
          }
          Profile profile =
              signUp.createAccount(
                  p.email(),
                  new HashedPassword(p.passwordHash()),
                  p.displayName(),
                  p.phoneE164(),
                  null);
          pending.delete(id);
          return profile;
        });
  }

  /** Deletes expired pending sign-ups and returns how many went; housekeeping for the job. */
  public int purgeExpired() {
    return pending.deleteExpired(now());
  }

  private Pending live(UUID id) {
    Pending p = pending.findLocked(id).orElseThrow(PhoneProblems::signUpExpired);
    if (!p.expiresAt().isAfter(now())) {
      throw PhoneProblems.signUpExpired();
    }
    return p;
  }

  private OffsetDateTime now() {
    return OffsetDateTime.ofInstant(clock.instant(), ZoneOffset.UTC);
  }
}
