package dev.kanakku.identity.exception;

import dev.kanakku.platform.problem.ProblemException;
import dev.kanakku.platform.problem.Problems;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;

/** Problem details for the phone verification flows; each is thrown as a ProblemException. */
public final class PhoneProblems {

  private PhoneProblems() {}

  /** 410 for a sign-up that never existed, expired or was already completed. */
  public static ProblemException signUpExpired() {
    return new ProblemException(
        Problems.of(
            HttpStatus.GONE,
            "sign-up-expired",
            "This sign-up has expired",
            "Start the sign-up again."));
  }

  public static ProblemException smsNotConfigured() {
    return new ProblemException(
        Problems.of(
            HttpStatus.CONFLICT,
            "sms-not-configured",
            "Phone verification is not available",
            "This server cannot send text messages, so numbers stay unverified."));
  }

  public static ProblemException phoneMissing() {
    return new ProblemException(
        Problems.of(
            HttpStatus.CONFLICT,
            "phone-missing",
            "No number to verify",
            "Add a mobile number first."));
  }

  public static ProblemException alreadyVerified() {
    return new ProblemException(
        Problems.of(
            HttpStatus.CONFLICT,
            "phone-already-verified",
            "Number already verified",
            "This number is already verified."));
  }

  /** 422 for a wrong code, and also for "no code is pending"; the two look the same. */
  public static ProblemException wrongCode() {
    return new ProblemException(
        Problems.of(
            HttpStatus.UNPROCESSABLE_ENTITY,
            "wrong-code",
            "That code is not right",
            "Check the code in your text message and try again."));
  }

  public static ProblemException codeExpired() {
    return new ProblemException(
        Problems.of(
            HttpStatus.GONE,
            "code-expired",
            "That code has expired",
            "Codes work for 10 minutes. Ask for a new one."));
  }

  /** 429 after five wrong codes. */
  public static ProblemException codeLocked(long retryAfterSeconds) {
    return tooMany(
        "code-locked",
        "Too many wrong codes",
        "Too many wrong codes. Try again later.",
        retryAfterSeconds);
  }

  /** 429 after the hourly send limit. */
  public static ProblemException tooManyCodes(long retryAfterSeconds) {
    return tooMany(
        "too-many-codes",
        "Too many codes requested",
        "You asked for too many codes. Try again later.",
        retryAfterSeconds);
  }

  /** 422 for a valid number whose country calling code this server does not text. */
  public static ProblemException countryNotSupported(int callingCode) {
    return new ProblemException(
        Problems.of(
            HttpStatus.UNPROCESSABLE_ENTITY,
            "phone-country-unsupported",
            "We cannot text that number",
            callingCode > 0
                ? "This server does not send codes to numbers with the country code +"
                    + callingCode
                    + "."
                : "This server does not send codes to numbers from that country."));
  }

  /** 429 when the instance-wide hourly ceiling on texts is used up. */
  public static ProblemException smsBusy(long retryAfterSeconds) {
    return tooMany(
        "sms-busy",
        "Text messages are busy",
        "This server has sent as many codes as it allows this hour. Try again later.",
        retryAfterSeconds);
  }

  private static ProblemException tooMany(
      String slug, String title, String detail, long retryAfterSeconds) {
    ProblemDetail problem = Problems.of(HttpStatus.TOO_MANY_REQUESTS, slug, title, detail);
    problem.setProperty("retryAfterSeconds", retryAfterSeconds);
    HttpHeaders headers = new HttpHeaders();
    headers.set(HttpHeaders.RETRY_AFTER, Long.toString(retryAfterSeconds));
    return new ProblemException(problem, headers);
  }
}
