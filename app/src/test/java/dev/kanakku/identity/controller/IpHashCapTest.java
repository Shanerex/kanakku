package dev.kanakku.identity.controller;

import static dev.kanakku.jooq.Tables.SIGN_IN_FAILURES;
import static dev.kanakku.jooq.Tables.USERS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import dev.kanakku.identity.service.IpHashGuard;
import dev.kanakku.identity.service.SignUpService;
import dev.kanakku.support.Csrf;
import dev.kanakku.support.PostgresIntegrationTest;
import dev.kanakku.support.TestData;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.jooq.DSLContext;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import tools.jackson.databind.json.JsonMapper;

/**
 * One IP (an IPv6 customer counts by its /64) may have only a few sign-in or sign-up hashes in
 * flight, waiting for a slot included. The excess gets a 429 at once, whatever the email is, so a
 * flood cannot park every request thread and the answer reveals nothing about an account. Other IPs
 * are not affected.
 */
@SpringBootTest(
    properties = {
      "kanakku.auth.max-concurrent-unknown-hashes=0",
      "kanakku.auth.unknown-hash-wait=3s",
      "kanakku.auth.max-hashes-per-ip=2"
    })
@AutoConfigureMockMvc
class IpHashCapTest extends PostgresIntegrationTest {

  static final String PASSWORD = "correct horse battery";
  static final String FLOODER = "203.0.113.7";

  @Autowired MockMvc mvc;
  @Autowired SignUpService signUp;
  @Autowired IpHashGuard guard;
  @Autowired DSLContext dsl;
  @Autowired JsonMapper json;

  static RequestPostProcessor from(String ip) {
    return request -> {
      request.setRemoteAddr(ip);
      return request;
    };
  }

  int signInStatus(String email, String password, String ip) throws Exception {
    return mvc.perform(
            post("/api/v1/auth/sign-in")
                .with(Csrf.token(mvc))
                .with(from(ip))
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("email", email, "password", password))))
        .andReturn()
        .getResponse()
        .getStatus();
  }

  void fillTheCap(String ip) {
    guard.tryEnter(ip).orElseThrow();
    guard.tryEnter(ip).orElseThrow();
  }

  @Test
  void aFloodFromOneIpParksNoMoreThanItsCapOfThreads() throws Exception {
    int requests = 10;
    ExecutorService pool = Executors.newFixedThreadPool(requests);
    CountDownLatch go = new CountDownLatch(1);
    List<Future<Integer>> results = new ArrayList<>();
    for (int i = 0; i < requests; i++) {
      String email = TestData.email();
      results.add(
          pool.submit(
              () -> {
                go.await();
                return signInStatus(email, PASSWORD, "198.51.100.9");
              }));
    }
    go.countDown();
    List<Integer> statuses = new ArrayList<>();
    for (Future<Integer> result : results) {
      statuses.add(result.get(30, TimeUnit.SECONDS));
    }
    pool.shutdownNow();

    // Slots are all taken, so the two admitted tries wait out the 3 s and get the generic 401;
    // every other try is refused at once with a 429.
    assertThat(statuses.stream().filter(s -> s == 401).count()).isLessThanOrEqualTo(2);
    assertThat(statuses.stream().filter(s -> s == 429).count()).isGreaterThanOrEqualTo(8);
  }

  @Test
  void overTheCapIsA429ForKnownAndUnknownEmailsAlike() throws Exception {
    String known = TestData.email();
    signUp.signUp(known, PASSWORD, "Asha");
    fillTheCap("198.51.100.20");

    String unknownBody = signInBody(TestData.email(), PASSWORD, "198.51.100.20");
    String knownBody = signInBody(known, PASSWORD, "198.51.100.20");

    assertThat(unknownBody).contains("urn:kanakku:problem:busy");
    assertThat(unknownBody).isEqualTo(knownBody);
  }

  String signInBody(String email, String password, String ip) throws Exception {
    return mvc.perform(
            post("/api/v1/auth/sign-in")
                .with(Csrf.token(mvc))
                .with(from(ip))
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("email", email, "password", password))))
        .andExpect(status().isTooManyRequests())
        .andExpect(header().string("Retry-After", "1"))
        .andExpect(jsonPath("$.retryAfterSeconds").value(1))
        .andReturn()
        .getResponse()
        .getContentAsString()
        .replaceAll("\"instance\":\"[^\"]*\"", "");
  }

  @Test
  void anotherIpStillSignsIn() throws Exception {
    String email = TestData.email();
    signUp.signUp(email, PASSWORD, "Asha");
    fillTheCap(FLOODER);

    assertThat(signInStatus(email, PASSWORD, "198.51.100.30")).isEqualTo(200);
  }

  @Test
  void anOverCapSignInIsNotCountedAsAFailedTry() throws Exception {
    String email = TestData.email();
    signUp.signUp(email, PASSWORD, "Asha");
    fillTheCap("198.51.100.40");

    for (int i = 0; i < 8; i++) {
      assertThat(signInStatus(email, "wrong password", "198.51.100.40")).isEqualTo(429);
    }

    assertThat(dsl.fetchCount(SIGN_IN_FAILURES, SIGN_IN_FAILURES.EMAIL_KEY.eq(email))).isZero();
  }

  @Test
  void aSignUpOverTheCapIs429AndCreatesNothing() throws Exception {
    fillTheCap("198.51.100.50");
    String email = TestData.email();

    mvc.perform(
            post("/api/v1/auth/sign-up")
                .with(Csrf.token(mvc))
                .with(from("198.51.100.50"))
                .header("Idempotency-Key", UUID.randomUUID().toString())
                .contentType(MediaType.APPLICATION_JSON)
                .content(signUpBody(email)))
        .andExpect(status().isTooManyRequests())
        .andExpect(header().string("Retry-After", "1"))
        .andExpect(jsonPath("$.type").value("urn:kanakku:problem:busy"));

    assertThat(dsl.fetchCount(USERS, USERS.EMAIL.equalIgnoreCase(email))).isZero();
  }

  @Test
  void aSignUpFromAnotherIpStillWorks() throws Exception {
    fillTheCap("198.51.100.60");

    mvc.perform(
            post("/api/v1/auth/sign-up")
                .with(Csrf.token(mvc))
                .with(from("198.51.100.61"))
                .header("Idempotency-Key", UUID.randomUUID().toString())
                .contentType(MediaType.APPLICATION_JSON)
                .content(signUpBody(TestData.email())))
        .andExpect(status().isCreated());
  }

  String signUpBody(String email) throws Exception {
    return json.writeValueAsString(
        Map.of("email", email, "password", PASSWORD, "displayName", "Asha"));
  }
}
