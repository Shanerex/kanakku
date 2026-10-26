package dev.kanakku.identity.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import dev.kanakku.support.ApiTest;
import dev.kanakku.support.Csrf;
import dev.kanakku.support.TestData;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

/** With no SMS provider set (the default) no code is sent and the profile stays unverified. */
class PhoneVerificationNoProviderTest extends ApiTest {

  @Test
  void noCodeIsSentAndTheProfileShowsUnverified() throws Exception {
    Session s = newUser("Asha");
    mvc.perform(
            MockMvcRequestBuilders.put("/api/v1/me/phone")
                .with(freshClient())
                .with(Csrf.token(mvc))
                .cookie(s.cookie())
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(map("phone", TestData.phone(), "version", 0))))
        .andExpect(status().isOk());

    post(s, "/api/v1/me/phone/verification", null)
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.type").value("urn:kanakku:problem:sms-not-configured"));

    get(s, "/api/v1/me")
        .andExpect(jsonPath("$.phone").exists())
        .andExpect(jsonPath("$.phoneVerified").value(false));
    assertThat(
            dsl.fetchCount(
                dev.kanakku.jooq.Tables.PHONE_CHALLENGES,
                dev.kanakku.jooq.Tables.PHONE_CHALLENGES.USER_ID.eq(s.id())))
        .isZero();
  }
}
