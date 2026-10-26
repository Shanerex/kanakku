package dev.kanakku.groups.request;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.UUID;

/** Body of a write-off: the members who share the balance between them, equally. */
public record WriteOffRequest(@NotEmpty @Size(max = 50) List<@NotNull UUID> absorberMemberIds) {}
