package dev.kanakku.groups.service;

import static dev.kanakku.jooq.Tables.CLAIM_SUGGESTIONS;
import static dev.kanakku.jooq.Tables.GROUP_MEMBERS;
import static dev.kanakku.jooq.Tables.USERS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import dev.kanakku.identity.api.PhoneVerified;
import dev.kanakku.support.ApiTest;
import dev.kanakku.support.FakeSmsConfig;
import dev.kanakku.support.FakeSmsGateway;
import dev.kanakku.support.TestData;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

/** A verified number equal to a guest's number in one of the user's groups creates a suggestion. */
@Import(FakeSmsConfig.class)
class ClaimSuggestionCreationTest extends ApiTest {

  @Autowired FakeSmsGateway sms;
  @Autowired ApplicationEventPublisher events;

  @BeforeEach
  void reset() {
    sms.reset();
  }

  void giveNumber(Session user, String e164, boolean verified) {
    dsl.update(USERS)
        .set(USERS.PHONE_E164, e164)
        .set(USERS.PHONE_VERIFIED_AT, verified ? OffsetDateTime.now() : null)
        .where(USERS.ID.eq(user.id()))
        .execute();
  }

  String guestWithPhone(Session by, String group, String name, String e164) throws Exception {
    return read(post(
                by,
                "/api/v1/groups/" + group + "/guests",
                map("name", name, "phone", e164.substring(3)))
            .andExpect(status().isCreated()))
        .get("id")
        .asString();
  }

  List<String> suggestedMembers(Session user) {
    return dsl.select(CLAIM_SUGGESTIONS.MEMBER_ID)
        .from(CLAIM_SUGGESTIONS)
        .where(CLAIM_SUGGESTIONS.USER_ID.eq(user.id()))
        .fetch(r -> r.value1().toString());
  }

  void verified(Session user) {
    events.publishEvent(new PhoneVerified(user.id()));
  }

  @Test
  void verifyingAMatchingNumberCreatesASuggestionAndChangesNothingElse() throws Exception {
    Session asha = newUser("Asha");
    Session bala = newUser("Bala");
    String group = createGroup(asha, "Goa");
    join(group, bala);
    String phone = TestData.phone();
    String guest = guestWithPhone(asha, group, "Bala (guest)", phone);
    giveNumber(bala, phone, true);

    verified(bala);

    assertThat(suggestedMembers(bala)).containsExactly(guest);
    var row =
        dsl.selectFrom(CLAIM_SUGGESTIONS).where(CLAIM_SUGGESTIONS.USER_ID.eq(bala.id())).fetchOne();
    assertThat(row.getStatus()).isEqualTo("PENDING");
    assertThat(row.getGroupId()).isEqualTo(UUID.fromString(group));
    // The guest is untouched: still a guest, still current, unmerged.
    var guestRow =
        dsl.selectFrom(GROUP_MEMBERS).where(GROUP_MEMBERS.ID.eq(UUID.fromString(guest))).fetchOne();
    assertThat(guestRow.getUserId()).isNull();
    assertThat(guestRow.getLeftAt()).isNull();
    assertThat(guestRow.getMergedIntoMemberId()).isNull();
  }

  List<String> pendingFor(Session user) {
    return dsl.select(CLAIM_SUGGESTIONS.MEMBER_ID)
        .from(CLAIM_SUGGESTIONS)
        .where(CLAIM_SUGGESTIONS.USER_ID.eq(user.id()))
        .and(CLAIM_SUGGESTIONS.STATUS.eq("PENDING"))
        .fetch(r -> r.value1().toString());
  }

  @Test
  void aGuestAddedWithANumberAMemberVerifiedEarlierCreatesASuggestion() throws Exception {
    Session asha = newUser("Asha");
    Session bala = newUser("Bala");
    String group = createGroup(asha, "Goa");
    join(group, bala);
    String phone = TestData.phone();
    giveNumber(bala, phone, true);

    String guest = guestWithPhone(asha, group, "Bala (guest)", phone);

    assertThat(pendingFor(bala)).containsExactly(guest);
  }

