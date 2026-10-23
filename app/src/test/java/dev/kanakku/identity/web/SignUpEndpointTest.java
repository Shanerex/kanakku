package dev.kanakku.identity.web;

import static dev.kanakku.jooq.Tables.USERS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import dev.kanakku.support.Csrf;
import dev.kanakku.support.PostgresIntegrationTest;
import dev.kanakku.support.TestData;
import java.util.Map;
import java.util.UUID;
import org.jooq.DSLContext;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import tools.jackson.databind.json.JsonMapper;

@SpringBootTest
@AutoConfigureMockMvc
class SignUpEndpointTest extends PostgresIntegrationTest {

  static final String PASSWORD = "correct horse battery";

  @Autowired MockMvc mvc;
  @Autowired DSLContext dsl;
  @Autowired JsonMapper json;

  ResultActions signUp(String key, String email, String password, String name) throws Exception {
    var request =
        post("/api/v1/auth/sign-up")
            .with(Csrf.token(mvc))
            .contentType(MediaType.APPLICATION_JSON)
            .content(
                json.writeValueAsString(
                    Map.of("email", email, "password", password, "displayName", name)));
    if (key != null) {
      request.header("Idempotency-Key", key);
    }
    return mvc.perform(request);
  }

  String key() {
    return UUID.randomUUID().toString();
  }

  long users(String email) {
    return dsl.fetchCount(USERS, USERS.EMAIL.equalIgnoreCase(email));
  }

  @Test
  void createsAnAccountAndStartsNoSession() throws Exception {
    String email = TestData.email();

    signUp(key(), email, PASSWORD, "Asha")
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.email").value(email))
        .andExpect(jsonPath("$.emailVerified").value(false))
        .andExpect(jsonPath("$.displayName").value("Asha"))
        .andExpect(jsonPath("$.defaultCurrency").value("INR"))
        .andExpect(jsonPath("$.version").value(0))
        .andExpect(jsonPath("$.id").isNotEmpty())
        .andExpect(content().string(not(containsString("password"))))
        .andExpect(content().string(not(containsString("argon2"))))
        .andExpect(header().doesNotExist("Set-Cookie"));
    assertThat(users(email)).isEqualTo(1);
  }

  @Test
  void aMissingIdempotencyKeyIs400() throws Exception {
    String email = TestData.email();

    signUp(null, email, PASSWORD, "Asha")
        .andExpect(status().isBadRequest())
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
        .andExpect(jsonPath("$.type").value("urn:kanakku:problem:idempotency-key-required"));
    assertThat(users(email)).isZero();
  }

  @Test
  void passwordsOfSevenAnd129CharactersAreRefusedWithNoAccount() throws Exception {
    for (int length : new int[] {7, 129}) {
      String email = TestData.email();
      String password = "p".repeat(length);

      signUp(key(), email, password, "Asha")
          .andExpect(status().isBadRequest())
          .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
          .andExpect(jsonPath("$.errors[0].field").value("password"))
          .andExpect(content().string(not(containsString(password))));
      assertThat(users(email)).isZero();
    }
  }

  @Test
  void passwordsOfEightAnd128CharactersAreAccepted() throws Exception {
    for (int length : new int[] {8, 128}) {
      String email = TestData.email();

      signUp(key(), email, "p".repeat(length), "Asha").andExpect(status().isCreated());
      assertThat(users(email)).isEqualTo(1);
    }
  }

  @Test
  void badEmailAndBlankNameAreRefused() throws Exception {
    signUp(key(), "not-an-email", PASSWORD, "Asha")
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errors[0].field").value("email"));
    signUp(key(), TestData.email(), PASSWORD, "   ")
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errors[0].field").value("displayName"));
  }

  @Test
  void anExistingEmailInAnyLetterCaseIs409AndCreatesNoSecondAccount() throws Exception {
    String email = TestData.email();
    signUp(key(), email, PASSWORD, "Asha").andExpect(status().isCreated());

    signUp(key(), email.toUpperCase(), "another password", "Other")
        .andExpect(status().isConflict())
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
        .andExpect(jsonPath("$.type").value("urn:kanakku:problem:email-taken"));
    assertThat(users(email)).isEqualTo(1);
  }

  @Test
  void repeatingTheKeyReturnsTheOriginalResultAndCreatesOneAccount() throws Exception {
    String email = TestData.email();
    String key = key();
    String first =
        signUp(key, email, PASSWORD, "Asha")
            .andExpect(status().isCreated())
            .andReturn()
            .getResponse()
            .getContentAsString();

    String second =
        signUp(key, email, "a different password", "Asha")
            .andExpect(status().isCreated())
            .andExpect(header().string("Idempotent-Replayed", "true"))
            .andReturn()
            .getResponse()
            .getContentAsString();

    assertThat(json.readTree(second)).isEqualTo(json.readTree(first));
    assertThat(users(email)).isEqualTo(1);
  }

  @Test
  void theSameKeyWithAnotherBodyIs422() throws Exception {
    String key = key();
    signUp(key, TestData.email(), PASSWORD, "Asha").andExpect(status().isCreated());

    String other = TestData.email();
    signUp(key, other, PASSWORD, "Asha")
        .andExpect(status().isUnprocessableContent())
        .andExpect(jsonPath("$.type").value("urn:kanakku:problem:idempotency-key-reuse"));
    assertThat(users(other)).isZero();
  }

  @Test
  void signUpWithoutACsrfTokenIs403() throws Exception {
    mvc.perform(
            post("/api/v1/auth/sign-up")
                .header("Idempotency-Key", key())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    json.writeValueAsString(
                        Map.of(
                            "email", TestData.email(), "password", PASSWORD, "displayName", "A"))))
        .andExpect(status().isForbidden());
  }
}
