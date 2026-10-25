package dev.kanakku.groups.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Body of {@code POST /groups/{id}/guests}: a name, and optionally an email. */
public record AddGuestRequest(
    @NotBlank @Size(max = 80) String name, @Email @Size(max = 254) String email) {}
