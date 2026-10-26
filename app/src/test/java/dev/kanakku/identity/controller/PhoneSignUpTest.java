package dev.kanakku.identity.controller;

import static dev.kanakku.jooq.Tables.IDEMPOTENCY_KEYS;
import static dev.kanakku.jooq.Tables.PASSWORD_CREDENTIALS;
import static dev.kanakku.jooq.Tables.PENDING_SIGN_UPS;
import static dev.kanakku.jooq.Tables.RATE_LIMIT_HITS;
import static dev.kanakku.jooq.Tables.USERS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import dev.kanakku.identity.service.PendingSignUpService;
import dev.kanakku.platform.phone.PhoneNumbers;
import dev.kanakku.support.Csrf;
import dev.kanakku.support.FakeSmsConfig;
import dev.kanakku.support.FakeSmsGateway;
import dev.kanakku.support.PostgresIntegrationTest;
import dev.kanakku.support.TestClockConfig;
import dev.kanakku.support.TestData;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.jooq.DSLContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** Sign-up with an SMS provider: the account exists only after the code is confirmed. */
@SpringBootTest
@AutoConfigureMockMvc
@Import({FakeSmsConfig.class, TestClockConfig.class})
class PhoneSignUpTest extends PostgresIntegrationTest {

  static final String PASSWORD = "correct horse battery";

  @Autowired MockMvc mvc;
  @Autowired DSLContext dsl;
  @Autowired JsonMapper json;
  @Autowired FakeSmsGateway sms;
  @Autowired PendingSignUpService pending;

  @BeforeEach
  void reset() {
    sms.reset();
    TestClockConfig.reset();
  }

  /** What a started sign-up needs afterwards. */
  record Started(String email, String phone, String id) {}

  /** A fresh public IP per call, so the per-IP send cap never bleeds between tests. */
  static String freshIp() {
    UUID u = UUID.randomUUID();
    return "10."
        + (u.getMostSignificantBits() & 0x7f)
        + "."
        + (u.getLeastSignificantBits() & 0x7f)
        + "."
        + (1 + (u.getMostSignificantBits() >>> 8 & 0x7f));
  }

  ResultActions signUpAt(
      String ip, String key, String email, String password, String name, String phone)
      throws Exception {
    Map<String, Object> body = new HashMap<>();
    body.put("email", email);
    body.put("password", password);
    body.put("displayName", name);
    if (phone != null) {
      body.put("phone", phone);
    }
    var request =
        post("/api/v1/auth/sign-up")
            .with(Csrf.token(mvc))
            .with(
                r -> {
                  r.setRemoteAddr(ip);
                  return r;
                })
            .contentType(MediaType.APPLICATION_JSON)
            .content(json.writeValueAsString(body));
    if (key != null) {
      request.header("Idempotency-Key", key);
    }
    return mvc.perform(request);
  }

  ResultActions signUp(String email, String phone) throws Exception {
    return signUpAt(freshIp(), UUID.randomUUID().toString(), email, PASSWORD, "Asha", phone);
  }

  Started start() throws Exception {
    String email = TestData.email();
    String phone = TestData.phone();
    String id = read(signUp(email, phone).andExpect(status().isAccepted())).get("id").asString();
    return new Started(email, phone, id);
  }

  JsonNode read(ResultActions result) throws Exception {
    return json.readTree(result.andReturn().getResponse().getContentAsString());
  }

  ResultActions action(String path, String key, Object body) throws Exception {
    var request =
        post(path)
            .with(Csrf.token(mvc))
            .with(
                r -> {
                  r.setRemoteAddr(freshIp());
                  return r;
                })
            .contentType(MediaType.APPLICATION_JSON);
    if (key != null) {
      request.header("Idempotency-Key", key);
    }
    if (body != null) {
      request.content(json.writeValueAsString(body));
    }
    return mvc.perform(request);
  }

  ResultActions verify(Started s, String code) throws Exception {
    return action(
        "/api/v1/auth/sign-ups/" + s.id() + "/verify",
        UUID.randomUUID().toString(),
        Map.of("code", code));
  }

  ResultActions resend(Started s) throws Exception {
    return action(
        "/api/v1/auth/sign-ups/" + s.id() + "/resend", UUID.randomUUID().toString(), null);
  }

  String codeFor(Started s) {
    return sms.lastCode(s.phone()).orElseThrow();
  }

  String wrongCodeFor(String right) {
    return right.equals("000000") ? "000001" : "000000";
  }

