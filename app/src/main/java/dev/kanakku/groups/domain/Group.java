package dev.kanakku.groups.domain;

import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.media.Schema.RequiredMode;
import java.time.OffsetDateTime;
import java.util.UUID;

/** A group as the signed-in caller sees it. */
public record Group(
    @Schema(requiredMode = RequiredMode.REQUIRED) UUID id,
    @Schema(requiredMode = RequiredMode.REQUIRED) String name,
    @Schema(requiredMode = RequiredMode.REQUIRED) GroupType type,
    @Schema(requiredMode = RequiredMode.REQUIRED, example = "INR") String currency,
    @Schema(
            requiredMode = RequiredMode.NOT_REQUIRED,
            nullable = true,
            example = "color:mango",
            description = "A colour or emoji preset")
        String cover,
    @Schema(
            requiredMode = RequiredMode.NOT_REQUIRED,
            nullable = true,
            description = "Set while the group is archived (read-only except settlements)")
        OffsetDateTime archivedAt,
    @Schema(requiredMode = RequiredMode.REQUIRED, description = "Send back on every edit")
        int version,
    @Schema(requiredMode = RequiredMode.REQUIRED, description = "The caller's role in this group")
        Role myRole) {}
