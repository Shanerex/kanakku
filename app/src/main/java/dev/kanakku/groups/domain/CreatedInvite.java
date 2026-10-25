package dev.kanakku.groups.domain;

import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.media.Schema.RequiredMode;

/** A new invite and its token, which is shown once and never stored in the clear. */
public record CreatedInvite(
    @Schema(requiredMode = RequiredMode.REQUIRED) Invite invite,
    @Schema(
            requiredMode = RequiredMode.REQUIRED,
            description = "Put it in the link fragment: /invite#<token>")
        String token) {}
