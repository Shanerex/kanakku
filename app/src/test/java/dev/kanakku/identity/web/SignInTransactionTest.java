package dev.kanakku.identity.web;

import static org.assertj.core.api.Assertions.assertThat;

import dev.kanakku.identity.internal.InvalidCredentialsException;
import dev.kanakku.identity.internal.PasswordSignIn;
import dev.kanakku.identity.internal.SignUpService;
import dev.kanakku.support.PostgresIntegrationTest;
import dev.kanakku.support.TestData;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.security.crypto.argon2.Argon2PasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/** Argon2 must not run while a transaction holds a pooled connection and the advisory lock. */
@SpringBootTest
@Import(SignInTransactionTest.SpyConfig.class)
class SignInTransactionTest extends PostgresIntegrationTest {

  static final String PASSWORD = "correct horse battery";
  static final List<Boolean> TX_DURING_MATCHES = new CopyOnWriteArrayList<>();

  @TestConfiguration
  static class SpyConfig {
    @Bean
    @Primary
    PasswordEncoder spyingEncoder() {
      PasswordEncoder real = new Argon2PasswordEncoder(16, 32, 1, 19456, 2);
      return new PasswordEncoder() {
        @Override
        public String encode(CharSequence raw) {
          return real.encode(raw);
        }

        @Override
        public boolean matches(CharSequence raw, String encoded) {
          TX_DURING_MATCHES.add(TransactionSynchronizationManager.isActualTransactionActive());
          return real.matches(raw, encoded);
        }
      };
    }
  }

  @Autowired PasswordSignIn passwordSignIn;
  @Autowired SignUpService signUp;

  @Test
  void hashingRunsOutsideAnyTransaction() {
    String email = TestData.email();
    signUp.signUp(email, PASSWORD, "Asha");
    TX_DURING_MATCHES.clear();

    passwordSignIn.signIn(email, PASSWORD, "10.7.7.1");
    try {
      passwordSignIn.signIn(email, "wrong password", "10.7.7.2");
    } catch (InvalidCredentialsException expected) {
      // fine
    }
    try {
      passwordSignIn.signIn(TestData.email(), PASSWORD, "10.7.7.3");
    } catch (InvalidCredentialsException expected) {
      // fine
    }

    assertThat(new ArrayList<>(TX_DURING_MATCHES)).hasSize(3).doesNotContain(true);
  }
}
