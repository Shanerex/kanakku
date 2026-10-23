package dev.kanakku.identity.web;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Sign-in body. No password rule here: it must not reveal what a valid password looks like. */
record SignInRequest(
    @NotBlank @Size(max = 254) String email, @NotBlank @Size(max = 1024) String password) {

  @Override
  public String toString() {
    return "SignInRequest[email=" + email + ", password=****]";
  }
}
