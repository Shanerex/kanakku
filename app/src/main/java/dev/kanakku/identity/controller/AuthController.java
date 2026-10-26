package dev.kanakku.identity.controller;

import dev.kanakku.identity.domain.HashedPassword;
import dev.kanakku.identity.domain.PendingSignUp;
import dev.kanakku.identity.domain.Profile;
import dev.kanakku.identity.domain.SignUpOptions;
import dev.kanakku.identity.request.SignInRequest;
import dev.kanakku.identity.request.SignUpRequest;
import dev.kanakku.identity.service.Deferred;
import dev.kanakku.identity.service.PasswordSignIn;
import dev.kanakku.identity.service.PendingSignUpService;
import dev.kanakku.identity.service.PhoneCodeService;
import dev.kanakku.identity.service.SessionStarter;
import dev.kanakku.identity.service.SignUpService;
import dev.kanakku.platform.idempotency.IdempotencyService;
import dev.kanakku.platform.phone.PhoneNumbers;
import dev.kanakku.platform.problem.ProblemException;
import dev.kanakku.platform.problem.ProblemResponse;
import dev.kanakku.platform.problem.Problems;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import java.util.Locale;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import tools.jackson.databind.json.JsonMapper;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

  private static final String PROBLEM = "application/problem+json";
  private static final String SIGN_UP_SCOPE = "sign-up";
  private static final int MAX_KEY_LENGTH = 255;

  private final SignUpService signUpService;
  private final IdempotencyService idempotency;
  private final JsonMapper json;
  private final PasswordSignIn passwordSignIn;
  private final SessionStarter sessions;
  private final PhoneCodeService phoneCodes;
  private final PendingSignUpService pendingSignUps;

  AuthController(
      SignUpService signUpService,
      IdempotencyService idempotency,
      JsonMapper json,
      PasswordSignIn passwordSignIn,
      SessionStarter sessions,
      PhoneCodeService phoneCodes,
      PendingSignUpService pendingSignUps) {
    this.signUpService = signUpService;
    this.idempotency = idempotency;
    this.json = json;
    this.passwordSignIn = passwordSignIn;
    this.sessions = sessions;
    this.phoneCodes = phoneCodes;
    this.pendingSignUps = pendingSignUps;
  }

  /** Public, so the sign-up form knows before it renders whether a phone code is part of it. */
  @Operation(
      operationId = "getSignUpOptions",
      summary = "Whether sign-up verifies a phone number by SMS")
  @ApiResponse(
      responseCode = "200",
      description = "The sign-up options",
      content = @Content(schema = @Schema(implementation = SignUpOptions.class)))
  @GetMapping(value = "/sign-up-options", produces = MediaType.APPLICATION_JSON_VALUE)
  SignUpOptions signUpOptions() {
    return new SignUpOptions(phoneCodes.enabled());
  }

  /** Checks the password and starts a session; every failure is the same generic 401. */
  @Operation(operationId = "signIn", summary = "Sign in with email and password")
  @ApiResponse(
      responseCode = "200",
      description = "Signed in; the SESSION cookie is set",
      content = @Content(schema = @Schema(implementation = Profile.class)))
  @ApiResponse(
      responseCode = "400",
      description = "Invalid fields",
      content =
          @Content(mediaType = PROBLEM, schema = @Schema(implementation = ProblemResponse.class)))
  @ApiResponse(
      responseCode = "401",
      description = "Email and password do not match (one generic answer)",
      content =
          @Content(mediaType = PROBLEM, schema = @Schema(implementation = ProblemResponse.class)))
  @ApiResponse(
      responseCode = "429",
      description =
          "Too many failed tries, or this IP has too many sign-ins in flight; see Retry-After",
      content =
          @Content(mediaType = PROBLEM, schema = @Schema(implementation = ProblemResponse.class)))
  @PostMapping(value = "/sign-in", produces = MediaType.APPLICATION_JSON_VALUE)
  Profile signIn(
      @Valid @RequestBody SignInRequest request,
      HttpServletRequest httpRequest,
      HttpServletResponse httpResponse) {
    Profile profile =
        passwordSignIn.signIn(request.email(), request.password(), httpRequest.getRemoteAddr());
    sessions.start(profile.id(), httpRequest, httpResponse);
    return profile;
  }

  /** Revokes the session server-side. Signing out twice is fine. */
  @Operation(operationId = "signOut", summary = "Sign out and revoke the session")
  @ApiResponse(responseCode = "204", description = "Session revoked")
  @PostMapping("/sign-out")
  ResponseEntity<Void> signOut(HttpServletRequest httpRequest) {
    sessions.end(httpRequest);
    return ResponseEntity.noContent().build();
  }

  /**
   * With an SMS provider and a phone this starts a sign-up and answers 202; the account is created
   * when the code is confirmed or the user finishes without it. Otherwise it creates the account at
   * once, with any phone unverified. The phone is always optional. Starts no session. A repeated
   * key replays the first answer.
   */
  @Operation(operationId = "signUp", summary = "Create a password account")
  @ApiResponse(
      responseCode = "201",
      description =
          "Account created (no phone, or no SMS provider); the number is shown masked. No session"
              + " is started;"
              + " call sign-in next.",
      content = @Content(schema = @Schema(implementation = Profile.class)))
  @ApiResponse(
      responseCode = "202",
      description = "An SMS code was sent and no account exists yet; confirm it with verifySignUp",
      content = @Content(schema = @Schema(implementation = PendingSignUp.class)))
  @ApiResponse(
      responseCode = "400",
      description = "Invalid fields or no Idempotency-Key",
      content =
          @Content(mediaType = PROBLEM, schema = @Schema(implementation = ProblemResponse.class)))
  @ApiResponse(
      responseCode = "409",
      description = "Email already registered, or the number is verified on another account",
      content =
          @Content(mediaType = PROBLEM, schema = @Schema(implementation = ProblemResponse.class)))
  @ApiResponse(
      responseCode = "422",
      description = "Idempotency-Key reused with a different body",
      content =
          @Content(mediaType = PROBLEM, schema = @Schema(implementation = ProblemResponse.class)))
  @ApiResponse(
      responseCode = "429",
      description =
          "Too busy: this IP has too many sign-ins or sign-ups in flight, no hash slot was free in"
              + " time, or too many codes were sent; see Retry-After",
      content =
          @Content(mediaType = PROBLEM, schema = @Schema(implementation = ProblemResponse.class)))
  @PostMapping(value = "/sign-up", produces = MediaType.APPLICATION_JSON_VALUE)
  ResponseEntity<String> signUp(
      @Parameter(required = true, description = "A fresh value per submit attempt")
          @RequestHeader(name = "Idempotency-Key", required = false)
          String key,
      @Valid @RequestBody SignUpRequest request,
      HttpServletRequest httpRequest) {
    String idempotencyKey = requireKey(key);
    String ip = httpRequest.getRemoteAddr();
    String phone =
        request.phone() == null || request.phone().isBlank()
            ? null
            : PhoneNumbers.parse(request.phone());
    boolean sendsCodes = phone != null && phoneCodes.enabled();
    if (phone != null) {
      // Every "is this number taken" answer counts, provider or not. Before the sign-up
      // transaction, so the count survives a 409 that rolls it back.
      pendingSignUps.requirePhoneCheckAllowed(ip);
    }
    // Hash before the idempotency transaction opens, so no pooled connection waits on Argon2.
    HashedPassword hash = signUpService.hashPassword(request.password(), ip);
    String email = request.email().strip().toLowerCase(Locale.ROOT);
    String name = request.displayName().strip();
    byte[] requestHash =
        phone == null
            ? IdempotencyService.hash(email, name)
            : IdempotencyService.hash(email, name, phone);

    IdempotencyService.Outcome outcome =
        idempotency.executeStaged(
            SIGN_UP_SCOPE,
            idempotencyKey,
            requestHash,
            () -> {
              if (sendsCodes) {
                Deferred<PendingSignUp> pending =
                    pendingSignUps.start(request.email(), hash, request.displayName(), phone, ip);
                return new IdempotencyService.Staged(
                    new IdempotencyService.StoredResponse(
                        HttpStatus.ACCEPTED.value(),
                        json.writeValueAsString(pending.provisional())),
                    () ->
                        new IdempotencyService.StoredResponse(
                            HttpStatus.ACCEPTED.value(),
                            json.writeValueAsString(pending.finish().get())));
              }
              // The number is stored masked: the replay table must not hold a full number.
              Profile profile =
                  signUpService
                      .signUp(request.email(), hash, request.displayName(), phone)
                      .withMaskedPhone();
              IdempotencyService.StoredResponse created =
                  new IdempotencyService.StoredResponse(
                      HttpStatus.CREATED.value(), json.writeValueAsString(profile));
              return new IdempotencyService.Staged(created, () -> created);
            });

    if (outcome.replayed()) {
      UUID pendingId =
          outcome.response().status() == HttpStatus.ACCEPTED.value()
              ? UUID.fromString(json.readTree(outcome.response().body()).get("id").asString())
              : null;
      signUpService.verifyReplayedPassword(request.email(), request.password(), ip, pendingId);
    }

    ResponseEntity.BodyBuilder response =
        ResponseEntity.status(outcome.response().status()).contentType(MediaType.APPLICATION_JSON);
    if (outcome.replayed()) {
      response.header("Idempotent-Replayed", "true");
    }
    return response.body(outcome.response().body());
  }

  private static String requireKey(String key) {
    if (key == null
        || key.isBlank()
        || key.length() > MAX_KEY_LENGTH
        || key.chars().anyMatch(c -> c < 0x20 || c == 0x7f)) {
      throw new ProblemException(
          Problems.of(
              HttpStatus.BAD_REQUEST,
              "idempotency-key-required",
              "Idempotency-Key required",
              "Send an Idempotency-Key header of 1 to 255 printable characters."));
    }
    return key;
  }
}
