package dev.kanakku.identity.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import dev.kanakku.identity.exception.EmailTakenException;
import dev.kanakku.identity.exception.InvalidCredentialsException;
import dev.kanakku.identity.exception.TooManyAttemptsException;
import dev.kanakku.identity.exception.VersionConflictException;
import dev.kanakku.support.PostgresIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Import;
import org.springframework.core.Ordered;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.method.ControllerAdviceBean;

/**
 * Platform's catch-all advice and identity's own advice are both present. Identity's must win by an
 * explicit order, never by the accident of bean registration order.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(AdviceOrderTest.Throws.class)
class AdviceOrderTest extends PostgresIntegrationTest {

  @TestConfiguration
  @RestController
  static class Throws {
    @GetMapping("/api/v1/test/throws/{kind}")
    String throwIt(@PathVariable("kind") String kind) {
      switch (kind) {
        case "invalid-credentials" -> throw new InvalidCredentialsException();
        case "too-many" -> throw new TooManyAttemptsException(42);
        case "email-taken" -> throw new EmailTakenException();
        case "version-conflict" -> throw new VersionConflictException();
        default -> throw new IllegalStateException("boom");
      }
    }
  }

  @Autowired MockMvc mvc;
  @Autowired ApplicationContext context;

  @Test
  void identityFailuresAreNotSwallowedByTheCatchAll() throws Exception {
    mvc.perform(get("/api/v1/test/throws/invalid-credentials").with(user("u")))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.type").value("urn:kanakku:problem:invalid-credentials"));
    mvc.perform(get("/api/v1/test/throws/too-many").with(user("u")))
        .andExpect(status().isTooManyRequests())
        .andExpect(header().string("Retry-After", "42"))
        .andExpect(jsonPath("$.type").value("urn:kanakku:problem:too-many-attempts"));
    mvc.perform(get("/api/v1/test/throws/email-taken").with(user("u")))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.type").value("urn:kanakku:problem:email-taken"));
    mvc.perform(get("/api/v1/test/throws/version-conflict").with(user("u")))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.type").value("urn:kanakku:problem:version-conflict"));
  }

  @Test
  void anythingElseStillGetsTheGeneric500() throws Exception {
    mvc.perform(get("/api/v1/test/throws/other").with(user("u")))
        .andExpect(status().isInternalServerError())
        .andExpect(jsonPath("$.type").value("urn:kanakku:problem:internal"));
  }

  @Test
  void theOrderIsExplicit() {
    int identity = orderOf("IdentityProblemAdvice");
    int platform = orderOf("ProblemAdvice");

    assertThat(platform).isEqualTo(Ordered.LOWEST_PRECEDENCE);
    assertThat(identity).isLessThan(platform);
  }

  private int orderOf(String simpleClassName) {
    return ControllerAdviceBean.findAnnotatedBeans(context).stream()
        .filter(b -> b.getBeanType() != null)
        .filter(b -> b.getBeanType().getSimpleName().equals(simpleClassName))
        .findFirst()
        .orElseThrow()
        .getOrder();
  }
}