  long users(String email) {
    return dsl.fetchCount(USERS, USERS.EMAIL.equalIgnoreCase(email));
  }

  long pendings(String email) {
    return dsl.fetchCount(PENDING_SIGN_UPS, PENDING_SIGN_UPS.EMAIL.equalIgnoreCase(email));
  }

  @Test
  void signUpWithAProviderCreatesNoAccountUntilTheCodeIsConfirmed() throws Exception {
    String email = TestData.email();
    String phone = TestData.phone();

    signUp(email, phone)
        .andExpect(status().isAccepted())
        .andExpect(jsonPath("$.id").exists())
        .andExpect(jsonPath("$.smsSent").value(true))
        .andExpect(jsonPath("$.codeExpiresAt").exists())
        .andExpect(
            jsonPath("$.maskedPhone").value(org.hamcrest.Matchers.startsWith("+91 ••••• ••")));

    assertThat(users(email)).isZero();
    assertThat(pendings(email)).isEqualTo(1);
    assertThat(sms.sentTo(phone)).hasSize(1);
  }

  @Test
  void theResponseNeverCarriesTheNumberOrTheCode() throws Exception {
    String phone = TestData.phone();

    String body =
        signUp(TestData.email(), phone)
            .andExpect(status().isAccepted())
            .andReturn()
            .getResponse()
            .getContentAsString();

    assertThat(body).doesNotContain(phone).doesNotContain(phone.substring(3));
    assertThat(body).doesNotContain(sms.lastCode(phone).orElseThrow());
  }

