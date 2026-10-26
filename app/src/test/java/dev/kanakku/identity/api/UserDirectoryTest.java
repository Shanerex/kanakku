package dev.kanakku.identity.api;

import static dev.kanakku.jooq.Tables.USERS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.kanakku.platform.problem.ProblemException;
import dev.kanakku.support.PostgresIntegrationTest;
import dev.kanakku.support.TestData;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.IntStream;
import org.jooq.DSLContext;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
class UserDirectoryTest extends PostgresIntegrationTest {

  @Autowired UserDirectory directory;
  @Autowired DSLContext dsl;

  UUID user(String name, String email, String directAdd) {
    UUID id = UUID.randomUUID();
    dsl.insertInto(USERS)
        .set(USERS.ID, id)
        .set(USERS.EMAIL, email)
        .set(USERS.DISPLAY_NAME, name)
        .set(USERS.DIRECT_ADD, directAdd)
        .execute();
    return id;
  }

  static String tag() {
    return "zq" + UUID.randomUUID().toString().replace("-", "").substring(0, 10);
  }

  static String email() {
    return "u-" + UUID.randomUUID() + "@example.com";
  }

  @Test
  void findsByNameOrEmailCaseInsensitivelyAndMasksTheEmail() {
    String tag = tag();
    UUID byName = user("Priya " + tag, email(), "ALLOW");
    UUID byEmail = user("Someone Else", "x." + tag + "@example.com", "ALLOW");

    List<UserMatch> found = directory.search(tag.toUpperCase(), Set.of(), 10);

    assertThat(found).extracting(UserMatch::id).containsExactlyInAnyOrder(byName, byEmail);
    UserMatch hit = found.stream().filter(m -> m.id().equals(byEmail)).findFirst().orElseThrow();
    assertThat(hit.maskedEmail()).isEqualTo("x***@example.com");
    assertThat(hit.maskedPhone()).isNull();
    assertThat(hit.matchedBy()).isEqualTo(MatchedBy.EMAIL);
    assertThat(hit.displayName()).isEqualTo("Someone Else");
    UserMatch byNameHit =
        found.stream().filter(m -> m.id().equals(byName)).findFirst().orElseThrow();
    assertThat(byNameHit.matchedBy()).isEqualTo(MatchedBy.NAME);
  }

  @Test
  void rejectsAQueryUnderThreeCharacters() {
    for (String q : new String[] {"", "a", "ab", "  a ", null}) {
      assertThatThrownBy(() -> directory.search(q, Set.of(), 10))
          .isInstanceOfSatisfying(
              ProblemException.class, e -> assertThat(e.problem().getStatus()).isEqualTo(400));
    }
  }

  @Test
  void returnsAtMostTenResultsEvenWhenAskedForMore() {
    String tag = tag();
    IntStream.range(0, 14)
        .forEach(i -> user("Member " + tag + " " + i, i + "-" + tag + "@example.com", "ALLOW"));

    assertThat(directory.search(tag, Set.of(), 10)).hasSize(10);
    assertThat(directory.search(tag, Set.of(), 500)).hasSize(10);
    assertThat(directory.search(tag, Set.of(), 3)).hasSize(3);
  }

  @Test
  void includesInviteOnlyUsersFlaggedAndLeavesOutExcludedIds() {
    String tag = tag();
    UUID allowed = user("Allowed " + tag, tag + "-a@example.com", "ALLOW");
    UUID excluded = user("Excluded " + tag, tag + "-b@example.com", "ALLOW");
    UUID inviteOnly = user("Invited " + tag, tag + "-c@example.com", "INVITE_ONLY");

    List<UserMatch> found = directory.search(tag, Set.of(excluded), 10);

    assertThat(found).extracting(UserMatch::id).containsExactlyInAnyOrder(allowed, inviteOnly);
    assertThat(found).extracting(UserMatch::id).doesNotContain(excluded);
    assertThat(
            found.stream()
                .filter(m -> m.id().equals(allowed))
                .findFirst()
                .orElseThrow()
                .directAdd())
        .isTrue();
    assertThat(
            found.stream()
                .filter(m -> m.id().equals(inviteOnly))
                .findFirst()
                .orElseThrow()
                .directAdd())
        .isFalse();
  }

