package dev.kanakku.groups.service;

import static dev.kanakku.jooq.Tables.CLAIM_SUGGESTIONS;
import static dev.kanakku.jooq.Tables.GROUP_INVITES;
import static dev.kanakku.jooq.Tables.GROUP_MEMBERS;
import static dev.kanakku.jooq.Tables.JOURNAL_ENTRIES;
import static dev.kanakku.jooq.Tables.MEMBER_BALANCES;
import static dev.kanakku.jooq.Tables.USERS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import dev.kanakku.identity.api.PhoneVerified;
import dev.kanakku.support.ApiTest;
import dev.kanakku.support.TestData;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import tools.jackson.databind.JsonNode;

/** Listing, declining and confirming a claim suggestion. */
class ClaimSuggestionFlowTest extends ApiTest {

  @Autowired ApplicationEventPublisher events;

  /** An Owner, a member who verified the guest's number, and the guest, with a suggestion. */
  record Scene(Session owner, Session user, String group, String guest, String suggestion) {}

  Scene scene() throws Exception {
    Session asha = newUser("Asha");
    Session bala = newUser("Bala");
    String group = createGroup(asha, "Goa");
    join(group, bala);
    String phone = TestData.phone();
    String guest =
        read(post(
                    asha,
                    "/api/v1/groups/" + group + "/guests",
                    map("name", "Bala (guest)", "phone", phone.substring(3)))
                .andExpect(status().isCreated()))
            .get("id")
            .asString();
    dsl.update(USERS)
        .set(USERS.PHONE_E164, phone)
        .set(USERS.PHONE_VERIFIED_AT, OffsetDateTime.now())
        .where(USERS.ID.eq(bala.id()))
        .execute();
    events.publishEvent(new PhoneVerified(bala.id()));
    JsonNode list = read(get(bala, "/api/v1/claim-suggestions").andExpect(status().isOk()));
    String suggestion =
        list.valueStream()
            .filter(s -> s.get("guestMemberId").asString().equals(guest))
            .findFirst()
            .orElseThrow()
            .get("id")
            .asString();
    return new Scene(asha, bala, group, guest, suggestion);
  }

  UUID memberRowOf(String group, Session user) {
    return dsl.select(GROUP_MEMBERS.ID)
        .from(GROUP_MEMBERS)
        .where(GROUP_MEMBERS.GROUP_ID.eq(UUID.fromString(group)))
        .and(GROUP_MEMBERS.USER_ID.eq(user.id()))
        .fetchOne(GROUP_MEMBERS.ID);
  }

  long balanceOf(String group, String member) {
    return dsl.select(
            org.jooq.impl.DSL.coalesce(org.jooq.impl.DSL.sum(MEMBER_BALANCES.BALANCE_MINOR), 0L))
        .from(MEMBER_BALANCES)
        .where(MEMBER_BALANCES.GROUP_ID.eq(UUID.fromString(group)))
        .and(MEMBER_BALANCES.MEMBER_ID.eq(UUID.fromString(member)))
        .fetchOne(0, Long.class);
  }

  long groupSum(String group) {
    return dsl.select(
            org.jooq.impl.DSL.coalesce(org.jooq.impl.DSL.sum(MEMBER_BALANCES.BALANCE_MINOR), 0L))
        .from(MEMBER_BALANCES)
        .where(MEMBER_BALANCES.GROUP_ID.eq(UUID.fromString(group)))
        .fetchOne(0, Long.class);
  }

  String path(Scene s, String action) {
    return "/api/v1/claim-suggestions/" + s.suggestion() + "/" + action;
  }

  // --- list

  @Test
  void theListShowsMySuggestionWithTheGroupAndGuestNames() throws Exception {
    Scene s = scene();

    JsonNode list = read(get(s.user(), "/api/v1/claim-suggestions").andExpect(status().isOk()));

    JsonNode mine =
        list.valueStream()
            .filter(x -> x.get("id").asString().equals(s.suggestion()))
            .findFirst()
            .orElseThrow();
    assertThat(mine.get("groupName").asString()).isEqualTo("Goa");
    assertThat(mine.get("guestName").asString()).isEqualTo("Bala (guest)");
    assertThat(mine.get("status").asString()).isEqualTo("PENDING");
    assertThat(mine.get("version").asInt()).isZero();
  }

  @Test
  void otherPeopleNeverSeeMySuggestions() throws Exception {
    Scene s = scene();

    assertThat(read(get(s.owner(), "/api/v1/claim-suggestions")).toString())
        .doesNotContain(s.suggestion());
    assertThat(read(get(newUser("Stranger"), "/api/v1/claim-suggestions"))).isEmpty();
  }

