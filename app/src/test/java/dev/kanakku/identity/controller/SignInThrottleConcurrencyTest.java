package dev.kanakku.identity.controller;

import static dev.kanakku.jooq.Tables.SIGN_IN_FAILURES;
import static org.assertj.core.api.Assertions.assertThat;

import dev.kanakku.identity.exception.InvalidCredentialsException;
import dev.kanakku.identity.exception.TooManyAttemptsException;
import dev.kanakku.identity.service.PasswordSignIn;
import dev.kanakku.identity.service.SignUpService;
import dev.kanakku.support.PostgresIntegrationTest;
import dev.kanakku.support.TestData;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.jooq.DSLContext;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * R-7 / AC-7: parallel sign-ins from one IP must not each get a full guess. The per-IP hash cap is
 * raised so this test exercises the per-pair throttle alone.
 */
@SpringBootTest(properties = "kanakku.auth.max-hashes-per-ip=100")
class SignInThrottleConcurrencyTest extends PostgresIntegrationTest {

  static final int PARALLEL = 20;

  @Autowired PasswordSignIn passwordSignIn;
  @Autowired SignUpService signUp;
  @Autowired DSLContext dsl;

  @Test
  void parallelWrongGuessesFromOneIpGetAtMostFiveTries() throws Exception {
    String email = TestData.email();
    signUp.signUp(email, "correct horse battery", "Asha");
    String ip = "10.9.9.9";

    CountDownLatch start = new CountDownLatch(1);
    ExecutorService pool = Executors.newFixedThreadPool(PARALLEL);
    List<Future<String>> results = new ArrayList<>();
    for (int i = 0; i < PARALLEL; i++) {
      Callable<String> attempt =
          () -> {
            start.await();
            try {
              passwordSignIn.signIn(email, "wrong password", ip);
              return "ok";
            } catch (InvalidCredentialsException e) {
              return "guess";
            } catch (TooManyAttemptsException e) {
              return "throttled";
            }
          };
      results.add(pool.submit(attempt));
    }
    start.countDown();
    List<String> outcomes = new ArrayList<>();
    for (Future<String> result : results) {
      outcomes.add(result.get());
    }
    pool.shutdown();

    assertThat(outcomes).filteredOn("guess"::equals).hasSize(5);
    assertThat(outcomes).filteredOn("throttled"::equals).hasSize(PARALLEL - 5);
    assertThat(dsl.fetchCount(SIGN_IN_FAILURES, SIGN_IN_FAILURES.EMAIL_KEY.eq(email.toLowerCase())))
        .isEqualTo(5);
  }

  @Test
  void parallelRightPasswordIsNeverLockedOutByOtherIps() throws Exception {
    String email = TestData.email();
    signUp.signUp(email, "correct horse battery", "Asha");
    ExecutorService pool = Executors.newFixedThreadPool(4);
    List<Future<Object>> results = new ArrayList<>();
    for (int i = 0; i < 4; i++) {
      String ip = "10.8.8." + i;
      results.add(pool.submit(() -> passwordSignIn.signIn(email, "correct horse battery", ip)));
    }
    for (Future<Object> result : results) {
      assertThat(result.get()).isNotNull();
    }
    pool.shutdown();
  }
}
