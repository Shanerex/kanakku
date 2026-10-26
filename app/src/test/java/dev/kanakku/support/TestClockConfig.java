package dev.kanakku.support;

import java.time.Clock;
import java.time.Instant;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

/**
 * Replaces the application clock with {@link #CLOCK}, which a test can move. Import it with
 * {@code @Import(TestClockConfig.class)}; the clock is shared by every test that imports it, so
 * reset it with {@link #reset()} at the start of a test that depends on the time.
 */
@TestConfiguration
public class TestClockConfig {

  public static final MutableClock CLOCK = new MutableClock(Instant.now());

  /** Puts the shared clock at the real current time. */
  public static void reset() {
    CLOCK.set(Instant.now());
  }

  @Bean
  @Primary
  Clock testClock() {
    return CLOCK;
  }
}