  @Test
  void leavingTheGroupHidesTheSuggestion() throws Exception {
    Scene s = scene();
    post(s.user(), "/api/v1/groups/" + s.group() + "/leave", null, null)
        .andExpect(status().isNoContent());

    assertThat(read(get(s.user(), "/api/v1/claim-suggestions")).toString())
        .doesNotContain(s.suggestion());
    post(s.user(), path(s, "confirm"), map("version", 0), null).andExpect(status().isNotFound());
  }

  @Test
  void aGuestWhoWasRemovedOrClaimedDropsOutOfTheList() throws Exception {
    Scene s = scene();
    dsl.update(GROUP_MEMBERS)
        .set(GROUP_MEMBERS.LEFT_AT, OffsetDateTime.now())
        .where(GROUP_MEMBERS.ID.eq(UUID.fromString(s.guest())))
        .execute();

    assertThat(read(get(s.user(), "/api/v1/claim-suggestions")).toString())
        .doesNotContain(s.suggestion());
    post(s.user(), path(s, "confirm"), map("version", 0), null)
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.type").value("urn:kanakku:problem:suggestion-resolved"));
  }

  // --- decline

  @Test
  void decliningLeavesTheGuestUnmergedAndRemovesTheSuggestion() throws Exception {
    Scene s = scene();
    owe(s.group(), memberRowOf(s.group(), s.owner()).toString(), s.guest(), 500);

    post(s.user(), path(s, "decline"), map("version", 0), null)
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("DECLINED"))
        .andExpect(jsonPath("$.version").value(1));

