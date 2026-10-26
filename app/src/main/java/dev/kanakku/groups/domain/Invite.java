package dev.kanakku.groups.domain;

import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.media.Schema.RequiredMode;
import java.time.OffsetDateTime;
import java.util.UUID;

/** A single-use invite bound to one guest. It never carries the token. */
public record Invite(
    @Schema(requiredMode = RequiredMode.REQUIRED) UUID id,
    @Schema(requiredMode = RequiredMode.REQUIRED, description = "The guest this invite is for")
        UUID memberId,
    @Schema(requiredMode = RequiredMode.REQUIRED) String guestName,
    @Schema(requiredMode = RequiredMode.REQUIRED) InviteStatus status,
    @Schema(requiredMode = RequiredMode.REQUIRED) OffsetDateTime expiresAt,
    @Schema(requiredMode = RequiredMode.REQUIRED) OffsetDateTime createdAt,
    @Schema(requiredMode = RequiredMode.REQUIRED, description = "Send back when revoking")
        int version) {}
