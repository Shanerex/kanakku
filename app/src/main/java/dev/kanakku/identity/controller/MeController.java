package dev.kanakku.identity.controller;

import dev.kanakku.identity.api.CurrentUser;
import dev.kanakku.identity.domain.CodeSent;
import dev.kanakku.identity.domain.Profile;
import dev.kanakku.identity.request.ConfirmPhoneCodeRequest;
import dev.kanakku.identity.request.UpdatePhoneRequest;
import dev.kanakku.identity.request.UpdateProfileRequest;
import dev.kanakku.identity.service.PhoneVerificationService;
import dev.kanakku.identity.service.ProfileService;
import dev.kanakku.platform.problem.ProblemResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(value = "/api/v1/me", produces = MediaType.APPLICATION_JSON_VALUE)
public class MeController {

  private static final String PROBLEM = "application/problem+json";

  private final ProfileService profiles;
  private final CurrentUser currentUser;
  private final PhoneVerificationService verification;
  private final IdempotentActions actions;

  MeController(
      ProfileService profiles,
      CurrentUser currentUser,
      PhoneVerificationService verification,
      IdempotentActions actions) {
    this.profiles = profiles;
    this.currentUser = currentUser;
    this.verification = verification;
    this.actions = actions;
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
        request.directAdd(),
        request.version());
  }

  /**
   * Sets my one mobile number. A new number starts unverified; a number another account has
   * verified is refused.
   */
  @Operation(operationId = "updateMyPhone", summary = "Set my mobile number")
  @ApiResponse(
      responseCode = "200",
      description = "The saved profile; the number is unverified until a code confirms it",
      content = @Content(schema = @Schema(implementation = Profile.class)))
  @ApiResponse(
      responseCode = "400",
      description = "Not a valid phone number",
      content =
          @Content(mediaType = PROBLEM, schema = @Schema(implementation = ProblemResponse.class)))
  @ApiResponse(
      responseCode = "409",
      description = "Stale version, or the number is verified on another account (phone-taken)",
      content =
          @Content(mediaType = PROBLEM, schema = @Schema(implementation = ProblemResponse.class)))
  @ApiResponse(
      responseCode = "429",
      description =
          "Ten saves an hour per user and thirty per address (too-many-codes); see Retry-After",
      content =
          @Content(mediaType = PROBLEM, schema = @Schema(implementation = ProblemResponse.class)))
  @PutMapping("/phone")
  Profile updatePhone(
      @Valid @RequestBody UpdatePhoneRequest request, HttpServletRequest httpRequest) {
    return profiles.updatePhone(
        currentUser.require().id(),
        request.phone(),
        request.version(),
        httpRequest.getRemoteAddr());
  }

  /** Removes my mobile number, verified or not. */
  @Operation(operationId = "removeMyPhone", summary = "Remove my mobile number")
  @ApiResponse(
      responseCode = "200",
      description = "The saved profile, with no number",
      content = @Content(schema = @Schema(implementation = Profile.class)))
  @ApiResponse(
      responseCode = "409",
      description = "Stale version",
      content =
          @Content(mediaType = PROBLEM, schema = @Schema(implementation = ProblemResponse.class)))
  @DeleteMapping("/phone")
  Profile removePhone(@RequestParam(name = "version") @NotNull @PositiveOrZero Integer version) {
    return profiles.removePhone(currentUser.require().id(), version);
  }

  /** Texts a code to my number. A repeated key replays the first answer and sends nothing. */
  @Operation(operationId = "sendMyPhoneCode", summary = "Text a verification code to my number")
  @ApiResponse(
      responseCode = "202",
      description = "A code was made; smsSent is false if the provider failed",
      content = @Content(schema = @Schema(implementation = CodeSent.class)))
  @ApiResponse(
      responseCode = "409",
      description =
          "No SMS provider (sms-not-configured), no number, already verified, or phone-taken",
      content =
          @Content(mediaType = PROBLEM, schema = @Schema(implementation = ProblemResponse.class)))
  @ApiResponse(
      responseCode = "429",
      description =
          "Four codes an hour per user and per number, ten per address, or the server's hourly"
              + " ceiling (too-many-codes, sms-busy); see Retry-After",
      content =
          @Content(mediaType = PROBLEM, schema = @Schema(implementation = ProblemResponse.class)))
  @PostMapping("/phone/verification")
  ResponseEntity<String> sendCode(
      @Parameter(required = true, description = "A fresh value per submit attempt")
          @RequestHeader(name = "Idempotency-Key", required = false)
          String key,
      HttpServletRequest httpRequest) {
    var caller = currentUser.require().id();
    String ip = httpRequest.getRemoteAddr();
    return actions.runDeferred(
        "phone-code:" + caller,
        key,
        HttpStatus.ACCEPTED,
        () -> verification.send(caller, ip),
        caller.toString());
  }

  /** Checks the code I was texted; a right one verifies my number. */
  @Operation(operationId = "confirmMyPhoneCode", summary = "Confirm the code I was texted")
  @ApiResponse(
      responseCode = "200",
      description = "My profile, with the number verified",
      content = @Content(schema = @Schema(implementation = Profile.class)))
  @ApiResponse(
      responseCode = "410",
      description = "The code expired (code-expired)",
      content =
          @Content(mediaType = PROBLEM, schema = @Schema(implementation = ProblemResponse.class)))
  @ApiResponse(
      responseCode = "422",
      description = "Wrong code (wrong-code)",
      content =
          @Content(mediaType = PROBLEM, schema = @Schema(implementation = ProblemResponse.class)))
  @ApiResponse(
      responseCode = "429",
      description = "Five wrong codes lock this for 15 minutes (code-locked); see Retry-After",
      content =
          @Content(mediaType = PROBLEM, schema = @Schema(implementation = ProblemResponse.class)))
  @PostMapping("/phone/verification/confirm")
  Profile confirmCode(
      @Valid @RequestBody ConfirmPhoneCodeRequest request, HttpServletRequest httpRequest) {
    return verification.confirm(
        currentUser.require().id(), request.code(), httpRequest.getRemoteAddr());
  }
}