  @Test
  void aGuestNumberThatOnlyMatchesAnUnverifiedOrOutsideHolderCreatesNone() throws Exception {
    Session asha = newUser("Asha");
    Session bala = newUser("Bala");
    Session outsider = newUser("Out");
    String group = createGroup(asha, "Goa");
    join(group, bala);
    String unverifiedPhone = TestData.phone();
    String outsiderPhone = TestData.phone();
    giveNumber(bala, unverifiedPhone, false);
    giveNumber(outsider, outsiderPhone, true);

    guestWithPhone(asha, group, "G1", unverifiedPhone);
    guestWithPhone(asha, group, "G2", outsiderPhone);

    assertThat(suggestedMembers(bala)).isEmpty();
    assertThat(suggestedMembers(outsider)).isEmpty();
  }

  @Test
  void settingAGuestsNumberSuggestsItAndChangingItWithdrawsThePendingSuggestion() throws Exception {
    Session asha = newUser("Asha");
    Session bala = newUser("Bala");
    String group = createGroup(asha, "Goa");
    join(group, bala);
    String phone = TestData.phone();
    giveNumber(bala, phone, true);
    String guest = addGuest(asha, group, "Bala (guest)");
    String path = "/api/v1/groups/" + group + "/members/" + guest + "/guest-phone";

    put(asha, path, map("phone", phone.substring(3), "version", 0)).andExpect(status().isOk());
    assertThat(pendingFor(bala)).containsExactly(guest);

    put(asha, path, map("phone", TestData.phone().substring(3), "version", 1))
        .andExpect(status().isOk());
    assertThat(pendingFor(bala)).isEmpty();
    assertThat(
            dsl.select(CLAIM_SUGGESTIONS.STATUS)
                .from(CLAIM_SUGGESTIONS)
                .where(CLAIM_SUGGESTIONS.USER_ID.eq(bala.id()))
                .fetchOne(CLAIM_SUGGESTIONS.STATUS))
        .isEqualTo("STALE");
  }

  @Test
  void aNumberThatIsNotVerifiedCreatesNoSuggestion() throws Exception {
    Session asha = newUser("Asha");
    Session bala = newUser("Bala");
    String group = createGroup(asha, "Goa");
    join(group, bala);
    String phone = TestData.phone();
    guestWithPhone(asha, group, "Bala (guest)", phone);
    giveNumber(bala, phone, false);

    verified(bala);

    assertThat(suggestedMembers(bala)).isEmpty();
  }

  @Test
  void aGroupTheUserIsNotInGetsNoSuggestion() throws Exception {
    Session asha = newUser("Asha");
    Session bala = newUser("Bala");
    String group = createGroup(asha, "Goa");
    String phone = TestData.phone();
    guestWithPhone(asha, group, "Bala (guest)", phone);
    giveNumber(bala, phone, true);

    verified(bala);

    assertThat(suggestedMembers(bala)).isEmpty();
  }

  @Test
  void aGroupTheUserLeftGetsNoSuggestion() throws Exception {
    Session asha = newUser("Asha");
    Session bala = newUser("Bala");
    String group = createGroup(asha, "Goa");
    join(group, bala);
    String phone = TestData.phone();
    guestWithPhone(asha, group, "Bala (guest)", phone);
    post(bala, "/api/v1/groups/" + group + "/leave", null, null).andExpect(status().isNoContent());
    giveNumber(bala, phone, true);

    verified(bala);

    assertThat(suggestedMembers(bala)).isEmpty();
  }

  @Test
  void onlyGuestsWithTheSameNumberInTheUsersGroupsAreSuggested() throws Exception {
    Session asha = newUser("Asha");
    Session bala = newUser("Bala");
    String goa = createGroup(asha, "Goa");
    String hampi = createGroup(asha, "Hampi");
    String other = createGroup(newUser("Chitra"), "Elsewhere");
    join(goa, bala);
    join(hampi, bala);
    String phone = TestData.phone();
    String inGoa = guestWithPhone(asha, goa, "Bala guest", phone);
    String inHampi = guestWithPhone(asha, hampi, "B guest", phone);
    guestWithPhone(asha, goa, "Someone else", TestData.phone());
    // A guest in a group Bala is not in, and a claimed guest, are never suggested.
    dsl.insertInto(GROUP_MEMBERS)
        .set(GROUP_MEMBERS.ID, UUID.randomUUID())
        .set(GROUP_MEMBERS.GROUP_ID, UUID.fromString(other))
        .set(GROUP_MEMBERS.GUEST_NAME, "Not mine")
        .set(GROUP_MEMBERS.GUEST_PHONE_E164, phone)
        .set(GROUP_MEMBERS.ROLE, "MEMBER")
        .execute();
    giveNumber(bala, phone, true);

    verified(bala);

    assertThat(suggestedMembers(bala)).containsExactlyInAnyOrder(inGoa, inHampi);
  }

