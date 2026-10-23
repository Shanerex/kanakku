package dev.kanakku.identity.web;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** Sign-up body. {@code toString} masks the password so it can never reach a log line. */
record SignUpRequest(
    @NotBlank @Email @Size(max = 254) String email,
    @NotNull @ValidPassword String password,
    @NotBlank @Size(max = 80) String displayName) {

  @Override
  public String toString() {
    return "SignUpRequest[email=" + email + ", password=****, displayName=" + displayName + "]";
  }
}
