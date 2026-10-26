package dev.kanakku.ledger.api;

import java.util.UUID;

/** A member row's balance in one currency. */
public record MemberBalance(UUID memberId, String currency, long amountMinor) {}
