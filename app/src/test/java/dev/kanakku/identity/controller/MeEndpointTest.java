package dev.kanakku.identity.controller;

import static dev.kanakku.jooq.Tables.USERS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import dev.kanakku.identity.domain.Profile;
import dev.kanakku.identity.service.SignUpService;
import dev.kanakku.support.Csrf;
import dev.kanakku.support.PostgresIntegrationTest;
import dev.kanakku.support.TestData;
import jakarta.servlet.http.Cookie;
import java.util.HashMap;
import java.util.Map;
import org.jooq.DSLContext;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import tools.jackson.databind.json.JsonMapper;

@SpringBootTest
@AutoConfigureMockMvc
class MeEndpointTest extends PostgresIntegrationTest {

  static final String PASSWORD = "correct horse battery";

  @Autowired MockMvc mvc;
  @Autowired SignUpService signUp;
  @Autowired DSLContext dsl;
  @Autowired JsonMapper json;

  record Session(Profile profile, Cookie cookie) {}

  Session newUser(String name) throws Exception {
    String email = TestData.email();
    Profile profile = signUp.signUp(email, PASSWORD, name);
    Cookie cookie =
        mvc.perform(
                post("/api/v1/auth/sign-in")
                    .with(Csrf.token(mvc))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(json.writeValueAsString(Map.of("email", email, "password", PASSWORD))))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getCookie("SESSION");
    return new Session(profile, cookie);
  }

  ResultActions update(Session who, Map<String, Object> body) throws Exception {
    return mvc.perform(
        put("/api/v1/me")
            .with(Csrf.token(mvc))
            .cookie(who.cookie())
            .contentType(MediaType.APPLICATION_JSON)
            .content(json.writeValueAsString(body)));
  }

  Map<String, Object> body(String name, String currency, String upi, Integer version) {
    Map<String, Object> body = new HashMap<>();
    body.put("displayName", name);
    body.put("defaultCurrency", currency);
    body.put("upiId", upi);
    body.put("version", version);
    return body;
  }

