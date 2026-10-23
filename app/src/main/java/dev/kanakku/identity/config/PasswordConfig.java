package dev.kanakku.identity.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.argon2.Argon2PasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

/** Argon2id with the OWASP minimum parameters: 19 MiB memory, 2 iterations, 1 lane. */
@Configuration
public class PasswordConfig {

  private static final int SALT_BYTES = 16;
  private static final int HASH_BYTES = 32;
  private static final int PARALLELISM = 1;
  private static final int MEMORY_KIB = 19456;
  private static final int ITERATIONS = 2;

  @Bean
  PasswordEncoder passwordEncoder() {
    return new Argon2PasswordEncoder(SALT_BYTES, HASH_BYTES, PARALLELISM, MEMORY_KIB, ITERATIONS);
  }
}
