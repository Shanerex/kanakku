package dev.kanakku.groups.domain;

import dev.kanakku.ledger.api.MoneyBalance;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.media.Schema.RequiredMode;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/** A member of a group: a registered user or a guest. */
public record Member(
    @Schema(
            requiredMode = RequiredMode.REQUIRED,
            description = "The member row; ledger lines name it")
        UUID id,
    @Schema(
            requiredMode = RequiredMode.NOT_REQUIRED,
            nullable = true,
            description = "The account behind this member; absent for a guest")
        UUID userId,
    @Schema(requiredMode = RequiredMode.REQUIRED) String displayName,
    @Schema(requiredMode = RequiredMode.REQUIRED) Role role,
    @Schema(requiredMode = RequiredMode.REQUIRED, description = "True until an account claims it")
        boolean guest,
    @Schema(
            requiredMode = RequiredMode.NOT_REQUIRED,
            nullable = true,
            description = "A guest's email, shown to the Owner only")
        String guestEmail,
    @Schema(requiredMode = RequiredMode.REQUIRED) OffsetDateTime joinedAt,
    @Schema(
            requiredMode = RequiredMode.REQUIRED,
            description = "Non-zero balances per currency; empty when settled")
        List<MoneyBalance> balances,
    @Schema(requiredMode = RequiredMode.REQUIRED, description = "Send back when removing")
        int version) {}
