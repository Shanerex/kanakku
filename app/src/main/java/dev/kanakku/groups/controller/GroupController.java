package dev.kanakku.groups.controller;

import dev.kanakku.groups.domain.Group;
import dev.kanakku.groups.request.CreateGroupRequest;
import dev.kanakku.groups.request.UpdateGroupRequest;
import dev.kanakku.groups.request.VersionRequest;
import dev.kanakku.groups.service.GroupService;
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
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(value = "/api/v1/groups", produces = MediaType.APPLICATION_JSON_VALUE)
public class GroupController {

  private static final String PROBLEM = "application/problem+json";

  private final GroupService groups;
  private final CurrentUser currentUser;
  private final IdempotentCreates creates;

  GroupController(GroupService groups, CurrentUser currentUser, IdempotentCreates creates) {
    this.groups = groups;
    this.currentUser = currentUser;
    this.creates = creates;
  }

  @Operation(operationId = "createGroup", summary = "Create a group; the caller becomes its Owner")
  @ApiResponse(
      responseCode = "201",
      description = "The new group",
      content = @Content(schema = @Schema(implementation = Group.class)))
  @ApiResponse(
      responseCode = "400",
      description = "Invalid fields or missing Idempotency-Key",
      content =
          @Content(mediaType = PROBLEM, schema = @Schema(implementation = ProblemResponse.class)))
  @ApiResponse(
      responseCode = "422",
      description = "Idempotency-Key reused with a different body",
      content =
          @Content(mediaType = PROBLEM, schema = @Schema(implementation = ProblemResponse.class)))
  @PostMapping
  ResponseEntity<String> create(
      @Parameter(required = true, description = "A fresh value per submit attempt")
          @RequestHeader(name = "Idempotency-Key", required = false)
          String key,
      @Valid @RequestBody CreateGroupRequest request) {
    UUID caller = currentUser.require().id();
    return creates.run(
        "group-create",
        caller,
        key,
        () ->
            groups.create(
                caller, request.name(), request.type(), request.currency(), request.cover()),
        request.name().strip(),
        request.type().name(),
        request.currency(),
        String.valueOf(request.cover()));
  }

  @Operation(operationId = "listGroups", summary = "List the groups I belong to")
  @ApiResponse(
      responseCode = "200",
      description = "My groups, newest first",
      content = @Content(array = @ArraySchema(schema = @Schema(implementation = Group.class))))
  @GetMapping
  List<Group> list() {
    return groups.list(currentUser.require().id());
  }

  @Operation(operationId = "getGroup", summary = "Read one group I belong to")
  @ApiResponse(
      responseCode = "200",
      description = "The group",
      content = @Content(schema = @Schema(implementation = Group.class)))
  @ApiResponse(
      responseCode = "404",
      description = "No such group, or I am not in it",
      content =
          @Content(mediaType = PROBLEM, schema = @Schema(implementation = ProblemResponse.class)))
  @GetMapping("/{groupId}")
  Group get(@PathVariable("groupId") UUID groupId) {
    return groups.get(groupId, currentUser.require().id());
  }

  @Operation(operationId = "updateGroup", summary = "Edit a group (Owner only)")
  @ApiResponse(
      responseCode = "200",
      description = "The saved group, with its new version",
      content = @Content(schema = @Schema(implementation = Group.class)))
  @ApiResponse(
      responseCode = "403",
      description = "Only the Owner can edit",
      content =
          @Content(mediaType = PROBLEM, schema = @Schema(implementation = ProblemResponse.class)))
  @ApiResponse(
      responseCode = "409",
      description = "Stale version, or the group is archived",
      content =
          @Content(mediaType = PROBLEM, schema = @Schema(implementation = ProblemResponse.class)))
  @PatchMapping("/{groupId}")
  Group update(
      @PathVariable("groupId") UUID groupId, @Valid @RequestBody UpdateGroupRequest request) {
    return groups.update(
        groupId,
        currentUser.require().id(),
        request.version(),
        request.name(),
        request.type(),
        request.cover());
  }

  @Operation(operationId = "archiveGroup", summary = "Archive a group (Owner only)")
  @ApiResponse(
      responseCode = "200",
      description = "The archived group",
      content = @Content(schema = @Schema(implementation = Group.class)))
  @ApiResponse(
      responseCode = "409",
      description = "Stale version",
      content =
          @Content(mediaType = PROBLEM, schema = @Schema(implementation = ProblemResponse.class)))
  @PostMapping("/{groupId}/archive")
  Group archive(@PathVariable("groupId") UUID groupId, @Valid @RequestBody VersionRequest request) {
    return groups.archive(groupId, currentUser.require().id(), request.version());
  }

  @Operation(operationId = "unarchiveGroup", summary = "Unarchive a group (Owner only)")
  @ApiResponse(
      responseCode = "200",
      description = "The reopened group",
      content = @Content(schema = @Schema(implementation = Group.class)))
  @ApiResponse(
      responseCode = "409",
      description = "Stale version, or the group is not archived",
      content =
          @Content(mediaType = PROBLEM, schema = @Schema(implementation = ProblemResponse.class)))
  @PostMapping("/{groupId}/unarchive")
  Group unarchive(
      @PathVariable("groupId") UUID groupId, @Valid @RequestBody VersionRequest request) {
    return groups.unarchive(groupId, currentUser.require().id(), request.version());
  }
}
