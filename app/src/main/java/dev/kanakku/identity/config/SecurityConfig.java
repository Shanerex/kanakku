package dev.kanakku.identity.config;

import dev.kanakku.identity.api.AuthenticatedUser;
import dev.kanakku.identity.api.CurrentUser;
import dev.kanakku.platform.problem.ProblemWriter;
import dev.kanakku.platform.problem.Problems;
import java.util.Optional;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfException;
import org.springframework.security.web.csrf.CsrfTokenRepository;

/** Session-cookie security for the API: CSRF on every unsafe method, problem-detail refusals. */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

  @Bean
  SecurityContextRepository securityContextRepository() {
    return new HttpSessionSecurityContextRepository();
  }

  @Bean
  CsrfTokenRepository csrfTokenRepository() {
    CookieCsrfTokenRepository csrfRepository = CookieCsrfTokenRepository.withHttpOnlyFalse();
    csrfRepository.setCookieCustomizer(cookie -> cookie.sameSite("Lax"));
    return csrfRepository;
  }

  @Bean
  SecurityFilterChain securityFilterChain(
      HttpSecurity http,
      ProblemWriter problems,
      SecurityContextRepository contextRepository,
      CsrfTokenRepository csrfRepository)
      throws Exception {

    AuthenticationEntryPoint entryPoint =
        (request, response, e) ->
            problems.write(
                response,
                Problems.of(
                    HttpStatus.UNAUTHORIZED,
                    "unauthenticated",
                    "Sign in required",
                    "Sign in to continue."));
    AccessDeniedHandler deniedHandler =
        (request, response, e) -> {
          boolean csrf = e instanceof CsrfException;
          ProblemDetail problem =
              csrf
                  ? Problems.of(
                      HttpStatus.FORBIDDEN,
                      "csrf-token-invalid",
                      "Request could not be verified",
                      "The security token is missing or has expired. Reload and try again.")
                  : Problems.of(
                      HttpStatus.FORBIDDEN,
                      "forbidden",
                      "Not allowed",
                      "You do not have access to this.");
          problems.write(response, problem);
        };

    http.cors(Customizer.withDefaults())
        .csrf(
            csrf ->
                csrf.csrfTokenRepository(csrfRepository)
                    .csrfTokenRequestHandler(new SpaCsrfTokenRequestHandler()))
        .securityContext(context -> context.securityContextRepository(contextRepository))
        .requestCache(cache -> cache.disable())
        .sessionManagement(sessions -> sessions.sessionCreationPolicy(SessionCreationPolicy.NEVER))
        .httpBasic(basic -> basic.disable())
        .formLogin(form -> form.disable())
        .logout(logout -> logout.disable())
        .exceptionHandling(
            e -> e.authenticationEntryPoint(entryPoint).accessDeniedHandler(deniedHandler))
        .authorizeHttpRequests(
            auth ->
                auth.requestMatchers(HttpMethod.OPTIONS, "/**")
                    .permitAll()
                    .requestMatchers("/error", "/actuator/health", "/actuator/health/**")
                    .permitAll()
                    .requestMatchers(
                        "/api/v1/auth/csrf",
                        "/api/v1/auth/sign-up",
                        "/api/v1/auth/sign-in",
                        "/api/v1/auth/sign-out")
                    .permitAll()
                    .requestMatchers("/v3/api-docs", "/v3/api-docs/**")
                    .permitAll()
                    .requestMatchers("/api/**")
                    .authenticated()
                    .anyRequest()
                    .denyAll());
    return http.build();
  }

  @Bean
  CurrentUser currentUser() {
    return () -> {
      Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
      if (authentication != null
          && authentication.isAuthenticated()
          && authentication.getPrincipal() instanceof AuthenticatedUser user) {
        return Optional.of(user);
      }
      return Optional.empty();
    };
  }
}
