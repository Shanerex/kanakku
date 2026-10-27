package dev.kanakku.identity.util;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class PhoneMaskTest {

  @Test
  void keepsTheCountryCodeAndLastTwoDigits() {
    assertThat(PhoneMask.mask("+919876543210")).isEqualTo("+91 ••••• ••10");
    assertThat(PhoneMask.mask("+16502530000")).isEqualTo("+1 ••••• ••00");
  }

  @Test
  void neverShowsTheMiddleDigits() {
    String masked = PhoneMask.mask("+919876543210");

    assertThat(masked).doesNotContain("9876", "5432");
  }

  @Test
  void anUnreadableValueIsFullyHidden() {
    assertThat(PhoneMask.mask(null)).doesNotContainPattern("\\d");
    assertThat(PhoneMask.mask("")).doesNotContainPattern("\\d");
    assertThat(PhoneMask.mask("nonsense")).doesNotContainPattern("\\d");
  }
}
