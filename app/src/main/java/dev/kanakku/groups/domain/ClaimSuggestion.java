package dev.kanakku.groups.domain;

import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.media.Schema.RequiredMode;
import java.time.OffsetDateTime;
import java.util.UUID;

/** "You might be {@code guestName} in {@code groupName}": a guest the user's number matches. */
public record ClaimSuggestion(
    @Schema(requiredMode = RequiredMode.REQUIRED) UUID id,
    @Schema(requiredMode = RequiredMode.REQUIRED) UUID groupId,
    @Schema(requiredMode = RequiredMode.REQUIRED) String groupName,
    @Schema(requiredMode = RequiredMode.REQUIRED, description = "The guest's member row")
        UUID guestMemberId,
    @Schema(requiredMode = RequiredMode.REQUIRED) String guestName,
    @Schema(requiredMode = RequiredMode.REQUIRED) ClaimSuggestionStatus status,
    @Schema(requiredMode = RequiredMode.REQUIRED, description = "Send back to confirm or decline")
        int version,
    @Schema(requiredMode = RequiredMode.REQUIRED) OffsetDateTime createdAt) {}
