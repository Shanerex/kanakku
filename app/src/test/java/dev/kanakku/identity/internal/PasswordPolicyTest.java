package dev.kanakku.identity.internal;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class PasswordPolicyTest {

  @Test
  void sevenCharactersIsTooShort() {
    assertThat(PasswordPolicy.isValid("a".repeat(7))).isFalse();
  }

  @Test
  void eightCharactersIsAccepted() {
    assertThat(PasswordPolicy.isValid("a".repeat(8))).isTrue();
  }

  @Test
  void oneHundredTwentyEightCharactersIsAccepted() {
    assertThat(PasswordPolicy.isValid("a".repeat(128))).isTrue();
  }

  @Test
  void oneHundredTwentyNineCharactersIsTooLong() {
    assertThat(PasswordPolicy.isValid("a".repeat(129))).isFalse();
  }

  @Test
  void lengthIsCountedInCodePointsNotUtf16Units() {
    String emoji = "😀"; // one code point, two UTF-16 units
    assertThat(PasswordPolicy.isValid(emoji.repeat(8))).isTrue();
    assertThat(PasswordPolicy.isValid(emoji.repeat(7))).isFalse();
    assertThat(PasswordPolicy.isValid(emoji.repeat(128))).isTrue();
    assertThat(PasswordPolicy.isValid(emoji.repeat(129))).isFalse();
  }

  @Test
  void hasNoCompositionRules() {
    assertThat(PasswordPolicy.isValid("        ")).isTrue();
    assertThat(PasswordPolicy.isValid("aaaaaaaa")).isTrue();
  }

  @Test
  void nullIsInvalid() {
    assertThat(PasswordPolicy.isValid(null)).isFalse();
  }
}
