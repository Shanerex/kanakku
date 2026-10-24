package dev.kanakku.support;

import dev.kanakku.identity.api.CurrentUser;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/** Test-only endpoint that echoes the signed-in user's id, to prove a session cookie works. */
@TestConfiguration
@RestController
public class WhoAmI {

  @Autowired CurrentUser currentUser;

  @GetMapping("/api/v1/test/whoami")
  String whoAmI() {
    return currentUser.require().id().toString();
  }
}
