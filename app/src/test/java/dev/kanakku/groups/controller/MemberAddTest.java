package dev.kanakku.groups.controller;

import static dev.kanakku.jooq.Tables.GROUP_MEMBERS;
import static dev.kanakku.jooq.Tables.USERS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import dev.kanakku.support.ApiTest;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.ResultActions;
import tools.jackson.databind.JsonNode;

class MemberAddTest extends ApiTest {

  String tag() {
    return "zq" + UUID.randomUUID().toString().replace("-", "").substring(0, 10);
  }

  Session userNamed(String tag) throws Exception {
    return newUser("Person " + tag);
  }

  int memberCount(String group) {
    return dsl.fetchCount(GROUP_MEMBERS, GROUP_MEMBERS.GROUP_ID.eq(UUID.fromString(group)));
  }

  // --- user-search

  ResultActions search(Session by, String group, String q) throws Exception {
    return post(by, "/api/v1/groups/" + group + "/user-search", map("q", q), null);
  }

  @Test
  void searchFindsUsersByNameExcludingCurrentMembersAndMasksEmails() throws Exception {
    Session asha = newUser("Asha");
    String group = createGroup(asha, "Goa");
    String tag = tag();
    Session priya = userNamed(tag + "a");
    Session arun = userNamed(tag + "b");
    directAdd(asha, group, arun);

    JsonNode hits = read(search(asha, group, tag).andExpect(status().isOk()));

    assertThat(hits.valueStream().map(h -> h.get("id").asString()))
        .containsExactly(priya.id().toString());
    JsonNode hit = hits.get(0);
    assertThat(hit.propertyNames())
        .containsExactlyInAnyOrder("id", "displayName", "matchedBy", "maskedEmail", "directAdd");
    assertThat(hit.get("matchedBy").asString()).isEqualTo("NAME");
    assertThat(hit.get("directAdd").asBoolean()).isTrue();
    assertThat(hit.get("maskedEmail").asString())
        .startsWith("u***@")
        .doesNotContain(priya.email().substring(1, 12));
  }

  @Test
  void searchUnderThreeCharactersIsAProblem() throws Exception {
    Session asha = newUser("Asha");
    String group = createGroup(asha, "Goa");

    search(asha, group, "ab")
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.type").value("urn:kanakku:problem:search-too-short"));
    search(asha, group, "").andExpect(status().isBadRequest());
    post(asha, "/api/v1/groups/" + group + "/user-search", map(), null)
        .andExpect(status().isBadRequest());
  }

  @Test
  void searchReturnsAtMostTenAndNowIncludesInviteOnlyUsersFlaggedForInvite() throws Exception {
    Session asha = newUser("Asha");
    String group = createGroup(asha, "Goa");
    String tag = tag();
    for (int i = 0; i < 12; i++) {
      userNamed(tag + i);
    }
    Session invite = userNamed(tag + "hidden");
    setDirectAdd(invite, "INVITE_ONLY");

    JsonNode hits = read(search(asha, group, tag + "hidden"));

    assertThat(read(search(asha, group, tag))).hasSize(10);
    assertThat(hits).hasSize(1);
    assertThat(hits.get(0).get("id").asString()).isEqualTo(invite.id().toString());
    assertThat(hits.get(0).get("directAdd").asBoolean()).isFalse();
  }

  @Test
  void onlyTheOwnerMaySearch() throws Exception {
    Session asha = newUser("Asha");
    Session bala = newUser("Bala");
    Session outsider = newUser("Outsider");
    String group = createGroup(asha, "Goa");
    join(group, bala);

    search(bala, group, "Asha").andExpect(status().isForbidden());
    search(outsider, group, "Asha").andExpect(status().isNotFound());
  }

  // --- direct add (T-11)

  @Test
  void theOwnerDirectAddsAUserWhoAllowsItAndTheyAppearAsAMember() throws Exception {
    Session asha = newUser("Asha");
    Session bala = newUser("Bala");
    String group = createGroup(asha, "Goa");

    post(asha, "/api/v1/groups/" + group + "/members", map("userId", bala.id().toString()))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.userId").value(bala.id().toString()))
        .andExpect(jsonPath("$.displayName").value("Bala"))
        .andExpect(jsonPath("$.role").value("MEMBER"))
        .andExpect(jsonPath("$.guest").value(false));

    assertThat(
            read(get(asha, "/api/v1/groups/" + group + "/members"))
                .valueStream()
                .map(m -> m.get("displayName").asString()))
        .containsExactly("Asha", "Bala");
    get(bala, "/api/v1/groups/" + group).andExpect(status().isOk());
  }

  @Test
  void aMemberCallerIsRefusedAndAddsNobody() throws Exception {
    Session asha = newUser("Asha");
    Session bala = newUser("Bala");
    Session chitra = newUser("Chitra");
    String group = createGroup(asha, "Goa");
    join(group, bala);

    post(bala, "/api/v1/groups/" + group + "/members", map("userId", chitra.id().toString()))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.type").value("urn:kanakku:problem:owner-only"));

    assertThat(memberCount(group)).isEqualTo(2);
  }

  @Test
  void aNonMemberCannotDirectAdd() throws Exception {
    Session asha = newUser("Asha");
    Session outsider = newUser("Outsider");
    String group = createGroup(asha, "Goa");

    post(outsider, "/api/v1/groups/" + group + "/members", map("userId", outsider.id().toString()))
        .andExpect(status().isNotFound());
    assertThat(memberCount(group)).isEqualTo(1);
  }

