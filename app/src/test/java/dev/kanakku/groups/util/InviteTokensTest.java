package dev.kanakku.groups.util;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class InviteTokensTest {

  static final byte[] SECRET =
      "a-test-secret-of-at-least-32-chars!".getBytes(StandardCharsets.UTF_8);

  @Test
  void aTokenIsRecomputedFromTheIdAndTheSecretAlone() {
    UUID id = UUID.randomUUID();

    String token = InviteTokens.derive(SECRET, id);

    assertThat(InviteTokens.derive(SECRET, id)).isEqualTo(token);
    assertThat(token).hasSize(43).matches("[A-Za-z0-9_-]+");
  }

  @Test
  void anotherIdOrAnotherSecretGivesAnotherToken() {
    UUID id = UUID.randomUUID();

    assertThat(InviteTokens.derive(SECRET, UUID.randomUUID()))
        .isNotEqualTo(InviteTokens.derive(SECRET, id));
    assertThat(
            InviteTokens.derive(
                "another-secret-also-32-chars-long!!".getBytes(StandardCharsets.UTF_8), id))
        .isNotEqualTo(InviteTokens.derive(SECRET, id));
  }
}
