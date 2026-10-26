package dev.kanakku.identity.domain;

import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.media.Schema.RequiredMode;
import java.time.OffsetDateTime;
import java.util.UUID;

/** A sign-up waiting for the SMS code. No account exists yet. */
public record PendingSignUp(
    @Schema(requiredMode = RequiredMode.REQUIRED, description = "Use it for verify and resend")
        UUID id,
    @Schema(requiredMode = RequiredMode.REQUIRED, example = "+91 ••••• ••10") String maskedPhone,
    @Schema(requiredMode = RequiredMode.REQUIRED) OffsetDateTime codeExpiresAt,
    @Schema(
            requiredMode = RequiredMode.REQUIRED,
            description = "False when the provider failed to take the text")
        boolean smsSent) {}
