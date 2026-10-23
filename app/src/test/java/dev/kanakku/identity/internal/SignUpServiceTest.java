package dev.kanakku.identity.internal;

import static dev.kanakku.jooq.Tables.PASSWORD_CREDENTIALS;
import static dev.kanakku.jooq.Tables.USERS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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

@SpringBootTest
class SignUpServiceTest extends PostgresIntegrationTest {

  @Autowired SignUpService signUp;
  @Autowired DSLContext dsl;

  @Test
  void createsAnUnverifiedInrUserWithAnArgon2idHash() {
    String email = TestData.email();

    Profile profile = signUp.signUp(email, "correct horse battery", "Asha");

    assertThat(profile.email()).isEqualTo(email);
    assertThat(profile.emailVerified()).isFalse();
    assertThat(profile.displayName()).isEqualTo("Asha");
    assertThat(profile.defaultCurrency()).isEqualTo("INR");
    assertThat(profile.upiId()).isNull();
    assertThat(profile.version()).isZero();
    String hash =
        dsl.select(PASSWORD_CREDENTIALS.PASSWORD_HASH)
            .from(PASSWORD_CREDENTIALS)
            .where(PASSWORD_CREDENTIALS.USER_ID.eq(profile.id()))
            .fetchOne(PASSWORD_CREDENTIALS.PASSWORD_HASH);
    assertThat(hash).startsWith("$argon2id$").isNotEqualTo("correct horse battery");
  }

  @Test
  void trimsTheEmail() {
    String email = TestData.email();

    Profile profile = signUp.signUp("  " + email + " ", "correct horse battery", "Asha");

    assertThat(profile.email()).isEqualTo(email);
  }

  @Test
  void refusesTheSameEmailInAnotherLetterCaseAndCreatesNoSecondAccount() {
    String email = TestData.email();
    signUp.signUp(email, "correct horse battery", "Asha");

    assertThatThrownBy(() -> signUp.signUp(email.toUpperCase(), "another password", "Other"))
        .isInstanceOf(EmailTakenException.class);

    assertThat(dsl.fetchCount(USERS, USERS.EMAIL.equalIgnoreCase(email))).isEqualTo(1);
  }

  @Test
  void twoConcurrentSignUpsForOneEmailCreateExactlyOneAccount() throws Exception {
    String email = TestData.email();
    int attempts = 4;
    CountDownLatch start = new CountDownLatch(1);
    ExecutorService pool = Executors.newFixedThreadPool(attempts);
    try {
      List<Future<Boolean>> results = new ArrayList<>();
      for (int i = 0; i < attempts; i++) {
        String variant = i % 2 == 0 ? email : email.toUpperCase();
        Callable<Boolean> task =
            () -> {
              start.await();
              try {
                signUp.signUp(variant, "correct horse battery", "Racer");
                return true;
              } catch (EmailTakenException e) {
                return false;
              }
            };
        results.add(pool.submit(task));
      }
      start.countDown();
      int created = 0;
      for (Future<Boolean> result : results) {
        if (result.get()) {
          created++;
        }
      }
      assertThat(created).isEqualTo(1);
      assertThat(dsl.fetchCount(USERS, USERS.EMAIL.equalIgnoreCase(email))).isEqualTo(1);
    } finally {
      pool.shutdownNow();
    }
  }
}
