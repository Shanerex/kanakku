package dev.kanakku.identity.controller;

import static dev.kanakku.jooq.Tables.PHONE_CHALLENGES;
import static dev.kanakku.jooq.Tables.USERS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import dev.kanakku.support.ApiTest;
import dev.kanakku.support.Csrf;
import dev.kanakku.support.TestData;
import java.time.OffsetDateTime;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

class PhoneProfileTest extends ApiTest {

  /** Ten national digits of a valid number, unique enough that tests can share one database. */
  static String uniqueNumber() {
    return TestData.phoneDigits();
  }

  ResultActions putPhone(Session s, String phone, Integer version) throws Exception {
    return putPhoneFrom(freshIp(), s, phone, version);
  }

  ResultActions putPhoneFrom(String ip, Session s, String phone, Integer version) throws Exception {
    var body = map("phone", phone, "version", version);
    return mvc.perform(
        MockMvcRequestBuilders.put("/api/v1/me/phone")
            .with(Csrf.token(mvc))
            .with(
                r -> {
                  r.setRemoteAddr(ip);
                  return r;
                })
            .cookie(s.cookie())
            .contentType(MediaType.APPLICATION_JSON)
            .content(json.writeValueAsString(body)));
  }

  String phoneOf(Session s) {
    return dsl.select(USERS.PHONE_E164).from(USERS).where(USERS.ID.eq(s.id())).fetchOne().value1();
  }

  void markVerified(Session s) {
    dsl.update(USERS)
        .set(USERS.PHONE_VERIFIED_AT, OffsetDateTime.now())
        .where(USERS.ID.eq(s.id()))
        .execute();
  }

  @Test
  void aNewUserHasNoNumberAndIsNotVerified() throws Exception {
    Session a = newUser("Asha");

    get(a, "/api/v1/me")
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.phone").doesNotExist())
        .andExpect(jsonPath("$.phoneVerified").value(false));
  }

  @Test
  void threeWaysOfWritingTheSameNumberStoreTheSameE164() throws Exception {
    String national = uniqueNumber();
    int version = 0;
    for (String raw :
        new String[] {
          national.substring(0, 5) + " " + national.substring(5),
          "+91 " + national.substring(0, 5) + " " + national.substring(5),
          "0" + national
        }) {
      Session a = newUser("Asha");
      putPhone(a, raw, 0)
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.phone").value("+91" + national))
          .andExpect(jsonPath("$.phoneVerified").value(false))
          .andExpect(jsonPath("$.version").value(version + 1));
      assertThat(phoneOf(a)).isEqualTo("+91" + national);
      // Two accounts may hold the same unverified number: clear it for the next form.
      dsl.update(USERS).setNull(USERS.PHONE_E164).where(USERS.ID.eq(a.id())).execute();
    }
  }

  @Test
  void anInvalidNumberIsAProblemAndStoresNothing() throws Exception {
    Session a = newUser("Asha");

    putPhone(a, "12345", 0)
        .andExpect(status().isBadRequest())
        .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
        .andExpect(jsonPath("$.type").value("urn:kanakku:problem:invalid-phone"));
    putPhone(a, "", 0).andExpect(status().isBadRequest());

    assertThat(phoneOf(a)).isNull();
  }

  @Test
  void aSecondNumberReplacesTheFirstAndResetsItToUnverified() throws Exception {
    Session a = newUser("Asha");
    putPhone(a, uniqueNumber(), 0).andExpect(status().isOk());
    markVerified(a);
    String second = uniqueNumber();

    putPhone(a, second, 1)
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.phone").value("+91" + second))
        .andExpect(jsonPath("$.phoneVerified").value(false));
    assertThat(phoneOf(a)).isEqualTo("+91" + second);
  }

  @Test
  void savingTheSameVerifiedNumberAgainKeepsItVerified() throws Exception {
    Session a = newUser("Asha");
    String number = uniqueNumber();
    putPhone(a, number, 0).andExpect(status().isOk());
    markVerified(a);

    putPhone(a, number, 1)
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.phoneVerified").value(true));
  }

  @Test
  void aNumberVerifiedOrNotCanBeRemovedAndThenAnotherAccountCanVerifyIt() throws Exception {
    Session a = newUser("Asha");
    String number = uniqueNumber();
    putPhone(a, number, 0).andExpect(status().isOk());
    markVerified(a);
    dsl.insertInto(PHONE_CHALLENGES)
        .set(PHONE_CHALLENGES.USER_ID, a.id())
        .set(PHONE_CHALLENGES.PHONE_E164, phoneOf(a))
        .set(PHONE_CHALLENGES.CODE_HASH, new byte[] {1})
        .set(PHONE_CHALLENGES.EXPIRES_AT, OffsetDateTime.now().plusMinutes(5))
        .execute();

    delete(a, "/api/v1/me/phone?version=1")
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.phone").doesNotExist())
        .andExpect(jsonPath("$.phoneVerified").value(false));

    assertThat(phoneOf(a)).isNull();
    assertThat(dsl.fetchCount(PHONE_CHALLENGES, PHONE_CHALLENGES.USER_ID.eq(a.id()))).isZero();
    Session b = newUser("Bala");
    putPhone(b, number, 0).andExpect(status().isOk());
  }

  @Test
  void removingWithAStaleVersionIsAConflictAndKeepsTheNumber() throws Exception {
    Session a = newUser("Asha");
    putPhone(a, uniqueNumber(), 0).andExpect(status().isOk());

    delete(a, "/api/v1/me/phone?version=0").andExpect(status().isConflict());

    assertThat(phoneOf(a)).isNotNull();
  }

