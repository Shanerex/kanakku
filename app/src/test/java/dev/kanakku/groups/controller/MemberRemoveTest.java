package dev.kanakku.groups.controller;

import static dev.kanakku.jooq.Tables.GROUP_MEMBERS;
import static dev.kanakku.jooq.Tables.JOURNAL_ENTRIES;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import dev.kanakku.ledger.api.Balances;
import dev.kanakku.ledger.api.MemberBalance;
import dev.kanakku.support.ApiTest;
import dev.kanakku.support.Seed;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.JsonNode;

class MemberRemoveTest extends ApiTest {

  @Autowired Balances balances;
  @Autowired TransactionTemplate tx;

  /** A group with an Owner (asha), the owner's member id and one guest who owes the Owner. */
  record Setup(Session asha, String group, String ashaMember, String guest) {}

  Setup withDebt(long owed) throws Exception {
    Session asha = newUser("Asha");
    String group = createGroup(asha, "Goa");
    String ashaMember =
        read(get(asha, "/api/v1/groups/" + group + "/members")).get(0).get("id").asString();
    String guest = addGuest(asha, group, "Karthik");
    if (owed != 0) {
      owe(group, ashaMember, guest, owed);
    }
    return new Setup(asha, group, ashaMember, guest);
  }

  int version(Session who, String group, String member) throws Exception {
    return read(get(who, "/api/v1/groups/" + group + "/members"))
        .valueStream()
        .filter(m -> m.get("id").asString().equals(member))
        .findFirst()
        .orElseThrow()
        .get("version")
        .asInt();
  }

  org.springframework.test.web.servlet.ResultActions remove(
      Session who, String group, String member, int version) throws Exception {
    return delete(who, "/api/v1/groups/" + group + "/members/" + member + "?version=" + version);
  }

  org.springframework.test.web.servlet.ResultActions writeOff(
      Session who, String group, String member, List<String> absorbers) throws Exception {
    return post(
        who,
        "/api/v1/groups/" + group + "/members/" + member + "/write-offs",
        map("absorberMemberIds", absorbers));
  }

  long groupSum(String group) {
    return balances
        .ofGroup(UUID.fromString(group), Seed.caller(dsl, UUID.fromString(group)))
        .stream()
        .mapToLong(MemberBalance::amountMinor)
        .sum();
  }

  // --- remove

  @Test
  void theOwnerRemovesAMemberWithAZeroBalance() throws Exception {
    Setup s = withDebt(0);

    remove(s.asha(), s.group(), s.guest(), version(s.asha(), s.group(), s.guest()))
        .andExpect(status().isNoContent());

    assertThat(
            read(get(s.asha(), "/api/v1/groups/" + s.group() + "/members"))
                .valueStream()
                .map(m -> m.get("id").asString()))
        .doesNotContain(s.guest());
    assertThat(
            dsl.select(GROUP_MEMBERS.LEFT_AT)
                .from(GROUP_MEMBERS)
                .where(GROUP_MEMBERS.ID.eq(UUID.fromString(s.guest())))
                .fetchOne(GROUP_MEMBERS.LEFT_AT))
        .isNotNull();
  }

  @Test
  void aRemovedUserLosesAccessToTheGroup() throws Exception {
    Setup s = withDebt(0);
    Session bala = newUser("Bala");
    String balaMember = directAdd(s.asha(), s.group(), bala);

    remove(s.asha(), s.group(), balaMember, version(s.asha(), s.group(), balaMember))
        .andExpect(status().isNoContent());

    get(bala, "/api/v1/groups/" + s.group()).andExpect(status().isNotFound());
    get(bala, "/api/v1/groups")
        .andExpect(
            org.springframework.test.web.servlet.result.MockMvcResultMatchers.content().json("[]"));
  }

  @Test
  void aMemberWhoOwesIsRefusedWithTheAmountAndStaysInTheGroup() throws Exception {
    Setup s = withDebt(4500);

    remove(s.asha(), s.group(), s.guest(), 0)
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.type").value("urn:kanakku:problem:member-has-balance"))
        .andExpect(jsonPath("$.balances[0].currency").value("INR"))
        .andExpect(jsonPath("$.balances[0].amountMinor").value(-4500));

    assertThat(read(get(s.asha(), "/api/v1/groups/" + s.group() + "/members"))).hasSize(2);
  }

  @Test
  void aMemberWhoIsOwedIsRefusedToo() throws Exception {
    Setup s = withDebt(0);
    owe(s.group(), s.guest(), s.ashaMember(), 900);

    remove(s.asha(), s.group(), s.guest(), 0)
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.balances[0].amountMinor").value(900));
  }

  @Test
  void aStaleVersionIsRefused() throws Exception {
    Setup s = withDebt(0);

    remove(s.asha(), s.group(), s.guest(), 9)
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.type").value("urn:kanakku:problem:stale-version"));
    assertThat(read(get(s.asha(), "/api/v1/groups/" + s.group() + "/members"))).hasSize(2);
  }

