package dev.kanakku.identity.internal;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.session.web.http.DefaultCookieSerializer;

/**
 * The session cookie: HttpOnly, SameSite=Lax, and Secure whenever the request is HTTPS. The 14-day
 * idle timeout is {@code spring.session.timeout} in application.yml.
 */
@Configuration
class SessionConfig {

  @Bean
  DefaultCookieSerializer cookieSerializer() {
    DefaultCookieSerializer serializer = new DefaultCookieSerializer();
    serializer.setCookieName("SESSION");
    serializer.setCookiePath("/");
    serializer.setUseHttpOnlyCookie(true);
    serializer.setSameSite("Lax");
    return serializer;
  }
}
