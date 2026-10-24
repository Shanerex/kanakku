package dev.kanakku.identity.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.session.web.http.DefaultCookieSerializer;

/**
 * The session cookie: HttpOnly, SameSite=Lax, and Secure always. Only {@code
 * kanakku.auth.secure-cookies=false}, for plain-HTTP local development, falls back to Secure on
 * HTTPS requests only. The 14-day idle timeout is {@code spring.session.timeout} in
 * application.yml.
 */
@Configuration
public class SessionConfig {

  @Bean
  DefaultCookieSerializer cookieSerializer(
      @Value("${kanakku.auth.secure-cookies:true}") boolean secureCookies) {
    DefaultCookieSerializer serializer = new DefaultCookieSerializer();
    serializer.setCookieName("SESSION");
    serializer.setCookiePath("/");
    serializer.setUseHttpOnlyCookie(true);
    serializer.setSameSite("Lax");
    if (secureCookies) {
      serializer.setUseSecureCookie(true);
    }
    return serializer;
  }
}
