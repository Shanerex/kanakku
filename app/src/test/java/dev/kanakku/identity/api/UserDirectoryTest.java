package dev.kanakku.identity.api;

import static dev.kanakku.jooq.Tables.USERS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.kanakku.platform.problem.ProblemException;
import dev.kanakku.support.PostgresIntegrationTest;
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
    assertThat(hit.displayName()).isEqualTo("Someone Else");
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
  void leavesOutInviteOnlyUsersAndExcludedIds() {
    String tag = tag();
    UUID allowed = user("Allowed " + tag, tag + "-a@example.com", "ALLOW");
    UUID excluded = user("Excluded " + tag, tag + "-b@example.com", "ALLOW");
    UUID inviteOnly = user("Hidden " + tag, tag + "-c@example.com", "INVITE_ONLY");

    List<UserMatch> found = directory.search(tag, Set.of(excluded), 10);

    assertThat(found).extracting(UserMatch::id).containsExactly(allowed);
    assertThat(found).extracting(UserMatch::id).doesNotContain(inviteOnly, excluded);
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
}
