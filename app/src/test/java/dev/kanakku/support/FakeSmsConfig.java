package dev.kanakku.support;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

/** Replaces the SMS provider with a {@link FakeSmsGateway}; import it to run with a provider. */
@TestConfiguration
public class FakeSmsConfig {

  @Bean
  @Primary
  FakeSmsGateway fakeSmsGateway() {
    return new FakeSmsGateway();
  }
}
