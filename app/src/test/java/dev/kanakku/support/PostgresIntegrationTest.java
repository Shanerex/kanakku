package dev.kanakku.support;

import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * Base class for tests that need a real Postgres. One container is shared by the whole test JVM, so
 * tests must use unique data (for example {@link TestData#email()}) and not assume empty tables.
 */
public abstract class PostgresIntegrationTest {

  private static final PostgreSQLContainer POSTGRES =
      new PostgreSQLContainer("postgres:17")
          // Every distinct Spring test context keeps its own connection pool alive.
          .withCommand("postgres", "-c", "max_connections=500");

  static {
    POSTGRES.start();
  }

  @DynamicPropertySource
  static void datasource(DynamicPropertyRegistry registry) {
    registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
    registry.add("spring.datasource.username", POSTGRES::getUsername);
    registry.add("spring.datasource.password", POSTGRES::getPassword);
  }
}
