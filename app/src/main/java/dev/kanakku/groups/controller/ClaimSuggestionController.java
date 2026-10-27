package dev.kanakku.groups.controller;

import dev.kanakku.groups.domain.ClaimSuggestion;
import dev.kanakku.groups.request.VersionRequest;
import dev.kanakku.groups.service.ClaimSuggestionService;
import dev.kanakku.identity.api.CurrentUser;
import dev.kanakku.platform.problem.ProblemResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** The caller's own suggestions to claim a guest whose number they verified. */
@RestController
@RequestMapping(value = "/api/v1/claim-suggestions", produces = MediaType.APPLICATION_JSON_VALUE)
public class ClaimSuggestionController {

  private static final String PROBLEM = "application/problem+json";

  private final ClaimSuggestionService suggestions;
  private final CurrentUser currentUser;

  ClaimSuggestionController(ClaimSuggestionService suggestions, CurrentUser currentUser) {
    this.suggestions = suggestions;
    this.currentUser = currentUser;
  }

  @Operation(
      operationId = "listClaimSuggestions",
      summary = "List my pending suggestions to claim a guest")
  @ApiResponse(
      responseCode = "200",
      description = "Only mine, only in groups I am in, oldest first",
      content =
          @Content(array = @ArraySchema(schema = @Schema(implementation = ClaimSuggestion.class))))
  @GetMapping
  List<ClaimSuggestion> list() {
    return suggestions.listPending(currentUser.require().id());
  }

  @Operation(
      operationId = "confirmClaimSuggestion",
      summary = "It is me: move the guest's balance onto my own row and retire the guest")
  @ApiResponse(
      responseCode = "200",
      description = "The suggestion, now CONFIRMED",
      content = @Content(schema = @Schema(implementation = ClaimSuggestion.class)))
  @ApiResponse(
      responseCode = "404",
      description = "Not my suggestion",
      content =
          @Content(mediaType = PROBLEM, schema = @Schema(implementation = ProblemResponse.class)))
  @ApiResponse(
      responseCode = "409",
      description = "Stale version, already answered, the guest is gone or the group is archived",
      content =
          @Content(mediaType = PROBLEM, schema = @Schema(implementation = ProblemResponse.class)))
  @PostMapping("/{id}/confirm")
  ClaimSuggestion confirm(@PathVariable("id") UUID id, @Valid @RequestBody VersionRequest request) {
    return suggestions.confirm(id, currentUser.require().id(), request.version());
  }

  @Operation(operationId = "declineClaimSuggestion", summary = "Not me: leave the guest alone")
  @ApiResponse(
      responseCode = "200",
      description = "The suggestion, now DECLINED",
      content = @Content(schema = @Schema(implementation = ClaimSuggestion.class)))
  @ApiResponse(
      responseCode = "404",
      description = "Not my suggestion",
      content =
          @Content(mediaType = PROBLEM, schema = @Schema(implementation = ProblemResponse.class)))
  @ApiResponse(
      responseCode = "409",
      description = "Stale version or already answered",
      content =
          @Content(mediaType = PROBLEM, schema = @Schema(implementation = ProblemResponse.class)))
  @PostMapping("/{id}/decline")
  ClaimSuggestion decline(@PathVariable("id") UUID id, @Valid @RequestBody VersionRequest request) {
    return suggestions.decline(id, currentUser.require().id(), request.version());
  }
}
