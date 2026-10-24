package dev.kanakku.platform.problem;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

/**
 * Documents the RFC 9457 body every error carries ({@code application/problem+json}). The server
 * builds the real body from Spring's {@code ProblemDetail}; this type exists for the OpenAPI
 * document and the generated clients.
 */
@Schema(name = "Problem", description = "RFC 9457 problem details")
public record ProblemResponse(
    @Schema(
            requiredMode = Schema.RequiredMode.REQUIRED,
            example = "urn:kanakku:problem:email-taken")
        String type,
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String title,
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) int status,
    String detail,
    String instance,
    @Schema(description = "Field errors on a 400; never contains the rejected value")
        List<FieldError> errors,
    @Schema(description = "Seconds until a throttled sign-in may be retried (429 only)")
        Integer retryAfterSeconds) {

  /** One rejected field. */
  public record FieldError(String field, String message) {}
}
