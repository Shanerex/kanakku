package dev.kanakku.groups.domain;

import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.media.Schema.RequiredMode;

/** All a token holder learns before claiming: the group's name and the guest's name. */
public record InvitePreview(
    @Schema(requiredMode = RequiredMode.REQUIRED) String groupName,
    @Schema(requiredMode = RequiredMode.REQUIRED) String guestName) {}
