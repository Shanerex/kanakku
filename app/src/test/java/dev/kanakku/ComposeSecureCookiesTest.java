package dev.kanakku;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

/**
 * The stack ships with Secure session cookies. Only the development override turns that off, so a
 * deployment can never lose the flag by accident.
 */
class ComposeSecureCookiesTest {

  private static final Path ROOT = Path.of("..").toAbsolutePath().normalize();

  private static String read(String file) throws Exception {
    return Files.readString(ROOT.resolve(file));
  }

  @Test
  void composeDefaultsToSecureCookiesWithNoEnvironmentOptOut() throws Exception {
    String compose = read("docker-compose.yml");

    assertThat(compose).contains("KANAKKU_AUTH_SECURE_COOKIES: \"true\"");
    assertThat(compose).doesNotContain("SECURE_COOKIES:-");
  }

  @Test
  void onlyTheDevOverrideOptsOut() throws Exception {
    assertThat(read("docker-compose.dev.yml")).contains("KANAKKU_AUTH_SECURE_COOKIES: \"false\"");
  }
}
