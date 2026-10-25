package dev.kanakku.groups.controller;

import dev.kanakku.groups.domain.CreatedInvite;
import dev.kanakku.groups.domain.Invite;
import dev.kanakku.groups.request.CreateInviteRequest;
import dev.kanakku.groups.request.VersionRequest;
import dev.kanakku.groups.service.InviteService;
import dev.kanakku.identity.api.CurrentUser;
import dev.kanakku.platform.problem.ProblemResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import tools.jackson.databind.json.JsonMapper;

@RestController
@RequestMapping(
    value = "/api/v1/groups/{groupId}/invites",
    produces = MediaType.APPLICATION_JSON_VALUE)
public class InviteController {

  private static final String PROBLEM = "application/problem+json";

  private final InviteService invites;
  private final CurrentUser currentUser;
  private final IdempotentCreates creates;
  private final JsonMapper json;

  InviteController(
      InviteService invites, CurrentUser currentUser, IdempotentCreates creates, JsonMapper json) {
    this.invites = invites;
    this.currentUser = currentUser;
    this.creates = creates;
    this.json = json;
  }

  @Operation(
      operationId = "createInvite",
      summary = "Create a single-use invite for a guest (Owner only)")
  @ApiResponse(
      responseCode = "201",
      description = "The invite and its token (shown once)",
      content = @Content(schema = @Schema(implementation = CreatedInvite.class)))
  @ApiResponse(
      responseCode = "400",
      description = "Expiry is not 1, 7 or 30 days, or Idempotency-Key is missing",
      content =
          @Content(mediaType = PROBLEM, schema = @Schema(implementation = ProblemResponse.class)))
  @PostMapping
  ResponseEntity<String> create(
      @PathVariable("groupId") UUID groupId,
      @Parameter(required = true, description = "A fresh value per submit attempt")
          @RequestHeader(name = "Idempotency-Key", required = false)
          String key,
      @Valid @RequestBody CreateInviteRequest request) {
    UUID caller = currentUser.require().id();
    return creates.run(
        "invite-create",
        caller,
        key,
        // Only the invite is stored; the token is recomputed from its id when the body is sent.
        () -> invites.create(groupId, caller, request.memberId(), request.expiryDays()).invite(),
        this::withToken,
        groupId.toString(),
        request.memberId().toString(),
        String.valueOf(request.expiryDays()));
  }

  private String withToken(String storedInvite) {
    Invite invite = json.readValue(storedInvite, Invite.class);
    return json.writeValueAsString(new CreatedInvite(invite, invites.tokenFor(invite.id())));
  }

  @Operation(operationId = "listInvites", summary = "List a group's invites")
  @ApiResponse(
      responseCode = "200",
      description = "Newest first; never includes a token",
      content = @Content(array = @ArraySchema(schema = @Schema(implementation = Invite.class))))
  @GetMapping
  List<Invite> list(@PathVariable("groupId") UUID groupId) {
    return invites.list(groupId, currentUser.require().id());
  }

  @Operation(operationId = "revokeInvite", summary = "Revoke an invite (Owner only)")
  @ApiResponse(
      responseCode = "200",
      description = "The revoked invite",
      content = @Content(schema = @Schema(implementation = Invite.class)))
  @ApiResponse(
      responseCode = "409",
      description = "Stale version, or the invite was already used or revoked",
      content =
          @Content(mediaType = PROBLEM, schema = @Schema(implementation = ProblemResponse.class)))
  @PostMapping("/{inviteId}/revoke")
  Invite revoke(
      @PathVariable("groupId") UUID groupId,
      @PathVariable("inviteId") UUID inviteId,
      @Valid @RequestBody VersionRequest request) {
    return invites.revoke(groupId, inviteId, currentUser.require().id(), request.version());
  }
}
