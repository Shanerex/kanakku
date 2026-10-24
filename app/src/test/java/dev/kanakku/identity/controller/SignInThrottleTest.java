package dev.kanakku.identity.controller;

import static dev.kanakku.jooq.Tables.SIGN_IN_FAILURES;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import dev.kanakku.identity.service.SignInThrottle;
import dev.kanakku.identity.service.SignUpService;
import dev.kanakku.support.Csrf;
import dev.kanakku.support.MutableClock;
import dev.kanakku.support.PostgresIntegrationTest;
import dev.kanakku.support.TestData;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import org.jooq.DSLContext;
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
import org.springframework.test.web.servlet.ResultActions;
import tools.jackson.databind.json.JsonMapper;

@SpringBootTest
@AutoConfigureMockMvc
@Import(SignInThrottleTest.ClockConfig.class)
class SignInThrottleTest extends PostgresIntegrationTest {

  static final String PASSWORD = "correct horse battery";
  static final MutableClock CLOCK = new MutableClock(Instant.parse("2026-10-23T10:00:00Z"));

  @TestConfiguration
  static class ClockConfig {
    @Bean
    @Primary
    Clock testClock() {
      return CLOCK;
    }
  }

  @Autowired MockMvc mvc;
  @Autowired SignInThrottle throttle;
  @Autowired SignUpService signUp;
  @Autowired DSLContext dsl;
  @Autowired JsonMapper json;

  ResultActions signIn(String email, String password, String ip) throws Exception {
    return mvc.perform(
        post("/api/v1/auth/sign-in")
            .with(Csrf.token(mvc))
            .with(
                request -> {
                  request.setRemoteAddr(ip);
                  return request;
                })
            .contentType(MediaType.APPLICATION_JSON)
            .content(json.writeValueAsString(Map.of("email", email, "password", password))));
  }

  void failFiveTimes(String email, String ip) throws Exception {
    for (int i = 0; i < 5; i++) {
      signIn(email, "wrong password", ip).andExpect(status().isUnauthorized());
    }
  }

  @Test
  void theSixthTryWithTheCorrectPasswordIs429WithRetryAfter() throws Exception {
    String email = TestData.email();
    signUp.signUp(email, PASSWORD, "Asha");
    failFiveTimes(email, "10.1.1.1");

    signIn(email, PASSWORD, "10.1.1.1")
        .andExpect(status().isTooManyRequests())
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
        .andExpect(header().string("Retry-After", "600"))
        .andExpect(jsonPath("$.retryAfterSeconds").value(600))
        .andExpect(jsonPath("$.type").value("urn:kanakku:problem:too-many-attempts"));
  }

  @Test
  void aThrottledTryRecordsNothingAndTheCountdownShrinks() throws Exception {
    String email = TestData.email();
    signUp.signUp(email, PASSWORD, "Asha");
    failFiveTimes(email, "10.1.2.1");
    int rows = dsl.fetchCount(SIGN_IN_FAILURES, SIGN_IN_FAILURES.EMAIL_KEY.eq(email));

    CLOCK.advance(Duration.ofMinutes(4));
    signIn(email, PASSWORD, "10.1.2.1")
        .andExpect(status().isTooManyRequests())
        .andExpect(header().string("Retry-After", "360"));

    assertThat(dsl.fetchCount(SIGN_IN_FAILURES, SIGN_IN_FAILURES.EMAIL_KEY.eq(email)))
        .isEqualTo(rows);
  }

  @Test
  void theSameEmailFromAnotherIpIsNotLimited() throws Exception {
    String email = TestData.email();
    signUp.signUp(email, PASSWORD, "Asha");
    failFiveTimes(email, "10.1.3.1");

    signIn(email, PASSWORD, "10.1.3.2").andExpect(status().isOk());
  }

