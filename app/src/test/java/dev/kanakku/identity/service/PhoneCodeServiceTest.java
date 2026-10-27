package dev.kanakku.identity.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.kanakku.identity.repository.NoSmsGateway;
import dev.kanakku.support.FakeSmsGateway;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.constraints.IntRange;
import org.junit.jupiter.api.Test;

class PhoneCodeServiceTest {

  static final String SECRET = "unit-test-sms-code-secret-0123456789ab";
  static final String NUMBER = "+919876543210";

  final FakeSmsGateway gateway = new FakeSmsGateway();
  final PhoneCodeService codes = new PhoneCodeService(gateway, SECRET);

  @Test
  void aCodeIsSixDigits() {
    for (int i = 0; i < 500; i++) {
      assertThat(codes.newCode()).matches("\\d{6}");
    }
  }

  @Test
  void theRightCodeMatchesAndAWrongOneDoesNot() {
    byte[] stored = codes.hash(NUMBER, "123456");

    assertThat(codes.matches(stored, NUMBER, "123456")).isTrue();
    assertThat(codes.matches(stored, NUMBER, "123457")).isFalse();
    assertThat(codes.matches(stored, NUMBER, null)).isFalse();
    assertThat(codes.matches(null, NUMBER, "123456")).isFalse();
  }

  @Test
  void aCodeIsBoundToItsNumber() {
    byte[] stored = codes.hash(NUMBER, "123456");

    assertThat(codes.matches(stored, "+919876543211", "123456")).isFalse();
  }

  @Test
  void theHashDependsOnTheServerSecret() {
    var other = new PhoneCodeService(gateway, "another-sms-code-secret-0123456789abcd");

    assertThat(other.hash(NUMBER, "123456")).isNotEqualTo(codes.hash(NUMBER, "123456"));
  }

  @Test
  void theStoredValueIsNotTheCode() {
    assertThat(
            new String(codes.hash(NUMBER, "123456"), java.nio.charset.StandardCharsets.ISO_8859_1))
        .doesNotContain("123456");
  }

  @Property
  void everyGeneratedCodeVerifiesAgainstItsOwnHash(@ForAll @IntRange(max = 50) int ignored) {
    String code = codes.newCode();

    assertThat(codes.matches(codes.hash(NUMBER, code), NUMBER, code)).isTrue();
  }

  @Test
  void sendPutsTheCodeInTheMessage() {
    assertThat(codes.send(NUMBER, "654321")).isTrue();

    assertThat(gateway.lastCode(NUMBER)).contains("654321");
  }

  @Test
  void aSecretIsRequiredOnlyWhenAProviderIsConfigured() {
    assertThatThrownBy(() -> new PhoneCodeService(gateway, "too-short"))
        .isInstanceOf(IllegalStateException.class);
    assertThatThrownBy(() -> new PhoneCodeService(gateway, ""))
        .isInstanceOf(IllegalStateException.class);

    var without = new PhoneCodeService(new NoSmsGateway(), "");
    assertThat(without.enabled()).isFalse();
  }
}
