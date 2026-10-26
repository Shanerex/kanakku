package dev.kanakku.groups.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Body of {@code POST /groups/{id}/user-search}. The query may be a phone number, so {@code
 * toString} hides it.
 */
public record UserSearchRequest(@NotNull @Size(max = 100) String q) {

  @Override
  public String toString() {
    return "UserSearchRequest[q=****]";
  }
}
