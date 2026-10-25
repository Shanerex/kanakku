package dev.kanakku.identity.util;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class EmailMaskTest {

  @Test
  void keepsTheFirstLetterAndTheDomain() {
    assertThat(EmailMask.mask("asha.k@example.com")).isEqualTo("a***@example.com");
  }

  @Test
  void aOneLetterLocalPartIsFullyHidden() {
    assertThat(EmailMask.mask("a@example.com")).isEqualTo("*@example.com");
  }

  @Test
  void neverRevealsMoreThanOneLetterOfTheLocalPart() {
    String masked = EmailMask.mask("bala.subramanian+trip@mail.example.org");
    assertThat(masked).isEqualTo("b***@mail.example.org").doesNotContain("subramanian");
  }

  @Test
  void anythingWithoutAnAtSignIsFullyHidden() {
    assertThat(EmailMask.mask("not-an-email")).isEqualTo("***");
    assertThat(EmailMask.mask("")).isEqualTo("***");
    assertThat(EmailMask.mask(null)).isEqualTo("***");
  }
}
