package dev.kanakku.platform.phone;

import com.google.i18n.phonenumbers.NumberParseException;
import com.google.i18n.phonenumbers.PhoneNumberUtil;
import com.google.i18n.phonenumbers.PhoneNumberUtil.PhoneNumberFormat;
import com.google.i18n.phonenumbers.Phonenumber.PhoneNumber;
import dev.kanakku.platform.problem.ProblemException;
import dev.kanakku.platform.problem.Problems;
import java.util.Optional;
import java.util.OptionalInt;
import org.springframework.http.HttpStatus;

/**
 * Turns what a person types into one canonical E.164 value and reads search queries. Numbers
 * without a country code are Indian (+91). An invalid number never appears in an error message.
 */
public final class PhoneNumbers {

  /** Region assumed when a number carries no country code. */
  public static final String DEFAULT_REGION = "IN";

  /** The fewest digits a phone search may have. */
  public static final int MIN_SEARCH_DIGITS = 4;

  private static final PhoneNumberUtil UTIL = PhoneNumberUtil.getInstance();

  private PhoneNumbers() {}

  /**
   * The E.164 form of {@code raw} ({@code +919876543210}).
   *
   * @throws ProblemException 400 {@code invalid-phone} when it is not a valid number
   */
  public static String parse(String raw) {
    return tryParse(raw)
        .orElseThrow(
            () ->
                new ProblemException(
                    Problems.of(
                        HttpStatus.BAD_REQUEST,
                        "invalid-phone",
                        "That phone number is not valid",
                        "Enter a mobile number, for example 98765 43210 or +91 98765 43210.")));
  }

  /** As {@link #parse} but empty instead of throwing. */
  public static Optional<String> tryParse(String raw) {
    if (raw == null || raw.isBlank() || raw.length() > 40) {
      return Optional.empty();
    }
    try {
      PhoneNumber number = UTIL.parse(raw.strip(), DEFAULT_REGION);
      if (!UTIL.isValidNumber(number)) {
        return Optional.empty();
      }
      return Optional.of(UTIL.format(number, PhoneNumberFormat.E164));
    } catch (NumberParseException e) {
      return Optional.empty();
    }
  }

  private static final String HIDDEN = "+•• ••••• •••••";

  /**
   * Hides most of an E.164 number before it is shown to anyone but its owner: the country code and
   * the last two digits stay, in a fixed shape that does not reveal the length ({@code
   * +919876543210} becomes {@code +91 ••••• ••10}). Anything unreadable is fully hidden.
   */
  public static String mask(String e164) {
    if (e164 == null || e164.length() < 4) {
      return HIDDEN;
    }
    OptionalInt code = callingCode(e164);
    if (code.isEmpty()) {
      return HIDDEN;
    }
    return "+" + code.getAsInt() + " ••••• ••" + e164.substring(e164.length() - 2);
  }

  /** The country calling code of an E.164 number ({@code 91} for {@code +919876543210}). */
  public static OptionalInt callingCode(String e164) {
    if (e164 == null || e164.isBlank()) {
      return OptionalInt.empty();
    }
    try {
      return OptionalInt.of(UTIL.parse(e164.strip(), null).getCountryCode());
    } catch (NumberParseException e) {
      return OptionalInt.empty();
    }
  }

  /**
   * The digits to search by when {@code raw} is a phone-like query: only digits, spaces, dashes,
   * dots, parentheses and a leading plus. A single leading 0 (the Indian trunk prefix) is dropped
   * unless that leaves nothing. Empty when the query has any other character, which makes it a name
   * or email query. The caller enforces {@link #MIN_SEARCH_DIGITS}.
   */
  public static Optional<String> searchDigits(String raw) {
    if (raw == null) {
      return Optional.empty();
    }
    String q = raw.strip();
    if (q.isEmpty() || !q.matches("\\+?[0-9 ().\\-]*")) {
      return Optional.empty();
    }
    boolean international = q.startsWith("+");
    String digits = q.replaceAll("[^0-9]", "");
    if (digits.isEmpty()) {
      return Optional.empty();
    }
    if (!international && digits.length() > 1 && digits.startsWith("0")) {
      digits = digits.substring(1);
    }
    return Optional.of(digits);
  }
}
