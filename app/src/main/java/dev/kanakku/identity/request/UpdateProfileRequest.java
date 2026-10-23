package dev.kanakku.identity.request;

import dev.kanakku.identity.validation.ValidCurrency;
import dev.kanakku.identity.validation.ValidUpiId;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

/** Body of {@code PUT /me}. It names no user: the caller is always the signed-in user. */
public record UpdateProfileRequest(
    @NotBlank @Size(max = 80) String displayName,
    @NotNull @ValidCurrency String defaultCurrency,
    @ValidUpiId @Size(max = 400) String upiId,
    @NotNull @PositiveOrZero Integer version) {}