  @Test
  void aCorrectCodeCreatesTheVerifiedAccountAndTheCredentials() throws Exception {
    Started s = start();

    verify(s, codeFor(s))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.email").value(s.email()))
        .andExpect(jsonPath("$.phone").value(PhoneNumbers.mask(s.phone())))
        .andExpect(jsonPath("$.phoneVerified").value(true));

    assertThat(users(s.email())).isEqualTo(1);
    assertThat(pendings(s.email())).isZero();
    var userId =
        dsl.select(USERS.ID).from(USERS).where(USERS.EMAIL.eq(s.email())).fetchOne().value1();
    assertThat(dsl.fetchCount(PASSWORD_CREDENTIALS, PASSWORD_CREDENTIALS.USER_ID.eq(userId)))
        .isEqualTo(1);
    // The new account can sign in with the password it chose.
    mvc.perform(
            post("/api/v1/auth/sign-in")
                .with(Csrf.token(mvc))
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("email", s.email(), "password", PASSWORD))))
        .andExpect(status().isOk());
  }

  @Test
  void signUpWithoutAPhoneCreatesTheAccountAtOnceEvenWithAProvider() throws Exception {
    String email = TestData.email();

    signUp(email, null)
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.phoneVerified").value(false));
    signUp(TestData.email(), "  ").andExpect(status().isCreated());

    assertThat(users(email)).isEqualTo(1);
    assertThat(pendings(email)).isZero();
    assertThat(sms.sent()).isEmpty();
  }

  @Test
  void anInvalidPhoneIsRefusedAndNothingIsStored() throws Exception {
    String email = TestData.email();

    signUp(email, "12345")
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.type").value("urn:kanakku:problem:invalid-phone"));

    assertThat(pendings(email)).isZero();
    assertThat(sms.sent()).isEmpty();
  }

  @Test
  void aWrongCodeCreatesNoAccountAndTheSameEmailCanSignUpAgain() throws Exception {
    Started s = start();

    verify(s, wrongCodeFor(codeFor(s)))
        .andExpect(status().isUnprocessableEntity())
        .andExpect(jsonPath("$.type").value("urn:kanakku:problem:wrong-code"));
    assertThat(users(s.email())).isZero();

    signUpAt(freshIp(), UUID.randomUUID().toString(), s.email(), PASSWORD, "Asha", TestData.phone())
        .andExpect(status().isAccepted());
  }

  @Test
  void anAbandonedCodeStepLeavesNoUserRow() throws Exception {
    Started s = start();

    assertThat(users(s.email())).isZero();
    TestClockConfig.CLOCK.advance(Duration.ofDays(2));
    assertThat(pending.purgeExpired()).isGreaterThanOrEqualTo(1);
    assertThat(pendings(s.email())).isZero();
    assertThat(users(s.email())).isZero();
  }

  @Test
  void purgeKeepsALivePendingSignUp() throws Exception {
    Started s = start();

    pending.purgeExpired();

    assertThat(pendings(s.email())).isEqualTo(1);
  }

  @Test
  void aNumberVerifiedElsewhereIsRefusedAtSignUpAndCreatesNoAccount() throws Exception {
    Started first = start();
    verify(first, codeFor(first)).andExpect(status().isCreated());
    String email = TestData.email();

    signUp(email, first.phone())
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.type").value("urn:kanakku:problem:phone-taken"));

    assertThat(users(email)).isZero();
    assertThat(pendings(email)).isZero();
    assertThat(users(first.email())).isEqualTo(1);
  }

  @Test
  void anAddressCannotProbeTakenNumbersWithoutLimit() throws Exception {
    Started first = start();
    verify(first, codeFor(first)).andExpect(status().isCreated());
    String ip = freshIp();

    for (int i = 0; i < 30; i++) {
      signUpAt(ip, UUID.randomUUID().toString(), TestData.email(), PASSWORD, "Asha", first.phone())
          .andExpect(status().isConflict())
          .andExpect(jsonPath("$.type").value("urn:kanakku:problem:phone-taken"));
    }

    signUpAt(ip, UUID.randomUUID().toString(), TestData.email(), PASSWORD, "Asha", first.phone())
        .andExpect(status().isTooManyRequests())
        .andExpect(jsonPath("$.type").value("urn:kanakku:problem:too-many-codes"))
        .andExpect(header().exists("Retry-After"));
    signUp(TestData.email(), first.phone()).andExpect(status().isConflict());
  }

  @Test
  void ifTheNumberGetsVerifiedElsewhereMidFlowTheConfirmIsRefusedAndCreatesNoAccount()
      throws Exception {
    String phone = TestData.phone();
    String emailA = TestData.email();
    String emailB = TestData.email();
    String idA = read(signUp(emailA, phone).andExpect(status().isAccepted())).get("id").asString();
    String idB = read(signUp(emailB, phone).andExpect(status().isAccepted())).get("id").asString();
    String codeB = codeFor(new Started(emailB, phone, idB));
    // B's text is the later one; A confirms with its own earlier code.
    var a = new Started(emailA, phone, idA);
    String codeA = sms.sentTo(phone).get(0).text().replaceAll(".*\\b(\\d{6})\\b.*", "$1");
    verify(a, codeA).andExpect(status().isCreated());

    verify(new Started(emailB, phone, idB), codeB)
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.type").value("urn:kanakku:problem:phone-taken"));

    assertThat(users(emailB)).isZero();
    assertThat(users(emailA)).isEqualTo(1);
  }

  @Test
  void aTakenEmailIsRefusedBeforeAnySmsIsSent() throws Exception {
    Started first = start();
    verify(first, codeFor(first)).andExpect(status().isCreated());
    sms.reset();

    signUp(first.email(), TestData.phone())
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.type").value("urn:kanakku:problem:email-taken"));

    assertThat(sms.sent()).isEmpty();
  }

  @Test
  void afterFiveWrongCodesTheSixthIsRefusedEvenWithTheRightCode() throws Exception {
    Started s = start();
    String right = codeFor(s);
    for (int i = 0; i < 5; i++) {
      verify(s, wrongCodeFor(right)).andExpect(status().isUnprocessableEntity());
    }

    verify(s, right)
        .andExpect(status().isTooManyRequests())
        .andExpect(jsonPath("$.type").value("urn:kanakku:problem:code-locked"));
    assertThat(users(s.email())).isZero();
  }

  @Test
  void theLockIsKeyedToTheNumberSoAnotherSignUpForItIsLockedToo() throws Exception {
    Started s = start();
    String right = codeFor(s);
    for (int i = 0; i < 5; i++) {
      verify(s, wrongCodeFor(right)).andExpect(status().isUnprocessableEntity());
    }
    String idOther =
        read(signUp(TestData.email(), s.phone()).andExpect(status().isAccepted()))
            .get("id")
            .asString();
    Started other = new Started("x", s.phone(), idOther);

    verify(other, codeFor(other)).andExpect(status().isTooManyRequests());
  }

  @Test
  void theLockEndsAfterFifteenMinutes() throws Exception {
    Started s = start();
    for (int i = 0; i < 5; i++) {
      verify(s, wrongCodeFor(codeFor(s))).andExpect(status().isUnprocessableEntity());
    }
    TestClockConfig.CLOCK.advance(Duration.ofMinutes(15).plusSeconds(1));
    resend(s).andExpect(status().isAccepted());

    verify(s, codeFor(s)).andExpect(status().isCreated());
  }

  @Test
  void aCorrectCodeIsRefusedAfterTenMinutes() throws Exception {
    Started s = start();
    String code = codeFor(s);
    TestClockConfig.CLOCK.advance(Duration.ofMinutes(10).plusSeconds(1));

    verify(s, code)
        .andExpect(status().isGone())
        .andExpect(jsonPath("$.type").value("urn:kanakku:problem:code-expired"));
    assertThat(users(s.email())).isZero();

    resend(s).andExpect(status().isAccepted());
    verify(s, codeFor(s)).andExpect(status().isCreated());
  }

  @Test
  void theFourthResendWithinAnHourIsRefused() throws Exception {
    Started s = start();
    for (int i = 0; i < 3; i++) {
      resend(s).andExpect(status().isAccepted());
    }

    resend(s)
        .andExpect(status().isTooManyRequests())
        .andExpect(header().exists("Retry-After"))
        .andExpect(jsonPath("$.type").value("urn:kanakku:problem:too-many-codes"));
    assertThat(sms.sentTo(s.phone())).hasSize(4);

    TestClockConfig.CLOCK.advance(Duration.ofMinutes(61));
    // The window slid past the earlier texts, so the number can be used again (a new sign-up).
    signUp(s.email(), s.phone()).andExpect(status().isAccepted());
  }

  @Test
  void aResendReplacesTheCode() throws Exception {
    Started s = start();
    String first = codeFor(s);
    resend(s).andExpect(status().isAccepted()).andExpect(jsonPath("$.smsSent").value(true));
    String second = codeFor(s);

    if (!first.equals(second)) {
      verify(s, first).andExpect(status().isUnprocessableEntity());
    }
    verify(s, second).andExpect(status().isCreated());
  }

  @Test
  void anIpCanStartOnlyTenSignUpsAnHour() throws Exception {
    String ip = freshIp();
    for (int i = 0; i < 10; i++) {
      signUpAt(ip, UUID.randomUUID().toString(), TestData.email(), PASSWORD, "A", TestData.phone())
          .andExpect(status().isAccepted());
    }

    signUpAt(ip, UUID.randomUUID().toString(), TestData.email(), PASSWORD, "A", TestData.phone())
        .andExpect(status().isTooManyRequests())
        .andExpect(header().exists("Retry-After"));
    // Another address is not affected.
    signUpAt(
            freshIp(),
            UUID.randomUUID().toString(),
            TestData.email(),
            PASSWORD,
            "A",
            TestData.phone())
        .andExpect(status().isAccepted());
  }

  @Test
  void theIpCapCountsAnIpv6SixtyFourPrefixAsOneClient() throws Exception {
    String prefix = "2001:db8:" + Integer.toHexString((int) (Math.random() * 60000) + 1) + ":1:";
    for (int i = 0; i < 10; i++) {
      signUpAt(
              prefix + i + ":0:0:1",
              UUID.randomUUID().toString(),
              TestData.email(),
              PASSWORD,
              "A",
              TestData.phone())
          .andExpect(status().isAccepted());
    }

    signUpAt(
            prefix + "ff:0:0:2",
            UUID.randomUUID().toString(),
            TestData.email(),
            PASSWORD,
            "A",
            TestData.phone())
        .andExpect(status().isTooManyRequests());
  }

  @Test
  void aRepeatedSignUpKeyReplaysTheSameAnswerAndTextsOnce() throws Exception {
    String key = UUID.randomUUID().toString();
    String email = TestData.email();
    String phone = TestData.phone();
    String ip = freshIp();

    JsonNode first =
        read(signUpAt(ip, key, email, PASSWORD, "Asha", phone).andExpect(status().isAccepted()));
    JsonNode replay =
        read(
            signUpAt(ip, key, email, PASSWORD, "Asha", phone)
                .andExpect(status().isAccepted())
                .andExpect(header().string("Idempotent-Replayed", "true")));

    assertThat(replay).isEqualTo(first);
    assertThat(sms.sentTo(phone)).hasSize(1);
    assertThat(pendings(email)).isEqualTo(1);
  }

  @Test
  void aReplayedKeyWithAnotherPasswordIsRefused() throws Exception {
    String key = UUID.randomUUID().toString();
    String email = TestData.email();
    String phone = TestData.phone();
    String ip = freshIp();
    signUpAt(ip, key, email, PASSWORD, "Asha", phone).andExpect(status().isAccepted());

    signUpAt(ip, key, email, "a different password", "Asha", phone)
        .andExpect(status().isUnprocessableEntity());
  }

  @Test
  void aRepeatedVerifyKeyReplaysTheProfileAndCreatesOneAccount() throws Exception {
    Started s = start();
    String key = UUID.randomUUID().toString();
    String path = "/api/v1/auth/sign-ups/" + s.id() + "/verify";
    JsonNode first =
        read(action(path, key, Map.of("code", codeFor(s))).andExpect(status().isCreated()));

    JsonNode replay =
        read(
            action(path, key, Map.of("code", codeFor(s)))
                .andExpect(status().isCreated())
                .andExpect(header().string("Idempotent-Replayed", "true")));

    assertThat(replay).isEqualTo(first);
    assertThat(users(s.email())).isEqualTo(1);
  }

  @Test
  void verifyAndResendNeedAnIdempotencyKey() throws Exception {
    Started s = start();

    action("/api/v1/auth/sign-ups/" + s.id() + "/verify", null, Map.of("code", codeFor(s)))
        .andExpect(status().isBadRequest());
    action("/api/v1/auth/sign-ups/" + s.id() + "/resend", null, null)
        .andExpect(status().isBadRequest());
    assertThat(users(s.email())).isZero();
  }

  @Test
  void anUnknownOrPurgedSignUpIsGone() throws Exception {
    action(
            "/api/v1/auth/sign-ups/" + UUID.randomUUID() + "/verify",
            UUID.randomUUID().toString(),
            Map.of("code", "123456"))
        .andExpect(status().isGone())
        .andExpect(jsonPath("$.type").value("urn:kanakku:problem:sign-up-expired"));
    action(
            "/api/v1/auth/sign-ups/" + UUID.randomUUID() + "/resend",
            UUID.randomUUID().toString(),
            null)
        .andExpect(status().isGone());
  }

  @Test
  void aPendingSignUpHoldsOnlyHashes() throws Exception {
    Started s = start();

    var row =
        dsl.selectFrom(PENDING_SIGN_UPS)
            .where(PENDING_SIGN_UPS.ID.eq(UUID.fromString(s.id())))
            .fetchOne();

    assertThat(row.getPasswordHash()).startsWith("$argon2");
    assertThat(row.getPasswordHash()).doesNotContain(PASSWORD);
    assertThat(new String(row.getCodeHash(), java.nio.charset.StandardCharsets.ISO_8859_1))
        .doesNotContain(codeFor(s));
    assertThat(row.getExpiresAt()).isAfter(OffsetDateTime.now().minusMinutes(1));
  }

  @Test
  void aProviderFailureStillCreatesThePendingSignUpAndSaysTheTextWasNotSent() throws Exception {
    sms.failing(true);

    signUp(TestData.email(), TestData.phone())
        .andExpect(status().isAccepted())
        .andExpect(jsonPath("$.smsSent").value(false));
  }

  @Test
  void afterAFailedSendTheSignUpCanFinishWithTheNumberUnverified() throws Exception {
    sms.failing(true);
    Started s = start();

    action(
            "/api/v1/auth/sign-ups/" + s.id() + "/finish-unverified",
            UUID.randomUUID().toString(),
            null)
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.email").value(s.email()))
        .andExpect(jsonPath("$.phone").value(PhoneNumbers.mask(s.phone())))
        .andExpect(jsonPath("$.phoneVerified").value(false));

    assertThat(users(s.email())).isEqualTo(1);
    assertThat(pendings(s.email())).isZero();
  }

  @Test
  void aSignUpWhoseTextWasSentCanStillFinishWithTheNumberUnverified() throws Exception {
    Started s = start();

    action(
            "/api/v1/auth/sign-ups/" + s.id() + "/finish-unverified",
            UUID.randomUUID().toString(),
            null)
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.phoneVerified").value(false));

    assertThat(users(s.email())).isEqualTo(1);
    assertThat(pendings(s.email())).isZero();
  }

  @Test
  void aRepeatedFinishKeyReplaysTheProfile() throws Exception {
    sms.failing(true);
    Started s = start();
    String key = UUID.randomUUID().toString();
    String path = "/api/v1/auth/sign-ups/" + s.id() + "/finish-unverified";
    JsonNode first = read(action(path, key, null).andExpect(status().isCreated()));

    JsonNode replay = read(action(path, key, null).andExpect(status().isCreated()));

    assertThat(replay).isEqualTo(first);
    assertThat(users(s.email())).isEqualTo(1);
  }

  @Test
  void aFailedSendCannotStartASignUpWithANumberVerifiedElsewhere() throws Exception {
    Started winner = start();
    verify(winner, codeFor(winner)).andExpect(status().isCreated());
    sms.failing(true);
    signUp(TestData.email(), winner.phone()).andExpect(status().isConflict());
  }

  // --- review fixes: S-3, S-4, S-6

  @Test
  void aNumberOutsideTheAllowedCountriesCannotStartASignUp() throws Exception {
    signUp(TestData.email(), "+16502530000")
        .andExpect(status().isUnprocessableEntity())
        .andExpect(jsonPath("$.type").value("urn:kanakku:problem:phone-country-unsupported"));

    assertThat(sms.sent()).isEmpty();
  }

  @Test
  void theTextGoesOutWithNoTransactionOpenAndAFailureIsRecordedOnTheRow() throws Exception {
    Started ok = start();
    assertThat(sms.anySentInTransaction()).isFalse();
    resend(ok).andExpect(status().isAccepted());
    assertThat(sms.anySentInTransaction()).isFalse();

    sms.failing(true);
    Started failed = start();
    assertThat(
            dsl.select(PENDING_SIGN_UPS.SMS_FAILED)
                .from(PENDING_SIGN_UPS)
                .where(PENDING_SIGN_UPS.ID.eq(UUID.fromString(failed.id())))
                .fetchOne(PENDING_SIGN_UPS.SMS_FAILED))
        .isTrue();
    assertThat(sms.anySentInTransaction()).isFalse();
  }

  @Test
  void aReplayedStartAfterAFailedTextStillSaysItFailed() throws Exception {
    sms.failing(true);
    String ip = freshIp();
    String key = UUID.randomUUID().toString();
    String email = TestData.email();
    String phone = TestData.phone();
    JsonNode first =
        read(signUpAt(ip, key, email, PASSWORD, "Asha", phone).andExpect(status().isAccepted()));

    JsonNode replay =
        read(
            signUpAt(ip, key, email, PASSWORD, "Asha", phone)
                .andExpect(header().string("Idempotent-Replayed", "true")));

    assertThat(first.get("smsSent").asBoolean()).isFalse();
    assertThat(replay).isEqualTo(first);
    assertThat(sms.sent()).hasSize(1);
  }

  @Test
  void failedTextsStillCountAgainstTheNumberLimit() throws Exception {
    sms.failing(true);
    String phone = TestData.phone();
    for (int i = 0; i < 4; i++) {
      signUp(TestData.email(), phone).andExpect(status().isAccepted());
    }

    signUp(TestData.email(), phone).andExpect(status().isTooManyRequests());
  }

  @Test
  void noFullNumberIsKeptInTheLimitTableOrTheStoredReplayBodies() throws Exception {
    Started s = start();
    verify(s, wrongCodeFor(codeFor(s))).andExpect(status().isUnprocessableEntity());
    String verifyKey = UUID.randomUUID().toString();
    action("/api/v1/auth/sign-ups/" + s.id() + "/verify", verifyKey, Map.of("code", codeFor(s)))
        .andExpect(status().isCreated());

    String digits = s.phone().substring(1);
    for (String key :
        dsl.select(RATE_LIMIT_HITS.KEY).from(RATE_LIMIT_HITS).fetch(RATE_LIMIT_HITS.KEY)) {
      assertThat(key).doesNotContain(digits).doesNotContain(digits.substring(2));
    }
    for (var body :
        dsl.select(IDEMPOTENCY_KEYS.RESPONSE)
            .from(IDEMPOTENCY_KEYS)
            .fetch(IDEMPOTENCY_KEYS.RESPONSE)) {
      assertThat(body.data()).doesNotContain(digits.substring(2));
    }
  }

  @Test
  void aReplayedVerifyReturnsTheSameMaskedProfile() throws Exception {
    Started s = start();
    String key = UUID.randomUUID().toString();
    String path = "/api/v1/auth/sign-ups/" + s.id() + "/verify";
    JsonNode first =
        read(action(path, key, Map.of("code", codeFor(s))).andExpect(status().isCreated()));

    JsonNode replay =
        read(
            action(path, key, Map.of("code", codeFor(s)))
                .andExpect(status().isCreated())
                .andExpect(header().string("Idempotent-Replayed", "true")));

    assertThat(replay).isEqualTo(first);
    assertThat(first.get("phone").asString()).isEqualTo(PhoneNumbers.mask(s.phone()));
  }
}
