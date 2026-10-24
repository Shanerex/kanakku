package dev.kanakku.identity.api;

import java.io.Serializable;
import java.security.Principal;
import java.util.UUID;

/**
 * The signed-in caller. Its id is the only identity later modules should scope reads by. The
 * principal name is the id, which is also what the session store indexes by.
 */
public record AuthenticatedUser(UUID id) implements Principal, Serializable {

  @Override
  public String getName() {
    return id.toString();
  }
}
