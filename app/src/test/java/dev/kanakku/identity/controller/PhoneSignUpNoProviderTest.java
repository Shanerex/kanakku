package dev.kanakku.identity.controller;

import static dev.kanakku.jooq.Tables.PENDING_SIGN_UPS;
import static dev.kanakku.jooq.Tables.USERS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import dev.kanakku.support.Csrf;
import dev.kanakku.support.PostgresIntegrationTest;
import dev.kanakku.support.TestData;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.jooq.DSLContext;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import tools.jackson.databind.json.JsonMapper;

/** With no SMS provider (the default) sign-up works with or without a phone, unverified. */
@SpringBootTest
@AutoConfigureMockMvc
class PhoneSignUpNoProviderTest extends PostgresIntegrationTest {

  static final String PASSWORD = "correct horse battery";

  @Autowired MockMvc mvc;
  @Autowired DSLContext dsl;
  @Autowired JsonMapper json;

  ResultActions signUp(String email, String phone) throws Exception {
    Map<String, Object> body = new HashMap<>();
    body.put("email", email);
    body.put("password", PASSWORD);
    body.put("displayName", "Asha");
    if (phone != null) {
      body.put("phone", phone);
    }
    return mvc.perform(
        post("/api/v1/auth/sign-up")
            .with(Csrf.token(mvc))
            .header("Idempotency-Key", UUID.randomUUID().toString())
            .contentType(MediaType.APPLICATION_JSON)
            .content(json.writeValueAsString(body)));
  }

  @Test
  void signUpWithoutAPhoneWorksAsBefore() throws Exception {
    String email = TestData.email();

    signUp(email, null)
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.phone").doesNotExist())
        .andExpect(jsonPath("$.phoneVerified").value(false));

    assertThat(dsl.fetchCount(USERS, USERS.EMAIL.eq(email))).isEqualTo(1);
  }

  @Test
  void signUpWithAPhoneStoresItUnverifiedAndSendsNothing() throws Exception {
    String email = TestData.email();
    String digits = TestData.phoneDigits();

    signUp(email, digits.substring(0, 5) + " " + digits.substring(5))
        .andExpect(status().isCreated())
        .andExpect(
            jsonPath("$.phone").value(dev.kanakku.platform.phone.PhoneNumbers.mask("+91" + digits)))
        .andExpect(jsonPath("$.phoneVerified").value(false));

    var row = dsl.selectFrom(USERS).where(USERS.EMAIL.eq(email)).fetchOne();
    assertThat(row.getPhoneE164()).isEqualTo("+91" + digits);
    assertThat(row.getPhoneVerifiedAt()).isNull();
    assertThat(dsl.fetchCount(PENDING_SIGN_UPS, PENDING_SIGN_UPS.EMAIL.eq(email))).isZero();
  }

  @Test
  void anInvalidPhoneIsRefusedAndNoAccountIsCreated() throws Exception {
    String email = TestData.email();

    signUp(email, "12345")
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.type").value("urn:kanakku:problem:invalid-phone"));

    assertThat(dsl.fetchCount(USERS, USERS.EMAIL.eq(email))).isZero();
  }

  @Test
  void aBlankPhoneCountsAsNone() throws Exception {
    signUp(TestData.email(), "  ")
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.phone").doesNotExist());
  }

  @Test
  void twoAccountsMayHoldTheSameUnverifiedNumber() throws Exception {
    String phone = TestData.phoneDigits();

    signUp(TestData.email(), phone).andExpect(status().isCreated());
    signUp(TestData.email(), phone).andExpect(status().isCreated());
  }

  @Test
  void aNumberVerifiedElsewhereIsRefusedAtSignUp() throws Exception {
    String email = TestData.email();
    String phone = TestData.phone();
    dsl.insertInto(USERS)
        .set(USERS.ID, UUID.randomUUID())
        .set(USERS.EMAIL, TestData.email())
        .set(USERS.DISPLAY_NAME, "Owner")
        .set(USERS.PHONE_E164, phone)
        .set(USERS.PHONE_VERIFIED_AT, java.time.OffsetDateTime.now())
        .execute();

    signUp(email, phone)
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.type").value("urn:kanakku:problem:phone-taken"));

    assertThat(dsl.fetchCount(USERS, USERS.EMAIL.eq(email))).isZero();
  }

  @Test
  void theSignUpStepEndpointsDoNothingWithoutAProvider() throws Exception {
    mvc.perform(
            post("/api/v1/auth/sign-ups/" + UUID.randomUUID() + "/resend")
                .with(Csrf.token(mvc))
                .header("Idempotency-Key", UUID.randomUUID().toString()))
        .andExpect(status().isGone());
    mvc.perform(
            post("/api/v1/auth/sign-ups/" + UUID.randomUUID() + "/finish-unverified")
                .with(Csrf.token(mvc))
                .header("Idempotency-Key", UUID.randomUUID().toString()))
        .andExpect(status().isGone());
  }
}
