package dev.kanakku.identity.controller;

import static dev.kanakku.jooq.Tables.RATE_LIMIT_HITS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import dev.kanakku.identity.service.SmsPolicy;
import dev.kanakku.support.ApiTest;
import dev.kanakku.support.Csrf;
import dev.kanakku.support.FakeSmsConfig;
import dev.kanakku.support.FakeSmsGateway;
import dev.kanakku.support.TestData;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

/** A widened country allow-list and a low instance-wide hourly ceiling. */
@Import(FakeSmsConfig.class)
@TestPropertySource(
    properties = {
      "kanakku.sms.allowed-calling-codes=91, +1",
      "kanakku.sms.max-sends-per-hour=3",
    })
class PhoneSmsPolicyTest extends ApiTest {

  @Autowired FakeSmsGateway sms;

  @BeforeEach
  void reset() {
    sms.reset();
    dsl.deleteFrom(RATE_LIMIT_HITS).execute();
  }

  String saveNumber(String phone) throws Exception {
    Session s = newUser("Asha");
    mvc.perform(
            MockMvcRequestBuilders.put("/api/v1/me/phone")
                .with(Csrf.token(mvc))
                .cookie(s.cookie())
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(map("phone", phone, "version", 0))))
        .andExpect(status().isOk());
    sessions.put(phone, s);
    return phone;
  }

  final java.util.Map<String, Session> sessions = new java.util.HashMap<>();

  org.springframework.test.web.servlet.ResultActions send(String phone) throws Exception {
    return post(
        sessions.get(phone), "/api/v1/me/phone/verification", null, UUID.randomUUID().toString());
  }

  @Test
  void aCountryAddedToTheAllowListIsTexted() throws Exception {
    String us = saveNumber("+16502530000");

    send(us).andExpect(status().isAccepted());

    assertThat(sms.sentTo(us)).hasSize(1);
  }

  @Test
  void aCountryOffTheAllowListIsStillRefused() throws Exception {
    String uk = saveNumber("+442071838750");

    send(uk)
        .andExpect(status().isUnprocessableEntity())
        .andExpect(jsonPath("$.type").value("urn:kanakku:problem:phone-country-unsupported"));
    assertThat(sms.sent()).isEmpty();
  }

  org.springframework.test.web.servlet.ResultActions signUpFromFreshIp() throws Exception {
    return mvc.perform(
        MockMvcRequestBuilders.post("/api/v1/auth/sign-up")
            .with(Csrf.token(mvc))
            .with(freshClient())
            .header("Idempotency-Key", UUID.randomUUID().toString())
            .contentType(MediaType.APPLICATION_JSON)
            .content(
                json.writeValueAsString(
                    map(
                        "email",
                        TestData.email(),
                        "password",
                        PASSWORD,
                        "displayName",
                        "Asha",
                        "phone",
                        TestData.phone()))));
  }

  @Test
  void signUpSendsCannotUseTheShareKeptForProfileSends() throws Exception {
    ListAppender<ILoggingEvent> logs = new ListAppender<>();
    logs.start();
    Logger logger = (Logger) LoggerFactory.getLogger(SmsPolicy.class);
    logger.addAppender(logs);
    try {
      signUpFromFreshIp().andExpect(status().isAccepted());
      signUpFromFreshIp().andExpect(status().isAccepted());

      signUpFromFreshIp()
          .andExpect(status().isTooManyRequests())
          .andExpect(header().exists("Retry-After"))
          .andExpect(jsonPath("$.type").value("urn:kanakku:problem:sms-busy"));

      String phone = TestData.phone();
      send(saveNumber(phone)).andExpect(status().isAccepted());
      send(saveNumber(TestData.phone()))
          .andExpect(status().isTooManyRequests())
          .andExpect(jsonPath("$.type").value("urn:kanakku:problem:sms-busy"));
      assertThat(sms.sent()).hasSize(3);

      assertThat(logs.list)
          .filteredOn(e -> e.getLevel() == Level.WARN)
          .hasSize(2)
          .allSatisfy(
              e -> {
                String line = e.getFormattedMessage();
                assertThat(line).contains("SMS").contains("reached");
                assertThat(line).doesNotContain("+91").doesNotContain(phone);
              });
    } finally {
      logger.detachAppender(logs);
    }
  }

  @Test
  void theInstanceWideHourlyCeilingStopsFurtherSends() throws Exception {
    for (int i = 0; i < 3; i++) {
      send(saveNumber(TestData.phone())).andExpect(status().isAccepted());
    }

    send(saveNumber(TestData.phone()))
        .andExpect(status().isTooManyRequests())
        .andExpect(header().exists("Retry-After"))
        .andExpect(jsonPath("$.type").value("urn:kanakku:problem:sms-busy"));
    assertThat(sms.sent()).hasSize(3);
  }
}
