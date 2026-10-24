package dev.kanakku.identity.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import dev.kanakku.identity.exception.InvalidCredentialsException;
import dev.kanakku.identity.service.PasswordSignIn;
import dev.kanakku.support.Csrf;
import dev.kanakku.support.PostgresIntegrationTest;
import dev.kanakku.support.TestData;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionException;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.json.JsonMapper;

/**
 * A commit that fails after the unknown-email step must not leave a hash slot taken: with the
 * default of four slots, four failed commits would otherwise lock every unknown email out.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(SignInPermitLeakTest.FailingCommitConfig.class)
class SignInPermitLeakTest extends PostgresIntegrationTest {

  static final AtomicInteger COMMITS_TO_FAIL = new AtomicInteger();

  @TestConfiguration
  static class FailingCommitConfig {
    @Bean
    @Primary
    TransactionTemplate failingCommitTemplate(PlatformTransactionManager real) {
      return new TransactionTemplate(
          new PlatformTransactionManager() {
            @Override
            public TransactionStatus getTransaction(TransactionDefinition definition)
                throws TransactionException {
              return real.getTransaction(definition);
            }

            @Override
            public void commit(TransactionStatus status) throws TransactionException {
              if (COMMITS_TO_FAIL.getAndUpdate(n -> Math.max(0, n - 1)) > 0) {
                real.rollback(status);
                throw new IllegalStateException("commit failed");
              }
              real.commit(status);
            }

            @Override
            public void rollback(TransactionStatus status) throws TransactionException {
              real.rollback(status);
            }
          });
    }
  }

  @Autowired PasswordSignIn passwordSignIn;
  @Autowired MockMvc mvc;
  @Autowired JsonMapper json;

  @Test
  void failedCommitsDoNotUseUpUnknownEmailSlots() throws Exception {
    COMMITS_TO_FAIL.set(4);
    for (int i = 0; i < 4; i++) {
      assertThatThrownBy(() -> passwordSignIn.signIn(TestData.email(), "any password", "10.8.8.1"))
          .isInstanceOf(IllegalStateException.class);
    }
    assertThat(COMMITS_TO_FAIL.get()).isZero();

    mvc.perform(
            post("/api/v1/auth/sign-in")
                .with(Csrf.token(mvc))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    json.writeValueAsString(
                        Map.of("email", TestData.email(), "password", "any password"))))
        .andExpect(status().isUnauthorized());
    assertThatThrownBy(() -> passwordSignIn.signIn(TestData.email(), "any password", "10.8.8.2"))
        .isInstanceOf(InvalidCredentialsException.class);
  }
}
