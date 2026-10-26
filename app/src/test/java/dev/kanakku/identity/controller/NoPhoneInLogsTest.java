package dev.kanakku.identity.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import dev.kanakku.groups.request.AddGuestRequest;
import dev.kanakku.groups.request.GuestPhoneRequest;
import dev.kanakku.groups.request.UserSearchRequest;
import dev.kanakku.identity.api.MatchedBy;
import dev.kanakku.identity.api.UserMatch;
import dev.kanakku.identity.domain.DirectAdd;
import dev.kanakku.identity.domain.PendingSignUp;
import dev.kanakku.identity.domain.Profile;
import dev.kanakku.identity.repository.PendingSignUpRepository;
import dev.kanakku.identity.repository.UserRepository;
import dev.kanakku.identity.request.ConfirmPhoneCodeRequest;
import dev.kanakku.identity.request.SignUpRequest;
import dev.kanakku.identity.request.UpdatePhoneRequest;
import dev.kanakku.support.ApiTest;
import dev.kanakku.support.Csrf;
import dev.kanakku.support.FakeSmsConfig;
import dev.kanakku.support.FakeSmsGateway;
import dev.kanakku.support.TestClockConfig;
import dev.kanakku.support.TestData;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import tools.jackson.databind.JsonNode;

/**
 * Runs every phone flow with DEBUG logging and looks for the numbers, the codes and the invite
 * tokens in the output. Phone numbers must never reach a log line or a share link.
 */
@SpringBootTest(properties = "logging.level.root=DEBUG")
@ExtendWith(OutputCaptureExtension.class)
@Import({FakeSmsConfig.class, TestClockConfig.class})
class NoPhoneInLogsTest extends ApiTest {

  @Autowired FakeSmsGateway sms;

  @BeforeEach
  void reset() {
    sms.reset();
    TestClockConfig.reset();
  }

  /** The ways a ten-digit number could be written into a log line. */
  static List<String> spellings(String digits) {
    return List.of(
        digits,
        "+91" + digits,
        "91" + digits,
        digits.substring(0, 5) + " " + digits.substring(5),
        "+91 " + digits.substring(0, 5) + " " + digits.substring(5),
        digits.substring(0, 5) + "-" + digits.substring(5),
        digits.substring(1, 8));
  }

  JsonNode putPhone(Session s, String raw, int version) throws Exception {
    return read(
        mvc.perform(
                MockMvcRequestBuilders.put("/api/v1/me/phone")
                    .with(freshClient())
                    .with(Csrf.token(mvc))
                    .cookie(s.cookie())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(json.writeValueAsString(map("phone", raw, "version", version))))
            .andExpect(status().isOk()));
  }

