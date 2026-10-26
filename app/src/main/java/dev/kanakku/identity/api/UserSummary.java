package dev.kanakku.identity.api;

import java.util.UUID;

/** A user's public face inside a group: an id and a display name, nothing else. */
public record UserSummary(UUID id, String displayName) {}