  @Test
  void twoAddressesInOneIpv6Slash64ShareABucket() throws Exception {
    String email = TestData.email();
    signUp.signUp(email, PASSWORD, "Asha");
    for (int i = 0; i < 5; i++) {
      signIn(email, "wrong password", "2001:db8:1:2::" + (i + 1))
          .andExpect(status().isUnauthorized());
    }

    signIn(email, PASSWORD, "2001:db8:1:2:abcd:ef01:2345:6789")
        .andExpect(status().isTooManyRequests());
  }

  @Test
  void anotherIpv6Slash64IsNotLimited() throws Exception {
    String email = TestData.email();
    signUp.signUp(email, PASSWORD, "Asha");
    failFiveTimes(email, "2001:db8:3:4::1");

    signIn(email, PASSWORD, "2001:db8:3:5::1").andExpect(status().isOk());
  }

  @Test
  void anotherEmailFromTheSameIpIsNotLimited() throws Exception {
    String email = TestData.email();
    String other = TestData.email();
    signUp.signUp(other, PASSWORD, "Other");
    failFiveTimes(email, "10.1.4.1");

    signIn(other, PASSWORD, "10.1.4.1").andExpect(status().isOk());
  }

  @Test
  void signInWorksAgainOnceTheWindowClears() throws Exception {
    String email = TestData.email();
    signUp.signUp(email, PASSWORD, "Asha");
    failFiveTimes(email, "10.1.5.1");

    CLOCK.advance(Duration.ofMinutes(9).plusSeconds(59));
    signIn(email, PASSWORD, "10.1.5.1").andExpect(status().isTooManyRequests());

    CLOCK.advance(Duration.ofSeconds(2));
    signIn(email, PASSWORD, "10.1.5.1").andExpect(status().isOk());
  }

  @Test
  void unknownEmailsAreCountedToo() throws Exception {
    String email = TestData.email();
    failFiveTimes(email, "10.1.6.1");

    signIn(email, PASSWORD, "10.1.6.1").andExpect(status().isTooManyRequests());
  }

  @Test
  void aSignInNoLongerDeletesOldFailuresButThePurgeJobDoes() throws Exception {
    String email = TestData.email();
    signUp.signUp(email, PASSWORD, "Asha");
    signIn(email, "wrong password", "10.1.7.1").andExpect(status().isUnauthorized());
    assertThat(dsl.fetchCount(SIGN_IN_FAILURES, SIGN_IN_FAILURES.EMAIL_KEY.eq(email))).isEqualTo(1);

    CLOCK.advance(Duration.ofMinutes(61));
    signIn(TestData.email(), "wrong password", "10.1.7.2").andExpect(status().isUnauthorized());
    assertThat(dsl.fetchCount(SIGN_IN_FAILURES, SIGN_IN_FAILURES.EMAIL_KEY.eq(email))).isEqualTo(1);

    assertThat(throttle.purgeExpired()).isPositive();

    assertThat(dsl.fetchCount(SIGN_IN_FAILURES, SIGN_IN_FAILURES.EMAIL_KEY.eq(email))).isZero();
  }

  @Test
  void theThrottleKeyIsTheSameAsPostgresLower() throws Exception {
    // U+0130 is where Java and Postgres lower-casing can differ; the key must come from Postgres.
    String email = "\u0130stanbul" + TestData.email();
    String expected = dsl.fetchSingle("select lower(?)", email).get(0, String.class);

    signIn(email, "wrong password", "10.1.8.1").andExpect(status().isUnauthorized());

    assertThat(dsl.fetchCount(SIGN_IN_FAILURES, SIGN_IN_FAILURES.EMAIL_KEY.eq(expected)))
        .isEqualTo(1);
  }

  @Test
  void failedAtIsIndexedSoThePruneIsCheap() {
    int indexes =
        dsl.fetchSingle(
                "select count(*) from pg_indexes where tablename = 'sign_in_failures'"
                    + " and indexdef like '%(failed_at)%'")
            .get(0, Integer.class);
    assertThat(indexes).isEqualTo(1);
  }
}