  @Test
  void noPhoneNumberCodeOrTokenReachesTheLogInAnyPhoneFlow(CapturedOutput output) throws Exception {
    List<String> secrets = new ArrayList<>();

    // Sign-up with a provider: start, resend, a wrong code, the right code.
    String signUpDigits = TestData.phoneDigits();
    String signUpEmail = TestData.email();
    JsonNode pending =
        read(
            mvc.perform(
                    MockMvcRequestBuilders.post("/api/v1/auth/sign-up")
                        .with(Csrf.token(mvc))
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(
                            json.writeValueAsString(
                                map(
                                    "email",
                                    signUpEmail,
                                    "password",
                                    "pw-" + UUID.randomUUID(),
                                    "displayName",
                                    "Asha",
                                    "phone",
                                    signUpDigits.substring(0, 5)
                                        + " "
                                        + signUpDigits.substring(5)))))
                .andExpect(status().isAccepted()));
    String pendingId = pending.get("id").asString();
    String e164 = "+91" + signUpDigits;
    String firstCode = sms.lastCode(e164).orElseThrow();
    mvc.perform(
            MockMvcRequestBuilders.post("/api/v1/auth/sign-ups/" + pendingId + "/resend")
                .with(Csrf.token(mvc))
                .header("Idempotency-Key", UUID.randomUUID().toString()))
        .andExpect(status().isAccepted());
    String code = sms.lastCode(e164).orElseThrow();
    String wrong = code.equals("000000") ? "000001" : "000000";
    mvc.perform(
            MockMvcRequestBuilders.post("/api/v1/auth/sign-ups/" + pendingId + "/verify")
                .with(Csrf.token(mvc))
                .header("Idempotency-Key", UUID.randomUUID().toString())
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(map("code", wrong))))
        .andExpect(status().isUnprocessableEntity());
    mvc.perform(
            MockMvcRequestBuilders.post("/api/v1/auth/sign-ups/" + pendingId + "/verify")
                .with(Csrf.token(mvc))
                .header("Idempotency-Key", UUID.randomUUID().toString())
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(map("code", code))))
        .andExpect(status().isCreated());
    secrets.addAll(spellings(signUpDigits));
    secrets.add(firstCode);
    secrets.add(code);

    // Sign-up whose text fails, finishing unverified.
    String failDigits = TestData.phoneDigits();
    sms.failing(true);
    String failId =
        read(mvc.perform(
                    MockMvcRequestBuilders.post("/api/v1/auth/sign-up")
                        .with(Csrf.token(mvc))
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(
                            json.writeValueAsString(
                                map(
                                    "email",
                                    TestData.email(),
                                    "password",
                                    "pw-" + UUID.randomUUID(),
                                    "displayName",
                                    "Bala",
                                    "phone",
                                    failDigits))))
                .andExpect(status().isAccepted()))
            .get("id")
            .asString();
    sms.failing(false);
    mvc.perform(
            MockMvcRequestBuilders.post("/api/v1/auth/sign-ups/" + failId + "/finish-unverified")
                .with(Csrf.token(mvc))
                .header("Idempotency-Key", UUID.randomUUID().toString()))
        .andExpect(status().isCreated());
    secrets.addAll(spellings(failDigits));

    // Profile: save a number, an invalid one, ask for a code, a wrong and the right code.
    Session chitra = newUser("Chitra");
    String profileDigits = TestData.phoneDigits();
    putPhone(chitra, profileDigits, 0);
    mvc.perform(
            MockMvcRequestBuilders.put("/api/v1/me/phone")
                .with(freshClient())
                .with(Csrf.token(mvc))
                .cookie(chitra.cookie())
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(map("phone", "55555 55555", "version", 1))))
        .andExpect(status().isBadRequest());
    post(chitra, "/api/v1/me/phone/verification", null).andExpect(status().isAccepted());
    String profileCode = sms.lastCode("+91" + profileDigits).orElseThrow();
    post(
            chitra,
            "/api/v1/me/phone/verification/confirm",
            map("code", profileCode.equals("000000") ? "000001" : "000000"),
            null)
        .andExpect(status().isUnprocessableEntity());
    post(chitra, "/api/v1/me/phone/verification/confirm", map("code", profileCode), null)
        .andExpect(status().isOk());
    secrets.addAll(spellings(profileDigits));
    secrets.add(profileCode);
    secrets.add("55555");

    // Groups: a guest with a number, searching by number, inviting, direct add, a suggestion.
    Session asha = newUser("Asha Owner");
    String group = createGroup(asha, "Goa");
    join(group, chitra);
    String guestDigits = TestData.phoneDigits();
    post(asha, "/api/v1/groups/" + group + "/guests", map("name", "Guest", "phone", guestDigits))
        .andExpect(status().isCreated());
    String searchDigits = TestData.phoneDigits();
    Session hidden = newUser("Hidden");
    putPhone(hidden, searchDigits, 0);
    setDirectAdd(hidden, "INVITE_ONLY");
    post(
            asha,
            "/api/v1/groups/" + group + "/user-search",
            map("q", searchDigits.substring(2, 8)),
            null)
        .andExpect(status().isOk());
    post(asha, "/api/v1/groups/" + group + "/user-search", map("q", "+91 " + searchDigits), null)
        .andExpect(status().isOk());
    String invited =
        post(
                asha,
                "/api/v1/groups/" + group + "/user-invites",
                map("userId", hidden.id().toString()))
            .andExpect(status().isCreated())
            .andReturn()
            .getResponse()
            .getContentAsString();
    String token = json.readTree(invited).get("token").asString();
    post(asha, "/api/v1/groups/" + group + "/members", map("userId", hidden.id().toString()))
        .andExpect(status().isConflict());
    Session unverified = newUser("Unverified");
    String unverifiedDigits = TestData.phoneDigits();
    putPhone(unverified, unverifiedDigits, 0);
    post(asha, "/api/v1/groups/" + group + "/members", map("userId", unverified.id().toString()))
        .andExpect(status().isCreated());
    delete(unverified, "/api/v1/me/phone?version=1").andExpect(status().isOk());
    String editedGuest = addGuest(asha, group, "Edited");
    String editedDigits = TestData.phoneDigits();
    put(
            asha,
            "/api/v1/groups/" + group + "/members/" + editedGuest + "/guest-phone",
            map("phone", editedDigits, "version", 0))
        .andExpect(status().isOk());
    get(chitra, "/api/v1/claim-suggestions").andExpect(status().isOk());
    secrets.addAll(spellings(guestDigits));
    secrets.addAll(spellings(searchDigits));
    secrets.addAll(spellings(unverifiedDigits));
    secrets.addAll(spellings(editedDigits));

    String log = output.getAll();
    assertThat(log).contains("DEBUG");
    for (String secret : secrets) {
      assertThat(log)
          .as("log must not contain %s", "a phone number or code")
          .doesNotContain(secret);
    }
    // A share link carries only the opaque token: no digit run of a phone number.
    assertThat(token).hasSize(43);
    for (String digits : List.of(guestDigits, searchDigits, unverifiedDigits)) {
      assertThat(token).doesNotContain(digits.substring(0, 6));
    }
    assertThat(log).doesNotContain(token);
  }

  @Test
  void problemResponsesNeverEchoTheNumberTyped() throws Exception {
    Session s = newUser("Asha");
    String bad = "55555 55555";

    String body =
        mvc.perform(
                MockMvcRequestBuilders.put("/api/v1/me/phone")
                    .with(freshClient())
                    .with(Csrf.token(mvc))
                    .cookie(s.cookie())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(json.writeValueAsString(map("phone", bad, "version", 0))))
            .andExpect(status().isBadRequest())
            .andReturn()
            .getResponse()
            .getContentAsString();

    assertThat(body).doesNotContain("55555");
  }

  @Test
  void requestAndResultRecordsHideTheNumberInToString() {
    String digits = TestData.phoneDigits();
    String e164 = "+91" + digits;
    String code = "483920";
    List<Object> records = new ArrayList<>();
    records.add(new SignUpRequest("a@b.co", "pw-secret-123", "A", digits));
    records.add(new UpdatePhoneRequest(digits, 0));
    records.add(new ConfirmPhoneCodeRequest(code));
    records.add(new UserSearchRequest(digits));
    records.add(new AddGuestRequest("Guest", "g@example.com", digits));
    records.add(
        new Profile(
            UUID.randomUUID(), "a@b.co", false, "A", "INR", null, DirectAdd.ALLOW, e164, true, 0));
    records.add(
        new UserMatch(
            UUID.randomUUID(), "A", MatchedBy.PHONE, null, "+91 ••••• ••10", false, false));
    records.add(
        new PendingSignUpRepository.Pending(
            UUID.randomUUID(),
            "a@b.co",
            "A",
            "$argon2id$x",
            e164,
            new byte[] {1},
            OffsetDateTime.now(),
            false,
            OffsetDateTime.now()));
    records.add(new UserRepository.Hit(UUID.randomUUID(), "A", "a@b.co", e164, false, true, false));
    records.add(new GuestPhoneRequest(digits, 0));
    records.add(new PendingSignUp(UUID.randomUUID(), "+91 ••••• ••10", OffsetDateTime.now(), true));

    for (Object record : records) {
      assertThat(record.toString())
          .as(record.getClass().getSimpleName())
          .doesNotContain(digits)
          .doesNotContain(digits.substring(2, 8))
          .doesNotContain(code)
          .doesNotContain("pw-secret-123")
          .doesNotContain("$argon2id$");
    }
  }

  @Test
  void searchRequestsPutNoQueryInTheUrl() throws Exception {
    Session asha = newUser("Asha");
    String group = createGroup(asha, "Goa");

    // The old GET with the query in the URL no longer exists.
    get(asha, "/api/v1/groups/" + group + "/user-search?q=" + TestData.phoneDigits())
        .andExpect(status().is4xxClientError());
  }
}
