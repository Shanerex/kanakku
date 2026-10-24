package dev.kanakku.identity.service;

import static org.assertj.core.api.Assertions.assertThat;

import dev.kanakku.identity.domain.Profile;
import dev.kanakku.identity.exception.VersionConflictException;
import dev.kanakku.support.PostgresIntegrationTest;
import dev.kanakku.support.TestData;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
class ProfileServiceConcurrencyTest extends PostgresIntegrationTest {

  @Autowired SignUpService signUp;
  @Autowired ProfileService profiles;

  @Test
  void twoEditsWithTheSameVersionGiveOneProfileAndOneConflict() throws Exception {
    Profile created = signUp.signUp(TestData.email(), "correct horse battery", "Asha");
    int threads = 6;
    CountDownLatch start = new CountDownLatch(1);
    ExecutorService pool = Executors.newFixedThreadPool(threads);
    try {
      List<Future<Profile>> results = new ArrayList<>();
      for (int i = 0; i < threads; i++) {
        String name = "Name " + i;
        results.add(
            pool.submit(
                () -> {
                  start.await();
                  return profiles.update(created.id(), name, "INR", null, created.version());
                }));
      }
      start.countDown();

      int ok = 0;
      int conflicts = 0;
      for (Future<Profile> f : results) {
        try {
          Profile p = f.get();
          assertThat(p.version()).isEqualTo(created.version() + 1);
          assertThat(p.displayName()).startsWith("Name ");
          ok++;
        } catch (java.util.concurrent.ExecutionException e) {
          assertThat(e.getCause()).isInstanceOf(VersionConflictException.class);
          conflicts++;
        }
      }

      assertThat(ok).isOne();
      assertThat(conflicts).isEqualTo(threads - 1);
      assertThat(profiles.get(created.id()).version()).isEqualTo(created.version() + 1);
    } finally {
      pool.shutdownNow();
    }
  }

  @Test
  void theReturnedProfileIsTheRowThatThisUpdateWrote() {
    Profile created = signUp.signUp(TestData.email(), "correct horse battery", "Asha");

    Profile updated = profiles.update(created.id(), "Asha K", "USD", "asha@okbank", 0);

    assertThat(updated.displayName()).isEqualTo("Asha K");
    assertThat(updated.defaultCurrency()).isEqualTo("USD");
    assertThat(updated.upiId()).isEqualTo("asha@okbank");
    assertThat(updated.version()).isEqualTo(1);
  }
}
