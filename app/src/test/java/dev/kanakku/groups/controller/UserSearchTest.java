package dev.kanakku.groups.controller;

import static dev.kanakku.jooq.Tables.USERS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import dev.kanakku.support.ApiTest;
import dev.kanakku.support.TestClockConfig;
import dev.kanakku.support.TestData;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.ResultActions;
import tools.jackson.databind.JsonNode;

/** The Owner's user search by name, email or phone number (F-27). */
@Import(TestClockConfig.class)
class UserSearchTest extends ApiTest {

  @BeforeEach
  void clock() {
    TestClockConfig.reset();
  }

  ResultActions search(Session by, String group, String q) throws Exception {
    return post(by, "/api/v1/groups/" + group + "/user-search", map("q", q), null);
  }

  void givePhone(Session user, String e164, boolean verified) {
    dsl.update(USERS)
        .set(USERS.PHONE_E164, e164)
        .set(USERS.PHONE_VERIFIED_AT, verified ? OffsetDateTime.now() : null)
        .where(USERS.ID.eq(user.id()))
        .execute();
  }

  @Test
  void aPartialPhoneFindsVerifiedAndUnverifiedUsersAndShowsOnlyAMaskedNumber() throws Exception {
    Session asha = newUser("Asha");
    String group = createGroup(asha, "Goa");
    String digits = TestData.phoneDigits();
    Session verified = newUser("Verified Vi");
    Session unverified = newUser("Unverified Un");
    givePhone(verified, "+91" + digits, true);
    givePhone(unverified, "+91" + digits, false);

    JsonNode hits = read(search(asha, group, digits.substring(1, 6)).andExpect(status().isOk()));

    assertThat(hits.valueStream().map(h -> h.get("id").asString()))
        .contains(verified.id().toString(), unverified.id().toString());
    JsonNode hit =
        hits.valueStream()
            .filter(h -> h.get("id").asString().equals(unverified.id().toString()))
            .findFirst()
            .orElseThrow();
    assertThat(hit.propertyNames())
        .containsExactlyInAnyOrder(
            "id", "displayName", "matchedBy", "maskedPhone", "phoneVerified", "directAdd");
    assertThat(hit.get("matchedBy").asString()).isEqualTo("PHONE");
    assertThat(hit.get("phoneVerified").asBoolean()).isFalse();
    assertThat(hit.get("directAdd").asBoolean()).isTrue();
    assertThat(
            hits.valueStream()
                .filter(h -> h.get("id").asString().equals(verified.id().toString()))
                .findFirst()
                .orElseThrow()
                .get("phoneVerified")
                .asBoolean())
        .isTrue();
    assertThat(hit.get("maskedPhone").asString()).isEqualTo("+91 ••••• ••" + digits.substring(8));
    assertThat(hit.toString()).doesNotContain(digits.substring(2, 8));
  }

  @Test
  void fewerThanFourDigitsIsRefusedWithAProblem() throws Exception {
    Session asha = newUser("Asha");
    String group = createGroup(asha, "Goa");

    search(asha, group, "987")
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.type").value("urn:kanakku:problem:search-too-short"));
  }

  @Test
  void membersAreExcludedFromPhoneResultsToo() throws Exception {
    Session asha = newUser("Asha");
    String group = createGroup(asha, "Goa");
    String digits = TestData.phoneDigits();
    Session bala = newUser("Bala");
    givePhone(bala, "+91" + digits, false);
    join(group, bala);

    assertThat(read(search(asha, group, digits)).valueStream().map(h -> h.get("id").asString()))
        .doesNotContain(bala.id().toString());
  }

  @Test
  void anInviteOnlyUserFoundByPhoneOrEmailIsFlaggedForInviteInsteadOfAdd() throws Exception {
    Session asha = newUser("Asha");
    String group = createGroup(asha, "Goa");
    String digits = TestData.phoneDigits();
    Session bala = newUser("Inv " + UUID.randomUUID().toString().substring(0, 8));
    givePhone(bala, "+91" + digits, false);
    setDirectAdd(bala, "INVITE_ONLY");

    for (String q : new String[] {digits, bala.email()}) {
      JsonNode hit = read(search(asha, group, q).andExpect(status().isOk())).get(0);
      assertThat(hit.get("id").asString()).isEqualTo(bala.id().toString());
      assertThat(hit.get("directAdd").asBoolean()).isFalse();
    }
  }

  @Test
  void theTwentyFirstSearchInAMinuteIs429AndTheLimitSlides() throws Exception {
    Session asha = newUser("Asha");
    String group = createGroup(asha, "Goa");
    for (int i = 0; i < 20; i++) {
      search(asha, group, "zzzzzz").andExpect(status().isOk());
    }

    search(asha, group, "zzzzzz")
        .andExpect(status().isTooManyRequests())
        .andExpect(header().exists("Retry-After"))
        .andExpect(jsonPath("$.type").value("urn:kanakku:problem:search-rate-limited"));

    TestClockConfig.CLOCK.advance(Duration.ofSeconds(61));
    search(asha, group, "zzzzzz").andExpect(status().isOk());
  }

  @Test
  void theLimitIsPerUser() throws Exception {
    Session asha = newUser("Asha");
    Session bala = newUser("Bala");
    String ashaGroup = createGroup(asha, "Goa");
    String balaGroup = createGroup(bala, "Hampi");
    for (int i = 0; i < 20; i++) {
      search(asha, ashaGroup, "zzzzzz").andExpect(status().isOk());
    }
    search(asha, ashaGroup, "zzzzzz").andExpect(status().isTooManyRequests());

    search(bala, balaGroup, "zzzzzz").andExpect(status().isOk());
  }

  @Test
  void aNonOwnerIsRefusedAndDoesNotUseUpTheOwnersLimit() throws Exception {
    Session asha = newUser("Asha");
    Session bala = newUser("Bala");
    String group = createGroup(asha, "Goa");
    join(group, bala);

    search(bala, group, "zzzzzz").andExpect(status().isForbidden());
    search(asha, group, "zzzzzz").andExpect(status().isOk());
  }
}
