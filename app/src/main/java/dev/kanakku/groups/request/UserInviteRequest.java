package dev.kanakku.groups.request;

import dev.kanakku.groups.validation.ValidExpiryDays;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;

/** Body of {@code POST /groups/{id}/user-invites}. */
public record UserInviteRequest(
    @NotNull @Schema(description = "A registered user found by search") UUID userId,
    @ValidExpiryDays @Schema(description = "1, 7 or 30; 7 when left out", example = "7")
        Integer expiryDays) {}
