package dev.kanakku.identity.service;

import dev.kanakku.identity.exception.PhoneProblems;
import dev.kanakku.identity.util.ThrottleIp;
import dev.kanakku.platform.phone.PhoneNumbers;
import dev.kanakku.platform.ratelimit.RateLimiter;
import java.time.Duration;
import java.util.LinkedHashSet;
import java.util.OptionalLong;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * Who may be texted, and how often, against SMS pumping and number harassment. Every send, from
 * sign-up or from a profile, passes {@link #requireSendAllowed}: the number's country calling code
 * must be on the allow-list ({@code kanakku.sms.allowed-calling-codes}, default 91 only), and the
 * number (four an hour, the first plus three resends), the client address (ten an hour, an IPv6 /64
 * counting as one) and the whole instance ({@code kanakku.sms.max-sends-per-hour}) must be under
 * their caps. Sign-up sends may use only the larger half of the instance ceiling (rounded up), so a
 * crowd of addresses starting sign-ups cannot leave signed-in users unable to verify their number;
 * the other half is kept for profile sends. Locks are taken in the fixed order number, address,
 * sign-up share, instance, so two requests can never wait on each other. A ceiling that refuses a
 * send is logged at WARN, with no number or address, so an operator can see it.
 */
@Service
public class SmsPolicy {

  /** Where a send comes from; sign-up sends have a smaller share of the instance ceiling. */
  enum Origin {
    SIGN_UP,
    PROFILE
  }

  private static final Logger log = LoggerFactory.getLogger(SmsPolicy.class);

  static final String NUMBER_SEND_BUCKET = "sms-number-send";
  static final String IP_SEND_BUCKET = "sms-ip-send";
  static final String GLOBAL_BUCKET = "sms-global";
  static final String SIGN_UP_BUCKET = "sms-global-sign-up";
  static final String GLOBAL_KEY = "instance";
  static final int MAX_SENDS_PER_NUMBER = 4;
  static final int MAX_SENDS_PER_IP = 10;
  static final Duration SEND_WINDOW = Duration.ofHours(1);

  private final PhoneCodeService codes;
  private final RateLimiter limiter;
  private final Set<Integer> allowedCallingCodes;
  private final int maxSendsPerHour;
  private final int maxSignUpSendsPerHour;

  SmsPolicy(
      PhoneCodeService codes,
      RateLimiter limiter,
      @Value("${kanakku.sms.allowed-calling-codes:91}") String allowedCallingCodes,
      @Value("${kanakku.sms.max-sends-per-hour:200}") int maxSendsPerHour) {
    this.codes = codes;
    this.limiter = limiter;
    this.allowedCallingCodes = parseCodes(allowedCallingCodes);
    if (maxSendsPerHour < 1) {
      throw new IllegalStateException(
          "kanakku.sms.max-sends-per-hour (KANAKKU_SMS_MAX_SENDS_PER_HOUR) must be at least 1");
    }
    this.maxSendsPerHour = maxSendsPerHour;
    this.maxSignUpSendsPerHour = maxSendsPerHour - maxSendsPerHour / 2;
  }

  private static Set<Integer> parseCodes(String raw) {
    Set<Integer> codes = new LinkedHashSet<>();
    for (String part : (raw == null ? "" : raw).split(",")) {
      String code = part.strip();
      if (code.startsWith("+")) {
        code = code.substring(1);
      }
      if (code.isEmpty()) {
        continue;
      }
      try {
        codes.add(Integer.parseInt(code));
      } catch (NumberFormatException e) {
        throw new IllegalStateException(
            "kanakku.sms.allowed-calling-codes (KANAKKU_SMS_ALLOWED_CALLING_CODES) must be a comma"
                + " separated list of country calling codes such as 91,1,44");
      }
    }
    if (codes.isEmpty()) {
      throw new IllegalStateException(
          "kanakku.sms.allowed-calling-codes (KANAKKU_SMS_ALLOWED_CALLING_CODES) must list at least"
              + " one country calling code");
    }
    return Set.copyOf(codes);
  }

  /**
   * Counts one send to {@code e164} from {@code ip} against every cap, or refuses. Run it in the
   * transaction that stores the code, so a refused send leaves no trace.
   *
   * @param origin sign-up sends are also held to their share of the instance ceiling
   * @throws dev.kanakku.platform.problem.ProblemException 422 for a country that is not on the
   *     allow-list, 429 past a cap
   */
  void requireSendAllowed(String e164, String ip, Origin origin) {
    int callingCode = PhoneNumbers.callingCode(e164).orElse(-1);
    if (!allowedCallingCodes.contains(callingCode)) {
      throw PhoneProblems.countryNotSupported(callingCode);
    }
    refuseIfWaiting(
        limiter.tryHit(
            NUMBER_SEND_BUCKET, codes.numberKey(e164), MAX_SENDS_PER_NUMBER, SEND_WINDOW));
    refuseIfWaiting(
        limiter.tryHit(IP_SEND_BUCKET, "ip:" + ThrottleIp.of(ip), MAX_SENDS_PER_IP, SEND_WINDOW));
    if (origin == Origin.SIGN_UP) {
      refuseIfBusy(
          limiter.tryHit(SIGN_UP_BUCKET, GLOBAL_KEY, maxSignUpSendsPerHour, SEND_WINDOW),
          "sign-up share of the instance ceiling");
    }
    refuseIfBusy(
        limiter.tryHit(GLOBAL_BUCKET, GLOBAL_KEY, maxSendsPerHour, SEND_WINDOW),
        "instance ceiling");
  }

  private static void refuseIfBusy(OptionalLong wait, String ceiling) {
    if (wait.isPresent()) {
      log.warn(
          "SMS {} reached (kanakku.sms.max-sends-per-hour); sends refused for {} more seconds",
          ceiling,
          wait.getAsLong());
      throw PhoneProblems.smsBusy(wait.getAsLong());
    }
  }

  private static void refuseIfWaiting(OptionalLong wait) {
    if (wait.isPresent()) {
      throw PhoneProblems.tooManyCodes(wait.getAsLong());
    }
  }
}