  @Test
  void aNonOwnerCannotRemoveAnotherMember() throws Exception {
    Setup s = withDebt(0);
    Session bala = newUser("Bala");
    join(s.group(), bala);
    Session outsider = newUser("Outsider");

    remove(bala, s.group(), s.guest(), 0)
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.type").value("urn:kanakku:problem:owner-only"));
    remove(outsider, s.group(), s.guest(), 0).andExpect(status().isNotFound());
    assertThat(read(get(s.asha(), "/api/v1/groups/" + s.group() + "/members"))).hasSize(3);
  }

  @Test
  void theOwnerCannotBeRemovedNotEvenBySelf() throws Exception {
    Setup s = withDebt(0);

    remove(s.asha(), s.group(), s.ashaMember(), 0)
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.type").value("urn:kanakku:problem:last-owner"));
    assertThat(read(get(s.asha(), "/api/v1/groups/" + s.group() + "/members"))).hasSize(2);
  }

  @Test
  void anUnknownMemberOrOneFromAnotherGroupIs404() throws Exception {
    Setup s = withDebt(0);
    Setup other = withDebt(0);

    remove(s.asha(), s.group(), UUID.randomUUID().toString(), 0).andExpect(status().isNotFound());
    remove(s.asha(), s.group(), other.guest(), 0).andExpect(status().isNotFound());
  }

  @Test
  void anArchivedGroupRefusesRemoval() throws Exception {
    Setup s = withDebt(0);
    post(s.asha(), "/api/v1/groups/" + s.group() + "/archive", map("version", 0), null)
        .andExpect(status().isOk());

    remove(s.asha(), s.group(), s.guest(), 0)
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.type").value("urn:kanakku:problem:group-archived"));
  }

  @Test
  void aPostThatLandsWhileTheRemovalWaitsIsStillSeen() throws Exception {
    Setup s = withDebt(0);
    CountDownLatch locked = new CountDownLatch(1);
    ExecutorService pool = Executors.newSingleThreadExecutor();
    Future<?> poster =
        pool.submit(
            () ->
                tx.executeWithoutResult(
                    status -> {
                      dsl.select(GROUP_MEMBERS.ID)
                          .from(GROUP_MEMBERS)
                          .where(GROUP_MEMBERS.ID.eq(UUID.fromString(s.guest())))
                          .forUpdate()
                          .fetch();
                      locked.countDown();
                      sleep(800);
                      owe(s.group(), s.ashaMember(), s.guest(), 1000);
                    }));
    locked.await();

    remove(s.asha(), s.group(), s.guest(), 0)
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.type").value("urn:kanakku:problem:member-has-balance"));

    poster.get();
    pool.shutdown();
    assertThat(read(get(s.asha(), "/api/v1/groups/" + s.group() + "/members"))).hasSize(2);
  }

  static void sleep(long millis) {
    try {
      Thread.sleep(millis);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
    }
  }

  // --- write-off

  @Test
  void afterAWriteOffTheMemberCanBeRemovedAndTheGroupStillSumsToZero() throws Exception {
    Setup s = withDebt(4500);

    writeOff(s.asha(), s.group(), s.guest(), List.of(s.ashaMember()))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.writtenOff[0].currency").value("INR"))
        .andExpect(jsonPath("$.writtenOff[0].amountMinor").value(-4500));

    assertThat(groupSum(s.group())).isZero();
    JsonNode list = read(get(s.asha(), "/api/v1/groups/" + s.group() + "/members"));
    assertThat(list.get(0).get("balances")).isEmpty();
    remove(s.asha(), s.group(), s.guest(), version(s.asha(), s.group(), s.guest()))
        .andExpect(status().isNoContent());
    assertThat(groupSum(s.group())).isZero();
    assertThat(
            dsl.fetchCount(
                JOURNAL_ENTRIES,
                JOURNAL_ENTRIES
                    .GROUP_ID
                    .eq(UUID.fromString(s.group()))
                    .and(JOURNAL_ENTRIES.KIND.eq("WRITE_OFF"))))
        .isEqualTo(1);
  }

