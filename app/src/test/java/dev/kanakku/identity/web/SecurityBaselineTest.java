package dev.kanakku.identity.web;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import dev.kanakku.support.PostgresIntegrationTest;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

@SpringBootTest(properties = "kanakku.cors.allowed-origins=http://localhost:3000")
@AutoConfigureMockMvc
@Import(SecurityBaselineTest.EchoConfig.class)
class SecurityBaselineTest extends PostgresIntegrationTest {

  @TestConfiguration
  @RestController
  static class EchoConfig {
    @PostMapping("/api/v1/test/echo")
    String post() {
      return "posted";
    }

    @GetMapping("/api/v1/test/echo")
    String get() {
      return "got";
    }
  }

  @Autowired MockMvc mvc;
  @Autowired JsonMapper json;

  @Test
  void csrfEndpointReturnsTheTokenAndSetsTheCookie() throws Exception {
    mvc.perform(get("/api/v1/auth/csrf"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.headerName").value("X-XSRF-TOKEN"))
        .andExpect(jsonPath("$.token").isNotEmpty())
        .andExpect(cookie().exists("XSRF-TOKEN"))
        .andExpect(cookie().httpOnly("XSRF-TOKEN", false));
  }

  @Test
  void postWithoutATokenIsRefusedAsAProblem() throws Exception {
    mvc.perform(post("/api/v1/test/echo").with(user("u")))
        .andExpect(status().isForbidden())
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
        .andExpect(jsonPath("$.status").value(403))
        .andExpect(jsonPath("$.type").value("urn:kanakku:problem:csrf-token-invalid"));
  }

  @Test
  void postWithAWrongTokenIsRefused() throws Exception {
    MvcResult csrf = mvc.perform(get("/api/v1/auth/csrf")).andReturn();
    mvc.perform(
            post("/api/v1/test/echo")
                .with(user("u"))
                .cookie(csrf.getResponse().getCookie("XSRF-TOKEN"))
                .header("X-XSRF-TOKEN", "not-the-token"))
        .andExpect(status().isForbidden());
  }

  @Test
  void postWithTheTokenIsAccepted() throws Exception {
    MvcResult csrf = mvc.perform(get("/api/v1/auth/csrf")).andReturn();
    JsonNode body = json.readTree(csrf.getResponse().getContentAsString());
    Cookie cookie = csrf.getResponse().getCookie("XSRF-TOKEN");

    mvc.perform(
            post("/api/v1/test/echo")
                .with(user("u"))
                .cookie(cookie)
                .header(body.get("headerName").asString(), body.get("token").asString()))
        .andExpect(status().isOk())
        .andExpect(content().string("posted"));
  }

  @Test
  void unauthenticatedApiCallIsAProblem401() throws Exception {
    mvc.perform(get("/api/v1/test/echo"))
        .andExpect(status().isUnauthorized())
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
        .andExpect(jsonPath("$.type").value("urn:kanakku:problem:unauthenticated"))
        .andExpect(header().string("WWW-Authenticate", not(containsString("Basic"))));
  }

  @Test
  void corsAllowsCredentialsFromTheConfiguredOrigin() throws Exception {
    mvc.perform(get("/api/v1/auth/csrf").header("Origin", "http://localhost:3000"))
        .andExpect(header().string("Access-Control-Allow-Origin", "http://localhost:3000"))
        .andExpect(header().string("Access-Control-Allow-Credentials", "true"));
  }

  @Test
  void healthStaysPublic() throws Exception {
    mvc.perform(get("/actuator/health/liveness")).andExpect(status().isOk());
  }
}
