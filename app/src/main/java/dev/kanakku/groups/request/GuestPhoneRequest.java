package dev.kanakku.groups.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

/**
 * Body of {@code PUT /groups/{id}/members/{memberId}/guest-phone}: the new number, or null or blank
 * to clear it, and the member's version. {@code toString} hides the number.
 */
public record GuestPhoneRequest(
    @Size(max = 40) String phone, @NotNull @PositiveOrZero Integer version) {

  @Override
  public String toString() {
    return "GuestPhoneRequest[phone="
        + (phone == null ? "none" : "****")
        + ", version="
        + version
        + "]";
  }
}