  @Test
  void anInviteOnlyUserIsRefusedAndNotAdded() throws Exception {
    Session asha = newUser("Asha");
    Session bala = newUser("Bala");
    String group = createGroup(asha, "Goa");
    setDirectAdd(bala, "INVITE_ONLY");

    post(asha, "/api/v1/groups/" + group + "/members", map("userId", bala.id().toString()))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.type").value("urn:kanakku:problem:invite-only"));

    assertThat(memberCount(group)).isEqualTo(1);
  }

  @Test
  void aUserWithAnUnverifiedNumberIsAddedLikeAnyOther() throws Exception {
    Session asha = newUser("Asha");
    Session bala = newUser("Bala");
    String group = createGroup(asha, "Goa");
    dsl.update(USERS)
        .set(USERS.PHONE_E164, dev.kanakku.support.TestData.phone())
        .where(USERS.ID.eq(bala.id()))
        .execute();

    post(asha, "/api/v1/groups/" + group + "/members", map("userId", bala.id().toString()))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.userId").value(bala.id().toString()))
        .andExpect(jsonPath("$.role").value("MEMBER"));

    assertThat(memberCount(group)).isEqualTo(2);
  }

  @Test
  void aUserWithAVerifiedNumberOrNoNumberIsAddedAsBefore() throws Exception {
    Session asha = newUser("Asha");
    Session verified = newUser("Vera");
    Session none = newUser("Nora");
    String group = createGroup(asha, "Goa");
    dsl.update(USERS)
        .set(USERS.PHONE_E164, dev.kanakku.support.TestData.phone())
        .set(USERS.PHONE_VERIFIED_AT, java.time.OffsetDateTime.now())
        .where(USERS.ID.eq(verified.id()))
        .execute();

    post(asha, "/api/v1/groups/" + group + "/members", map("userId", verified.id().toString()))
        .andExpect(status().isCreated());
    post(asha, "/api/v1/groups/" + group + "/members", map("userId", none.id().toString()))
        .andExpect(status().isCreated());
  }

  @Test
  void anInviteOnlyUserWithAnUnverifiedNumberStillGetsTheInviteOnlyAnswer() throws Exception {
    Session asha = newUser("Asha");
    Session bala = newUser("Bala");
    String group = createGroup(asha, "Goa");
    dsl.update(USERS)
        .set(USERS.PHONE_E164, dev.kanakku.support.TestData.phone())
        .set(USERS.DIRECT_ADD, "INVITE_ONLY")
        .where(USERS.ID.eq(bala.id()))
        .execute();

    post(asha, "/api/v1/groups/" + group + "/members", map("userId", bala.id().toString()))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.type").value("urn:kanakku:problem:invite-only"));
  }

  @Test
  void theOutcomeFollowsTheToggle() throws Exception {
    Session asha = newUser("Asha");
    Session bala = newUser("Bala");
    String group = createGroup(asha, "Goa");
    String body = bala.id().toString();

    setDirectAdd(bala, "INVITE_ONLY");
    post(asha, "/api/v1/groups/" + group + "/members", map("userId", body))
        .andExpect(status().isConflict());
    setDirectAdd(bala, "ALLOW");
    post(asha, "/api/v1/groups/" + group + "/members", map("userId", body))
        .andExpect(status().isCreated());
  }

  @Test
  void anUnknownUserLooksLikeAnInviteOnlyOne() throws Exception {
    Session asha = newUser("Asha");
    String group = createGroup(asha, "Goa");

    post(asha, "/api/v1/groups/" + group + "/members", map("userId", UUID.randomUUID().toString()))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.type").value("urn:kanakku:problem:invite-only"));
  }

  @Test
  void addingSomeoneAlreadyInTheGroupIsAConflict() throws Exception {
    Session asha = newUser("Asha");
    Session bala = newUser("Bala");
    String group = createGroup(asha, "Goa");
    directAdd(asha, group, bala);

    post(asha, "/api/v1/groups/" + group + "/members", map("userId", bala.id().toString()))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.type").value("urn:kanakku:problem:already-member"));
    post(asha, "/api/v1/groups/" + group + "/members", map("userId", asha.id().toString()))
        .andExpect(status().isConflict());
    assertThat(memberCount(group)).isEqualTo(2);
  }

  @Test
  void repeatingTheKeyReplaysTheFirstResultWithoutAddingTwice() throws Exception {
    Session asha = newUser("Asha");
    Session bala = newUser("Bala");
    String group = createGroup(asha, "Goa");
    String key = UUID.randomUUID().toString();
    String path = "/api/v1/groups/" + group + "/members";

    String first =
        read(post(asha, path, map("userId", bala.id().toString()), key)).get("id").asString();
    post(asha, path, map("userId", bala.id().toString()), key)
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.id").value(first));
    post(asha, path, map("userId", bala.id().toString()), null).andExpect(status().isBadRequest());
    assertThat(memberCount(group)).isEqualTo(2);
  }

  @Test
  void anArchivedGroupTakesNoDirectAdds() throws Exception {
    Session asha = newUser("Asha");
    Session bala = newUser("Bala");
    String group = createGroup(asha, "Goa");
    post(asha, "/api/v1/groups/" + group + "/archive", map("version", 0), null)
        .andExpect(status().isOk());

    post(asha, "/api/v1/groups/" + group + "/members", map("userId", bala.id().toString()))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.type").value("urn:kanakku:problem:group-archived"));
  }

  @Test
  void aJustAddedUserSettingIsReadFromTheirProfile() throws Exception {
    Session bala = newUser("Bala");
    assertThat(
            dsl.select(USERS.DIRECT_ADD)
                .from(USERS)
                .where(USERS.ID.eq(bala.id()))
                .fetchOne(USERS.DIRECT_ADD))
        .isEqualTo("ALLOW");
  }
}