  @Test
  void theWriteOffIsSplitEquallyOverTheChosenAbsorbersOldestFirst() throws Exception {
    Setup s = withDebt(1000);
    Session bala = newUser("Bala");
    String balaMember = directAdd(s.asha(), s.group(), bala);
    String third = addGuest(s.asha(), s.group(), "Third");

    // The caller lists the absorbers in any order; the split follows join order.
    writeOff(s.asha(), s.group(), s.guest(), List.of(third, balaMember, s.ashaMember()))
        .andExpect(status().isCreated());

    long ashaBalance =
        balances
            .ofMember(UUID.fromString(s.group()), UUID.fromString(s.ashaMember()), s.asha().id())
            .get(0)
            .amountMinor();
    long balaBalance =
        balances
            .ofMember(UUID.fromString(s.group()), UUID.fromString(balaMember), s.asha().id())
            .get(0)
            .amountMinor();
    long thirdBalance =
        balances
            .ofMember(UUID.fromString(s.group()), UUID.fromString(third), s.asha().id())
            .get(0)
            .amountMinor();
    // Owner was owed 1000; the guest's -1000 is split 334/333/333 oldest first: owner gets the
    // extra unit.
    assertThat(ashaBalance).isEqualTo(1000 - 334);
    assertThat(balaBalance).isEqualTo(-333);
    assertThat(thirdBalance).isEqualTo(-333);
    assertThat(groupSum(s.group())).isZero();
  }

  @Test
  void aWriteOffWaitsForAbsorberRowsSoADepartedMemberNeverHoldsABalance() throws Exception {
    Setup s = withDebt(1000);
    Session bala = newUser("Bala");
    String balaMember = directAdd(s.asha(), s.group(), bala);
    CountDownLatch locked = new CountDownLatch(1);
    ExecutorService pool = Executors.newSingleThreadExecutor();
    Future<?> leaver =
        pool.submit(
            () ->
                tx.executeWithoutResult(
                    status -> {
                      dsl.select(GROUP_MEMBERS.ID)
                          .from(GROUP_MEMBERS)
                          .where(GROUP_MEMBERS.ID.eq(UUID.fromString(balaMember)))
                          .forUpdate()
                          .fetch();
                      locked.countDown();
                      sleep(800);
                      dsl.update(GROUP_MEMBERS)
                          .set(GROUP_MEMBERS.LEFT_AT, java.time.OffsetDateTime.now())
                          .where(GROUP_MEMBERS.ID.eq(UUID.fromString(balaMember)))
                          .execute();
                    }));
    locked.await();

    writeOff(s.asha(), s.group(), s.guest(), List.of(balaMember))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.type").value("urn:kanakku:problem:invalid-absorbers"));

    leaver.get();
    pool.shutdown();
    assertThat(
            balances.ofMember(
                UUID.fromString(s.group()), UUID.fromString(balaMember), s.asha().id()))
        .allMatch(b -> b.amountMinor() == 0L);
    assertThat(groupSum(s.group())).isZero();
  }

  @Test
  void anAbsorberListOverFiftyIsRefused() throws Exception {
    Setup s = withDebt(1000);
    List<String> many =
        java.util.stream.Stream.generate(() -> UUID.randomUUID().toString()).limit(51).toList();

    writeOff(s.asha(), s.group(), s.guest(), many)
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errors[0].field").value("absorberMemberIds"));
  }

  @Test
  void badAbsorberListsAreRefused() throws Exception {
    Setup s = withDebt(1000);
    String gone = addGuest(s.asha(), s.group(), "Gone");
    remove(s.asha(), s.group(), gone, version(s.asha(), s.group(), gone))
        .andExpect(status().isNoContent());
    Setup other = withDebt(0);

    writeOff(s.asha(), s.group(), s.guest(), List.of()).andExpect(status().isBadRequest());
    writeOff(s.asha(), s.group(), s.guest(), List.of(s.guest()))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.type").value("urn:kanakku:problem:invalid-absorbers"));
    writeOff(s.asha(), s.group(), s.guest(), List.of(s.ashaMember(), s.ashaMember()))
        .andExpect(status().isBadRequest());
    writeOff(s.asha(), s.group(), s.guest(), List.of(UUID.randomUUID().toString()))
        .andExpect(status().isBadRequest());
    writeOff(s.asha(), s.group(), s.guest(), List.of(gone)).andExpect(status().isBadRequest());
    writeOff(s.asha(), s.group(), s.guest(), List.of(other.ashaMember()))
        .andExpect(status().isBadRequest());

    assertThat(
            balances
                .ofMember(UUID.fromString(s.group()), UUID.fromString(s.guest()), s.asha().id())
                .get(0)
                .amountMinor())
        .isEqualTo(-1000);
  }

  @Test
  void aNonOwnerCannotWriteOff() throws Exception {
    Setup s = withDebt(1000);
    Session bala = newUser("Bala");
    join(s.group(), bala);
    Session outsider = newUser("Outsider");

    writeOff(bala, s.group(), s.guest(), List.of(s.ashaMember())).andExpect(status().isForbidden());
    writeOff(outsider, s.group(), s.guest(), List.of(s.ashaMember()))
        .andExpect(status().isNotFound());

    assertThat(
            balances
                .ofMember(UUID.fromString(s.group()), UUID.fromString(s.guest()), s.asha().id())
                .get(0)
                .amountMinor())
        .isEqualTo(-1000);
  }

