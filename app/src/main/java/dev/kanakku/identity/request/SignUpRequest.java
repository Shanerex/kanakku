package dev.kanakku.identity.request;

import dev.kanakku.identity.validation.ValidPassword;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** Sign-up body. {@code toString} masks the password so it can never reach a log line. */
public record SignUpRequest(
    @NotBlank @Email @Size(max = 254) String email,
    @NotNull @ValidPassword String password,
    @NotBlank @Size(max = 80) String displayName,
    @Size(max = 40)
        @Schema(
            description =
                "Mobile number. Required when the server can send SMS codes (see sign-up-options),"
                    + " otherwise optional and left unverified.",
            example = "98765 43210")
        String phone) {

  @Override
  public String toString() {
    return "SignUpRequest[email="
        + email
        + ", password=****, displayName="
        + displayName
        + ", phone="
        + (phone == null ? "none" : "****")
        + "]";
  }
}
