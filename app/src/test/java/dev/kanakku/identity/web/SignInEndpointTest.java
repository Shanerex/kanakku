package dev.kanakku.identity.web;

import static dev.kanakku.jooq.Tables.SPRING_SESSION;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import dev.kanakku.identity.internal.Profile;
import dev.kanakku.identity.internal.SignUpService;
import dev.kanakku.support.Csrf;
import dev.kanakku.support.PostgresIntegrationTest;
import dev.kanakku.support.TestData;
import dev.kanakku.support.WhoAmI;
import jakarta.servlet.http.Cookie;
import java.util.Map;
import org.jooq.DSLContext;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;
import tools.jackson.databind.json.JsonMapper;

@SpringBootTest
@AutoConfigureMockMvc
@Import(WhoAmI.class)
class SignInEndpointTest extends PostgresIntegrationTest {

  static final String PASSWORD = "correct horse battery";

  @Autowired MockMvc mvc;
  @Autowired SignUpService signUp;
  @Autowired DSLContext dsl;
  @Autowired JsonMapper json;

  ResultActions signIn(String email, String password, Cookie session) throws Exception {
    var request =
        post("/api/v1/auth/sign-in")
            .with(Csrf.token(mvc))
            .contentType(MediaType.APPLICATION_JSON)
            .content(json.writeValueAsString(Map.of("email", email, "password", password)));
    if (session != null) {
      request.cookie(session);
    }
    return mvc.perform(request);
  }

  Cookie sessionCookie(ResultActions result) {
    return result.andReturn().getResponse().getCookie("SESSION");
  }

  ResultActions whoAmI(Cookie session) throws Exception {
    var request = get("/api/v1/test/whoami");
    if (session != null) {
      request.cookie(session);
    }
    return mvc.perform(request);
  }

  @Test
  void signingInWithTheSignUpCredentialsStartsASessionCookie() throws Exception {
    String email = TestData.email();
    Profile profile = signUp.signUp(email, PASSWORD, "Asha");

    ResultActions result =
        signIn(email, PASSWORD, null)
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.id").value(profile.id().toString()))
            .andExpect(jsonPath("$.email").value(email))
            .andExpect(content().string(not(containsString(PASSWORD))))
            .andExpect(content().string(not(containsString("argon2"))));

    String setCookie =
        result.andReturn().getResponse().getHeaders("Set-Cookie").stream()
            .filter(c -> c.startsWith("SESSION="))
            .findFirst()
            .orElseThrow();
    assertThat(setCookie).contains("SESSION=").contains("HttpOnly").contains("SameSite=Lax");
    whoAmI(sessionCookie(result))
        .andExpect(status().isOk())
        .andExpect(content().string(profile.id().toString()));
  }

  @Test
  void emailMatchesInAnyLetterCase() throws Exception {
    String email = TestData.email();
    signUp.signUp(email, PASSWORD, "Asha");

    signIn(email.toUpperCase(), PASSWORD, null).andExpect(status().isOk());
  }

  @Test
  void wrongPasswordAndUnknownEmailGiveTheSameResponseByteForByte() throws Exception {
    String email = TestData.email();
    signUp.signUp(email, PASSWORD, "Asha");

    MockHttpServletResponse wrongPassword =
        signIn(email, "not the password", null)
            .andExpect(status().isUnauthorized())
            .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
            .andExpect(header().doesNotExist("Set-Cookie"))
            .andReturn()
            .getResponse();
    MockHttpServletResponse unknownEmail =
        signIn(TestData.email(), "not the password", null)
            .andExpect(status().isUnauthorized())
            .andExpect(header().doesNotExist("Set-Cookie"))
            .andReturn()
            .getResponse();

    assertThat(unknownEmail.getStatus()).isEqualTo(wrongPassword.getStatus());
    assertThat(unknownEmail.getContentAsByteArray())
        .isEqualTo(wrongPassword.getContentAsByteArray());
    assertThat(wrongPassword.getContentAsString())
        .contains("urn:kanakku:problem:invalid-credentials");
  }

  @Test
  void signInChangesTheSessionId() throws Exception {
    String email = TestData.email();
    signUp.signUp(email, PASSWORD, "Asha");
    Cookie first = sessionCookie(signIn(email, PASSWORD, null));

    Cookie second = sessionCookie(signIn(email, PASSWORD, first));

    assertThat(second.getValue()).isNotEqualTo(first.getValue());
    whoAmI(first).andExpect(status().isUnauthorized());
    whoAmI(second).andExpect(status().isOk());
  }

  @Test
  void signOutRevokesTheSessionServerSide() throws Exception {
    String email = TestData.email();
    signUp.signUp(email, PASSWORD, "Asha");
    Cookie session = sessionCookie(signIn(email, PASSWORD, null));
    whoAmI(session).andExpect(status().isOk());
    int before = dsl.fetchCount(SPRING_SESSION);

    mvc.perform(post("/api/v1/auth/sign-out").with(Csrf.token(mvc)).cookie(session))
        .andExpect(status().isNoContent());

    whoAmI(session).andExpect(status().isUnauthorized());
    assertThat(dsl.fetchCount(SPRING_SESSION)).isEqualTo(before - 1);
  }

  @Test
  void signInWithoutACsrfTokenIs403() throws Exception {
    mvc.perform(
            post("/api/v1/auth/sign-in")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("email", "a@b.co", "password", "x"))))
        .andExpect(status().isForbidden());
  }

  @Test
  void blankFieldsAre400() throws Exception {
    signIn("", "", null).andExpect(status().isBadRequest());
  }

  @Test
  void signInRotatesTheCsrfToken() throws Exception {
    String email = TestData.email();
    signUp.signUp(email, PASSWORD, "Asha");
    MvcResult csrf = mvc.perform(get("/api/v1/auth/csrf")).andReturn();
    Cookie planted = csrf.getResponse().getCookie("XSRF-TOKEN");
    String header =
        json.readTree(csrf.getResponse().getContentAsString()).get("headerName").asString();

    var response =
        mvc.perform(
                post("/api/v1/auth/sign-in")
                    .cookie(planted)
                    .header(header, planted.getValue())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(json.writeValueAsString(Map.of("email", email, "password", PASSWORD))))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse();
    Cookie session = response.getCookie("SESSION");
    Cookie rotated = response.getCookie("XSRF-TOKEN");

    assertThat(rotated).as("sign-in sets a new XSRF-TOKEN cookie").isNotNull();
    assertThat(rotated.getValue()).isNotEqualTo(planted.getValue());
    mvc.perform(
            post("/api/v1/auth/sign-out")
                .cookie(session, rotated)
                .header(header, rotated.getValue()))
        .andExpect(status().isNoContent());
  }
}
