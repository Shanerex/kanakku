package dev.kanakku.groups.request;

import jakarta.validation.constraints.NotNull;
import java.util.UUID;

/** Body of {@code POST /groups/{id}/members}: the registered user to add directly. */
public record AddMemberRequest(@NotNull UUID userId) {}
