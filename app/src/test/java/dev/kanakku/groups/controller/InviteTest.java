package dev.kanakku.groups.controller;

import static dev.kanakku.jooq.Tables.GROUP_INVITES;
import static dev.kanakku.jooq.Tables.GROUP_MEMBERS;
import static dev.kanakku.jooq.Tables.IDEMPOTENCY_KEYS;
import static dev.kanakku.jooq.Tables.JOURNAL_LINES;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import dev.kanakku.support.ApiTest;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;

class InviteTest extends ApiTest {

  String invitesPath(String group) {
    return "/api/v1/groups/" + group + "/invites";
  }

  JsonNode createInvite(Session owner, String group, String guest) throws Exception {
    return read(
        post(owner, invitesPath(group), map("memberId", guest)).andExpect(status().isCreated()));
  }

  // --- T-12: create, list, revoke

  @Test
  void theOwnerCreatesASingleUseInviteThatExpiresInSevenDaysByDefault() throws Exception {
    Session asha = newUser("Asha");
    String group = createGroup(asha, "Goa");
    String guest = addGuest(asha, group, "Karthik");

    JsonNode created =
        read(
            post(asha, invitesPath(group), map("memberId", guest))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.invite.status").value("ACTIVE"))
                .andExpect(jsonPath("$.invite.guestName").value("Karthik"))
                .andExpect(jsonPath("$.invite.memberId").value(guest))
                .andExpect(jsonPath("$.invite.version").value(0)));

    String token = created.get("token").asString();
    assertThat(token).hasSize(43).matches("[A-Za-z0-9_-]+");
    OffsetDateTime expires =
        OffsetDateTime.parse(created.get("invite").get("expiresAt").asString());
    assertThat(Duration.between(OffsetDateTime.now(), expires).toDays()).isBetween(6L, 7L);
  }

