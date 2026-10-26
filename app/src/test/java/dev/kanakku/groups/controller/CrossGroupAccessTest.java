package dev.kanakku.groups.controller;

import static org.assertj.core.api.Assertions.assertThat;

import dev.kanakku.support.ApiTest;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.ResultActions;

/**
 * Every endpoint under a group id, called as a non-member and as a plain Member. A non-member gets
 * the same 404 a made-up group id gets and learns nothing; a Member gets only what a Member may.
 */
class CrossGroupAccessTest extends ApiTest {

  /** One call: how to make it, the status a Member should get, and the status a non-member gets. */
  record Call(String name, int memberStatus, Caller caller) {}

  @FunctionalInterface
  interface Caller {
    ResultActions call(Session who, Ids ids) throws Exception;
  }

  record Ids(String group, String owner, String guest, String invite) {}

  List<Call> calls() {
    return List.of(
        new Call("GET group", 200, (s, i) -> get(s, "/api/v1/groups/" + i.group())),
        new Call(
            "PATCH group",
            403,
            (s, i) ->
                patch(s, "/api/v1/groups/" + i.group(), map("name", "Hijacked", "version", 0))),
        new Call(
            "POST archive",
            403,
            (s, i) -> post(s, "/api/v1/groups/" + i.group() + "/archive", map("version", 0), null)),
        new Call(
            "POST unarchive",
            403,
            (s, i) ->
                post(s, "/api/v1/groups/" + i.group() + "/unarchive", map("version", 0), null)),
        new Call("GET members", 200, (s, i) -> get(s, "/api/v1/groups/" + i.group() + "/members")),
        new Call(
            "POST guests",
            201,
            (s, i) -> post(s, "/api/v1/groups/" + i.group() + "/guests", map("name", "Planted"))),
        new Call(
            "POST user-search",
            403,
            (s, i) -> post(s, "/api/v1/groups/" + i.group() + "/user-search", map("q", "Asha"))),
        new Call(
            "POST user-invites",
            403,
            (s, i) ->
                post(
                    s,
                    "/api/v1/groups/" + i.group() + "/user-invites",
                    map("userId", s.id().toString()))),
        new Call(
            "POST members",
            403,
            (s, i) ->
                post(
                    s,
                    "/api/v1/groups/" + i.group() + "/members",
                    map("userId", s.id().toString()))),
        new Call(
            "DELETE member",
            403,
            (s, i) ->
                delete(s, "/api/v1/groups/" + i.group() + "/members/" + i.guest() + "?version=0")),
        new Call(
            "POST write-off",
            403,
            (s, i) ->
                post(
                    s,
                    "/api/v1/groups/" + i.group() + "/members/" + i.guest() + "/write-offs",
                    map("absorberMemberIds", List.of(i.owner())))),
        new Call(
            "POST invites",
            403,
            (s, i) ->
                post(s, "/api/v1/groups/" + i.group() + "/invites", map("memberId", i.guest()))),
        new Call("GET invites", 200, (s, i) -> get(s, "/api/v1/groups/" + i.group() + "/invites")),
        new Call(
            "POST revoke",
            403,
            (s, i) ->
                post(
                    s,
                    "/api/v1/groups/" + i.group() + "/invites/" + i.invite() + "/revoke",
                    map("version", 0),
                    null)),
        new Call(
            "POST leave",
            204,
            (s, i) -> post(s, "/api/v1/groups/" + i.group() + "/leave", null, null)));
  }

  Ids setUp(Session owner, String name) throws Exception {
    String group = createGroup(owner, name);
    String ownerMember =
        read(get(owner, "/api/v1/groups/" + group + "/members")).get(0).get("id").asString();
    String guest = addGuest(owner, group, "Karthik");
    String invite =
        read(post(owner, "/api/v1/groups/" + group + "/invites", map("memberId", guest)))
            .get("invite")
            .get("id")
            .asString();
    return new Ids(group, ownerMember, guest, invite);
  }

  @Test
  void aNonMemberGetsTheSame404AsAMadeUpGroupAndLearnsNothing() throws Exception {
    Session asha = newUser("Asha");
    Session outsider = newUser("Outsider");
    Ids real = setUp(asha, "Secret Trip");
    Ids fake = new Ids(UUID.randomUUID().toString(), real.owner(), real.guest(), real.invite());

    for (Call c : calls()) {
      var onReal = c.caller().call(outsider, real).andReturn().getResponse();
      var onFake = c.caller().call(outsider, fake).andReturn().getResponse();

      assertThat(onReal.getStatus()).as(c.name() + " on a real group").isEqualTo(404);
      assertThat(onFake.getStatus()).as(c.name() + " on a made-up group").isEqualTo(404);
      String body = onReal.getContentAsString();
      assertThat(body).as(c.name()).doesNotContain("Secret Trip", "Karthik", "Asha");
      assertThat(json.readTree(body).get("type"))
          .as(c.name())
          .isEqualTo(json.readTree(onFake.getContentAsString()).get("type"));
    }
    // And nothing changed.
    assertThat(read(get(asha, "/api/v1/groups/" + real.group())).get("name").asString())
        .isEqualTo("Secret Trip");
    assertThat(read(get(asha, "/api/v1/groups/" + real.group() + "/members"))).hasSize(2);
  }

  @Test
  void aPlainMemberGetsOnlyWhatAMemberMay() throws Exception {
    Session asha = newUser("Asha");
    Ids ids = setUp(asha, "Goa");

    for (Call c : calls()) {
      Session member = newUser("Member for " + c.name());
      join(ids.group(), member);

      int status = c.caller().call(member, ids).andReturn().getResponse().getStatus();

      assertThat(status).as(c.name()).isEqualTo(c.memberStatus());
    }
    // The Member-only changes that were allowed: guests were added; nothing else moved.
    assertThat(read(get(asha, "/api/v1/groups/" + ids.group())).get("name").asString())
        .isEqualTo("Goa");
    assertThat(read(get(asha, "/api/v1/groups/" + ids.group())).get("archivedAt").isNull())
        .isTrue();
  }

  @Test
  void aFormerMemberIsANonMemberAgain() throws Exception {
    Session asha = newUser("Asha");
    Session bala = newUser("Bala");
    Ids ids = setUp(asha, "Goa");
    join(ids.group(), bala);
    post(bala, "/api/v1/groups/" + ids.group() + "/leave", null, null);

    for (Call c : calls()) {
      assertThat(c.caller().call(bala, ids).andReturn().getResponse().getStatus())
          .as(c.name())
          .isEqualTo(404);
    }
  }
}
