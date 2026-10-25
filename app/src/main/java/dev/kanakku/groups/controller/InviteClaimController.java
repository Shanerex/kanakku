package dev.kanakku.groups.controller;

import dev.kanakku.groups.domain.Group;
import dev.kanakku.groups.domain.InvitePreview;
import dev.kanakku.groups.request.InviteTokenRequest;
import dev.kanakku.groups.service.InviteService;
import dev.kanakku.identity.api.CurrentUser;
import dev.kanakku.platform.problem.ProblemResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Invite preview and claim. The token is in the body so no URL or log line ever holds it. */
@RestController
@RequestMapping(value = "/api/v1/invites", produces = MediaType.APPLICATION_JSON_VALUE)
public class InviteClaimController {

  private static final String PROBLEM = "application/problem+json";

  private final InviteService invites;
  private final CurrentUser currentUser;

  InviteClaimController(InviteService invites, CurrentUser currentUser) {
    this.invites = invites;
    this.currentUser = currentUser;
  }

  @Operation(operationId = "previewInvite", summary = "See which group and guest an invite is for")
  @ApiResponse(
      responseCode = "200",
      description = "The group name and the guest name",
      content = @Content(schema = @Schema(implementation = InvitePreview.class)))
  @ApiResponse(
      responseCode = "410",
      description = "Expired, revoked, used or unknown",
      content =
          @Content(mediaType = PROBLEM, schema = @Schema(implementation = ProblemResponse.class)))
  @PostMapping("/preview")
  InvitePreview preview(@Valid @RequestBody InviteTokenRequest request) {
    currentUser.require();
    return invites.preview(request.token().strip());
  }

  @Operation(operationId = "claimInvite", summary = "Take over a guest's place with an invite")
  @ApiResponse(
      responseCode = "200",
      description = "The group the caller has joined",
      content = @Content(schema = @Schema(implementation = Group.class)))
  @ApiResponse(
      responseCode = "409",
      description = "The caller is already in the group, or the group is archived",
      content =
          @Content(mediaType = PROBLEM, schema = @Schema(implementation = ProblemResponse.class)))
  @ApiResponse(
      responseCode = "410",
      description = "Expired, revoked, used or unknown",
      content =
          @Content(mediaType = PROBLEM, schema = @Schema(implementation = ProblemResponse.class)))
  @PostMapping("/claim")
  Group claim(@Valid @RequestBody InviteTokenRequest request) {
    return invites.claim(request.token().strip(), currentUser.require().id());
  }
}
