package dev.kanakku;

import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/** Allows the separately hosted web origin to call the API. Origins come from one property. */
@Configuration
class CorsConfig implements WebMvcConfigurer {

  private final List<String> allowedOrigins;

  CorsConfig(@Value("${kanakku.cors.allowed-origins}") List<String> allowedOrigins) {
    if (allowedOrigins.stream().anyMatch(o -> o.contains("*"))) {
      throw new IllegalArgumentException("kanakku.cors.allowed-origins must not contain '*'");
    }
    this.allowedOrigins = allowedOrigins;
  }

  @Override
  public void addCorsMappings(CorsRegistry registry) {
    registry
        .addMapping("/api/**")
        .allowedOrigins(allowedOrigins.toArray(String[]::new))
        .allowedMethods("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS")
        .allowedHeaders("*");
  }
}
