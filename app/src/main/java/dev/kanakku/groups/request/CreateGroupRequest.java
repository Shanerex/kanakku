package dev.kanakku.groups.request;

import dev.kanakku.groups.domain.GroupType;
import dev.kanakku.groups.validation.ValidCover;
import dev.kanakku.platform.validation.ValidCurrency;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** Body of {@code POST /groups}. The creator is always the signed-in caller. */
public record CreateGroupRequest(
    @NotBlank @Size(max = 60) String name,
    @NotNull GroupType type,
    @NotNull @ValidCurrency @Schema(example = "INR") String currency,
    @ValidCover @Schema(example = "color:mango", nullable = true) String cover) {}
