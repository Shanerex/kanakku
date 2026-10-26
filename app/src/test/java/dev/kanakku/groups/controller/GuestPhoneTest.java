package dev.kanakku.groups.controller;

import static dev.kanakku.jooq.Tables.GROUP_MEMBERS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import dev.kanakku.support.ApiTest;
import dev.kanakku.support.TestData;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;

/** A guest may carry a phone number; only the Owner sees it, and only masked. */
class GuestPhoneTest extends ApiTest {

  String phoneOf(String memberId) {
    return dsl.select(GROUP_MEMBERS.GUEST_PHONE_E164)
        .from(GROUP_MEMBERS)
        .where(GROUP_MEMBERS.ID.eq(UUID.fromString(memberId)))
        .fetchOne(GROUP_MEMBERS.GUEST_PHONE_E164);
  }

  @Test
  void anOptionalPhoneIsStoredAsE164AndShownMaskedToTheOwner() throws Exception {
    Session asha = newUser("Asha");
    String group = createGroup(asha, "Goa");
    String digits = TestData.phoneDigits();

    JsonNode guest =
        read(
            post(
                    asha,
                    "/api/v1/groups/" + group + "/guests",
                    map(
                        "name",
                        "Karthik",
                        "phone",
                        digits.substring(0, 5) + " " + digits.substring(5)))
                .andExpect(status().isCreated())
                .andExpect(
                    jsonPath("$.guestPhoneMasked").value("+91 ••••• ••" + digits.substring(8))));

    assertThat(phoneOf(guest.get("id").asString())).isEqualTo("+91" + digits);
    assertThat(guest.toString()).doesNotContain(digits.substring(2, 8));
  }

  @Test
  void theMaskedPhoneIsOnTheOwnersMemberListButNotAMembers() throws Exception {
    Session asha = newUser("Asha");
    Session bala = newUser("Bala");
    String group = createGroup(asha, "Goa");
    join(group, bala);
    String digits = TestData.phoneDigits();
    post(asha, "/api/v1/groups/" + group + "/guests", map("name", "Karthik", "phone", digits))
        .andExpect(status().isCreated());

    String asOwner = read(get(asha, "/api/v1/groups/" + group + "/members")).toString();
    String asMember = read(get(bala, "/api/v1/groups/" + group + "/members")).toString();

    assertThat(asOwner).contains("guestPhoneMasked");
    assertThat(asMember).doesNotContain("guestPhoneMasked").doesNotContain(digits.substring(2, 8));
  }

  @Test
  void aMemberWhoAddsAGuestWithAPhoneDoesNotSeeItBackEither() throws Exception {
    Session asha = newUser("Asha");
    Session bala = newUser("Bala");
    String group = createGroup(asha, "Goa");
    join(group, bala);
    String digits = TestData.phoneDigits();

    JsonNode created =
        read(
            post(bala, "/api/v1/groups/" + group + "/guests", map("name", "Meera", "phone", digits))
                .andExpect(status().isCreated()));

    assertThat(created.has("guestPhoneMasked")).isFalse();
    assertThat(phoneOf(created.get("id").asString())).isEqualTo("+91" + digits);
  }

  @Test
  void aGuestWithoutAPhoneHasNone() throws Exception {
    Session asha = newUser("Asha");
    String group = createGroup(asha, "Goa");

    String id = addGuest(asha, group, "Karthik");

    assertThat(phoneOf(id)).isNull();
  }

  String guestPath(String group, String member) {
    return "/api/v1/groups/" + group + "/members/" + member + "/guest-phone";
  }

  @Test
  void theOwnerCanSetChangeAndClearAGuestsNumberWithTheVersion() throws Exception {
    Session asha = newUser("Asha");
    String group = createGroup(asha, "Goa");
    String guest = addGuest(asha, group, "Karthik");
    String first = TestData.phoneDigits();
    String second = TestData.phoneDigits();

    put(asha, guestPath(group, guest), map("phone", first, "version", 0))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.guestPhoneMasked").value("+91 ••••• ••" + first.substring(8)))
        .andExpect(jsonPath("$.version").value(1));
    assertThat(phoneOf(guest)).isEqualTo("+91" + first);

    put(asha, guestPath(group, guest), map("phone", second, "version", 1))
        .andExpect(status().isOk());
    assertThat(phoneOf(guest)).isEqualTo("+91" + second);

    put(asha, guestPath(group, guest), map("phone", null, "version", 2))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.guestPhoneMasked").doesNotExist());
    assertThat(phoneOf(guest)).isNull();
  }

  @Test
  void aStaleVersionAnInvalidNumberOrANonOwnerChangesNothing() throws Exception {
    Session asha = newUser("Asha");
    Session bala = newUser("Bala");
    String group = createGroup(asha, "Goa");
    join(group, bala);
    String guest = addGuest(asha, group, "Karthik");

    put(asha, guestPath(group, guest), map("phone", TestData.phoneDigits(), "version", 5))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.type").value("urn:kanakku:problem:stale-version"));
    put(asha, guestPath(group, guest), map("phone", "12345", "version", 0))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.type").value("urn:kanakku:problem:invalid-phone"));
    put(bala, guestPath(group, guest), map("phone", TestData.phoneDigits(), "version", 0))
        .andExpect(status().isForbidden());

    assertThat(phoneOf(guest)).isNull();
  }

  @Test
  void aRegisteredMemberHasNoGuestNumberToSet() throws Exception {
    Session asha = newUser("Asha");
    Session bala = newUser("Bala");
    String group = createGroup(asha, "Goa");
    String member = directAdd(asha, group, bala);

    put(asha, guestPath(group, member), map("phone", TestData.phoneDigits(), "version", 0))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.type").value("urn:kanakku:problem:not-a-guest"));
  }

  @Test
  void anInvalidGuestPhoneIsRefusedAndNoGuestIsAdded() throws Exception {
    Session asha = newUser("Asha");
    String group = createGroup(asha, "Goa");

    post(asha, "/api/v1/groups/" + group + "/guests", map("name", "Karthik", "phone", "12345"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.type").value("urn:kanakku:problem:invalid-phone"));

    assertThat(read(get(asha, "/api/v1/groups/" + group + "/members"))).hasSize(1);
  }

  @Test
  void aBlankPhoneCountsAsNone() throws Exception {
    Session asha = newUser("Asha");
    String group = createGroup(asha, "Goa");

    String id =
        read(post(asha, "/api/v1/groups/" + group + "/guests", map("name", "Karthik", "phone", " "))
                .andExpect(status().isCreated()))
            .get("id")
            .asString();

    assertThat(phoneOf(id)).isNull();
  }
}