    var guest =
        dsl.selectFrom(GROUP_MEMBERS)
            .where(GROUP_MEMBERS.ID.eq(UUID.fromString(s.guest())))
            .fetchOne();
    assertThat(guest.getLeftAt()).isNull();
    assertThat(guest.getMergedIntoMemberId()).isNull();
    assertThat(balanceOf(s.group(), s.guest())).isEqualTo(-500);
    assertThat(read(get(s.user(), "/api/v1/claim-suggestions")).toString())
        .doesNotContain(s.suggestion());
    // It cannot be answered twice.
    post(s.user(), path(s, "confirm"), map("version", 1), null).andExpect(status().isConflict());
    post(s.user(), path(s, "decline"), map("version", 1), null).andExpect(status().isConflict());
  }

  @Test
  void aStaleVersionIsRefusedOnDeclineAndConfirm() throws Exception {
    Scene s = scene();

    post(s.user(), path(s, "decline"), map("version", 7), null)
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.type").value("urn:kanakku:problem:stale-version"));
    post(s.user(), path(s, "confirm"), map("version", 7), null).andExpect(status().isConflict());
    assertThat(
            dsl.select(CLAIM_SUGGESTIONS.STATUS)
                .from(CLAIM_SUGGESTIONS)
                .where(CLAIM_SUGGESTIONS.ID.eq(UUID.fromString(s.suggestion())))
                .fetchOne(CLAIM_SUGGESTIONS.STATUS))
        .isEqualTo("PENDING");
  }

  @Test
  void aMissingVersionIsAValidationProblem() throws Exception {
    Scene s = scene();

    post(s.user(), path(s, "confirm"), map(), null).andExpect(status().isBadRequest());
  }

  // --- confirm

  @Test
  void confirmingMovesTheGuestsBalanceOntoMyRowAndRetiresTheGuest() throws Exception {
    Scene s = scene();
    String ownerRow = memberRowOf(s.group(), s.owner()).toString();
    String myRow = memberRowOf(s.group(), s.user()).toString();
    owe(s.group(), ownerRow, s.guest(), 700); // the guest owes 700
    owe(s.group(), myRow, ownerRow, 200); // I am owed 200

    post(s.user(), path(s, "confirm"), map("version", 0), null)
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("CONFIRMED"));

    assertThat(balanceOf(s.group(), s.guest())).isZero();
    assertThat(balanceOf(s.group(), myRow)).isEqualTo(200 - 700);
    assertThat(groupSum(s.group())).isZero();
    var guest =
        dsl.selectFrom(GROUP_MEMBERS)
            .where(GROUP_MEMBERS.ID.eq(UUID.fromString(s.guest())))
            .fetchOne();
    assertThat(guest.getLeftAt()).isNotNull();
    assertThat(guest.getMergedIntoMemberId()).isEqualTo(UUID.fromString(myRow));
    assertThat(guest.getUserId()).isNull();
    assertThat(
            dsl.fetchCount(
                JOURNAL_ENTRIES,
                JOURNAL_ENTRIES
                    .GROUP_ID
                    .eq(UUID.fromString(s.group()))
                    .and(JOURNAL_ENTRIES.KIND.eq("MERGE"))))
        .isEqualTo(1);
    // The retired guest is no longer a current member; I remain exactly one member.
    JsonNode members = read(get(s.owner(), "/api/v1/groups/" + s.group() + "/members"));
    assertThat(members.valueStream().map(m -> m.get("displayName").asString()))
        .containsExactly("Asha", "Bala");
    assertThat(read(get(s.user(), "/api/v1/claim-suggestions")).toString())
        .doesNotContain(s.suggestion());
  }

  @Test
  void confirmingIsRefusedAndMarkedStaleWhenIHaveLostTheVerifiedNumber() throws Exception {
    Scene s = scene();
    String myRow = memberRowOf(s.group(), s.user()).toString();
    owe(s.group(), myRow, s.guest(), 300);
    dsl.update(USERS).setNull(USERS.PHONE_VERIFIED_AT).where(USERS.ID.eq(s.user().id())).execute();

    post(s.user(), path(s, "confirm"), map("version", 0), null)
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.type").value("urn:kanakku:problem:suggestion-resolved"));

    assertStaleAndUntouched(s);
  }

  @Test
  void confirmingIsRefusedAndMarkedStaleWhenIHoldADifferentVerifiedNumberNow() throws Exception {
    Scene s = scene();
    dsl.update(USERS)
        .set(USERS.PHONE_E164, TestData.phone())
        .where(USERS.ID.eq(s.user().id()))
        .execute();

    post(s.user(), path(s, "confirm"), map("version", 0), null)
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.type").value("urn:kanakku:problem:suggestion-resolved"));

    assertStaleAndUntouched(s);
  }

  @Test
  void confirmingIsRefusedAndMarkedStaleWhenTheGuestsNumberChanged() throws Exception {
    Scene s = scene();
    dsl.update(GROUP_MEMBERS)
        .set(GROUP_MEMBERS.GUEST_PHONE_E164, TestData.phone())
        .where(GROUP_MEMBERS.ID.eq(UUID.fromString(s.guest())))
        .execute();

    post(s.user(), path(s, "confirm"), map("version", 0), null)
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.type").value("urn:kanakku:problem:suggestion-resolved"));

    assertStaleAndUntouched(s);
  }

  @Test
  void confirmingIsRefusedAndMarkedStaleWhenTheGuestHasNoNumberAnymore() throws Exception {
    Scene s = scene();
    dsl.update(GROUP_MEMBERS)
        .setNull(GROUP_MEMBERS.GUEST_PHONE_E164)
        .where(GROUP_MEMBERS.ID.eq(UUID.fromString(s.guest())))
        .execute();

    post(s.user(), path(s, "confirm"), map("version", 0), null).andExpect(status().isConflict());

    assertStaleAndUntouched(s);
  }

  /** The suggestion is STALE and committed, the guest is still a current, unmerged guest. */
  void assertStaleAndUntouched(Scene s) throws Exception {
    assertThat(
            dsl.select(CLAIM_SUGGESTIONS.STATUS)
                .from(CLAIM_SUGGESTIONS)
                .where(CLAIM_SUGGESTIONS.ID.eq(UUID.fromString(s.suggestion())))
                .fetchOne(CLAIM_SUGGESTIONS.STATUS))
        .isEqualTo("STALE");
    var guest =
        dsl.selectFrom(GROUP_MEMBERS)
            .where(GROUP_MEMBERS.ID.eq(UUID.fromString(s.guest())))
            .fetchOne();
    assertThat(guest.getLeftAt()).isNull();
    assertThat(guest.getMergedIntoMemberId()).isNull();
    assertThat(
            dsl.fetchCount(
                JOURNAL_ENTRIES,
                JOURNAL_ENTRIES
                    .GROUP_ID
                    .eq(UUID.fromString(s.group()))
                    .and(JOURNAL_ENTRIES.KIND.eq("MERGE"))))
        .isZero();
    assertThat(groupSum(s.group())).isZero();
    assertThat(read(get(s.user(), "/api/v1/claim-suggestions")).toString())
        .doesNotContain(s.suggestion());
    post(s.user(), path(s, "confirm"), map("version", 1), null).andExpect(status().isConflict());
  }

  @Test
  void confirmingAGuestWithNothingOwedPostsNoEntryAndStillRetiresIt() throws Exception {
    Scene s = scene();

    post(s.user(), path(s, "confirm"), map("version", 0), null).andExpect(status().isOk());

    assertThat(
            dsl.fetchCount(
                JOURNAL_ENTRIES,
                JOURNAL_ENTRIES
                    .GROUP_ID
                    .eq(UUID.fromString(s.group()))
                    .and(JOURNAL_ENTRIES.KIND.eq("MERGE"))))
        .isZero();
    assertThat(
            dsl.select(GROUP_MEMBERS.LEFT_AT)
                .from(GROUP_MEMBERS)
                .where(GROUP_MEMBERS.ID.eq(UUID.fromString(s.guest())))
                .fetchOne(GROUP_MEMBERS.LEFT_AT))
        .isNotNull();
  }

  @Test
  void confirmingRevokesTheGuestsLiveInvites() throws Exception {
    Scene s = scene();
    post(s.owner(), "/api/v1/groups/" + s.group() + "/invites", map("memberId", s.guest()))
        .andExpect(status().isCreated());

    post(s.user(), path(s, "confirm"), map("version", 0), null).andExpect(status().isOk());

    assertThat(
            dsl.fetchCount(
                GROUP_INVITES,
                GROUP_INVITES
                    .MEMBER_ID
                    .eq(UUID.fromString(s.guest()))
                    .and(GROUP_INVITES.REVOKED_AT.isNull())
                    .and(GROUP_INVITES.USED_AT.isNull())))
        .isZero();
  }

  @Test
  void someoneElsesSuggestionIsA404ToEveryoneButItsUser() throws Exception {
    Scene s = scene();

    post(s.owner(), path(s, "confirm"), map("version", 0), null).andExpect(status().isNotFound());
    post(s.owner(), path(s, "decline"), map("version", 0), null).andExpect(status().isNotFound());
    post(newUser("Stranger"), path(s, "confirm"), map("version", 0), null)
        .andExpect(status().isNotFound());
    post(
            s.user(),
            "/api/v1/claim-suggestions/" + UUID.randomUUID() + "/confirm",
            map("version", 0),
            null)
        .andExpect(status().isNotFound());

    assertThat(balanceOf(s.group(), s.guest())).isZero();
    assertThat(
            dsl.select(GROUP_MEMBERS.LEFT_AT)
                .from(GROUP_MEMBERS)
                .where(GROUP_MEMBERS.ID.eq(UUID.fromString(s.guest())))
                .fetchOne(GROUP_MEMBERS.LEFT_AT))
        .isNull();
  }

  @Test
  void anArchivedGroupTakesNoConfirm() throws Exception {
    Scene s = scene();
    post(s.owner(), "/api/v1/groups/" + s.group() + "/archive", map("version", 0), null)
        .andExpect(status().isOk());

    post(s.user(), path(s, "confirm"), map("version", 0), null)
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.type").value("urn:kanakku:problem:group-archived"));
  }

  @Test
  void aGuestClaimedByInviteMeanwhileCannotAlsoBeMerged() throws Exception {
    Scene s = scene();
    Session chitra = newUser("Chitra");
    String token =
        read(post(
                s.owner(), "/api/v1/groups/" + s.group() + "/invites", map("memberId", s.guest())))
            .get("token")
            .asString();
    post(chitra, "/api/v1/invites/claim", map("token", token), null).andExpect(status().isOk());

    post(s.user(), path(s, "confirm"), map("version", 0), null)
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.type").value("urn:kanakku:problem:suggestion-resolved"));
  }

  @Test
  void twoConfirmsAtOnceMergeExactlyOnce() throws Exception {
    Scene s = scene();
    String ownerRow = memberRowOf(s.group(), s.owner()).toString();
    String myRow = memberRowOf(s.group(), s.user()).toString();
    owe(s.group(), ownerRow, s.guest(), 900);

    List<Integer> statuses =
        List.of(
                CompletableFuture.supplyAsync(() -> confirmStatus(s)),
                CompletableFuture.supplyAsync(() -> confirmStatus(s)))
            .stream()
            .map(CompletableFuture::join)
            .toList();

    assertThat(statuses).containsExactlyInAnyOrder(200, 409);
    assertThat(balanceOf(s.group(), myRow)).isEqualTo(-900);
    assertThat(groupSum(s.group())).isZero();
    assertThat(
            dsl.fetchCount(
                JOURNAL_ENTRIES,
                JOURNAL_ENTRIES
                    .GROUP_ID
                    .eq(UUID.fromString(s.group()))
                    .and(JOURNAL_ENTRIES.KIND.eq("MERGE"))))
        .isEqualTo(1);
  }

  int confirmStatus(Scene s) {
    try {
      return post(s.user(), path(s, "confirm"), map("version", 0), null)
          .andReturn()
          .getResponse()
          .getStatus();
    } catch (Exception e) {
      throw new IllegalStateException(e);
    }
  }
}
