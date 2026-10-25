package dev.kanakku;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import dev.kanakku.support.PostgresIntegrationTest;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.SerializationFeature;
import tools.jackson.databind.json.JsonMapper;

/**
 * The committed {@code openapi/kanakku-v1.json} is what the web and mobile clients are generated
 * from. This test fails when the live API differs from it; rewrite it with {@code
 * -Dopenapi.regenerate=true}.
 */
@SpringBootTest
@AutoConfigureMockMvc
class OpenApiSnapshotTest extends PostgresIntegrationTest {

  private static final Path SNAPSHOT = Path.of("..", "openapi", "kanakku-v1.json");

  @Autowired MockMvc mvc;

  @Test
  void liveApiMatchesTheCommittedSnapshot() throws Exception {
    JsonMapper json = JsonMapper.builder().enable(SerializationFeature.INDENT_OUTPUT).build();
    String live =
        mvc.perform(get("/v3/api-docs"))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString(StandardCharsets.UTF_8);
    JsonNode liveTree = json.readTree(live);

    if (Boolean.getBoolean("openapi.regenerate")) {
      Files.createDirectories(SNAPSHOT.getParent());
      Files.writeString(SNAPSHOT, json.writeValueAsString(liveTree) + "\n");
    }

    assertThat(SNAPSHOT).as("run with -Dopenapi.regenerate=true to create it").exists();
    assertThat(json.readTree(Files.readString(SNAPSHOT)))
        .as("openapi/kanakku-v1.json is out of date; rerun with -Dopenapi.regenerate=true")
        .isEqualTo(liveTree);
  }

  @Test
  void documentsTheV1AuthAndProfileEndpoints() throws Exception {
    JsonNode paths =
        JsonMapper.builder()
            .build()
            .readTree(
                mvc.perform(get("/v3/api-docs"))
                    .andReturn()
                    .getResponse()
                    .getContentAsString(StandardCharsets.UTF_8))
            .get("paths");
    assertThat(paths.propertyNames())
        .contains(
            "/api/v1/auth/csrf",
            "/api/v1/auth/sign-up",
            "/api/v1/auth/sign-in",
            "/api/v1/auth/sign-out",
            "/api/v1/me");
  }

  @Test
  void documentsTheGroupsMembersAndInviteEndpoints() throws Exception {
    JsonNode paths =
        JsonMapper.builder()
            .build()
            .readTree(
                mvc.perform(get("/v3/api-docs"))
                    .andReturn()
                    .getResponse()
                    .getContentAsString(StandardCharsets.UTF_8))
            .get("paths");
    assertThat(paths.propertyNames())
        .contains(
            "/api/v1/groups",
            "/api/v1/groups/{groupId}",
            "/api/v1/groups/{groupId}/archive",
            "/api/v1/groups/{groupId}/unarchive",
            "/api/v1/groups/{groupId}/members",
            "/api/v1/groups/{groupId}/guests",
            "/api/v1/groups/{groupId}/user-search",
            "/api/v1/groups/{groupId}/members/{memberId}",
            "/api/v1/groups/{groupId}/members/{memberId}/write-offs",
            "/api/v1/groups/{groupId}/leave",
            "/api/v1/groups/{groupId}/invites",
            "/api/v1/groups/{groupId}/invites/{inviteId}/revoke",
            "/api/v1/invites/preview",
            "/api/v1/invites/claim");
  }
}