  UUID userWithPhone(String name, String phone, boolean verified, String directAdd) {
    UUID id = user(name, email(), directAdd);
    dsl.update(USERS)
        .set(USERS.PHONE_E164, phone)
        .set(USERS.PHONE_VERIFIED_AT, verified ? java.time.OffsetDateTime.now() : null)
        .where(USERS.ID.eq(id))
        .execute();
    return id;
  }

  @Test
  void aPhoneQueryFindsPartialMatchesVerifiedOrNotAndShowsOnlyAMaskedNumber() {
    String digits = TestData.phoneDigits();
    UUID verified = userWithPhone("Verified " + tag(), "+91" + digits, true, "ALLOW");
    UUID unverified = userWithPhone("Unverified " + tag(), "+91" + digits, false, "INVITE_ONLY");

    for (String q :
        new String[] {
          digits.substring(2, 7),
          digits.substring(0, 5) + " " + digits.substring(5),
          "+91 " + digits,
          "0" + digits
        }) {
      List<UserMatch> found = directory.search(q, Set.of(), 10);
      assertThat(found).extracting(UserMatch::id).contains(verified, unverified);
    }
    UserMatch hit =
        directory.search(digits, Set.of(), 10).stream()
            .filter(m -> m.id().equals(unverified))
            .findFirst()
            .orElseThrow();
    assertThat(hit.matchedBy()).isEqualTo(MatchedBy.PHONE);
    assertThat(hit.maskedEmail()).isNull();
    assertThat(hit.maskedPhone()).isEqualTo("+91 ••••• ••" + digits.substring(8));
    assertThat(hit.directAdd()).isFalse();
  }

  @Test
  void aPhoneQueryUnderFourDigitsIsAProblem() {
    for (String q : new String[] {"123", "98", "+91", "0 12"}) {
      assertThatThrownBy(() -> directory.search(q, Set.of(), 10))
          .isInstanceOfSatisfying(
              ProblemException.class,
              e -> {
                assertThat(e.problem().getStatus()).isEqualTo(400);
                assertThat(e.problem().getType().toString()).endsWith("search-too-short");
              });
    }
  }

  @Test
  void aPhoneQueryNeverMatchesNamesOrEmailsAndSkipsUsersWithNoNumber() {
    String digits = TestData.phoneDigits();
    UUID noPhone = user("Name " + digits, digits + "@example.com", "ALLOW");

    assertThat(directory.search(digits, Set.of(), 10))
        .extracting(UserMatch::id)
        .doesNotContain(noPhone);
  }

  @Test
  void aPhoneSearchExcludesGivenUsersAndCapsAtTen() {
    String digits = TestData.phoneDigits();
    UUID excluded = userWithPhone("E " + tag(), "+91" + digits, true, "ALLOW");
    for (int i = 0; i < 12; i++) {
      userWithPhone("P " + tag(), "+91" + digits, false, "ALLOW");
    }

    List<UserMatch> found = directory.search(digits, Set.of(excluded), 500);

    assertThat(found).hasSize(10);
    assertThat(found).extracting(UserMatch::id).doesNotContain(excluded);
  }

  @Test
  void aSearchHitsToStringShowsNoContactData() {
    String digits = TestData.phoneDigits();
    userWithPhone("T " + tag(), "+91" + digits, false, "ALLOW");

    UserMatch hit = directory.search(digits, Set.of(), 10).get(0);

    assertThat(hit.toString()).doesNotContain(digits.substring(2)).doesNotContain("@");
  }

  @Test
  void aWildcardInTheQueryIsTakenLiterally() {
    String tag = tag();
    user("Plain " + tag, tag + "@example.com", "ALLOW");

    assertThat(directory.search("%%%", Set.of(), 10)).isEmpty();
    assertThat(directory.search("___", Set.of(), 10)).isEmpty();
  }

