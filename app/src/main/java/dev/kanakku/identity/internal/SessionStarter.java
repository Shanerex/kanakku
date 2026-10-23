package dev.kanakku.identity.internal;

import dev.kanakku.identity.api.AuthenticatedUser;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.List;
import java.util.UUID;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.web.csrf.CsrfTokenRepository;
import org.springframework.stereotype.Component;

/** Starts and ends the signed-in session. */
@Component
public class SessionStarter {

  private final SecurityContextRepository contextRepository;
  private final CsrfTokenRepository csrfTokenRepository;

  SessionStarter(
      SecurityContextRepository contextRepository, CsrfTokenRepository csrfTokenRepository) {
    this.contextRepository = contextRepository;
    this.csrfTokenRepository = csrfTokenRepository;
  }

  /**
   * Signs the user in. An existing session gets a new id, so a planted session id is useless, and
   * the CSRF token is replaced, so one planted before sign-in is useless too. The new token is in
   * the {@code XSRF-TOKEN} cookie of the response; the client fetches it again.
   */
  public void start(UUID userId, HttpServletRequest request, HttpServletResponse response) {
    if (request.getSession(false) != null) {
      request.changeSessionId();
    } else {
      request.getSession(true);
    }
    SecurityContext context = SecurityContextHolder.createEmptyContext();
    context.setAuthentication(
        UsernamePasswordAuthenticationToken.authenticated(
            new AuthenticatedUser(userId), null, List.of(new SimpleGrantedAuthority("ROLE_USER"))));
    SecurityContextHolder.setContext(context);
    contextRepository.saveContext(context, request, response);
    csrfTokenRepository.saveToken(csrfTokenRepository.generateToken(request), request, response);
  }

  /** Deletes the session row, so the cookie stops working at once. */
  public void end(HttpServletRequest request) {
    var session = request.getSession(false);
    if (session != null) {
      session.invalidate();
    }
    SecurityContextHolder.clearContext();
  }
}
