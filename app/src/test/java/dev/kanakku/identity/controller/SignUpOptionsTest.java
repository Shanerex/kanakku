package dev.kanakku.identity.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import dev.kanakku.support.FakeSmsConfig;
import dev.kanakku.support.FakeSmsGateway;
import dev.kanakku.support.PostgresIntegrationTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

/** The public sign-up form asks whether a phone must be verified before it draws the field. */
class SignUpOptionsTest {

  @SpringBootTest
  @AutoConfigureMockMvc
  @Nested
  class WithoutAProvider extends PostgresIntegrationTest {
    @Autowired MockMvc mvc;

    @Test
    void saysPhoneVerificationIsOffToAnAnonymousCaller() throws Exception {
      mvc.perform(get("/api/v1/auth/sign-up-options"))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.phoneVerification").value(false));
    }
  }

  @SpringBootTest
  @AutoConfigureMockMvc
  @Import(FakeSmsConfig.class)
  @Nested
  class WithAProvider extends PostgresIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired FakeSmsGateway sms;

    @AfterEach
    void reset() {
      sms.reset();
    }

    @Test
    void saysPhoneVerificationIsOn() throws Exception {
      mvc.perform(get("/api/v1/auth/sign-up-options"))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.phoneVerification").value(true));
    }
  }
}