  @Test
  void summariesReturnNamesOnlyForTheIdsAsked() {
    UUID a = user("Asha " + tag(), email(), "ALLOW");
    UUID b = user("Bala " + tag(), email(), "INVITE_ONLY");

    Map<UUID, UserSummary> summaries = directory.summaries(Set.of(a, b, UUID.randomUUID()));

    assertThat(summaries).containsOnlyKeys(a, b);
    assertThat(summaries.get(a).displayName()).startsWith("Asha");
    assertThat(directory.summaries(Set.of())).isEmpty();
  }

  @Test
  void allowsDirectAddFollowsTheStoredSettingAndUnknownUsersAreRefused() {
    UUID allow = user("Al " + tag(), email(), "ALLOW");
    UUID invite = user("In " + tag(), email(), "INVITE_ONLY");

    assertThat(directory.allowsDirectAdd(allow)).isTrue();
    assertThat(directory.allowsDirectAdd(invite)).isFalse();
    assertThat(directory.allowsDirectAdd(UUID.randomUUID())).isFalse();
  }

  @Test
  void aPhoneNumberNeverChangesWhetherAUserAllowsDirectAdd() {
    UUID unverified = userWithPhone("Un " + tag(), TestData.phone(), false, "ALLOW");
    UUID verified = userWithPhone("Ver " + tag(), TestData.phone(), true, "ALLOW");
    UUID inviteOnlyUnverified =
        userWithPhone("Both " + tag(), TestData.phone(), false, "INVITE_ONLY");

    assertThat(directory.allowsDirectAdd(unverified)).isTrue();
    assertThat(directory.allowsDirectAdd(verified)).isTrue();
    assertThat(directory.allowsDirectAdd(inviteOnlyUnverified)).isFalse();
  }

  @Test
  void aPhoneMatchSaysWhetherTheNumberIsVerifiedAndOtherMatchesSayNothing() {
    String digits = TestData.phoneDigits();
    String tag = tag();
    UUID unverified = userWithPhone("U " + tag, "+91" + digits, false, "ALLOW");
    UUID verified = userWithPhone("V " + tag, TestData.phone(), true, "ALLOW");

    UserMatch byPhone = directory.search(digits, Set.of(), 10).get(0);
    assertThat(byPhone.id()).isEqualTo(unverified);
    assertThat(byPhone.phoneVerified()).isFalse();

    String verifiedDigits = directory.verifiedPhone(verified).orElseThrow().substring(3);
    assertThat(directory.search(verifiedDigits, Set.of(), 10).get(0).phoneVerified()).isTrue();

    assertThat(directory.search("U " + tag, Set.of(), 10))
        .singleElement()
        .satisfies(m -> assertThat(m.phoneVerified()).isNull());
  }

  @Test
  void verifiedPhoneHolderFindsOnlyTheAccountThatVerifiedIt() {
    String shared = TestData.phone();
    userWithPhone("Squat " + tag(), shared, false, "ALLOW");
    UUID holder = userWithPhone("Hold " + tag(), shared, true, "ALLOW");

    assertThat(directory.verifiedPhoneHolder(shared)).contains(holder);
    assertThat(directory.verifiedPhoneHolder(TestData.phone())).isEmpty();
  }

  @Test
  void verifiedPhoneIsGivenOnlyForAVerifiedNumber() {
    String phone = TestData.phone();
    UUID verified = userWithPhone("Ver " + tag(), phone, true, "ALLOW");
    UUID unverified = userWithPhone("Un " + tag(), TestData.phone(), false, "ALLOW");
    UUID none = user("None " + tag(), email(), "ALLOW");

    assertThat(directory.verifiedPhone(verified)).contains(phone);
    assertThat(directory.verifiedPhone(unverified)).isEmpty();
    assertThat(directory.verifiedPhone(none)).isEmpty();
    assertThat(directory.verifiedPhone(UUID.randomUUID())).isEmpty();
  }
}
