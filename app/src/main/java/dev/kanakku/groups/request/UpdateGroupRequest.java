package dev.kanakku.groups.request;

import dev.kanakku.groups.domain.GroupType;
import dev.kanakku.groups.validation.ValidCover;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

/** Body of {@code PATCH /groups/{id}}: a field left out stays as it is. */
public record UpdateGroupRequest(
    @Size(max = 60) @Pattern(regexp = ".*\\S.*", message = "must not be blank") String name,
    GroupType type,
    @ValidCover @Schema(example = "color:mango", nullable = true) String cover,
    @NotNull @PositiveOrZero Integer version) {}