  @Test
  void onlyTheHashOfTheTokenIsStored() throws Exception {
    Session asha = newUser("Asha");
    String group = createGroup(asha, "Goa");
    JsonNode created = createInvite(asha, group, addGuest(asha, group, "Karthik"));
    String token = created.get("token").asString();

    byte[] stored =
        dsl.select(GROUP_INVITES.TOKEN_HASH)
            .from(GROUP_INVITES)
            .where(GROUP_INVITES.ID.eq(UUID.fromString(created.get("invite").get("id").asString())))
            .fetchOne(GROUP_INVITES.TOKEN_HASH);

    assertThat(stored)
        .isEqualTo(
            MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8)));
    assertThat(new String(stored, StandardCharsets.ISO_8859_1)).doesNotContain(token);
  }

  @Test
  void theIdempotencyRecordHoldsNoUsableTokenAndAReplayStillReturnsTheSameWorkingLink()
      throws Exception {
    Session asha = newUser("Asha");
    Session bala = newUser("Bala");
    String group = createGroup(asha, "Goa");
    String guest = addGuest(asha, group, "Karthik");
    String key = UUID.randomUUID().toString();

    JsonNode first =
        read(
            post(asha, invitesPath(group), map("memberId", guest), key)
                .andExpect(status().isCreated()));
    String token = first.get("token").asString();

    String stored =
        dsl.select(IDEMPOTENCY_KEYS.RESPONSE)
            .from(IDEMPOTENCY_KEYS)
            .where(IDEMPOTENCY_KEYS.KEY.eq(key))
            .fetchOne(IDEMPOTENCY_KEYS.RESPONSE)
            .data();
    assertThat(stored).doesNotContain(token).doesNotContain("token");
    assertThat(stored).contains(first.get("invite").get("id").asString());

    JsonNode replay =
        read(
            post(asha, invitesPath(group), map("memberId", guest), key)
                .andExpect(status().isCreated())
                .andExpect(
                    org.springframework.test.web.servlet.result.MockMvcResultMatchers.header()
                        .string("Idempotent-Replayed", "true")));
    assertThat(replay.get("token").asString()).isEqualTo(token);
    assertThat(replay.get("invite")).isEqualTo(first.get("invite"));
    post(bala, "/api/v1/invites/preview", map("token", replay.get("token").asString()), null)
        .andExpect(status().isOk());
  }

  @Test
  void tokensOfDifferentInvitesDiffer() throws Exception {
    Session asha = newUser("Asha");
    String group = createGroup(asha, "Goa");
    String guest = addGuest(asha, group, "Karthik");

    String a = createInvite(asha, group, guest).get("token").asString();
    String b = createInvite(asha, group, guest).get("token").asString();

    assertThat(a).isNotEqualTo(b);
  }

  @Test
  void expiryOfOneAndThirtyDaysIsHonouredAndOthersAreRefused() throws Exception {
    Session asha = newUser("Asha");
    String group = createGroup(asha, "Goa");
    String guest = addGuest(asha, group, "Karthik");

    for (int days : new int[] {1, 30}) {
      JsonNode created =
          read(
              post(asha, invitesPath(group), map("memberId", guest, "expiryDays", days))
                  .andExpect(status().isCreated()));
      long hours =
          Duration.between(
                  OffsetDateTime.now(),
                  OffsetDateTime.parse(created.get("invite").get("expiresAt").asString()))
              .toHours();
      assertThat(hours).isBetween(days * 24L - 1, days * 24L);
    }
    for (int bad : new int[] {0, 2, 8, 31, -7, 365}) {
      post(asha, invitesPath(group), map("memberId", guest, "expiryDays", bad))
          .andExpect(status().isBadRequest())
          .andExpect(jsonPath("$.errors[0].field").value("expiryDays"));
    }
  }

  @Test
  void listShowsInvitesWithoutTokensToAnyMemberAndNotToOutsiders() throws Exception {
    Session asha = newUser("Asha");
    Session bala = newUser("Bala");
    Session outsider = newUser("Outsider");
    String group = createGroup(asha, "Goa");
    join(group, bala);
    JsonNode created = createInvite(asha, group, addGuest(asha, group, "Karthik"));

    String asMember =
        get(bala, invitesPath(group))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();

    assertThat(json.readTree(asMember)).hasSize(1);
    assertThat(asMember).doesNotContain(created.get("token").asString()).doesNotContain("token");
    get(outsider, invitesPath(group)).andExpect(status().isNotFound());
  }

  @Test
  void onlyTheOwnerCreatesAndOnlyForGuestsOfThisGroup() throws Exception {
    Session asha = newUser("Asha");
    Session bala = newUser("Bala");
    Session outsider = newUser("Outsider");
    String group = createGroup(asha, "Goa");
    String other = createGroup(newUser("Other"), "Other");
    UUID balaMember = join(group, bala);
    String guest = addGuest(asha, group, "Karthik");

    post(bala, invitesPath(group), map("memberId", guest)).andExpect(status().isForbidden());
    post(outsider, invitesPath(group), map("memberId", guest)).andExpect(status().isNotFound());
    post(asha, invitesPath(group), map("memberId", balaMember.toString()))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.type").value("urn:kanakku:problem:not-a-guest"));
    post(asha, invitesPath(group), map("memberId", UUID.randomUUID().toString()))
        .andExpect(status().isNotFound());
    post(asha, invitesPath(other), map("memberId", guest)).andExpect(status().isNotFound());
    assertThat(dsl.fetchCount(GROUP_INVITES, GROUP_INVITES.GROUP_ID.eq(UUID.fromString(group))))
        .isZero();
  }

  @Test
  void repeatingTheKeyReturnsTheFirstInviteAndTokenOnly() throws Exception {
    Session asha = newUser("Asha");
    String group = createGroup(asha, "Goa");
    String guest = addGuest(asha, group, "Karthik");
    String key = UUID.randomUUID().toString();

    String first =
        read(post(asha, invitesPath(group), map("memberId", guest), key)).get("token").asString();
    post(asha, invitesPath(group), map("memberId", guest), key)
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.token").value(first));

    assertThat(dsl.fetchCount(GROUP_INVITES, GROUP_INVITES.GROUP_ID.eq(UUID.fromString(group))))
        .isEqualTo(1);
  }

  @Test
  void theOwnerRevokesWithAVersionCheck() throws Exception {
    Session asha = newUser("Asha");
    String group = createGroup(asha, "Goa");
    String invite =
        createInvite(asha, group, addGuest(asha, group, "Karthik"))
            .get("invite")
            .get("id")
            .asString();

    post(asha, invitesPath(group) + "/" + invite + "/revoke", map("version", 5), null)
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.type").value("urn:kanakku:problem:stale-version"));
    post(asha, invitesPath(group) + "/" + invite + "/revoke", map("version", 0), null)
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("REVOKED"))
        .andExpect(jsonPath("$.version").value(1));
    post(asha, invitesPath(group) + "/" + invite + "/revoke", map("version", 1), null)
        .andExpect(status().isConflict());
  }

  @Test
  void aMemberOrOutsiderCannotRevoke() throws Exception {
    Session asha = newUser("Asha");
    Session bala = newUser("Bala");
    Session outsider = newUser("Outsider");
    String group = createGroup(asha, "Goa");
    join(group, bala);
    String invite =
        createInvite(asha, group, addGuest(asha, group, "Karthik"))
            .get("invite")
            .get("id")
            .asString();

    post(bala, invitesPath(group) + "/" + invite + "/revoke", map("version", 0), null)
        .andExpect(status().isForbidden());
    post(outsider, invitesPath(group) + "/" + invite + "/revoke", map("version", 0), null)
        .andExpect(status().isNotFound());
    post(asha, invitesPath(group) + "/" + UUID.randomUUID() + "/revoke", map("version", 0), null)
        .andExpect(status().isNotFound());
    assertThat(
            dsl.select(GROUP_INVITES.REVOKED_AT)
                .from(GROUP_INVITES)
                .where(GROUP_INVITES.ID.eq(UUID.fromString(invite)))
                .fetchOne(GROUP_INVITES.REVOKED_AT))
        .isNull();
  }

  // --- T-13: preview and claim

  @Test
  void previewShowsOnlyTheGroupAndGuestNames() throws Exception {
    Session asha = newUser("Asha");
    Session bala = newUser("Bala");
    String group = createGroup(asha, "Goa Trip");
    String token =
        createInvite(asha, group, addGuest(asha, group, "Karthik")).get("token").asString();

    JsonNode preview =
        read(
            post(bala, "/api/v1/invites/preview", map("token", token), null)
                .andExpect(status().isOk()));

    assertThat(preview.propertyNames()).containsExactlyInAnyOrder("groupName", "guestName");
    assertThat(preview.get("groupName").asString()).isEqualTo("Goa Trip");
    assertThat(preview.get("guestName").asString()).isEqualTo("Karthik");
  }

  @Test
  void claimingTakesTheGuestsPlaceAndKeepsHistoryAndBalance() throws Exception {
    Session asha = newUser("Asha");
    Session karthik = newUser("Karthik Real");
    String group = createGroup(asha, "Goa");
    String ashaMember =
        read(get(asha, "/api/v1/groups/" + group + "/members")).get(0).get("id").asString();
    String guest = addGuest(asha, group, "Karthik");
    owe(group, ashaMember, guest, 7500);
    owe(group, guest, ashaMember, 1200);
    String token = createInvite(asha, group, guest).get("token").asString();
    int linesBefore =
        dsl.fetchCount(JOURNAL_LINES, JOURNAL_LINES.MEMBER_ID.eq(UUID.fromString(guest)));

    post(karthik, "/api/v1/invites/claim", map("token", token), null)
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.id").value(group))
        .andExpect(jsonPath("$.myRole").value("MEMBER"));

    JsonNode members = read(get(asha, "/api/v1/groups/" + group + "/members"));
    JsonNode claimed =
        members
            .valueStream()
            .filter(m -> m.get("id").asString().equals(guest))
            .findFirst()
            .orElseThrow();
    assertThat(members).hasSize(2);
    assertThat(claimed.get("userId").asString()).isEqualTo(karthik.id().toString());
    assertThat(claimed.get("displayName").asString()).isEqualTo("Karthik Real");
    assertThat(claimed.get("guest").asBoolean()).isFalse();
    assertThat(claimed.get("balances").get(0).get("amountMinor").asLong()).isEqualTo(-6300);
    assertThat(dsl.fetchCount(JOURNAL_LINES, JOURNAL_LINES.MEMBER_ID.eq(UUID.fromString(guest))))
        .isEqualTo(linesBefore);
    get(karthik, "/api/v1/groups/" + group).andExpect(status().isOk());
  }

  @Test
  void anInviteWorksOnce() throws Exception {
    Session asha = newUser("Asha");
    Session first = newUser("First");
    Session second = newUser("Second");
    String group = createGroup(asha, "Goa");
    String guest = addGuest(asha, group, "Karthik");
    String token = createInvite(asha, group, guest).get("token").asString();
    post(first, "/api/v1/invites/claim", map("token", token), null).andExpect(status().isOk());

    post(second, "/api/v1/invites/claim", map("token", token), null)
        .andExpect(status().isGone())
        .andExpect(jsonPath("$.type").value("urn:kanakku:problem:invite-unusable"));
    post(second, "/api/v1/invites/preview", map("token", token), null).andExpect(status().isGone());

    get(second, "/api/v1/groups/" + group).andExpect(status().isNotFound());
    assertThat(
            dsl.select(GROUP_MEMBERS.USER_ID)
                .from(GROUP_MEMBERS)
                .where(GROUP_MEMBERS.ID.eq(UUID.fromString(guest)))
                .fetchOne(GROUP_MEMBERS.USER_ID))
        .isEqualTo(first.id());
  }

  @Test
  void anExpiredInviteIsRefusedAndCreatesNoMember() throws Exception {
    Session asha = newUser("Asha");
    Session bala = newUser("Bala");
    String group = createGroup(asha, "Goa");
    String guest = addGuest(asha, group, "Karthik");
    JsonNode created = createInvite(asha, group, guest);
    dsl.update(GROUP_INVITES)
        .set(GROUP_INVITES.EXPIRES_AT, OffsetDateTime.now().minusMinutes(1))
        .where(GROUP_INVITES.ID.eq(UUID.fromString(created.get("invite").get("id").asString())))
        .execute();

    post(bala, "/api/v1/invites/claim", map("token", created.get("token").asString()), null)
        .andExpect(status().isGone());

    assertThat(
            dsl.select(GROUP_MEMBERS.USER_ID)
                .from(GROUP_MEMBERS)
                .where(GROUP_MEMBERS.ID.eq(UUID.fromString(guest)))
                .fetchOne(GROUP_MEMBERS.USER_ID))
        .isNull();
    get(bala, "/api/v1/groups/" + group).andExpect(status().isNotFound());
    assertThat(read(get(asha, invitesPath(group))).get(0).get("status").asString())
        .isEqualTo("EXPIRED");
  }

  @Test
  void aRevokedInviteIsRefused() throws Exception {
    Session asha = newUser("Asha");
    Session bala = newUser("Bala");
    String group = createGroup(asha, "Goa");
    JsonNode created = createInvite(asha, group, addGuest(asha, group, "Karthik"));
    post(
            asha,
            invitesPath(group) + "/" + created.get("invite").get("id").asString() + "/revoke",
            map("version", 0),
            null)
        .andExpect(status().isOk());

    post(bala, "/api/v1/invites/claim", map("token", created.get("token").asString()), null)
        .andExpect(status().isGone());
    post(bala, "/api/v1/invites/preview", map("token", created.get("token").asString()), null)
        .andExpect(status().isGone());
  }

  @Test
  void anUnknownTokenLooksTheSameAsADeadOne() throws Exception {
    Session bala = newUser("Bala");

    post(bala, "/api/v1/invites/claim", map("token", "nope-nope-nope"), null)
        .andExpect(status().isGone())
        .andExpect(jsonPath("$.type").value("urn:kanakku:problem:invite-unusable"));
    post(bala, "/api/v1/invites/claim", map("token", " "), null).andExpect(status().isBadRequest());
  }

  @Test
  void aUserAlreadyInTheGroupCannotClaimAndTheInviteSurvives() throws Exception {
    Session asha = newUser("Asha");
    Session bala = newUser("Bala");
    String group = createGroup(asha, "Goa");
    join(group, bala);
    String token =
        createInvite(asha, group, addGuest(asha, group, "Karthik")).get("token").asString();

    post(bala, "/api/v1/invites/claim", map("token", token), null)
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.type").value("urn:kanakku:problem:already-member"));
    post(asha, "/api/v1/invites/claim", map("token", token), null).andExpect(status().isConflict());
    assertThat(read(get(asha, invitesPath(group))).get(0).get("status").asString())
        .isEqualTo("ACTIVE");
  }

  @Test
  void claimAndPreviewNeedASession() throws Exception {
    mvc.perform(
            org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post(
                    "/api/v1/invites/preview")
                .with(dev.kanakku.support.Csrf.token(mvc))
                .contentType("application/json")
                .content("{\"token\":\"x\"}"))
        .andExpect(status().isUnauthorized());
  }
}
