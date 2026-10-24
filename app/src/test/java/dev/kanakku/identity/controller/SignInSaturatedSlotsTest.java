package dev.kanakku.identity.controller;

import static dev.kanakku.jooq.Tables.SIGN_IN_FAILURES;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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
import tools.jackson.databind.json.JsonMapper;

/**
 * With every unknown-email hash slot taken, an unknown email waits for a slot and then gets the
 * very same 401 a known email gets, so a flood cannot be used to tell the two apart by status.
 */
@SpringBootTest(
    properties = {
      "kanakku.auth.max-concurrent-unknown-hashes=0",
      "kanakku.auth.unknown-hash-wait=50ms"
    })
@AutoConfigureMockMvc
class SignInSaturatedSlotsTest extends PostgresIntegrationTest {

  static final String PASSWORD = "correct horse battery";

  @Autowired MockMvc mvc;
  @Autowired SignUpService signUp;
  @Autowired DSLContext dsl;
  @Autowired JsonMapper json;

  String signInBody(String email, String password) throws Exception {
    return mvc.perform(
            post("/api/v1/auth/sign-in")
                .with(Csrf.token(mvc))
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("email", email, "password", password))))
        .andExpect(status().isUnauthorized())
        .andReturn()
        .getResponse()
        .getContentAsString()
        .replaceAll("\"instance\":\"[^\"]*\"", "");
  }

  @Test
  void unknownAndKnownEmailsGetTheSameBodyWhenAllSlotsAreTaken() throws Exception {
    String known = TestData.email();
    signUp.signUp(known, PASSWORD, "Asha");

    String unknownBody = signInBody(TestData.email(), PASSWORD);
    String wrongPasswordBody = signInBody(known, "wrong password");

    assertThat(unknownBody).isEqualTo(wrongPasswordBody).contains("invalid-credentials");
  }

  @Test
  void theUnknownEmailTryStillCountsTowardsTheThrottle() throws Exception {
    String email = TestData.email();

    signInBody(email, PASSWORD);

    assertThat(dsl.fetchCount(SIGN_IN_FAILURES, SIGN_IN_FAILURES.EMAIL_KEY.eq(email))).isOne();
  }

  @Test
  void aKnownEmailStillSignsInWhenAllSlotsAreTaken() throws Exception {
    String email = TestData.email();
    signUp.signUp(email, PASSWORD, "Asha");

    mvc.perform(
            post("/api/v1/auth/sign-in")
                .with(Csrf.token(mvc))
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("email", email, "password", PASSWORD))))
        .andExpect(status().isOk());
  }
}
