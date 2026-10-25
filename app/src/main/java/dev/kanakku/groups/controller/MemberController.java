package dev.kanakku.groups.controller;

import dev.kanakku.groups.domain.Member;
import dev.kanakku.groups.domain.WriteOffResult;
import dev.kanakku.groups.request.AddGuestRequest;
import dev.kanakku.groups.request.AddMemberRequest;
import dev.kanakku.groups.request.WriteOffRequest;
import dev.kanakku.groups.service.MemberService;
import dev.kanakku.identity.api.CurrentUser;
import dev.kanakku.identity.api.UserMatch;
import dev.kanakku.platform.problem.ProblemResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(value = "/api/v1/groups/{groupId}", produces = MediaType.APPLICATION_JSON_VALUE)
public class MemberController {

  private static final String PROBLEM = "application/problem+json";
  private static final String KEY_DESCRIPTION = "A fresh value per submit attempt";

  private final MemberService members;
  private final CurrentUser currentUser;
  private final IdempotentCreates creates;

  MemberController(MemberService members, CurrentUser currentUser, IdempotentCreates creates) {
    this.members = members;
    this.currentUser = currentUser;
    this.creates = creates;
  }

  @Operation(operationId = "listMembers", summary = "List a group's members with their balances")
  @ApiResponse(
      responseCode = "200",
      description = "Current members, oldest first",
      content = @Content(array = @ArraySchema(schema = @Schema(implementation = Member.class))))
  @ApiResponse(
      responseCode = "404",
      description = "No such group, or I am not in it",
      content =
          @Content(mediaType = PROBLEM, schema = @Schema(implementation = ProblemResponse.class)))
  @GetMapping("/members")
  List<Member> list(@PathVariable("groupId") UUID groupId) {
    return members.list(groupId, currentUser.require().id());
  }

  @Operation(operationId = "addGuest", summary = "Add a guest (a name, optionally an email)")
  @ApiResponse(
      responseCode = "201",
      description = "The new guest member",
      content = @Content(schema = @Schema(implementation = Member.class)))
  @ApiResponse(
      responseCode = "409",
      description = "The group is archived",
      content =
          @Content(mediaType = PROBLEM, schema = @Schema(implementation = ProblemResponse.class)))
  @PostMapping("/guests")
  ResponseEntity<String> addGuest(
      @PathVariable("groupId") UUID groupId,
      @Parameter(required = true, description = KEY_DESCRIPTION)
          @RequestHeader(name = "Idempotency-Key", required = false)
          String key,
      @Valid @RequestBody AddGuestRequest request) {
    UUID caller = currentUser.require().id();
    return creates.run(
        "guest-add",
        caller,
        key,
        () -> members.addGuest(groupId, caller, request.name(), request.email()),
        groupId.toString(),
        request.name().strip(),
        String.valueOf(request.email()));
  }

  @Operation(operationId = "searchUsers", summary = "Search users to add directly (Owner only)")
  @ApiResponse(
      responseCode = "200",
      description = "At most 10 matches with masked emails",
      content = @Content(array = @ArraySchema(schema = @Schema(implementation = UserMatch.class))))
  @ApiResponse(
      responseCode = "400",
      description = "The query is under 3 characters",
      content =
          @Content(mediaType = PROBLEM, schema = @Schema(implementation = ProblemResponse.class)))
  @GetMapping("/user-search")
  List<UserMatch> search(
      @PathVariable("groupId") UUID groupId,
      @RequestParam(name = "q", defaultValue = "") String q) {
    return members.search(groupId, currentUser.require().id(), q);
  }

  @Operation(operationId = "addMember", summary = "Add a registered user directly (Owner only)")
  @ApiResponse(
      responseCode = "201",
      description = "The new member",
      content = @Content(schema = @Schema(implementation = Member.class)))
  @ApiResponse(
      responseCode = "409",
      description = "Already a member, invite-only, or the group is archived",
      content =
          @Content(mediaType = PROBLEM, schema = @Schema(implementation = ProblemResponse.class)))
  @PostMapping("/members")
  ResponseEntity<String> addMember(
      @PathVariable("groupId") UUID groupId,
      @Parameter(required = true, description = KEY_DESCRIPTION)
          @RequestHeader(name = "Idempotency-Key", required = false)
          String key,
      @Valid @RequestBody AddMemberRequest request) {
    UUID caller = currentUser.require().id();
    return creates.run(
        "member-add",
        caller,
        key,
        () -> members.addUser(groupId, caller, request.userId()),
        groupId.toString(),
        request.userId().toString());
  }

  @Operation(operationId = "removeMember", summary = "Remove a member with no balance (Owner only)")
  @ApiResponse(responseCode = "204", description = "Removed")
  @ApiResponse(
      responseCode = "409",
      description =
          "member-has-balance (lists the amounts), last-owner, stale-version or group-archived",
      content =
          @Content(mediaType = PROBLEM, schema = @Schema(implementation = ProblemResponse.class)))
  @DeleteMapping("/members/{memberId}")
  ResponseEntity<Void> remove(
      @PathVariable("groupId") UUID groupId,
      @PathVariable("memberId") UUID memberId,
      @RequestParam(name = "version") @NotNull @PositiveOrZero Integer version) {
    members.remove(groupId, memberId, currentUser.require().id(), version);
    return ResponseEntity.noContent().build();
  }

  @Operation(operationId = "leaveGroup", summary = "Leave the group (not the Owner)")
  @ApiResponse(responseCode = "204", description = "Left")
  @ApiResponse(
      responseCode = "409",
      description = "member-has-balance (lists the amounts), last-owner or group-archived",
      content =
          @Content(mediaType = PROBLEM, schema = @Schema(implementation = ProblemResponse.class)))
  @PostMapping("/leave")
  ResponseEntity<Void> leave(@PathVariable("groupId") UUID groupId) {
    members.leave(groupId, currentUser.require().id());
    return ResponseEntity.noContent().build();
  }

  @Operation(
      operationId = "writeOffBalance",
      summary = "Write off a member's balance onto chosen members (Owner only)")
  @ApiResponse(
      responseCode = "201",
      description = "What was written off, per currency",
      content = @Content(schema = @Schema(implementation = WriteOffResult.class)))
  @ApiResponse(
      responseCode = "400",
      description = "The absorbers are empty, repeat, include the member, or are not members",
      content =
          @Content(mediaType = PROBLEM, schema = @Schema(implementation = ProblemResponse.class)))
  @PostMapping("/members/{memberId}/write-offs")
  ResponseEntity<String> writeOff(
      @PathVariable("groupId") UUID groupId,
      @PathVariable("memberId") UUID memberId,
      @Parameter(required = true, description = KEY_DESCRIPTION)
          @RequestHeader(name = "Idempotency-Key", required = false)
          String key,
      @Valid @RequestBody WriteOffRequest request) {
    UUID caller = currentUser.require().id();
    String absorbers =
        request.absorberMemberIds().stream()
            .map(UUID::toString)
            .sorted()
            .collect(Collectors.joining(","));
    return creates.run(
        "write-off",
        caller,
        key,
        () -> members.writeOff(groupId, memberId, caller, request.absorberMemberIds()),
        groupId.toString(),
        memberId.toString(),
        absorbers);
  }
}
