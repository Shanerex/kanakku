package dev.kanakku.groups.domain;

import dev.kanakku.ledger.api.MoneyBalance;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.media.Schema.RequiredMode;
import java.util.List;

/** What a write-off cleared, per currency (empty when the member was already at zero). */
public record WriteOffResult(
    @Schema(requiredMode = RequiredMode.REQUIRED) List<MoneyBalance> writtenOff) {}
