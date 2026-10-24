package dev.kanakku.identity.controller;

import static dev.kanakku.jooq.Tables.USERS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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
import tools.jackson.databind.json.JsonMapper;

/** With no hash slot available the wait times out as a 429 and no account is created. */
@SpringBootTest(
    properties = {
      "kanakku.auth.max-concurrent-sign-up-hashes=0",
      "kanakku.auth.sign-up-hash-wait=50ms"
    })
@AutoConfigureMockMvc
class SignUpHashSlotsTest extends PostgresIntegrationTest {

  @Autowired MockMvc mvc;
  @Autowired DSLContext dsl;
  @Autowired JsonMapper json;

  @Test
  void aSignUpThatFindsNoHashSlotIs429AndCreatesNothing() throws Exception {
    String email = TestData.email();

    mvc.perform(
            post("/api/v1/auth/sign-up")
                .with(Csrf.token(mvc))
                .header("Idempotency-Key", UUID.randomUUID().toString())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    json.writeValueAsString(
                        Map.of(
                            "email",
                            email,
                            "password",
                            "correct horse battery",
                            "displayName",
                            "Asha"))))
        .andExpect(status().isTooManyRequests())
        .andExpect(header().string("Retry-After", "1"))
        .andExpect(jsonPath("$.type").value("urn:kanakku:problem:busy"));

    assertThat(dsl.fetchCount(USERS, USERS.EMAIL.equalIgnoreCase(email))).isZero();
  }
}
