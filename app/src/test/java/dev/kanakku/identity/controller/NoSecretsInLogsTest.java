package dev.kanakku.identity.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import dev.kanakku.identity.request.SignInRequest;
import dev.kanakku.identity.request.SignUpRequest;
import dev.kanakku.support.Csrf;
import dev.kanakku.support.PostgresIntegrationTest;
import dev.kanakku.support.TestData;
import jakarta.servlet.http.Cookie;
import java.util.Base64;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.json.JsonMapper;

/** Runs sign-up, sign-in and sign-out with DEBUG logging and looks for secrets in the output. */
@SpringBootTest(properties = "logging.level.root=DEBUG")
@AutoConfigureMockMvc
@ExtendWith(OutputCaptureExtension.class)
class NoSecretsInLogsTest extends PostgresIntegrationTest {

  @Autowired MockMvc mvc;
  @Autowired JsonMapper json;

  @Test
  void passwordAndSessionIdNeverAppearInTheLog(CapturedOutput output) throws Exception {
    String email = TestData.email();
    String password = "pw-" + UUID.randomUUID();

    mvc.perform(
            post("/api/v1/auth/sign-up")
                .with(Csrf.token(mvc))
                .header("Idempotency-Key", UUID.randomUUID().toString())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    json.writeValueAsString(
                        Map.of("email", email, "password", password, "displayName", "Asha"))))
        .andExpect(status().isCreated());
    mvc.perform(
            post("/api/v1/auth/sign-in")
                .with(Csrf.token(mvc))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    json.writeValueAsString(
                        Map.of("email", email, "password", "wrong-" + password))))
        .andExpect(status().isUnauthorized());
    Cookie session =
        mvc.perform(
                post("/api/v1/auth/sign-in")
                    .with(Csrf.token(mvc))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(json.writeValueAsString(Map.of("email", email, "password", password))))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getCookie("SESSION");
    mvc.perform(get("/api/v1/me").cookie(session)).andExpect(status().isOk());
    mvc.perform(post("/api/v1/auth/sign-out").with(Csrf.token(mvc)).cookie(session))
        .andExpect(status().isNoContent());

    String sessionId = new String(Base64.getDecoder().decode(session.getValue()));
    String log = output.getAll();
    assertThat(log).contains("DEBUG").contains("/api/v1/auth/sign-in");
    assertThat(log)
        .doesNotContain(password)
        .doesNotContain(session.getValue())
        .doesNotContain(sessionId)
        .doesNotContain("$argon2id$");
  }

  @Test
  void requestDtosMaskThePasswordInToString() {
    assertThat(new SignUpRequest("a@b.co", "secret-pw-123", "A").toString())
        .doesNotContain("secret-pw-123")
        .contains("a@b.co");
    assertThat(new SignInRequest("a@b.co", "secret-pw-123").toString())
        .doesNotContain("secret-pw-123");
  }
}
