package dev.kanakku.identity.internal;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.function.Supplier;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler;
import org.springframework.security.web.csrf.CsrfTokenRequestHandler;
import org.springframework.security.web.csrf.XorCsrfTokenRequestAttributeHandler;
import org.springframework.util.StringUtils;

/**
 * CSRF handling for a single-page app. The SPA gets the raw token from {@code GET
 * /api/v1/auth/csrf} and sends it back in a header, so the header is compared as is; the masked
 * (BREACH-safe) form is still used for form posts. Nothing is generated until the token is asked
 * for or a request needs checking.
 */
public final class SpaCsrfTokenRequestHandler implements CsrfTokenRequestHandler {

  /** Request attribute holding the {@code Supplier<CsrfToken>} for the raw token. */
  public static final String RAW_TOKEN_SUPPLIER = SpaCsrfTokenRequestHandler.class.getName();

  private final CsrfTokenRequestHandler plain = new CsrfTokenRequestAttributeHandler();
  private final CsrfTokenRequestHandler xor = new XorCsrfTokenRequestAttributeHandler();

  @Override
  public void handle(
      HttpServletRequest request,
      HttpServletResponse response,
      Supplier<CsrfToken> deferredCsrfToken) {
    xor.handle(request, response, deferredCsrfToken);
    request.setAttribute(RAW_TOKEN_SUPPLIER, deferredCsrfToken);
  }

  @Override
  public String resolveCsrfTokenValue(HttpServletRequest request, CsrfToken csrfToken) {
    String headerValue = request.getHeader(csrfToken.getHeaderName());
    return (StringUtils.hasText(headerValue) ? plain : xor)
        .resolveCsrfTokenValue(request, csrfToken);
  }
}