  @Test
  void replacingTheNumberDropsTheOldPendingCode() throws Exception {
    Session a = newUser("Asha");
    putPhone(a, uniqueNumber(), 0).andExpect(status().isOk());
    dsl.insertInto(PHONE_CHALLENGES)
        .set(PHONE_CHALLENGES.USER_ID, a.id())
        .set(PHONE_CHALLENGES.PHONE_E164, phoneOf(a))
        .set(PHONE_CHALLENGES.CODE_HASH, new byte[] {1})
        .set(PHONE_CHALLENGES.EXPIRES_AT, OffsetDateTime.now().plusMinutes(5))
        .execute();

    putPhone(a, uniqueNumber(), 1).andExpect(status().isOk());

    assertThat(dsl.fetchCount(PHONE_CHALLENGES, PHONE_CHALLENGES.USER_ID.eq(a.id()))).isZero();
  }

  @Test
  void aNumberVerifiedOnAnotherAccountIsRefusedAndTheFirstAccountIsUnchanged() throws Exception {
    Session first = newUser("Asha");
    Session second = newUser("Bala");
    String number = uniqueNumber();
    putPhone(first, number, 0).andExpect(status().isOk());
    markVerified(first);

    putPhone(second, number, 0)
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.type").value("urn:kanakku:problem:phone-taken"));

    assertThat(phoneOf(second)).isNull();
    get(first, "/api/v1/me")
        .andExpect(jsonPath("$.phone").value("+91" + number))
        .andExpect(jsonPath("$.phoneVerified").value(true));
  }

  @Test
  void anUnverifiedNumberCanBeSavedByManyAccounts() throws Exception {
    String number = uniqueNumber();
    putPhone(newUser("Asha"), number, 0).andExpect(status().isOk());
    putPhone(newUser("Bala"), number, 0).andExpect(status().isOk());
  }

  @Test
  void aStaleVersionIsAConflictAndChangesNothing() throws Exception {
    Session a = newUser("Asha");

    putPhone(a, uniqueNumber(), 5)
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.type").value("urn:kanakku:problem:version-conflict"));

    assertThat(phoneOf(a)).isNull();
  }

  @Test
  void aMissingVersionIsAValidationProblem() throws Exception {
    Session a = newUser("Asha");

    putPhone(a, uniqueNumber(), null).andExpect(status().isBadRequest());
  }

  @Test
  void anAnonymousCallerIsRefused() throws Exception {
    mvc.perform(
            MockMvcRequestBuilders.put("/api/v1/me/phone")
                .with(Csrf.token(mvc))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"phone\":\"9876543210\",\"version\":0}"))
        .andExpect(status().isUnauthorized());
    mvc.perform(MockMvcRequestBuilders.get("/api/v1/me")).andExpect(status().isUnauthorized());
  }

  @Test
  void theProfileEditLeavesThePhoneAlone() throws Exception {
    Session a = newUser("Asha");
    String number = uniqueNumber();
    putPhone(a, number, 0).andExpect(status().isOk());

    mvc.perform(
            MockMvcRequestBuilders.put("/api/v1/me")
                .with(Csrf.token(mvc))
                .cookie(a.cookie())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    json.writeValueAsString(
                        map("displayName", "Asha K", "defaultCurrency", "INR", "version", 1))))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.phone").value("+91" + number));
  }

  @Test
  void aUserCanSaveANumberTenTimesAnHourAndTheEleventhIsRefused() throws Exception {
    Session a = newUser("Asha");
    for (int i = 0; i < 10; i++) {
      putPhone(a, uniqueNumber(), i).andExpect(status().isOk());
    }

    putPhone(a, uniqueNumber(), 10)
        .andExpect(status().isTooManyRequests())
        .andExpect(jsonPath("$.type").value("urn:kanakku:problem:too-many-codes"))
        .andExpect(
            org.springframework.test.web.servlet.result.MockMvcResultMatchers.header()
                .exists("Retry-After"));
  }

  @Test
  void aPhoneTakenAnswerStillCountsSoItCannotBeProbedWithoutLimit() throws Exception {
    Session holder = newUser("Asha");
    String taken = uniqueNumber();
    putPhone(holder, taken, 0).andExpect(status().isOk());
    markVerified(holder);
    Session prober = newUser("Bala");

    for (int i = 0; i < 10; i++) {
      putPhone(prober, taken, 0)
          .andExpect(status().isConflict())
          .andExpect(jsonPath("$.type").value("urn:kanakku:problem:phone-taken"));
    }

    putPhone(prober, taken, 0)
        .andExpect(status().isTooManyRequests())
        .andExpect(jsonPath("$.type").value("urn:kanakku:problem:too-many-codes"));
  }

  @Test
  void anAddressCanSaveThirtyNumbersAnHourAcrossAccounts() throws Exception {
    String ip = freshIp();
    for (int i = 0; i < 30; i++) {
      putPhoneFrom(ip, newUser("User " + i), uniqueNumber(), 0).andExpect(status().isOk());
    }

    putPhoneFrom(ip, newUser("Late"), uniqueNumber(), 0)
        .andExpect(status().isTooManyRequests())
        .andExpect(jsonPath("$.type").value("urn:kanakku:problem:too-many-codes"));
    putPhoneFrom(freshIp(), newUser("Elsewhere"), uniqueNumber(), 0).andExpect(status().isOk());
  }
}
