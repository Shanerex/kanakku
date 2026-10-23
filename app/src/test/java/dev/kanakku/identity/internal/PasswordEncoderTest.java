package dev.kanakku.identity.internal;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.password.PasswordEncoder;

class PasswordEncoderTest {

  private final PasswordEncoder encoder = new PasswordConfig().passwordEncoder();

  @Test
  void hashIsArgon2idAndDiffersFromThePassword() {
    String hash = encoder.encode("correct horse battery");

    assertThat(hash).startsWith("$argon2id$").isNotEqualTo("correct horse battery");
    assertThat(hash).doesNotContain("correct horse battery");
  }

  @Test
  void usesTheOwaspMinimumParameters() {
    String hash = encoder.encode("correct horse battery");

    assertThat(hash).contains("m=19456,t=2,p=1");
  }

  @Test
  void matchesTheRightPasswordOnly() {
    String hash = encoder.encode("correct horse battery");

    assertThat(encoder.matches("correct horse battery", hash)).isTrue();
    assertThat(encoder.matches("wrong horse battery", hash)).isFalse();
  }

  @Test
  void saltsEveryHash() {
    assertThat(encoder.encode("same password")).isNotEqualTo(encoder.encode("same password"));
  }
}
