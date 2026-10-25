package dev.kanakku.ledger.api;

import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.media.Schema.RequiredMode;

/** An amount in minor units in one currency. */
public record MoneyBalance(
    @Schema(requiredMode = RequiredMode.REQUIRED, example = "INR") String currency,
    @Schema(
            requiredMode = RequiredMode.REQUIRED,
            description = "Minor units; positive means owed to the member, negative means owed by")
        long amountMinor) {}
