package dev.kanakku.groups.controller;

import static dev.kanakku.jooq.Tables.GROUPS;
import static dev.kanakku.jooq.Tables.GROUP_MEMBERS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import dev.kanakku.support.ApiTest;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;

class GroupCreateTest extends ApiTest {

  Map<String, Object> valid() {
    return map("name", "Goa Trip", "type", "TRIP", "currency", "INR", "cover", "color:mango");
  }

  @Test
  void createsAGroupWithTheCallerAsItsOnlyOwner() throws Exception {
    Session asha = newUser("Asha");

    JsonNode created =
        read(
            post(asha, "/api/v1/groups", valid())
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("Goa Trip"))
                .andExpect(jsonPath("$.type").value("TRIP"))
                .andExpect(jsonPath("$.currency").value("INR"))
                .andExpect(jsonPath("$.cover").value("color:mango"))
                .andExpect(jsonPath("$.myRole").value("OWNER"))
                .andExpect(jsonPath("$.version").value(0))
                .andExpect(jsonPath("$.archivedAt").doesNotExist()));

    UUID id = UUID.fromString(created.get("id").asString());
    var members = dsl.selectFrom(GROUP_MEMBERS).where(GROUP_MEMBERS.GROUP_ID.eq(id)).fetch();
    assertThat(members).hasSize(1);
    assertThat(members.get(0).getUserId()).isEqualTo(asha.id());
    assertThat(members.get(0).getRole()).isEqualTo("OWNER");
  }

  @Test
  void coverIsOptionalAndTheNameIsTrimmed() throws Exception {
    Session asha = newUser("Asha");

    post(asha, "/api/v1/groups", map("name", "  Flat 4B  ", "type", "HOME", "currency", "USD"))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.name").value("Flat 4B"))
        .andExpect(jsonPath("$.cover").doesNotExist());
  }

  @Test
  void acceptsAnEmojiPreset() throws Exception {
    Session asha = newUser("Asha");
    Map<String, Object> body = valid();
    body.put("cover", "emoji:🏖️");

    post(asha, "/api/v1/groups", body)
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.cover").value("emoji:🏖"));
  }

  @Test
  void invalidInputIsRefusedWithAProblemNamingTheField() throws Exception {
    Session asha = newUser("Asha");
    List<Map.Entry<String, Object>> bad =
        List.of(
            Map.entry("currency", "ZZZ"),
            Map.entry("currency", "inr"),
            Map.entry("currency", ""),
            Map.entry("name", ""),
            Map.entry("name", "   "),
            Map.entry("name", "x".repeat(61)),
            Map.entry("cover", "color:chartreuse"),
            Map.entry("cover", "emoji:x"),
            Map.entry("cover", "https://evil.example/a.png"));
    for (Map.Entry<String, Object> change : bad) {
      Map<String, Object> body = valid();
      body.put(change.getKey(), change.getValue());
      post(asha, "/api/v1/groups", body)
          .andExpect(status().isBadRequest())
          .andExpect(jsonPath("$.type").value("urn:kanakku:problem:validation"))
          .andExpect(jsonPath("$.errors[0].field").value(change.getKey()));
    }
    Map<String, Object> noType = valid();
    noType.remove("type");
    post(asha, "/api/v1/groups", noType).andExpect(status().isBadRequest());
    Map<String, Object> badType = valid();
    badType.put("type", "CASTLE");
    post(asha, "/api/v1/groups", badType).andExpect(status().isBadRequest());
  }

  @Test
  void aMissingIdempotencyKeyIs400() throws Exception {
    Session asha = newUser("Asha");
    post(asha, "/api/v1/groups", valid(), null)
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.type").value("urn:kanakku:problem:idempotency-key-required"));
  }

  @Test
  void repeatingTheKeyReturnsTheFirstResultAndCreatesOneGroup() throws Exception {
    Session asha = newUser("Asha");
    String key = UUID.randomUUID().toString();
    Map<String, Object> body = valid();
    body.put("name", "Once " + key);

    String first =
        read(post(asha, "/api/v1/groups", body, key).andExpect(status().isCreated()))
            .get("id")
            .asString();
    var second =
        post(asha, "/api/v1/groups", body, key)
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.id").value(first));

    assertThat(second.andReturn().getResponse().getHeader("Idempotent-Replayed")).isEqualTo("true");
    assertThat(dsl.fetchCount(GROUPS, GROUPS.NAME.eq("Once " + key))).isEqualTo(1);
  }

  @Test
  void theSameKeyWithAnotherBodyIs422() throws Exception {
    Session asha = newUser("Asha");
    String key = UUID.randomUUID().toString();
    post(asha, "/api/v1/groups", valid(), key).andExpect(status().isCreated());
    Map<String, Object> other = valid();
    other.put("name", "Different");

    post(asha, "/api/v1/groups", other, key).andExpect(status().isUnprocessableEntity());
  }

  @Test
  void anotherUsersKeyNeverReplaysSomeoneElsesGroup() throws Exception {
    Session asha = newUser("Asha");
    Session bala = newUser("Bala");
    String key = UUID.randomUUID().toString();
    String ashas = read(post(asha, "/api/v1/groups", valid(), key)).get("id").asString();

    String balas =
        read(post(bala, "/api/v1/groups", valid(), key).andExpect(status().isCreated()))
            .get("id")
            .asString();

    assertThat(balas).isNotEqualTo(ashas);
  }

  @Test
  void requiresASession() throws Exception {
    mvc.perform(
            org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get(
                "/api/v1/groups"))
        .andExpect(status().isUnauthorized());
  }
}
