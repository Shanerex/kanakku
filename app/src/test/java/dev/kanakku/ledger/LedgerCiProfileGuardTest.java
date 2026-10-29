package dev.kanakku.ledger;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/**
 * CI must run the 10,000-try ledger property. The Maven {@code ci} profile does that and sets
 * {@code kanakku.ci}; if CI is set but the profile did not activate, this fails the build.
 */
class LedgerCiProfileGuardTest {

  @Test
  void ciRunsTheFullLedgerProperty() {
    if (System.getenv("CI") != null) {
      assertThat(System.getProperty("kanakku.ci"))
          .as("CI is set, so the ci Maven profile must be active (it runs ledger-full)")
          .isEqualTo("true");
    }
  }
}
