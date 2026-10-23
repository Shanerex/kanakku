package dev.kanakku.identity.controller;

import static dev.kanakku.jooq.Tables.SPRING_SESSION;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import dev.kanakku.identity.service.SignUpService;
import dev.kanakku.support.Csrf;
import dev.kanakku.support.PostgresIntegrationTest;
import dev.kanakku.support.TestData;
import dev.kanakku.support.WhoAmI;
import jakarta.servlet.http.Cookie;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import org.jooq.DSLContext;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.json.JsonMapper;

@SpringBootTest
@AutoConfigureMockMvc
@Import(WhoAmI.class)
class SessionExpiryTest extends PostgresIntegrationTest {

  static final Duration IDLE = Duration.ofDays(14);

  @Autowired MockMvc mvc;
  @Autowired SignUpService signUp;
  @Autowired DSLContext dsl;
  @Autowired JsonMapper json;

  Cookie signInAndGetCookie() throws Exception {
    String email = TestData.email();
    signUp.signUp(email, "correct horse battery", "Asha");
    return mvc.perform(
            post("/api/v1/auth/sign-in")
                .with(Csrf.token(mvc))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    json.writeValueAsString(
                        Map.of("email", email, "password", "correct horse battery"))))
        .andExpect(status().isOk())
        .andReturn()
        .getResponse()
        .getCookie("SESSION");
  }

  /** Pretends the session was last used {@code idleFor} ago. */
  void lastUsed(Cookie cookie, Duration idleFor) {
    long last = Instant.now().minus(idleFor).toEpochMilli();
    dsl.update(SPRING_SESSION)
        .set(SPRING_SESSION.LAST_ACCESS_TIME, last)
        .set(SPRING_SESSION.EXPIRY_TIME, last + IDLE.toMillis())
        .where(
            SPRING_SESSION.SESSION_ID.eq(
                new String(java.util.Base64.getDecoder().decode(cookie.getValue()))))
        .execute();
  }

  @Test
  void theIdleTimeoutIsFourteenDays() throws Exception {
    Cookie cookie = signInAndGetCookie();

    Integer seconds =
        dsl.select(SPRING_SESSION.MAX_INACTIVE_INTERVAL)
            .from(SPRING_SESSION)
            .where(
                SPRING_SESSION.SESSION_ID.eq(
                    new String(java.util.Base64.getDecoder().decode(cookie.getValue()))))
            .fetchOne(SPRING_SESSION.MAX_INACTIVE_INTERVAL);

    assertThat(seconds).isEqualTo((int) IDLE.toSeconds());
  }

  @Test
  void aSessionUnusedForFourteenDaysAndAMinuteIsRejected() throws Exception {
    Cookie cookie = signInAndGetCookie();
    lastUsed(cookie, IDLE.plusMinutes(1));

    mvc.perform(get("/api/v1/test/whoami").cookie(cookie)).andExpect(status().isUnauthorized());
  }

  @Test
  void aSessionUsedThirteenDaysAgoIsAccepted() throws Exception {
    Cookie cookie = signInAndGetCookie();
    lastUsed(cookie, Duration.ofDays(13));

    mvc.perform(get("/api/v1/test/whoami").cookie(cookie)).andExpect(status().isOk());
  }
}