  @Test
  void writeOffsThatCrossEachOtherNeitherDeadlockNorFail() throws Exception {
    ExecutorService pool = Executors.newFixedThreadPool(2);
    try {
      for (int round = 0; round < 15; round++) {
        Setup s = withDebt(1000);
        CountDownLatch go = new CountDownLatch(1);
        // Guest onto Owner and Owner onto guest: each takes the other's member row as an absorber.
        Future<Integer> one =
            pool.submit(() -> statusOfWriteOff(go, s, s.guest(), List.of(s.ashaMember())));
        Future<Integer> two =
            pool.submit(() -> statusOfWriteOff(go, s, s.ashaMember(), List.of(s.guest())));
        go.countDown();

        assertThat(one.get(30, java.util.concurrent.TimeUnit.SECONDS)).isEqualTo(201);
        assertThat(two.get(30, java.util.concurrent.TimeUnit.SECONDS)).isEqualTo(201);
        assertThat(groupSum(s.group())).isZero();
      }
    } finally {
      pool.shutdownNow();
    }
  }

  int statusOfWriteOff(CountDownLatch go, Setup s, String target, List<String> absorbers)
      throws Exception {
    go.await();
    return writeOff(s.asha(), s.group(), target, absorbers).andReturn().getResponse().getStatus();
  }

  @Test
  void repeatingTheWriteOffKeyPostsItOnce() throws Exception {
    Setup s = withDebt(1000);
    String key = UUID.randomUUID().toString();
    String path = "/api/v1/groups/" + s.group() + "/members/" + s.guest() + "/write-offs";

    post(s.asha(), path, map("absorberMemberIds", List.of(s.ashaMember())), key)
        .andExpect(status().isCreated());
    post(s.asha(), path, map("absorberMemberIds", List.of(s.ashaMember())), key)
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.writtenOff[0].amountMinor").value(-1000));

    assertThat(
            dsl.fetchCount(
                JOURNAL_ENTRIES,
                JOURNAL_ENTRIES
                    .GROUP_ID
                    .eq(UUID.fromString(s.group()))
                    .and(JOURNAL_ENTRIES.KIND.eq("WRITE_OFF"))))
        .isEqualTo(1);
  }

  @Test
  void writingOffAMemberAtZeroWritesNothing() throws Exception {
    Setup s = withDebt(0);

    writeOff(s.asha(), s.group(), s.guest(), List.of(s.ashaMember()))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.writtenOff").isEmpty());
  }

  // --- leave

  @Test
  void aMemberAtZeroLeavesAndLosesAccess() throws Exception {
    Setup s = withDebt(0);
    Session bala = newUser("Bala");
    directAdd(s.asha(), s.group(), bala);

    post(bala, "/api/v1/groups/" + s.group() + "/leave", null, null)
        .andExpect(status().isNoContent());

    get(bala, "/api/v1/groups/" + s.group()).andExpect(status().isNotFound());
    assertThat(read(get(s.asha(), "/api/v1/groups/" + s.group() + "/members"))).hasSize(2);
  }

  @Test
  void aMemberWithABalanceCannotLeaveAndIsToldHowMuch() throws Exception {
    Setup s = withDebt(0);
    Session bala = newUser("Bala");
    String balaMember = directAdd(s.asha(), s.group(), bala);
    owe(s.group(), s.ashaMember(), balaMember, 2500);

    post(bala, "/api/v1/groups/" + s.group() + "/leave", null, null)
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.type").value("urn:kanakku:problem:member-has-balance"))
        .andExpect(jsonPath("$.balances[0].currency").value("INR"))
        .andExpect(jsonPath("$.balances[0].amountMinor").value(-2500));

    get(bala, "/api/v1/groups/" + s.group()).andExpect(status().isOk());
  }

  @Test
  void theOwnerCannotLeave() throws Exception {
    Setup s = withDebt(0);

    post(s.asha(), "/api/v1/groups/" + s.group() + "/leave", null, null)
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.type").value("urn:kanakku:problem:last-owner"));
    get(s.asha(), "/api/v1/groups/" + s.group()).andExpect(status().isOk());
  }

  @Test
  void aNonMemberCannotLeaveAndAnArchivedGroupCannotBeLeft() throws Exception {
    Setup s = withDebt(0);
    Session outsider = newUser("Outsider");
    Session bala = newUser("Bala");
    directAdd(s.asha(), s.group(), bala);

    post(outsider, "/api/v1/groups/" + s.group() + "/leave", null, null)
        .andExpect(status().isNotFound());
    post(s.asha(), "/api/v1/groups/" + s.group() + "/archive", map("version", 0), null)
        .andExpect(status().isOk());
    post(bala, "/api/v1/groups/" + s.group() + "/leave", null, null)
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.type").value("urn:kanakku:problem:group-archived"));
  }
}
