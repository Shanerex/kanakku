package dev.kanakku.identity.domain;

import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.media.Schema.RequiredMode;
import java.time.OffsetDateTime;

/** A code was made for the caller's number; {@code smsSent} is false when the provider failed. */
public record CodeSent(
    @Schema(requiredMode = RequiredMode.REQUIRED) OffsetDateTime codeExpiresAt,
    @Schema(
            requiredMode = RequiredMode.REQUIRED,
            description = "False when the SMS provider did not accept the message")
        boolean smsSent) {}
