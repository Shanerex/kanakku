package dev.kanakku.identity.controller;

import static dev.kanakku.jooq.Tables.PHONE_CHALLENGES;
import static dev.kanakku.jooq.Tables.RATE_LIMIT_HITS;
import static dev.kanakku.jooq.Tables.USERS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import dev.kanakku.support.ApiTest;
import dev.kanakku.support.Csrf;
import dev.kanakku.support.FakeSmsConfig;
import dev.kanakku.support.FakeSmsGateway;
import dev.kanakku.support.TestClockConfig;
import dev.kanakku.support.TestData;
import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

@Import({FakeSmsConfig.class, TestClockConfig.class})
class PhoneVerificationTest extends ApiTest {

  @Autowired FakeSmsGateway sms;

  @BeforeEach
  void reset() {
    sms.reset();
    TestClockConfig.reset();
  }

  /** A signed-in user who has saved a fresh number (unverified). */
  record Holder(Session session, String phone) {}

  Holder holder() throws Exception {
    return holderWith(TestData.phone());
  }

  Holder holderWith(String phone) throws Exception {
    Session s = newUser("Asha");
    mvc.perform(
            MockMvcRequestBuilders.put("/api/v1/me/phone")
                .with(freshClient())
                .with(Csrf.token(mvc))
                .cookie(s.cookie())
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(map("phone", phone, "version", 0))))
        .andExpect(status().isOk());
    return new Holder(s, phone);
  }

  ResultActions sendCode(Holder h) throws Exception {
    return post(h.session(), "/api/v1/me/phone/verification", null);
  }

  ResultActions confirm(Holder h, String code) throws Exception {
    return post(h.session(), "/api/v1/me/phone/verification/confirm", map("code", code), null);
  }

  String codeFor(Holder h) {
    return sms.lastCode(h.phone()).orElseThrow();
  }

  String wrongCodeFor(String right) {
    return right.equals("000000") ? "000001" : "000000";
  }

  boolean verified(Holder h) {
    return dsl.select(USERS.PHONE_VERIFIED_AT)
            .from(USERS)
            .where(USERS.ID.eq(h.session().id()))
            .fetchOne()
            .value1()
        != null;
  }

  @Test
  void aCorrectCodeVerifiesTheNumber() throws Exception {
    Holder h = holder();

    sendCode(h)
        .andExpect(status().isAccepted())
        .andExpect(jsonPath("$.smsSent").value(true))
        .andExpect(jsonPath("$.codeExpiresAt").exists());
    assertThat(sms.sentTo(h.phone())).hasSize(1);

    confirm(h, codeFor(h))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.phone").value(h.phone()))
        .andExpect(jsonPath("$.phoneVerified").value(true));
    assertThat(verified(h)).isTrue();
    assertThat(dsl.fetchCount(PHONE_CHALLENGES, PHONE_CHALLENGES.USER_ID.eq(h.session().id())))
        .isZero();
    get(h.session(), "/api/v1/me").andExpect(jsonPath("$.phoneVerified").value(true));
  }

  @Test
  void onlyAHashOfTheCodeIsStored() throws Exception {
    Holder h = holder();
    sendCode(h).andExpect(status().isAccepted());

    byte[] stored =
        dsl.select(PHONE_CHALLENGES.CODE_HASH)
            .from(PHONE_CHALLENGES)
            .where(PHONE_CHALLENGES.USER_ID.eq(h.session().id()))
            .fetchOne()
            .value1();
    assertThat(new String(stored, java.nio.charset.StandardCharsets.ISO_8859_1))
        .doesNotContain(codeFor(h));
    assertThat(stored).hasSize(32);
  }

  @Test
  void aWrongCodeDoesNotVerify() throws Exception {
    Holder h = holder();
    sendCode(h).andExpect(status().isAccepted());

    confirm(h, wrongCodeFor(codeFor(h)))
        .andExpect(status().isUnprocessableEntity())
        .andExpect(jsonPath("$.type").value("urn:kanakku:problem:wrong-code"));

    assertThat(verified(h)).isFalse();
  }

  @Test
  void confirmingWithNoCodePendingIsTheSameWrongCodeAnswer() throws Exception {
    Holder h = holder();

    confirm(h, "123456")
        .andExpect(status().isUnprocessableEntity())
        .andExpect(jsonPath("$.type").value("urn:kanakku:problem:wrong-code"));
  }

  @Test
  void aCodeThatIsNotSixDigitsIsAValidationProblem() throws Exception {
    Holder h = holder();

    confirm(h, "12ab56").andExpect(status().isBadRequest());
    confirm(h, "12345").andExpect(status().isBadRequest());
  }

  @Test
  void afterFiveWrongCodesTheSixthIsRefusedEvenWithTheRightCode() throws Exception {
    Holder h = holder();
    sendCode(h).andExpect(status().isAccepted());
    String right = codeFor(h);
    for (int i = 0; i < 5; i++) {
      confirm(h, wrongCodeFor(right)).andExpect(status().isUnprocessableEntity());
    }

    confirm(h, right)
        .andExpect(status().isTooManyRequests())
        .andExpect(header().exists("Retry-After"))
        .andExpect(jsonPath("$.type").value("urn:kanakku:problem:code-locked"));
    assertThat(verified(h)).isFalse();
  }

  @Test
  void theLockEndsAfterFifteenMinutes() throws Exception {
    Holder h = holder();
    sendCode(h).andExpect(status().isAccepted());
    for (int i = 0; i < 5; i++) {
      confirm(h, wrongCodeFor(codeFor(h))).andExpect(status().isUnprocessableEntity());
    }
    confirm(h, codeFor(h)).andExpect(status().isTooManyRequests());

    TestClockConfig.CLOCK.advance(Duration.ofMinutes(15).plusSeconds(1));
    sendCode(h).andExpect(status().isAccepted());

    confirm(h, codeFor(h)).andExpect(status().isOk());
    assertThat(verified(h)).isTrue();
  }

  @Test
  void aCorrectCodeIsRefusedAfterTenMinutes() throws Exception {
    Holder h = holder();
    sendCode(h).andExpect(status().isAccepted());
    String code = codeFor(h);

    TestClockConfig.CLOCK.advance(Duration.ofMinutes(10).plusSeconds(1));

    confirm(h, code)
        .andExpect(status().isGone())
        .andExpect(jsonPath("$.type").value("urn:kanakku:problem:code-expired"));
    assertThat(verified(h)).isFalse();
  }

  @Test
  void aCorrectCodeStillWorksJustBeforeTenMinutes() throws Exception {
    Holder h = holder();
    sendCode(h).andExpect(status().isAccepted());
    String code = codeFor(h);

    TestClockConfig.CLOCK.advance(Duration.ofMinutes(9).plusSeconds(59));

    confirm(h, code).andExpect(status().isOk());
  }

  @Test
  void theFourthResendWithinAnHourIsRefused() throws Exception {
    Holder h = holder();
    sendCode(h).andExpect(status().isAccepted());
    for (int resend = 1; resend <= 3; resend++) {
      sendCode(h).andExpect(status().isAccepted());
    }

    sendCode(h)
        .andExpect(status().isTooManyRequests())
        .andExpect(header().exists("Retry-After"))
        .andExpect(jsonPath("$.type").value("urn:kanakku:problem:too-many-codes"));
    assertThat(sms.sentTo(h.phone())).hasSize(4);

    TestClockConfig.CLOCK.advance(Duration.ofHours(1).plusSeconds(1));
    sendCode(h).andExpect(status().isAccepted());
  }

  @Test
  void aResendReplacesTheEarlierCode() throws Exception {
    Holder h = holder();
    sendCode(h).andExpect(status().isAccepted());
    String first = codeFor(h);
    sendCode(h).andExpect(status().isAccepted());
    String second = codeFor(h);

    if (!first.equals(second)) {
      confirm(h, first).andExpect(status().isUnprocessableEntity());
    }
    confirm(h, second).andExpect(status().isOk());
  }

  @Test
  void aRepeatedKeyReplaysTheAnswerAndTextsOnce() throws Exception {
    Holder h = holder();
    String key = UUID.randomUUID().toString();

    String first =
        post(h.session(), "/api/v1/me/phone/verification", null, key)
            .andExpect(status().isAccepted())
            .andReturn()
            .getResponse()
            .getContentAsString();
    String replay =
        post(h.session(), "/api/v1/me/phone/verification", null, key)
            .andExpect(status().isAccepted())
            .andExpect(header().string("Idempotent-Replayed", "true"))
            .andReturn()
            .getResponse()
            .getContentAsString();

    assertThat(json.readTree(replay)).isEqualTo(json.readTree(first));
    assertThat(sms.sentTo(h.phone())).hasSize(1);
  }

  @Test
  void sendingNeedsAnIdempotencyKey() throws Exception {
    Holder h = holder();

    post(h.session(), "/api/v1/me/phone/verification", null, null)
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.type").value("urn:kanakku:problem:idempotency-key-required"));
    assertThat(sms.sent()).isEmpty();
  }

  @Test
  void aProviderFailureStillAnswersAndSaysTheTextWasNotSent() throws Exception {
    Holder h = holder();
    sms.failing(true);

    sendCode(h).andExpect(status().isAccepted()).andExpect(jsonPath("$.smsSent").value(false));
  }

  @Test
  void aUserWithNoNumberCannotAskForACode() throws Exception {
    Session s = newUser("Bala");

    post(s, "/api/v1/me/phone/verification", null)
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.type").value("urn:kanakku:problem:phone-missing"));
  }

  @Test
  void aVerifiedNumberCannotBeVerifiedAgain() throws Exception {
    Holder h = holder();
    sendCode(h).andExpect(status().isAccepted());
    confirm(h, codeFor(h)).andExpect(status().isOk());

    sendCode(h)
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.type").value("urn:kanakku:problem:phone-already-verified"));
  }

  @Test
  void aNumberAnotherAccountAlreadyVerifiedIsRefusedWhenAskingForACode() throws Exception {
    Holder first = holder();
    sendCode(first).andExpect(status().isAccepted());
    confirm(first, codeFor(first)).andExpect(status().isOk());
    Session second = newUser("Bala");
    mvc.perform(
            MockMvcRequestBuilders.put("/api/v1/me/phone")
                .with(freshClient())
                .with(Csrf.token(mvc))
                .cookie(second.cookie())
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(map("phone", first.phone(), "version", 0))))
        .andExpect(status().isConflict());
  }

  @Test
  void ifTwoAccountsRaceForOneNumberTheSecondConfirmIsRefusedAndStaysUnverified() throws Exception {
    Holder a = holder();
    Session b = newUser("Bala");
    mvc.perform(
            MockMvcRequestBuilders.put("/api/v1/me/phone")
                .with(freshClient())
                .with(Csrf.token(mvc))
                .cookie(b.cookie())
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(map("phone", a.phone(), "version", 0))))
        .andExpect(status().isOk());
    Holder bHolder = new Holder(b, a.phone());
    sendCode(a).andExpect(status().isAccepted());
    String codeA = codeFor(a);
    sendCode(bHolder).andExpect(status().isAccepted());
    String codeB = codeFor(bHolder);

    confirm(a, codeA).andExpect(status().isOk());
    confirm(bHolder, codeB)
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.type").value("urn:kanakku:problem:phone-taken"));

    assertThat(verified(bHolder)).isFalse();
    assertThat(verified(a)).isTrue();
  }

  @Test
  void aCodeSentToAnotherUserDoesNotVerifyMine() throws Exception {
    Holder a = holder();
    Holder b = holder();
    sendCode(a).andExpect(status().isAccepted());

    confirm(b, codeFor(a)).andExpect(status().isUnprocessableEntity());
    assertThat(verified(b)).isFalse();
  }

  @Test
  void changingTheNumberAfterAskingForACodeInvalidatesIt() throws Exception {
    Holder h = holder();
    sendCode(h).andExpect(status().isAccepted());
    String code = codeFor(h);
    mvc.perform(
            MockMvcRequestBuilders.put("/api/v1/me/phone")
                .with(freshClient())
                .with(Csrf.token(mvc))
                .cookie(h.session().cookie())
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(map("phone", TestData.phone(), "version", 1))))
        .andExpect(status().isOk());

    confirm(h, code).andExpect(status().isUnprocessableEntity());
  }

  // --- caps shared by many accounts (S-2)

  ResultActions sendFrom(String ip, Holder h) throws Exception {
    return postFrom(
        ip, h.session(), "/api/v1/me/phone/verification", null, UUID.randomUUID().toString());
  }

  ResultActions confirmFrom(String ip, Holder h, String code) throws Exception {
    return postFrom(
        ip, h.session(), "/api/v1/me/phone/verification/confirm", map("code", code), null);
  }

  @Test
  void manyAccountsCannotGetMoreThanFourTextsAnHourToOneNumber() throws Exception {
    String phone = TestData.phone();
    for (int i = 0; i < 4; i++) {
      sendCode(holderWith(phone)).andExpect(status().isAccepted());
    }

    sendCode(holderWith(phone))
        .andExpect(status().isTooManyRequests())
        .andExpect(header().exists("Retry-After"))
        .andExpect(jsonPath("$.type").value("urn:kanakku:problem:too-many-codes"));
    assertThat(sms.sentTo(phone)).hasSize(4);
  }

  @Test
  void oneAddressCannotGetMoreThanTenTextsAnHour() throws Exception {
    String ip = freshIp();
    for (int i = 0; i < 10; i++) {
      sendFrom(ip, holder()).andExpect(status().isAccepted());
    }

    sendFrom(ip, holder())
        .andExpect(status().isTooManyRequests())
        .andExpect(jsonPath("$.type").value("urn:kanakku:problem:too-many-codes"));
    assertThat(sms.sent()).hasSize(10);
    sendFrom(freshIp(), holder()).andExpect(status().isAccepted());
  }

  @Test
  void anIpv6SixtyFourCountsAsOneAddress() throws Exception {
    String suffix = Integer.toHexString(ThreadLocalRandom.current().nextInt(0x10000));
    String prefix = "2001:db8:" + suffix + ":1:";
    for (int i = 0; i < 10; i++) {
      sendFrom(prefix + i + ":0:0:" + (i + 1), holder()).andExpect(status().isAccepted());
    }

    sendFrom(prefix + "ffff:0:0:1", holder()).andExpect(status().isTooManyRequests());
  }

  @Test
  void manyAccountsShareOneWrongCodeBudgetPerNumber() throws Exception {
    String phone = TestData.phone();
    Holder a = holderWith(phone);
    Holder b = holderWith(phone);
    sendCode(a).andExpect(status().isAccepted());
    sendCode(b).andExpect(status().isAccepted());
    String rightForB = codeFor(b);
    for (int i = 0; i < 5; i++) {
      confirm(a, wrongCodeFor(codeFor(a))).andExpect(status().isUnprocessableEntity());
    }

    confirm(b, rightForB)
        .andExpect(status().isTooManyRequests())
        .andExpect(jsonPath("$.type").value("urn:kanakku:problem:code-locked"));
    assertThat(verified(b)).isFalse();
  }

  @Test
  void oneAddressHasTwentyWrongGuessesAcrossAccounts() throws Exception {
    String ip = freshIp();
    for (int i = 0; i < 20; i++) {
      Holder h = holder();
      sendCode(h).andExpect(status().isAccepted());
      confirmFrom(ip, h, wrongCodeFor(codeFor(h))).andExpect(status().isUnprocessableEntity());
    }
    Holder last = holder();
    sendCode(last).andExpect(status().isAccepted());

    confirmFrom(ip, last, codeFor(last))
        .andExpect(status().isTooManyRequests())
        .andExpect(jsonPath("$.type").value("urn:kanakku:problem:code-locked"));
    assertThat(verified(last)).isFalse();
    confirmFrom(freshIp(), last, codeFor(last)).andExpect(status().isOk());
  }

  // --- country allow-list (S-3)

  @Test
  void aNumberOutsideTheAllowedCountriesIsSavedButNeverTexted() throws Exception {
    Holder h = holderWith("+16502530000");

    sendCode(h)
        .andExpect(status().isUnprocessableEntity())
        .andExpect(jsonPath("$.type").value("urn:kanakku:problem:phone-country-unsupported"));
    assertThat(sms.sent()).isEmpty();
  }

  // --- the provider call and the database (S-4)

  @Test
  void theTextGoesOutWithNoTransactionOpen() throws Exception {
    Holder h = holder();

    sendCode(h).andExpect(status().isAccepted());

    assertThat(sms.sentTo(h.phone())).hasSize(1);
    assertThat(sms.anySentInTransaction()).isFalse();
  }

  @Test
  void failedTextsStillCountAgainstTheLimits() throws Exception {
    Holder h = holder();
    sms.failing(true);
    for (int i = 0; i < 4; i++) {
      sendCode(h).andExpect(status().isAccepted()).andExpect(jsonPath("$.smsSent").value(false));
    }

    sendCode(h).andExpect(status().isTooManyRequests());
  }

  @Test
  void aReplayAfterAFailedTextSaysSoToo() throws Exception {
    Holder h = holder();
    sms.failing(true);
    String key = UUID.randomUUID().toString();

    post(h.session(), "/api/v1/me/phone/verification", null, key)
        .andExpect(jsonPath("$.smsSent").value(false));
    post(h.session(), "/api/v1/me/phone/verification", null, key)
        .andExpect(header().string("Idempotent-Replayed", "true"))
        .andExpect(jsonPath("$.smsSent").value(false));
    assertThat(sms.sent()).hasSize(1);
  }

  // --- no raw number in the limit table (S-6)

  @Test
  void rateLimitKeysHoldNoPhoneNumber() throws Exception {
    Holder h = holder();
    sendCode(h).andExpect(status().isAccepted());
    confirm(h, wrongCodeFor(codeFor(h))).andExpect(status().isUnprocessableEntity());

    var keys = dsl.select(RATE_LIMIT_HITS.KEY).from(RATE_LIMIT_HITS).fetch(RATE_LIMIT_HITS.KEY);
    assertThat(keys).isNotEmpty();
    String digits = h.phone().substring(1);
    for (String key : keys) {
      assertThat(key).doesNotContain(digits).doesNotContain(digits.substring(2));
    }
  }

  @Test
  void anAnonymousCallerIsRefused() throws Exception {
    mvc.perform(
            MockMvcRequestBuilders.post("/api/v1/me/phone/verification")
                .with(Csrf.token(mvc))
                .header("Idempotency-Key", "k"))
        .andExpect(status().isUnauthorized());
  }
}
