package dev.kanakku.groups.controller;

import static dev.kanakku.jooq.Tables.GROUP_MEMBERS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import dev.kanakku.support.ApiTest;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;

class GuestAndListTest extends ApiTest {

  @Test
  void aGuestWithOnlyANameAppearsInTheMemberList() throws Exception {
    Session asha = newUser("Asha");
    String group = createGroup(asha, "Goa");

    post(asha, "/api/v1/groups/" + group + "/guests", map("name", "Karthik"))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.displayName").value("Karthik"))
        .andExpect(jsonPath("$.guest").value(true))
        .andExpect(jsonPath("$.role").value("MEMBER"))
        .andExpect(jsonPath("$.userId").doesNotExist())
        .andExpect(jsonPath("$.balances").isEmpty());

    JsonNode list =
        read(get(asha, "/api/v1/groups/" + group + "/members").andExpect(status().isOk()));
    assertThat(list.valueStream().map(m -> m.get("displayName").asString()))
        .containsExactly("Asha", "Karthik");
    assertThat(list.get(0).get("role").asString()).isEqualTo("OWNER");
    assertThat(list.get(0).get("guest").asBoolean()).isFalse();
  }

  @Test
  void anOptionalEmailIsStoredAndOnlyTheOwnerSeesIt() throws Exception {
    Session asha = newUser("Asha");
    Session bala = newUser("Bala");
    String group = createGroup(asha, "Goa");
    join(group, bala);

    String guest =
        read(post(
                    asha,
                    "/api/v1/groups/" + group + "/guests",
                    map("name", "Karthik", "email", "k@example.com"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.guestEmail").value("k@example.com")))
            .get("id")
            .asString();

    assertThat(
            dsl.select(GROUP_MEMBERS.GUEST_EMAIL)
                .from(GROUP_MEMBERS)
                .where(GROUP_MEMBERS.ID.eq(UUID.fromString(guest)))
                .fetchOne(GROUP_MEMBERS.GUEST_EMAIL))
        .isEqualTo("k@example.com");
    JsonNode asBala = read(get(bala, "/api/v1/groups/" + group + "/members"));
    assertThat(asBala.toString()).doesNotContain("k@example.com");
  }

  @Test
  void aMemberCanAddAGuestToo() throws Exception {
    Session asha = newUser("Asha");
    Session bala = newUser("Bala");
    String group = createGroup(asha, "Goa");
    join(group, bala);

    post(bala, "/api/v1/groups/" + group + "/guests", map("name", "Meera"))
        .andExpect(status().isCreated());
  }

  @Test
  void invalidGuestsAreRefused() throws Exception {
    Session asha = newUser("Asha");
    String group = createGroup(asha, "Goa");

    post(asha, "/api/v1/groups/" + group + "/guests", map("name", "  "))
        .andExpect(status().isBadRequest());
    post(asha, "/api/v1/groups/" + group + "/guests", map("name", "x".repeat(81)))
        .andExpect(status().isBadRequest());
    post(asha, "/api/v1/groups/" + group + "/guests", map("name", "K", "email", "not-an-email"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errors[0].field").value("email"));
  }

  @Test
  void repeatingTheKeyAddsOneGuestOnly() throws Exception {
    Session asha = newUser("Asha");
    String group = createGroup(asha, "Goa");
    String key = UUID.randomUUID().toString();

    String first =
        read(post(asha, "/api/v1/groups/" + group + "/guests", map("name", "Karthik"), key))
            .get("id")
            .asString();
    post(asha, "/api/v1/groups/" + group + "/guests", map("name", "Karthik"), key)
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.id").value(first));

    assertThat(dsl.fetchCount(GROUP_MEMBERS, GROUP_MEMBERS.GROUP_ID.eq(UUID.fromString(group))))
        .isEqualTo(2);
    post(asha, "/api/v1/groups/" + group + "/guests", map("name", "Other"), key)
        .andExpect(status().isUnprocessableEntity());
  }

  @Test
  void aNonMemberCannotAddGuestsOrListMembers() throws Exception {
    Session asha = newUser("Asha");
    Session outsider = newUser("Outsider");
    String group = createGroup(asha, "Goa");

    post(outsider, "/api/v1/groups/" + group + "/guests", map("name", "Spy"))
        .andExpect(status().isNotFound());
    get(outsider, "/api/v1/groups/" + group + "/members").andExpect(status().isNotFound());
    assertThat(dsl.fetchCount(GROUP_MEMBERS, GROUP_MEMBERS.GROUP_ID.eq(UUID.fromString(group))))
        .isEqualTo(1);
  }

  @Test
  void anArchivedGroupTakesNoNewGuests() throws Exception {
    Session asha = newUser("Asha");
    String group = createGroup(asha, "Goa");
    post(asha, "/api/v1/groups/" + group + "/archive", map("version", 0), null)
        .andExpect(status().isOk());

    post(asha, "/api/v1/groups/" + group + "/guests", map("name", "Late"))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.type").value("urn:kanakku:problem:group-archived"));
  }

  @Test
  void theListCarriesNonZeroBalancesPerMember() throws Exception {
    Session asha = newUser("Asha");
    String group = createGroup(asha, "Goa");
    String ashaMember =
        read(get(asha, "/api/v1/groups/" + group + "/members")).get(0).get("id").asString();
    String karthik = addGuest(asha, group, "Karthik");
    addGuest(asha, group, "Settled");
    owe(group, ashaMember, karthik, 4500);

    JsonNode list = read(get(asha, "/api/v1/groups/" + group + "/members"));

    List<JsonNode> members = list.valueStream().toList();
    assertThat(members.get(0).get("balances").get(0).get("amountMinor").asLong()).isEqualTo(4500);
    assertThat(members.get(0).get("balances").get(0).get("currency").asString()).isEqualTo("INR");
    assertThat(members.get(1).get("balances").get(0).get("amountMinor").asLong()).isEqualTo(-4500);
    assertThat(members.get(2).get("balances")).isEmpty();
  }
}
