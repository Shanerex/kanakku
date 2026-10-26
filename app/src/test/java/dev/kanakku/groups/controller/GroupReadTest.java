package dev.kanakku.groups.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import dev.kanakku.support.ApiTest;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import tools.jackson.databind.JsonNode;

class GroupReadTest extends ApiTest {

  @Test
  void listsOnlyTheGroupsTheCallerBelongsTo() throws Exception {
    Session asha = newUser("Asha");
    Session bala = newUser("Bala");
    String mine = createGroup(asha, "Ashas group");
    String balas = createGroup(bala, "Balas group");

    JsonNode list = read(get(asha, "/api/v1/groups").andExpect(status().isOk()));

    assertThat(list.valueStream().map(g -> g.get("id").asString())).containsExactly(mine);
    assertThat(list.valueStream().map(g -> g.get("id").asString())).doesNotContain(balas);
  }

  @Test
  void aNonMemberGetsAnEmptyListAndNotFoundById() throws Exception {
    Session asha = newUser("Asha");
    Session outsider = newUser("Outsider");
    String group = createGroup(asha, "Private");

    get(outsider, "/api/v1/groups").andExpect(status().isOk()).andExpect(content().json("[]"));
    get(outsider, "/api/v1/groups/" + group)
        .andExpect(status().isNotFound())
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
        .andExpect(jsonPath("$.type").value("urn:kanakku:problem:group-not-found"));
  }

  @Test
  void anUnknownGroupLooksTheSameAsSomeoneElsesGroup() throws Exception {
    Session asha = newUser("Asha");
    Session outsider = newUser("Outsider");
    String group = createGroup(asha, "Private");

    String theirs =
        get(outsider, "/api/v1/groups/" + group).andReturn().getResponse().getContentAsString();
    String unknown =
        get(outsider, "/api/v1/groups/" + UUID.randomUUID())
            .andReturn()
            .getResponse()
            .getContentAsString();

    assertThat(json.readTree(theirs).get("detail")).isEqualTo(json.readTree(unknown).get("detail"));
  }

  @Test
  void aMemberReadsTheGroupWithTheirOwnRole() throws Exception {
    Session asha = newUser("Asha");
    Session bala = newUser("Bala");
    String group = createGroup(asha, "Goa");
    join(group, bala);

    get(bala, "/api/v1/groups/" + group)
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.name").value("Goa"))
        .andExpect(jsonPath("$.myRole").value("MEMBER"));
    get(asha, "/api/v1/groups/" + group).andExpect(jsonPath("$.myRole").value("OWNER"));
  }

  @Test
  void aMalformedGroupIdIs400NotAServerError() throws Exception {
    Session asha = newUser("Asha");
    get(asha, "/api/v1/groups/not-a-uuid").andExpect(status().isBadRequest());
  }
}
