package dev.kanakku.identity.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import dev.kanakku.identity.service.SignUpService;
import dev.kanakku.support.Csrf;
import dev.kanakku.support.PostgresIntegrationTest;
import dev.kanakku.support.TestData;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.json.JsonMapper;

/** By default both cookies are Secure, even when the request reached the app over plain HTTP. */
@SpringBootTest
@AutoConfigureMockMvc
class SecureCookiesTest extends PostgresIntegrationTest {

  @Autowired MockMvc mvc;
  @Autowired SignUpService signUp;
  @Autowired JsonMapper json;

  /** The Set-Cookie header of one cookie, found by its name. */
  static String cookie(List<String> setCookies, String name) {
    return setCookies.stream()
        .filter(c -> c.startsWith(name + "="))
        .findFirst()
        .orElseThrow(() -> new AssertionError(name + " cookie not set in " + setCookies));
  }

  List<String> signInCookies() throws Exception {
    String email = TestData.email();
    signUp.signUp(email, "correct horse battery", "Asha");
    return mvc.perform(
            post("/api/v1/auth/sign-in")
                .with(Csrf.token(mvc))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    json.writeValueAsString(
                        Map.of("email", email, "password", "correct horse battery"))))
        .andExpect(status().isOk())
        .andReturn()
        .getResponse()
        .getHeaders(HttpHeaders.SET_COOKIE);
  }

  boolean secure() {
    return true;
  }

  @Test
  void theSessionCookieHasTheExpectedSecureFlag() throws Exception {
    String session = cookie(signInCookies(), "SESSION");

    assertThat(session.contains("; Secure")).isEqualTo(secure());
    assertThat(session).contains("HttpOnly").contains("SameSite=Lax");
  }

  @Test
  void theCsrfCookieHasTheExpectedSecureFlag() throws Exception {
    List<String> cookies =
        mvc.perform(get("/api/v1/auth/csrf")).andReturn().getResponse().getHeaders("Set-Cookie");

    String xsrf = cookie(cookies, "XSRF-TOKEN");

    assertThat(xsrf.contains("; Secure")).isEqualTo(secure());
  }
}
