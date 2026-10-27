package dev.kanakku.identity.controller;

import dev.kanakku.identity.service.Deferred;
import dev.kanakku.platform.idempotency.IdempotencyService;
import dev.kanakku.platform.problem.ProblemException;
import dev.kanakku.platform.problem.Problems;
import java.util.function.Supplier;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

/**
 * Runs an action once per Idempotency-Key and answers with the first response on a repeat, so a
 * retried "send a code" never texts twice. The scope names the action and the caller, or the
 * sign-up it belongs to, so one key can never replay someone else's response.
 */
@Component
class IdempotentActions {

  private static final int MAX_KEY_LENGTH = 255;

  private final IdempotencyService idempotency;
  private final JsonMapper json;

  IdempotentActions(IdempotencyService idempotency, JsonMapper json) {
    this.idempotency = idempotency;
    this.json = json;
  }

  /**
   * @param requestParts everything that defines "the same request"; never a secret or a code
   * @param action runs in the key's transaction and returns the body to store and send
   */
  ResponseEntity<String> run(
      String scope,
      String key,
      HttpStatus status,
      Supplier<Object> action,
      String... requestParts) {
    IdempotencyService.Outcome outcome =
        idempotency.execute(
            scope,
            requireKey(key),
            IdempotencyService.hash(requestParts),
            () ->
                new IdempotencyService.StoredResponse(
                    status.value(), json.writeValueAsString(action.get())));
    ResponseEntity.BodyBuilder response =
        ResponseEntity.status(outcome.response().status()).contentType(MediaType.APPLICATION_JSON);
    if (outcome.replayed()) {
      response.header("Idempotent-Replayed", "true");
    }
    return response.body(outcome.response().body());
  }

  /**
   * As {@link #run}, for an action split around a slow call: the {@link Deferred} answer is stored
   * with the key, its finish step runs after the transaction ends, and the answer it returns is
   * sent and stored in place of the provisional one.
   */
  ResponseEntity<String> runDeferred(
      String scope,
      String key,
      HttpStatus status,
      Supplier<Deferred<?>> action,
      String... requestParts) {
    IdempotencyService.Outcome outcome =
        idempotency.executeStaged(
            scope,
            requireKey(key),
            IdempotencyService.hash(requestParts),
            () -> {
              Deferred<?> deferred = action.get();
              return new IdempotencyService.Staged(
                  new IdempotencyService.StoredResponse(
                      status.value(), json.writeValueAsString(deferred.provisional())),
                  () ->
                      new IdempotencyService.StoredResponse(
                          status.value(), json.writeValueAsString(deferred.finish().get())));
            });
    ResponseEntity.BodyBuilder response =
        ResponseEntity.status(outcome.response().status()).contentType(MediaType.APPLICATION_JSON);
    if (outcome.replayed()) {
      response.header("Idempotent-Replayed", "true");
    }
    return response.body(outcome.response().body());
  }

  /** An attempt that either succeeded with a body or was refused with a problem. */
  record Attempt(Object body, ProblemException refusal) {
    static Attempt ok(Object body) {
      return new Attempt(body, null);
    }

    static Attempt refused(ProblemException refusal) {
      return new Attempt(null, refusal);
    }
  }

  /**
   * As {@link #run}, but a refusal is stored and replayed like a success. For an action that must
   * commit what it recorded even when it refuses, such as counting a wrong code: throwing would
   * roll that back. A client sends a fresh key for each submit attempt, so a stored refusal never
   * blocks a corrected retry.
   */
  ResponseEntity<String> runRefusable(
      String scope,
      String key,
      HttpStatus successStatus,
      Supplier<Attempt> action,
      String... requestParts) {
    java.util.concurrent.atomic.AtomicReference<HttpHeaders> headers =
        new java.util.concurrent.atomic.AtomicReference<>(new HttpHeaders());
    IdempotencyService.Outcome outcome =
        idempotency.execute(
            scope,
            requireKey(key),
            IdempotencyService.hash(requestParts),
            () -> {
              Attempt attempt = action.get();
              if (attempt.refusal() != null) {
                headers.set(attempt.refusal().headers());
                return new IdempotencyService.StoredResponse(
                    attempt.refusal().problem().getStatus(),
                    json.writeValueAsString(attempt.refusal().problem()));
              }
              return new IdempotencyService.StoredResponse(
                  successStatus.value(), json.writeValueAsString(attempt.body()));
            });
    int code = outcome.response().status();
    ResponseEntity.BodyBuilder response =
        ResponseEntity.status(code)
            .headers(outcome.replayed() ? new HttpHeaders() : headers.get())
            .contentType(
                code >= 400 ? MediaType.APPLICATION_PROBLEM_JSON : MediaType.APPLICATION_JSON);
    if (outcome.replayed()) {
      response.header("Idempotent-Replayed", "true");
    }
    return response.body(outcome.response().body());
  }

  static String requireKey(String key) {
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
