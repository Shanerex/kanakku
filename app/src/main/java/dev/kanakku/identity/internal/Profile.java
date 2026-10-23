package dev.kanakku.identity.internal;

import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.media.Schema.RequiredMode;
import java.util.UUID;

/** A user's own profile. Never carries the password hash. */
public record Profile(
    @Schema(requiredMode = RequiredMode.REQUIRED) UUID id,
    @Schema(requiredMode = RequiredMode.REQUIRED) String email,
    @Schema(requiredMode = RequiredMode.REQUIRED) boolean emailVerified,
    @Schema(requiredMode = RequiredMode.REQUIRED) String displayName,
    @Schema(requiredMode = RequiredMode.REQUIRED, example = "INR") String defaultCurrency,
    @Schema(requiredMode = RequiredMode.NOT_REQUIRED, nullable = true, example = "name@okbank")
        String upiId,
    @Schema(requiredMode = RequiredMode.REQUIRED, description = "Send back on every edit")
        int version) {}
