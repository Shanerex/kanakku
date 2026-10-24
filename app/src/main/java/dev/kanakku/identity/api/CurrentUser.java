package dev.kanakku.identity.api;

import java.util.Optional;

/** Gives other modules the caller of the current request. */
public interface CurrentUser {

  /** The signed-in caller, or empty when the request is anonymous. */
  Optional<AuthenticatedUser> find();

  /**
   * The signed-in caller.
   *
   * @throws IllegalStateException when the request is anonymous
   */
  default AuthenticatedUser require() {
    return find().orElseThrow(() -> new IllegalStateException("no signed-in user"));
  }
}
