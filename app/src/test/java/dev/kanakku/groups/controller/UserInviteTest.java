package dev.kanakku.groups.controller;

import static dev.kanakku.jooq.Tables.GROUP_INVITES;
import static dev.kanakku.jooq.Tables.GROUP_MEMBERS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import dev.kanakku.support.ApiTest;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;

/** Inviting a found user who chose invite-only: a guest and a single-use invite, no member yet. */
class UserInviteTest extends ApiTest {

  String path(String group) {
    return "/api/v1/groups/" + group + "/user-invites";
  }

  Session inviteOnlyUser(String name) throws Exception {
    Session user = newUser(name);
    setDirectAdd(user, "INVITE_ONLY");
    return user;
  }

  int memberRows(String group) {
    return dsl.fetchCount(GROUP_MEMBERS, GROUP_MEMBERS.GROUP_ID.eq(UUID.fromString(group)));
  }

  int userMembers(String group, Session user) {
    return dsl.fetchCount(
        GROUP_MEMBERS,
        GROUP_MEMBERS
            .GROUP_ID
            .eq(UUID.fromString(group))
            .and(GROUP_MEMBERS.USER_ID.eq(user.id()))
            .and(GROUP_MEMBERS.LEFT_AT.isNull()));
  }

  @Test
  void theOwnerInvitesAFoundUserAndGetsAGuestAndAnInviteButNoMember() throws Exception {
    Session asha = newUser("Asha");
    Session bala = inviteOnlyUser("Bala Iyer");
    String group = createGroup(asha, "Goa");

    JsonNode created =
        read(
            post(asha, path(group), map("userId", bala.id().toString()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.invite.status").value("ACTIVE"))
                .andExpect(jsonPath("$.invite.guestName").value("Bala Iyer"))
                .andExpect(jsonPath("$.token").isString()));

    assertThat(created.get("token").asString()).hasSize(43);
    assertThat(memberRows(group)).isEqualTo(2);
    assertThat(userMembers(group, bala)).isZero();
    var guest =
        dsl.selectFrom(GROUP_MEMBERS)
            .where(
                GROUP_MEMBERS.ID.eq(
                    UUID.fromString(created.get("invite").get("memberId").asString())))
            .fetchOne();
    assertThat(guest.getUserId()).isNull();
    assertThat(guest.getGuestName()).isEqualTo("Bala Iyer");
    assertThat(dsl.fetchCount(GROUP_INVITES, GROUP_INVITES.GROUP_ID.eq(UUID.fromString(group))))
        .isEqualTo(1);
  }

  @Test
  void theInvitedUserBecomesAMemberOnlyByClaimingTheLink() throws Exception {
    Session asha = newUser("Asha");
    Session bala = inviteOnlyUser("Bala");
    String group = createGroup(asha, "Goa");
    String token =
        read(post(asha, path(group), map("userId", bala.id().toString()))).get("token").asString();

    post(bala, "/api/v1/invites/claim", map("token", token), null).andExpect(status().isOk());

    assertThat(userMembers(group, bala)).isEqualTo(1);
    assertThat(memberRows(group)).isEqualTo(2);
  }

  @Test
  void anotherUserHoldingTheLinkCannotClaimIt() throws Exception {
    Session asha = newUser("Asha");
    Session bala = inviteOnlyUser("Bala");
    Session chitra = newUser("Chitra");
    String group = createGroup(asha, "Goa");
    String token =
        read(post(asha, path(group), map("userId", bala.id().toString()))).get("token").asString();

    post(chitra, "/api/v1/invites/claim", map("token", token), null)
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.type").value("urn:kanakku:problem:invite-for-someone-else"));

    assertThat(userMembers(group, chitra)).isZero();
    post(bala, "/api/v1/invites/claim", map("token", token), null).andExpect(status().isOk());
    assertThat(userMembers(group, bala)).isEqualTo(1);
  }

  @Test
  void directAddOfThatUserIsStillRefused() throws Exception {
    Session asha = newUser("Asha");
    Session bala = inviteOnlyUser("Bala");
    String group = createGroup(asha, "Goa");
    post(asha, path(group), map("userId", bala.id().toString())).andExpect(status().isCreated());

    post(asha, "/api/v1/groups/" + group + "/members", map("userId", bala.id().toString()))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.type").value("urn:kanakku:problem:invite-only"));

    assertThat(userMembers(group, bala)).isZero();
  }

  @Test
  void theExpiryCanBeChosenAndMustBeOneSevenOrThirty() throws Exception {
    Session asha = newUser("Asha");
    Session bala = inviteOnlyUser("Bala");
    String group = createGroup(asha, "Goa");

    post(asha, path(group), map("userId", bala.id().toString(), "expiryDays", 30))
        .andExpect(status().isCreated());
    post(asha, path(group), map("userId", bala.id().toString(), "expiryDays", 5))
        .andExpect(status().isBadRequest());
  }

  @Test
  void aMemberCallerIsRefusedAndNothingIsCreated() throws Exception {
    Session asha = newUser("Asha");
    Session bala = newUser("Bala");
    Session chitra = inviteOnlyUser("Chitra");
    String group = createGroup(asha, "Goa");
    join(group, bala);

    post(bala, path(group), map("userId", chitra.id().toString()))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.type").value("urn:kanakku:problem:owner-only"));

    assertThat(memberRows(group)).isEqualTo(2);
  }

  @Test
  void aNonMemberGetsA404() throws Exception {
    Session asha = newUser("Asha");
    Session outsider = newUser("Outsider");
    Session bala = inviteOnlyUser("Bala");
    String group = createGroup(asha, "Goa");

    post(outsider, path(group), map("userId", bala.id().toString()))
        .andExpect(status().isNotFound());
    assertThat(memberRows(group)).isEqualTo(1);
  }

  @Test
  void anUnknownUserIsA404AndCreatesNothing() throws Exception {
    Session asha = newUser("Asha");
    String group = createGroup(asha, "Goa");

    post(asha, path(group), map("userId", UUID.randomUUID().toString()))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.type").value("urn:kanakku:problem:user-not-found"));

    assertThat(memberRows(group)).isEqualTo(1);
  }

  @Test
  void aUserAlreadyInTheGroupIsAConflict() throws Exception {
    Session asha = newUser("Asha");
    Session bala = newUser("Bala");
    String group = createGroup(asha, "Goa");
    join(group, bala);

    post(asha, path(group), map("userId", bala.id().toString()))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.type").value("urn:kanakku:problem:already-member"));
    assertThat(memberRows(group)).isEqualTo(2);
  }

  @Test
  void anArchivedGroupTakesNoInvites() throws Exception {
    Session asha = newUser("Asha");
    Session bala = inviteOnlyUser("Bala");
    String group = createGroup(asha, "Goa");
    post(asha, "/api/v1/groups/" + group + "/archive", map("version", 0), null)
        .andExpect(status().isOk());

    post(asha, path(group), map("userId", bala.id().toString()))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.type").value("urn:kanakku:problem:group-archived"));
  }

  @Test
  void repeatingTheKeyReplaysTheSameInviteAndTokenAndCreatesOneGuest() throws Exception {
    Session asha = newUser("Asha");
    Session bala = inviteOnlyUser("Bala");
    String group = createGroup(asha, "Goa");
    String key = UUID.randomUUID().toString();
    var body = map("userId", bala.id().toString());

    JsonNode first = read(post(asha, path(group), body, key).andExpect(status().isCreated()));
    JsonNode replay = read(post(asha, path(group), body, key).andExpect(status().isCreated()));

    assertThat(replay.get("token").asString()).isEqualTo(first.get("token").asString());
    assertThat(replay.get("invite").get("id").asString())
        .isEqualTo(first.get("invite").get("id").asString());
    assertThat(memberRows(group)).isEqualTo(2);
    post(asha, path(group), body, null).andExpect(status().isBadRequest());
    assertThat(memberRows(group)).isEqualTo(2);
  }

  @Test
  void onlyTheHashOfTheTokenIsStored() throws Exception {
    Session asha = newUser("Asha");
    Session bala = inviteOnlyUser("Bala");
    String group = createGroup(asha, "Goa");
    String token =
        read(post(asha, path(group), map("userId", bala.id().toString()))).get("token").asString();

    byte[] stored =
        dsl.select(GROUP_INVITES.TOKEN_HASH)
            .from(GROUP_INVITES)
            .where(GROUP_INVITES.GROUP_ID.eq(UUID.fromString(group)))
            .fetchOne()
            .value1();

    assertThat(new String(stored, java.nio.charset.StandardCharsets.ISO_8859_1))
        .doesNotContain(token);
  }
}
