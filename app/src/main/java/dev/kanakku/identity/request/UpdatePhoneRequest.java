package dev.kanakku.identity.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

/** Body of {@code PUT /me/phone}. {@code toString} hides the number so it never reaches a log. */
public record UpdatePhoneRequest(
    @NotBlank @Size(max = 40) String phone, @NotNull @PositiveOrZero Integer version) {

  @Override
  public String toString() {
    return "UpdatePhoneRequest[phone=****, version=" + version + "]";
  }
}
