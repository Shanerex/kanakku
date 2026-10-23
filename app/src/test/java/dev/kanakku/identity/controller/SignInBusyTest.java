package dev.kanakku.identity.controller;

import static dev.kanakku.jooq.Tables.SIGN_IN_FAILURES;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import dev.kanakku.identity.service.SignUpService;
import dev.kanakku.support.Csrf;
import dev.kanakku.support.PostgresIntegrationTest;
import dev.kanakku.support.TestData;
import java.util.Map;
import org.jooq.DSLContext;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import tools.jackson.databind.json.JsonMapper;

/** Unknown-email sign-ins each cost a full Argon2 hash; a bounded pool refuses the overflow. */
@SpringBootTest(properties = "kanakku.auth.max-concurrent-unknown-hashes=0")
@AutoConfigureMockMvc
class SignInBusyTest extends PostgresIntegrationTest {

  static final String PASSWORD = "correct horse battery";

  @Autowired MockMvc mvc;
  @Autowired SignUpService signUp;
  @Autowired DSLContext dsl;
  @Autowired JsonMapper json;

  ResultActions signIn(String email, String password) throws Exception {
    return mvc.perform(
        post("/api/v1/auth/sign-in")
            .with(Csrf.token(mvc))
            .contentType(MediaType.APPLICATION_JSON)
            .content(json.writeValueAsString(Map.of("email", email, "password", password))));
  }

  @Test
  void whenNoUnknownEmailHashSlotIsFreeTheAnswerIs503WithRetryAfterAndNothingIsRecorded()
      throws Exception {
    String email = TestData.email();

    signIn(email, PASSWORD)
        .andExpect(status().isServiceUnavailable())
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
        .andExpect(header().string("Retry-After", "1"))
        .andExpect(jsonPath("$.type").value("urn:kanakku:problem:sign-in-busy"));

    assertThat(dsl.fetchCount(SIGN_IN_FAILURES, SIGN_IN_FAILURES.EMAIL_KEY.eq(email))).isZero();
  }

  @Test
  void aKnownEmailIsNotAffected() throws Exception {
    String email = TestData.email();
    signUp.signUp(email, PASSWORD, "Asha");

    signIn(email, "wrong password").andExpect(status().isUnauthorized());
    signIn(email, PASSWORD).andExpect(status().isOk());
  }
}
