package dev.kanakku.identity.api;

import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.media.Schema.RequiredMode;
import java.util.UUID;

/** One user search hit. It carries only what a picker shows: a name and a masked email. */
public record UserMatch(
    @Schema(requiredMode = RequiredMode.REQUIRED) UUID id,
    @Schema(requiredMode = RequiredMode.REQUIRED) String displayName,
    @Schema(requiredMode = RequiredMode.REQUIRED, example = "a***@example.com")
        String maskedEmail) {}