  @Test
  void aGuestAlreadyClaimedByAnAccountIsNotSuggested() throws Exception {
    Session asha = newUser("Asha");
    Session bala = newUser("Bala");
    Session chitra = newUser("Chitra");
    String group = createGroup(asha, "Goa");
    join(group, bala);
    String phone = TestData.phone();
    String guest = guestWithPhone(asha, group, "Taken", phone);
    dsl.update(GROUP_MEMBERS)
        .set(GROUP_MEMBERS.USER_ID, chitra.id())
        .where(GROUP_MEMBERS.ID.eq(UUID.fromString(guest)))
        .execute();
    giveNumber(bala, phone, true);

    verified(bala);

    assertThat(suggestedMembers(bala)).isEmpty();
  }

  @Test
  void verifyingAgainDoesNotDuplicateOrReviveASuggestion() throws Exception {
    Session asha = newUser("Asha");
    Session bala = newUser("Bala");
    String group = createGroup(asha, "Goa");
    join(group, bala);
    String phone = TestData.phone();
    guestWithPhone(asha, group, "Bala (guest)", phone);
    giveNumber(bala, phone, true);
    verified(bala);
    dsl.update(CLAIM_SUGGESTIONS)
        .set(CLAIM_SUGGESTIONS.STATUS, "DECLINED")
        .where(CLAIM_SUGGESTIONS.USER_ID.eq(bala.id()))
        .execute();

    verified(bala);

    assertThat(suggestedMembers(bala)).hasSize(1);
    assertThat(
            dsl.select(CLAIM_SUGGESTIONS.STATUS)
                .from(CLAIM_SUGGESTIONS)
                .where(CLAIM_SUGGESTIONS.USER_ID.eq(bala.id()))
                .fetchOne(CLAIM_SUGGESTIONS.STATUS))
        .isEqualTo("DECLINED");
  }

  @Test
  void theEventCarriesNoPhoneNumber() {
    assertThat(PhoneVerified.class.getRecordComponents())
        .extracting(c -> c.getName())
        .containsExactly("userId");
  }

  @Test
  void confirmingACodeThroughTheApiCreatesTheSuggestionInTheSameRequest() throws Exception {
    Session asha = newUser("Asha");
    Session bala = newUser("Bala");
    String group = createGroup(asha, "Goa");
    join(group, bala);
    String phone = TestData.phone();
    String guest = guestWithPhone(asha, group, "Bala (guest)", phone);
    mvc.perform(
            MockMvcRequestBuilders.put("/api/v1/me/phone")
                .with(freshClient())
                .with(dev.kanakku.support.Csrf.token(mvc))
                .cookie(bala.cookie())
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(map("phone", phone, "version", 0))))
        .andExpect(status().isOk());
    post(bala, "/api/v1/me/phone/verification", null).andExpect(status().isAccepted());
    assertThat(suggestedMembers(bala)).isEmpty();

    post(
            bala,
            "/api/v1/me/phone/verification/confirm",
            map("code", sms.lastCode(phone).orElseThrow()),
            null)
        .andExpect(status().isOk());

    assertThat(suggestedMembers(bala)).containsExactly(guest);
  }

  @Test
  void aFailedConfirmCreatesNoSuggestion() throws Exception {
    Session asha = newUser("Asha");
    Session bala = newUser("Bala");
    String group = createGroup(asha, "Goa");
    join(group, bala);
    String phone = TestData.phone();
    guestWithPhone(asha, group, "Bala (guest)", phone);
    mvc.perform(
            MockMvcRequestBuilders.put("/api/v1/me/phone")
                .with(freshClient())
                .with(dev.kanakku.support.Csrf.token(mvc))
                .cookie(bala.cookie())
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(map("phone", phone, "version", 0))))
        .andExpect(status().isOk());
    post(bala, "/api/v1/me/phone/verification", null).andExpect(status().isAccepted());
    String right = sms.lastCode(phone).orElseThrow();

    post(
            bala,
            "/api/v1/me/phone/verification/confirm",
            map("code", right.equals("000000") ? "000001" : "000000"),
            null)
        .andExpect(status().isUnprocessableEntity());

    assertThat(suggestedMembers(bala)).isEmpty();
  }
}
