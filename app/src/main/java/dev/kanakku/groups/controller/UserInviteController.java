package dev.kanakku.groups.controller;

import dev.kanakku.groups.domain.CreatedInvite;
import dev.kanakku.groups.domain.Invite;
import dev.kanakku.groups.request.UserInviteRequest;
import dev.kanakku.groups.service.InviteService;
import dev.kanakku.identity.api.CurrentUser;
import dev.kanakku.platform.problem.ProblemResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import tools.jackson.databind.json.JsonMapper;

@RestController
@RequestMapping(
    value = "/api/v1/groups/{groupId}/user-invites",
    produces = MediaType.APPLICATION_JSON_VALUE)
public class UserInviteController {

  private static final String PROBLEM = "application/problem+json";

  private final InviteService invites;
  private final CurrentUser currentUser;
  private final IdempotentCreates creates;
  private final JsonMapper json;

  UserInviteController(
      InviteService invites, CurrentUser currentUser, IdempotentCreates creates, JsonMapper json) {
    this.invites = invites;
    this.currentUser = currentUser;
    this.creates = creates;
    this.json = json;
  }

  @Operation(
      operationId = "inviteUser",
      summary = "Invite a found user with a guest and a single-use invite (Owner only)")
  @ApiResponse(
      responseCode = "201",
      description =
          "The invite and its token (shown once). The user is not a member until they claim it.",
      content = @Content(schema = @Schema(implementation = CreatedInvite.class)))
  @ApiResponse(
      responseCode = "404",
      description = "No such group, or no such user",
      content =
          @Content(mediaType = PROBLEM, schema = @Schema(implementation = ProblemResponse.class)))
  @ApiResponse(
      responseCode = "409",
      description = "Already a member, or the group is archived",
      content =
          @Content(mediaType = PROBLEM, schema = @Schema(implementation = ProblemResponse.class)))
  @PostMapping
  ResponseEntity<String> inviteUser(
      @PathVariable("groupId") UUID groupId,
      @Parameter(required = true, description = "A fresh value per submit attempt")
          @RequestHeader(name = "Idempotency-Key", required = false)
          String key,
      @Valid @RequestBody UserInviteRequest request) {
    UUID caller = currentUser.require().id();
    return creates.run(
        "user-invite",
        caller,
        key,
        // Only the invite is stored; the token is recomputed from its id when the body is sent.
        () -> invites.inviteUser(groupId, caller, request.userId(), request.expiryDays()).invite(),
        this::withToken,
        groupId.toString(),
        request.userId().toString(),
        String.valueOf(request.expiryDays()));
  }

  private String withToken(String storedInvite) {
    Invite invite = json.readValue(storedInvite, Invite.class);
    return json.writeValueAsString(new CreatedInvite(invite, invites.tokenFor(invite.id())));
  }
}
