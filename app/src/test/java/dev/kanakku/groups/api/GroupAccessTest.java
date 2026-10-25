package dev.kanakku.groups.api;

import static dev.kanakku.jooq.Tables.GROUP_MEMBERS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import dev.kanakku.platform.problem.ProblemException;
import dev.kanakku.support.ApiTest;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class GroupAccessTest extends ApiTest {

  @Autowired GroupAccess access;

  @Test
  void returnsTheCallersMemberIdForBothActionsInAnActiveGroup() throws Exception {
    Session asha = newUser("Asha");
    UUID group = UUID.fromString(createGroup(asha, "Goa"));

    UUID member =
        dsl.select(GROUP_MEMBERS.ID)
            .from(GROUP_MEMBERS)
            .where(GROUP_MEMBERS.GROUP_ID.eq(group))
            .and(GROUP_MEMBERS.USER_ID.eq(asha.id()))
            .fetchOne(GROUP_MEMBERS.ID);

    assertThat(access.requireWritable(group, asha.id(), Action.ADD_EXPENSE)).isEqualTo(member);
    assertThat(access.requireWritable(group, asha.id(), Action.RECORD_SETTLEMENT))
        .isEqualTo(member);
  }

  @Test
  void anArchivedGroupRefusesExpensesButStillTakesSettlements() throws Exception {
    Session asha = newUser("Asha");
    String id = createGroup(asha, "Goa");
    UUID group = UUID.fromString(id);
    post(asha, "/api/v1/groups/" + id + "/archive", map("version", 0), null)
        .andExpect(status().isOk());

    assertThatThrownBy(() -> access.requireWritable(group, asha.id(), Action.ADD_EXPENSE))
        .isInstanceOfSatisfying(
            ProblemException.class,
            e -> {
              assertThat(e.problem().getStatus()).isEqualTo(409);
              assertThat(e.problem().getType().toString()).endsWith("group-archived");
            });
    assertThat(access.requireWritable(group, asha.id(), Action.RECORD_SETTLEMENT)).isNotNull();
  }

  @Test
  void unarchivingRestoresExpenses() throws Exception {
    Session asha = newUser("Asha");
    String id = createGroup(asha, "Goa");
    UUID group = UUID.fromString(id);
    post(asha, "/api/v1/groups/" + id + "/archive", map("version", 0), null)
        .andExpect(status().isOk());
    post(asha, "/api/v1/groups/" + id + "/unarchive", map("version", 1), null)
        .andExpect(status().isOk());

    assertThat(access.requireWritable(group, asha.id(), Action.ADD_EXPENSE)).isNotNull();
  }

  @Test
  void aNonMemberAndAnUnknownGroupAreBothNotFound() throws Exception {
    Session asha = newUser("Asha");
    Session outsider = newUser("Outsider");
    UUID group = UUID.fromString(createGroup(asha, "Goa"));

    for (Action action : Action.values()) {
      assertThatThrownBy(() -> access.requireWritable(group, outsider.id(), action))
          .isInstanceOfSatisfying(
              ProblemException.class, e -> assertThat(e.problem().getStatus()).isEqualTo(404));
      assertThatThrownBy(() -> access.requireWritable(UUID.randomUUID(), asha.id(), action))
          .isInstanceOfSatisfying(
              ProblemException.class, e -> assertThat(e.problem().getStatus()).isEqualTo(404));
    }
  }
}
