package dev.kanakku.identity.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

/** Body of a code confirmation. {@code toString} hides the code so it never reaches a log. */
public record ConfirmPhoneCodeRequest(@NotNull @Pattern(regexp = "\\d{6}") String code) {

  @Override
  public String toString() {
    return "ConfirmPhoneCodeRequest[code=****]";
  }
}
