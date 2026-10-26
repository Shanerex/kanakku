package dev.kanakku.identity.domain;

import dev.kanakku.platform.phone.PhoneNumbers;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.media.Schema.RequiredMode;
import java.util.UUID;

/** A user's own profile. Never carries the password hash. */
public record Profile(
    @Schema(requiredMode = RequiredMode.REQUIRED) UUID id,
    @Schema(requiredMode = RequiredMode.REQUIRED) String email,
    @Schema(requiredMode = RequiredMode.REQUIRED) boolean emailVerified,
    @Schema(requiredMode = RequiredMode.REQUIRED) String displayName,
    @Schema(requiredMode = RequiredMode.REQUIRED, example = "INR") String defaultCurrency,
    @Schema(requiredMode = RequiredMode.NOT_REQUIRED, nullable = true, example = "name@okbank")
        String upiId,
    @Schema(
            requiredMode = RequiredMode.REQUIRED,
            description = "Whether a group Owner may add me directly or I join by invite only")
        DirectAdd directAdd,
    @Schema(
            requiredMode = RequiredMode.NOT_REQUIRED,
            nullable = true,
            description = "My mobile number in E.164; only ever shown to me",
            example = "+919876543210")
        String phone,
    @Schema(
            requiredMode = RequiredMode.REQUIRED,
            description = "True once an SMS code confirmed the number")
        boolean phoneVerified,
    @Schema(requiredMode = RequiredMode.REQUIRED, description = "Send back on every edit")
        int version) {

  /**
   * The same profile with the number masked ({@code +91 ••••• ••10}), for an answer that is stored
   * for replay: the full number must not sit in the idempotency table.
   */
  public Profile withMaskedPhone() {
    if (phone == null) {
      return this;
    }
    return new Profile(
        id,
        email,
        emailVerified,
        displayName,
        defaultCurrency,
        upiId,
        directAdd,
        PhoneNumbers.mask(phone),
        phoneVerified,
        version);
  }

  /** Hides the number so a logged profile never carries it. */
  @Override
  public String toString() {
    return "Profile[id=" + id + ", phone=" + (phone == null ? "none" : "****") + "]";
  }
}
