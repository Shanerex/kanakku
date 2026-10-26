package dev.kanakku.identity.api;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.media.Schema.RequiredMode;
import java.util.UUID;

/**
 * One user search hit. It carries only what a picker shows: a name and the one masked field the
 * query matched. {@code toString} hides both masks, so a logged hit shows no contact data.
 */
public record UserMatch(
    @Schema(requiredMode = RequiredMode.REQUIRED) UUID id,
    @Schema(requiredMode = RequiredMode.REQUIRED) String displayName,
    @Schema(requiredMode = RequiredMode.REQUIRED, description = "The field the query matched")
        MatchedBy matchedBy,
    @JsonInclude(JsonInclude.Include.NON_NULL)
        @Schema(
            requiredMode = RequiredMode.NOT_REQUIRED,
            nullable = true,
            example = "a***@example.com",
            description = "Set for a name or email match; absent for a phone match")
        String maskedEmail,
    @JsonInclude(JsonInclude.Include.NON_NULL)
        @Schema(
            requiredMode = RequiredMode.NOT_REQUIRED,
            nullable = true,
            example = "+91 ••••• ••10",
            description = "Set for a phone match only")
        String maskedPhone,
    @JsonInclude(JsonInclude.Include.NON_NULL)
        @Schema(
            requiredMode = RequiredMode.NOT_REQUIRED,
            nullable = true,
            description =
                "Set for a phone match only: false means anyone could have typed that number,"
                    + " so the picker marks it Unverified. It never blocks adding")
        Boolean phoneVerified,
    @Schema(
            requiredMode = RequiredMode.REQUIRED,
            description = "True: add directly. False: the user is invite-only, so offer Invite")
        boolean directAdd) {

  @Override
  public String toString() {
    return "UserMatch[id=" + id + ", matchedBy=" + matchedBy + ", directAdd=" + directAdd + "]";
  }
}
