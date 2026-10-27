package dev.kanakku.support;

import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import dev.kanakku.identity.service.SignUpService;
import dev.kanakku.ledger.api.EntryKind;
import dev.kanakku.ledger.api.Journal;
import dev.kanakku.ledger.api.JournalEntry;
import dev.kanakku.ledger.api.JournalLine;
import jakarta.servlet.http.Cookie;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.jooq.DSLContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** Base for endpoint tests: real sign-in cookies, CSRF, and short helpers for the groups API. */
@SpringBootTest
@AutoConfigureMockMvc
public abstract class ApiTest extends PostgresIntegrationTest {

  protected static final String PASSWORD = "correct horse battery";

  @Autowired protected MockMvc mvc;
  @Autowired protected JsonMapper json;
  @Autowired protected DSLContext dsl;
  @Autowired protected Journal journal;
  @Autowired SignUpService signUp;

  /** A signed-in user: the id and the session cookie. */
  public record Session(UUID id, String email, String name, Cookie cookie) {}

  protected Session newUser(String name) throws Exception {
    String email = TestData.email();
    var profile = signUp.signUp(email, PASSWORD, name);
    Cookie cookie =
        mvc.perform(
                MockMvcRequestBuilders.post("/api/v1/auth/sign-in")
                    .with(Csrf.token(mvc))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(json.writeValueAsString(Map.of("email", email, "password", PASSWORD))))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getCookie("SESSION");
    return new Session(profile.id(), email, name, cookie);
  }

  protected ResultActions post(Session s, String path, Object body) throws Exception {
    return post(s, path, body, UUID.randomUUID().toString());
  }

  /** Posts from a fresh client address, so per-address limits never bleed between tests. */
  protected ResultActions post(Session s, String path, Object body, String key) throws Exception {
    return postFrom(freshIp(), s, path, body, key);
  }

  /** Posts as the client at {@code ip}, for tests of per-address limits. */
  protected ResultActions postFrom(String ip, Session s, String path, Object body, String key)
      throws Exception {
    MockHttpServletRequestBuilder request =
        MockMvcRequestBuilders.post(path)
            .with(Csrf.token(mvc))
            .with(
                r -> {
                  r.setRemoteAddr(ip);
                  return r;
                })
            .cookie(s.cookie())
            .contentType(MediaType.APPLICATION_JSON);
    if (key != null) {
      request.header("Idempotency-Key", key);
    }
    if (body != null) {
      request.content(json.writeValueAsString(body));
    }
    return mvc.perform(request);
  }

  /** A public-looking IPv4 address unlikely to repeat within a run. */
  protected static String freshIp() {
    UUID u = UUID.randomUUID();
    return "10."
        + (u.getMostSignificantBits() & 0x7f)
        + "."
        + (u.getLeastSignificantBits() & 0x7f)
        + "."
        + (1 + (u.getMostSignificantBits() >>> 8 & 0x7f));
  }

  /** A request post-processor that sends from a fresh client address (see {@link #freshIp}). */
  protected static org.springframework.test.web.servlet.request.RequestPostProcessor freshClient() {
    return r -> {
      r.setRemoteAddr(freshIp());
      return r;
    };
  }

  protected ResultActions get(Session s, String path) throws Exception {
    return mvc.perform(MockMvcRequestBuilders.get(path).cookie(s.cookie()));
  }

  protected ResultActions patch(Session s, String path, Object body) throws Exception {
    return mvc.perform(
        MockMvcRequestBuilders.patch(path)
            .with(Csrf.token(mvc))
            .cookie(s.cookie())
            .contentType(MediaType.APPLICATION_JSON)
            .content(json.writeValueAsString(body)));
  }

  protected ResultActions put(Session s, String path, Object body) throws Exception {
    return mvc.perform(
        MockMvcRequestBuilders.put(path)
            .with(Csrf.token(mvc))
            .cookie(s.cookie())
            .contentType(MediaType.APPLICATION_JSON)
            .content(json.writeValueAsString(body)));
  }

  protected ResultActions delete(Session s, String path) throws Exception {
    return mvc.perform(
        MockMvcRequestBuilders.delete(path).with(Csrf.token(mvc)).cookie(s.cookie()));
  }

  protected JsonNode read(ResultActions result) throws Exception {
    return json.readTree(result.andReturn().getResponse().getContentAsString());
  }

  protected Map<String, Object> map(Object... pairs) {
    Map<String, Object> map = new HashMap<>();
    for (int i = 0; i < pairs.length; i += 2) {
      map.put((String) pairs[i], pairs[i + 1]);
    }
    return map;
  }

  /** Creates a group through the API and returns its id. */
  protected String createGroup(Session owner, String name) throws Exception {
    return read(post(owner, "/api/v1/groups", map("name", name, "type", "TRIP", "currency", "INR"))
            .andExpect(status().isCreated()))
        .get("id")
        .asString();
  }

  /** Adds a guest through the API and returns the new member id. */
  protected String addGuest(Session by, String groupId, String name) throws Exception {
    return read(post(by, "/api/v1/groups/" + groupId + "/guests", map("name", name))
            .andExpect(status().isCreated()))
        .get("id")
        .asString();
  }

  /** Direct-adds a registered user through the API and returns the new member id. */
  protected String directAdd(Session owner, String groupId, Session user) throws Exception {
    return read(post(
                owner,
                "/api/v1/groups/" + groupId + "/members",
                map("userId", user.id().toString()))
            .andExpect(status().isCreated()))
        .get("id")
        .asString();
  }

  /** Posts an EXPENSE entry where {@code debtor} owes {@code creditor} an amount. */
  protected void owe(String groupId, String creditorMemberId, String debtorMemberId, long minor) {
    journal.post(
        new JournalEntry(
            UUID.fromString(groupId),
            EntryKind.EXPENSE,
            null,
            List.of(
                new JournalLine(UUID.fromString(creditorMemberId), "INR", minor),
                new JournalLine(UUID.fromString(debtorMemberId), "INR", -minor))));
  }

  /** Makes the user a Member of the group by inserting the row, without going through the API. */
  protected UUID join(String groupId, Session user) {
    UUID id = UUID.randomUUID();
    dsl.insertInto(dev.kanakku.jooq.Tables.GROUP_MEMBERS)
        .set(dev.kanakku.jooq.Tables.GROUP_MEMBERS.ID, id)
        .set(dev.kanakku.jooq.Tables.GROUP_MEMBERS.GROUP_ID, UUID.fromString(groupId))
        .set(dev.kanakku.jooq.Tables.GROUP_MEMBERS.USER_ID, user.id())
        .set(dev.kanakku.jooq.Tables.GROUP_MEMBERS.ROLE, "MEMBER")
        .execute();
    return id;
  }

  protected void setDirectAdd(Session user, String value) {
    dsl.update(dev.kanakku.jooq.Tables.USERS)
        .set(dev.kanakku.jooq.Tables.USERS.DIRECT_ADD, value)
        .where(dev.kanakku.jooq.Tables.USERS.ID.eq(user.id()))
        .execute();
  }
}
