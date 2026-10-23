package dev.kanakku.identity.web;

import dev.kanakku.identity.api.CurrentUser;
import dev.kanakku.identity.internal.Profile;
import dev.kanakku.identity.internal.ProfileService;
import dev.kanakku.platform.problem.ProblemResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(value = "/api/v1/me", produces = MediaType.APPLICATION_JSON_VALUE)
class MeController {

  private static final String PROBLEM = "application/problem+json";

  private final ProfileService profiles;
  private final CurrentUser currentUser;

  MeController(ProfileService profiles, CurrentUser currentUser) {
    this.profiles = profiles;
    this.currentUser = currentUser;
  }

  @Operation(operationId = "getMe", summary = "Read my profile")
  @ApiResponse(
      responseCode = "200",
      description = "My profile",
      content = @Content(schema = @Schema(implementation = Profile.class)))
  @ApiResponse(
      responseCode = "401",
      description = "Not signed in",
      content =
          @Content(mediaType = PROBLEM, schema = @Schema(implementation = ProblemResponse.class)))
  @GetMapping
  Profile get() {
    return profiles.get(currentUser.require().id());
  }

  /** Edits the caller's own profile; a stale {@code version} is a 409. */
  @Operation(operationId = "updateMe", summary = "Edit my profile")
  @ApiResponse(
      responseCode = "200",
      description = "The saved profile, with its new version",
      content = @Content(schema = @Schema(implementation = Profile.class)))
  @ApiResponse(
      responseCode = "400",
      description = "Invalid fields",
      content =
          @Content(mediaType = PROBLEM, schema = @Schema(implementation = ProblemResponse.class)))
  @ApiResponse(
      responseCode = "401",
      description = "Not signed in",
      content =
          @Content(mediaType = PROBLEM, schema = @Schema(implementation = ProblemResponse.class)))
  @ApiResponse(
      responseCode = "409",
      description = "Stale version",
      content =
          @Content(mediaType = PROBLEM, schema = @Schema(implementation = ProblemResponse.class)))
  @PutMapping
  Profile update(@Valid @RequestBody UpdateProfileRequest request) {
    return profiles.update(
        currentUser.require().id(),
        request.displayName(),
        request.defaultCurrency(),
        request.upiId(),
        request.version());
  }
}