  @Test
  void readsOnlyTheCallersOwnProfile() throws Exception {
    Session a = newUser("Asha");
    newUser("Bala");

    mvc.perform(get("/api/v1/me").cookie(a.cookie()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.id").value(a.profile().id().toString()))
        .andExpect(jsonPath("$.email").value(a.profile().email()))
        .andExpect(jsonPath("$.displayName").value("Asha"))
        .andExpect(jsonPath("$.defaultCurrency").value("INR"))
        .andExpect(jsonPath("$.emailVerified").value(false))
        .andExpect(jsonPath("$.version").value(0))
        .andExpect(jsonPath("$.passwordHash").doesNotExist())
        .andExpect(jsonPath("$.password").doesNotExist());
  }

  @Test
  void requiresASession() throws Exception {
    mvc.perform(get("/api/v1/me")).andExpect(status().isUnauthorized());
  }

  @Test
  void updatesNameCurrencyAndUpiAndReadsThemBack() throws Exception {
    Session a = newUser("Asha");

    update(a, body("Asha K", "USD", "asha@okbank", 0))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.displayName").value("Asha K"))
        .andExpect(jsonPath("$.defaultCurrency").value("USD"))
        .andExpect(jsonPath("$.upiId").value("asha@okbank"))
        .andExpect(jsonPath("$.version").value(1));

    mvc.perform(get("/api/v1/me").cookie(a.cookie()))
        .andExpect(jsonPath("$.displayName").value("Asha K"))
        .andExpect(jsonPath("$.defaultCurrency").value("USD"))
        .andExpect(jsonPath("$.upiId").value("asha@okbank"))
        .andExpect(jsonPath("$.version").value(1));
  }

  @Test
  void aStaleVersionIs409AndChangesNothing() throws Exception {
    Session a = newUser("Asha");
    update(a, body("First", "INR", null, 0)).andExpect(status().isOk());

    update(a, body("Stale", "INR", null, 0))
        .andExpect(status().isConflict())
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
        .andExpect(jsonPath("$.type").value("urn:kanakku:problem:version-conflict"));

    assertThat(
            dsl.select(USERS.DISPLAY_NAME)
                .from(USERS)
                .where(USERS.ID.eq(a.profile().id()))
                .fetchOne(USERS.DISPLAY_NAME))
        .isEqualTo("First");
  }

  @Test
  void invalidCurrencyCodesAreRefused() throws Exception {
    Session a = newUser("Asha");
    for (String bad : new String[] {"ZZZ", "inr", "IN", "RUPEES", ""}) {
      update(a, body("Asha", bad, null, 0))
          .andExpect(status().isBadRequest())
          .andExpect(jsonPath("$.errors[0].field").value("defaultCurrency"));
    }
  }

  @Test
  void upiMustLookLikeHandleAtPspButMayBeEmpty() throws Exception {
    Session a = newUser("Asha");
    for (String bad : new String[] {"no-at-sign", "a@b", "@okbank", "x y@okbank", "name@ok1bank"}) {
      update(a, body("Asha", "INR", bad, 0))
          .andExpect(status().isBadRequest())
          .andExpect(jsonPath("$.errors[0].field").value("upiId"));
    }
    update(a, body("Asha", "INR", "asha.k-1@okbank", 0))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.upiId").value("asha.k-1@okbank"));
    update(a, body("Asha", "INR", "", 1))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.upiId").doesNotExist());
    update(a, body("Asha", "INR", null, 2)).andExpect(status().isOk());
  }

  @Test
  void userACannotTouchUserBEvenByNamingBsId() throws Exception {
    Session a = newUser("Asha");
    Session b = newUser("Bala");
    Map<String, Object> sneaky = body("Hacked", "USD", "evil@okbank", 0);
    sneaky.put("id", b.profile().id().toString());
    sneaky.put("userId", b.profile().id().toString());
    sneaky.put("email", "hacked@example.com");

    update(a, sneaky)
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.id").value(a.profile().id().toString()))
        .andExpect(jsonPath("$.email").value(a.profile().email()));

    mvc.perform(get("/api/v1/me").cookie(b.cookie()))
        .andExpect(jsonPath("$.displayName").value("Bala"))
        .andExpect(jsonPath("$.defaultCurrency").value("INR"))
        .andExpect(jsonPath("$.version").value(0));
    assertThat(
            dsl.select(USERS.EMAIL)
                .from(USERS)
                .where(USERS.ID.eq(b.profile().id()))
                .fetchOne(USERS.EMAIL))
        .isEqualTo(b.profile().email());
  }

  @Test
  void blankNameAndMissingVersionAreRefused() throws Exception {
    Session a = newUser("Asha");
    update(a, body("  ", "INR", null, 0)).andExpect(status().isBadRequest());
    update(a, body("Asha", "INR", null, null)).andExpect(status().isBadRequest());
  }

  @Test
  void updateWithoutACsrfTokenIs403() throws Exception {
    Session a = newUser("Asha");
    mvc.perform(
            put("/api/v1/me")
                .cookie(a.cookie())
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(body("Asha", "INR", null, 0))))
        .andExpect(status().isForbidden());
  }

  @Test
  void directAddDefaultsToAllowAndCanBeToggledToInviteOnlyAndBack() throws Exception {
    Session a = newUser("Asha");
    mvc.perform(get("/api/v1/me").cookie(a.cookie()))
        .andExpect(jsonPath("$.directAdd").value("ALLOW"));

    Map<String, Object> toInviteOnly = body("Asha", "INR", null, 0);
    toInviteOnly.put("directAdd", "INVITE_ONLY");
    update(a, toInviteOnly)
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.directAdd").value("INVITE_ONLY"))
        .andExpect(jsonPath("$.version").value(1));
    mvc.perform(get("/api/v1/me").cookie(a.cookie()))
        .andExpect(jsonPath("$.directAdd").value("INVITE_ONLY"));

    Map<String, Object> back = body("Asha", "INR", null, 1);
    back.put("directAdd", "ALLOW");
    update(a, back).andExpect(status().isOk()).andExpect(jsonPath("$.directAdd").value("ALLOW"));
  }

  @Test
  void leavingDirectAddOutOfTheBodyKeepsTheStoredChoice() throws Exception {
    Session a = newUser("Asha");
    Map<String, Object> toInviteOnly = body("Asha", "INR", null, 0);
    toInviteOnly.put("directAdd", "INVITE_ONLY");
    update(a, toInviteOnly).andExpect(status().isOk());

    update(a, body("Asha K", "INR", null, 1))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.directAdd").value("INVITE_ONLY"));
  }

  @Test
  void anUnknownDirectAddValueIsRefused() throws Exception {
    Session a = newUser("Asha");
    Map<String, Object> bad = body("Asha", "INR", null, 0);
    bad.put("directAdd", "EVERYONE");
    update(a, bad).andExpect(status().isBadRequest());
  }

  @Test
  void aStaleVersionDoesNotChangeDirectAdd() throws Exception {
    Session a = newUser("Asha");
    update(a, body("First", "INR", null, 0)).andExpect(status().isOk());
    Map<String, Object> stale = body("Stale", "INR", null, 0);
    stale.put("directAdd", "INVITE_ONLY");
    update(a, stale).andExpect(status().isConflict());
    mvc.perform(get("/api/v1/me").cookie(a.cookie()))
        .andExpect(jsonPath("$.directAdd").value("ALLOW"));
  }
}
