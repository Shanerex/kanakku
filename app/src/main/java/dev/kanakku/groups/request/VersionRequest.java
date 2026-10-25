package dev.kanakku.groups.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

/** Body of an edit that only needs the version the caller last saw. */
public record VersionRequest(@NotNull @PositiveOrZero Integer version) {}
