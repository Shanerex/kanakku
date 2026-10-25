package dev.kanakku.platform.validation;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Currency;
import org.junit.jupiter.api.Test;

class ValidCurrencyTest {

  private final ValidCurrency.Validator validator = new ValidCurrency.Validator();

  private boolean valid(String code) {
    return validator.isValid(code, null);
  }

  @Test
  void realMoneyCurrenciesAreAccepted() {
    assertThat(valid("INR")).isTrue(); // 2 minor digits
    assertThat(valid("JPY")).isTrue(); // 0 minor digits
    assertThat(valid("KWD")).isTrue(); // 3 minor digits
    assertThat(valid("USD")).isTrue();
  }

  @Test
  void codesWithoutAMinorUnitAreRefused() {
    for (String code : new String[] {"XXX", "XTS", "XAU", "XAG", "XDR", "XPT", "XPD"}) {
      assertThat(valid(code)).as(code).isFalse();
    }
  }

  @Test
  void everyAcceptedCodeHasAMinorUnitAndEveryRefusedKnownCodeDoesNot() {
    for (Currency currency : Currency.getAvailableCurrencies()) {
      assertThat(valid(currency.getCurrencyCode()))
          .as(currency.getCurrencyCode())
          .isEqualTo(currency.getDefaultFractionDigits() >= 0);
    }
  }

  @Test
  void unknownOrMalformedCodesAreRefused() {
    for (String code : new String[] {"ZZZ", "inr", "IN", "RUPEES", ""}) {
      assertThat(valid(code)).as(code).isFalse();
    }
  }
}
