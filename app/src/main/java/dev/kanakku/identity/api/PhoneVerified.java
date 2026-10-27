package dev.kanakku.identity.api;

import java.util.UUID;

/**
 * Published, in the same transaction, when a user confirms an SMS code for their number. It names
 * the user only: a listener that needs the number asks {@link UserDirectory#verifiedPhone}.
 */
public record PhoneVerified(UUID userId) {}
