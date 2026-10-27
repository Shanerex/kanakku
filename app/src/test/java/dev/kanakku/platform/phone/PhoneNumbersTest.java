package dev.kanakku.platform.phone;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.kanakku.platform.problem.ProblemException;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class PhoneNumbersTest {

  @ParameterizedTest
  @ValueSource(strings = {"98765 43210", "+91 98765 43210", "09876543210", " +91-98765-43210 "})
  void theThreeWaysToWriteTheSameIndianNumberStoreIdentically(String raw) {
    assertThat(PhoneNumbers.parse(raw)).isEqualTo("+919876543210");
  }

  @Test
  void aForeignNumberKeepsItsOwnCountryCode() {
    assertThat(PhoneNumbers.parse("+1 650 253 0000")).isEqualTo("+16502530000");
  }

  @ParameterizedTest
  @ValueSource(strings = {"", "   ", "abc", "12345", "+91 12345", "98765 4321", "98765 432109"})
  void anInvalidNumberIsRejectedWithAProblem(String raw) {
    assertThatThrownBy(() -> PhoneNumbers.parse(raw))
        .isInstanceOfSatisfying(
            ProblemException.class,
            e -> {
              assertThat(e.problem().getStatus()).isEqualTo(400);
              assertThat(e.problem().getType().toString()).endsWith("invalid-phone");
            });
  }

  @Test
  void nullIsRejectedToo() {
    assertThatThrownBy(() -> PhoneNumbers.parse(null)).isInstanceOf(ProblemException.class);
    assertThat(PhoneNumbers.tryParse(null)).isEmpty();
  }

  @Test
  void tryParseGivesEmptyInsteadOfThrowing() {
    assertThat(PhoneNumbers.tryParse("nope")).isEmpty();
    assertThat(PhoneNumbers.tryParse("98765 43210")).contains("+919876543210");
  }

  @Test
  void searchDigitsAcceptsOnlyQueriesMadeOfPhoneCharacters() {
    assertThat(PhoneNumbers.searchDigits("98765")).contains("98765");
    assertThat(PhoneNumbers.searchDigits(" +91 (987) 65-4 ")).contains("91987654");
    assertThat(PhoneNumbers.searchDigits("asha")).isEqualTo(Optional.empty());
    assertThat(PhoneNumbers.searchDigits("asha 98765")).isEmpty();
    assertThat(PhoneNumbers.searchDigits("")).isEmpty();
    assertThat(PhoneNumbers.searchDigits(null)).isEmpty();
  }

  @Test
  void searchDigitsDropsASingleLeadingNationalZero() {
    assertThat(PhoneNumbers.searchDigits("09876")).contains("9876");
    assertThat(PhoneNumbers.searchDigits("0")).contains("0");
  }

  @Test
  void searchDigitsKeepsShortDigitStringsSoTheCallerCanRejectThem() {
    assertThat(PhoneNumbers.searchDigits("123")).contains("123");
    assertThat(PhoneNumbers.MIN_SEARCH_DIGITS).isEqualTo(4);
  }
}
