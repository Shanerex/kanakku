package dev.kanakku.identity.domain;

import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.media.Schema.RequiredMode;

/** What the public sign-up form needs to know before it is drawn. */
public record SignUpOptions(
    @Schema(
            requiredMode = RequiredMode.REQUIRED,
            description =
                "True when an SMS provider is set: the phone is required and verified by a code."
                    + " False: the phone is optional and stays unverified.")
        boolean phoneVerification) {}
