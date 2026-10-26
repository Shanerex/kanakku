package dev.kanakku.groups.controller;

import static dev.kanakku.jooq.Tables.GROUPS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import dev.kanakku.support.ApiTest;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class GroupUpdateTest extends ApiTest {

  @Test
  void theOwnerEditsNameTypeAndCoverAndTheVersionMovesOn() throws Exception {
    Session asha = newUser("Asha");
    String group = createGroup(asha, "Goa");

    patch(
            asha,
            "/api/v1/groups/" + group,
            map("name", "Goa 2026", "type", "OTHER", "cover", "color:teal", "version", 0))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.name").value("Goa 2026"))
        .andExpect(jsonPath("$.type").value("OTHER"))
        .andExpect(jsonPath("$.cover").value("color:teal"))
        .andExpect(jsonPath("$.currency").value("INR"))
        .andExpect(jsonPath("$.version").value(1));
  }

  @Test
  void aFieldLeftOutStaysAsItWas() throws Exception {
    Session asha = newUser("Asha");
    String group = createGroup(asha, "Goa");

    patch(asha, "/api/v1/groups/" + group, map("type", "HOME", "version", 0))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.name").value("Goa"))
        .andExpect(jsonPath("$.type").value("HOME"));
  }

  @Test
  void aStaleVersionIs409AndChangesNothing() throws Exception {
    Session asha = newUser("Asha");
    String group = createGroup(asha, "Goa");
    patch(asha, "/api/v1/groups/" + group, map("name", "First", "version", 0))
        .andExpect(status().isOk());

    patch(asha, "/api/v1/groups/" + group, map("name", "Stale", "version", 0))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.type").value("urn:kanakku:problem:stale-version"));

    assertThat(
            dsl.select(GROUPS.NAME)
                .from(GROUPS)
                .where(GROUPS.ID.eq(UUID.fromString(group)))
                .fetchOne(GROUPS.NAME))
        .isEqualTo("First");
  }

  @Test
  void invalidFieldsAreRefused() throws Exception {
    Session asha = newUser("Asha");
    String group = createGroup(asha, "Goa");

    patch(asha, "/api/v1/groups/" + group, map("name", "   ", "version", 0))
        .andExpect(status().isBadRequest());
    patch(asha, "/api/v1/groups/" + group, map("cover", "color:nope", "version", 0))
        .andExpect(status().isBadRequest());
    patch(asha, "/api/v1/groups/" + group, map("name", "x")).andExpect(status().isBadRequest());
  }

  @Test
  void aMemberCannotEditArchiveOrUnarchive() throws Exception {
    Session asha = newUser("Asha");
    Session bala = newUser("Bala");
    String group = createGroup(asha, "Goa");
    join(group, bala);

    patch(bala, "/api/v1/groups/" + group, map("name", "Mine now", "version", 0))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.type").value("urn:kanakku:problem:owner-only"));
    post(bala, "/api/v1/groups/" + group + "/archive", map("version", 0), null)
        .andExpect(status().isForbidden());
    post(bala, "/api/v1/groups/" + group + "/unarchive", map("version", 0), null)
        .andExpect(status().isForbidden());

    get(asha, "/api/v1/groups/" + group)
        .andExpect(jsonPath("$.name").value("Goa"))
        .andExpect(jsonPath("$.archivedAt").doesNotExist());
  }

  @Test
  void aNonMemberGets404ForEveryEdit() throws Exception {
    Session asha = newUser("Asha");
    Session outsider = newUser("Outsider");
    String group = createGroup(asha, "Goa");

    patch(outsider, "/api/v1/groups/" + group, map("name", "x", "version", 0))
        .andExpect(status().isNotFound());
    post(outsider, "/api/v1/groups/" + group + "/archive", map("version", 0), null)
        .andExpect(status().isNotFound());
  }

  @Test
  void theOwnerArchivesAndUnarchivesWithVersionChecks() throws Exception {
    Session asha = newUser("Asha");
    String group = createGroup(asha, "Goa");

    post(asha, "/api/v1/groups/" + group + "/archive", map("version", 0), null)
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.archivedAt").exists())
        .andExpect(jsonPath("$.version").value(1));
    post(asha, "/api/v1/groups/" + group + "/archive", map("version", 1), null)
        .andExpect(status().isConflict());
    post(asha, "/api/v1/groups/" + group + "/unarchive", map("version", 0), null)
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.type").value("urn:kanakku:problem:stale-version"));
    post(asha, "/api/v1/groups/" + group + "/unarchive", map("version", 1), null)
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.archivedAt").doesNotExist())
        .andExpect(jsonPath("$.version").value(2));
    post(asha, "/api/v1/groups/" + group + "/unarchive", map("version", 2), null)
        .andExpect(status().isConflict());
  }

  @Test
  void anArchivedGroupRefusesEditsUntilItIsUnarchived() throws Exception {
    Session asha = newUser("Asha");
    String group = createGroup(asha, "Goa");
    post(asha, "/api/v1/groups/" + group + "/archive", map("version", 0), null)
        .andExpect(status().isOk());

    patch(asha, "/api/v1/groups/" + group, map("name", "Nope", "version", 1))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.type").value("urn:kanakku:problem:group-archived"));
    post(asha, "/api/v1/groups/" + group + "/archive", map("version", 1), null)
        .andExpect(status().isConflict());
  }
}
