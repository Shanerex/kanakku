package dev.kanakku.identity.controller;

import dev.kanakku.identity.config.SpaCsrfTokenRequestHandler;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.servlet.http.HttpServletRequest;
import java.util.function.Supplier;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
public class CsrfController {

  /** The header name and token the client sends back on every unsafe request. */
  record CsrfTokenResponse(
      @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String headerName,
      @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String token) {}

  /** Also sets the XSRF-TOKEN cookie; the client sends the token back in the named header. */
  @Operation(operationId = "csrf", summary = "Get the CSRF token")
  @GetMapping(value = "/csrf", produces = MediaType.APPLICATION_JSON_VALUE)
  ResponseEntity<CsrfTokenResponse> csrf(HttpServletRequest request) {
    @SuppressWarnings("unchecked")
    Supplier<CsrfToken> supplier =
        (Supplier<CsrfToken>) request.getAttribute(SpaCsrfTokenRequestHandler.RAW_TOKEN_SUPPLIER);
    CsrfToken token = supplier.get();
    return ResponseEntity.ok()
        .cacheControl(CacheControl.noStore())
        .body(new CsrfTokenResponse(token.getHeaderName(), token.getToken()));
  }
}
