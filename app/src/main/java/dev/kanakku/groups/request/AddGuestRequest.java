package dev.kanakku.groups.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Body of {@code POST /groups/{id}/guests}: a name, and optionally an email and a mobile number
 * that lets the guest be matched to an account later. {@code toString} hides the number.
 */
public record AddGuestRequest(
    @NotBlank @Size(max = 80) String name,
    @Email @Size(max = 254) String email,
    @Size(max = 40) String phone) {

  @Override
  public String toString() {
    return "AddGuestRequest[name="
        + name
        + ", email="
        + (email == null ? "none" : "****")
        + ", phone="
        + (phone == null ? "none" : "****")
        + "]";
  }
}
