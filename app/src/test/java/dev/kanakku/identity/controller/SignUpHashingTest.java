package dev.kanakku.identity.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import dev.kanakku.support.Csrf;
import dev.kanakku.support.PostgresIntegrationTest;
import dev.kanakku.support.TestData;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.argon2.Argon2PasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import tools.jackson.databind.json.JsonMapper;

/** Argon2 must never run while a transaction holds a pooled connection, sign-up included. */
@SpringBootTest
@AutoConfigureMockMvc
@Import(SignUpHashingTest.SpyConfig.class)
class SignUpHashingTest extends PostgresIntegrationTest {

  static final String PASSWORD = "correct horse battery";
  static final List<Boolean> TX_DURING_HASH = new CopyOnWriteArrayList<>();

  @TestConfiguration
  static class SpyConfig {
    @Bean
    @Primary
    PasswordEncoder spyingEncoder() {
      PasswordEncoder real = new Argon2PasswordEncoder(16, 32, 1, 19456, 2);
      return new PasswordEncoder() {
        @Override
        public String encode(CharSequence raw) {
          TX_DURING_HASH.add(TransactionSynchronizationManager.isActualTransactionActive());
          return real.encode(raw);
        }

        @Override
        public boolean matches(CharSequence raw, String encoded) {
          TX_DURING_HASH.add(TransactionSynchronizationManager.isActualTransactionActive());
          return real.matches(raw, encoded);
        }
      };
    }
  }

  @Autowired MockMvc mvc;
  @Autowired JsonMapper json;

  ResultActions signUp(String key, String email, String password) throws Exception {
    return mvc.perform(
        post("/api/v1/auth/sign-up")
            .with(Csrf.token(mvc))
            .header("Idempotency-Key", key)
            .contentType(MediaType.APPLICATION_JSON)
            .content(
                json.writeValueAsString(
                    Map.of("email", email, "password", password, "displayName", "Asha"))));
  }

  @Test
  void signUpAndItsReplayHashOutsideAnyTransaction() throws Exception {
    String email = TestData.email();
    String key = UUID.randomUUID().toString();
    TX_DURING_HASH.clear();

    signUp(key, email, PASSWORD).andExpect(status().isCreated());
    signUp(key, email, PASSWORD).andExpect(status().isCreated());

    assertThat(TX_DURING_HASH).isNotEmpty().doesNotContain(true);
  }
}
