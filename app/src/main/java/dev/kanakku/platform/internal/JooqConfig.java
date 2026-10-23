package dev.kanakku.platform.internal;

import org.jooq.conf.Settings;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** jOOQ settings. Execute logging is off: it would print bound values such as hashes and emails. */
@Configuration
class JooqConfig {

  @Bean
  Settings jooqSettings() {
    return new Settings().withExecuteLogging(false);
  }
}
