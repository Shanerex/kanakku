package dev.kanakku.groups.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Body of invite preview and claim. The token travels in the body, never in a URL. */
public record InviteTokenRequest(@NotBlank @Size(max = 200) String token) {}
