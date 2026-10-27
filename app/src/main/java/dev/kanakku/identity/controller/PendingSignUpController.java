package dev.kanakku.identity.controller;

import dev.kanakku.identity.domain.PendingSignUp;
import dev.kanakku.identity.domain.Profile;
import dev.kanakku.identity.request.ConfirmPhoneCodeRequest;
import dev.kanakku.identity.service.PendingSignUpService;
import dev.kanakku.platform.problem.ProblemResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** The steps after {@code POST /auth/sign-up} answered 202: confirm the code, resend, or finish. */
@RestController
@RequestMapping(value = "/api/v1/auth/sign-ups", produces = MediaType.APPLICATION_JSON_VALUE)
public class PendingSignUpController {

  private static final String PROBLEM = "application/problem+json";
  private static final String KEY_DESCRIPTION = "A fresh value per submit attempt";

  private final PendingSignUpService signUps;
  private final IdempotentActions actions;

  PendingSignUpController(PendingSignUpService signUps, IdempotentActions actions) {
    this.signUps = signUps;
    this.actions = actions;
  }

  /**
   * Confirms the code and creates the account, verified. Starts no session. A wrong code, a locked
   * number and an expired code are stored answers: send a fresh key for the next try.
   */
  @Operation(operationId = "verifySignUp", summary = "Confirm the SMS code and create the account")
  @ApiResponse(
      responseCode = "201",
      description = "Account created with a verified number (shown masked). No session is started.",
      content = @Content(schema = @Schema(implementation = Profile.class)))
  @ApiResponse(
      responseCode = "409",
      description = "The email or the number got an account first (email-taken, phone-taken)",
      content =
          @Content(mediaType = PROBLEM, schema = @Schema(implementation = ProblemResponse.class)))
  @ApiResponse(
      responseCode = "410",
      description = "The sign-up expired (sign-up-expired) or the code did (code-expired)",
      content =
          @Content(mediaType = PROBLEM, schema = @Schema(implementation = ProblemResponse.class)))
  @ApiResponse(
      responseCode = "422",
      description = "Wrong code (wrong-code)",
      content =
          @Content(mediaType = PROBLEM, schema = @Schema(implementation = ProblemResponse.class)))
  @ApiResponse(
      responseCode = "429",
      description = "Five wrong codes lock this number for 15 minutes (code-locked)",
      content =
          @Content(mediaType = PROBLEM, schema = @Schema(implementation = ProblemResponse.class)))
  @PostMapping("/{id}/verify")
  ResponseEntity<String> verify(
      @PathVariable("id") UUID id,
      @Parameter(required = true, description = KEY_DESCRIPTION)
          @RequestHeader(name = "Idempotency-Key", required = false)
          String key,
      @Valid @RequestBody ConfirmPhoneCodeRequest request) {
    return actions.runRefusable(
        "sign-up-verify:" + id,
        key,
        HttpStatus.CREATED,
        () -> {
          var result = signUps.verify(id, request.code());
          return result.refusal() != null
              ? IdempotentActions.Attempt.refused(result.refusal())
              : IdempotentActions.Attempt.ok(result.profile().withMaskedPhone());
        },
        // The code is left out: it must not sit in a stored fingerprint.
        id.toString());
  }

  @Operation(operationId = "resendSignUpCode", summary = "Text a new code for a pending sign-up")
  @ApiResponse(
      responseCode = "202",
      description = "A new code was made; smsSent is false if the provider failed",
      content = @Content(schema = @Schema(implementation = PendingSignUp.class)))
  @ApiResponse(
      responseCode = "410",
      description = "The sign-up expired (sign-up-expired)",
      content =
          @Content(mediaType = PROBLEM, schema = @Schema(implementation = ProblemResponse.class)))
  @ApiResponse(
      responseCode = "429",
      description =
          "Four texts an hour per number, ten per address, or the server's hourly ceiling; see"
              + " Retry-After",
      content =
          @Content(mediaType = PROBLEM, schema = @Schema(implementation = ProblemResponse.class)))
  @PostMapping("/{id}/resend")
  ResponseEntity<String> resend(
      @PathVariable("id") UUID id,
      @Parameter(required = true, description = KEY_DESCRIPTION)
          @RequestHeader(name = "Idempotency-Key", required = false)
          String key,
      HttpServletRequest httpRequest) {
    String ip = httpRequest.getRemoteAddr();
    return actions.runDeferred(
        "sign-up-resend:" + id,
        key,
        HttpStatus.ACCEPTED,
        () -> signUps.resend(id, ip),
        id.toString());
  }

  @Operation(
      operationId = "finishSignUpUnverified",
      summary = "Create the account with the number unverified, skipping the code")
  @ApiResponse(
      responseCode = "201",
      description =
          "Account created; the number stays unverified (shown masked). No session is started.",
      content = @Content(schema = @Schema(implementation = Profile.class)))
  @ApiResponse(
      responseCode = "409",
      description = "The email is taken",
      content =
          @Content(mediaType = PROBLEM, schema = @Schema(implementation = ProblemResponse.class)))
  @ApiResponse(
      responseCode = "410",
      description = "The sign-up expired (sign-up-expired)",
      content =
          @Content(mediaType = PROBLEM, schema = @Schema(implementation = ProblemResponse.class)))
  @PostMapping("/{id}/finish-unverified")
  ResponseEntity<String> finishUnverified(
      @PathVariable("id") UUID id,
      @Parameter(required = true, description = KEY_DESCRIPTION)
          @RequestHeader(name = "Idempotency-Key", required = false)
          String key) {
    return actions.run(
        "sign-up-finish:" + id,
        key,
        HttpStatus.CREATED,
        () -> signUps.finishUnverified(id).withMaskedPhone(),
        id.toString());
  }
}
