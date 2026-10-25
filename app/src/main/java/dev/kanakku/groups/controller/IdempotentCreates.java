package dev.kanakku.groups.controller;

import dev.kanakku.platform.idempotency.IdempotencyService;
import dev.kanakku.platform.problem.ProblemException;
import dev.kanakku.platform.problem.Problems;
import java.util.UUID;
import java.util.function.Supplier;
import java.util.function.UnaryOperator;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

/**
 * Runs a create once per Idempotency-Key and answers 201 with the first result on a repeat. The
 * caller's id is part of the scope, so one user's key can never replay another user's response.
 */
@Component
class IdempotentCreates {

  private static final int MAX_KEY_LENGTH = 255;

  private final IdempotencyService idempotency;
  private final JsonMapper json;

  IdempotentCreates(IdempotencyService idempotency, JsonMapper json) {
    this.idempotency = idempotency;
    this.json = json;
  }

  /**
   * @param scope names the endpoint, for example {@code group-create}
   * @param requestParts everything that defines "the same request" (path ids and body fields);
   *     never a secret
   * @param action runs in the key's transaction and returns the body to store and send
   */
  ResponseEntity<String> run(
      String scope, UUID callerId, String key, Supplier<Object> action, String... requestParts) {
    return run(scope, callerId, key, action, UnaryOperator.identity(), requestParts);
  }

  /**
   * As above, but {@code render} turns the stored body into the body sent, on the first answer and
   * on every replay. Use it for a value that must not be stored, such as an invite token: the
   * action stores a body without it and {@code render} recomputes it.
   */
  ResponseEntity<String> run(
      String scope,
      UUID callerId,
      String key,
      Supplier<Object> action,
      UnaryOperator<String> render,
      String... requestParts) {
    String idempotencyKey = requireKey(key);
    IdempotencyService.Outcome outcome =
        idempotency.execute(
            scope + ":" + callerId,
            idempotencyKey,
            IdempotencyService.hash(requestParts),
            () ->
                new IdempotencyService.StoredResponse(
                    HttpStatus.CREATED.value(), json.writeValueAsString(action.get())));
    ResponseEntity.BodyBuilder response =
        ResponseEntity.status(outcome.response().status()).contentType(MediaType.APPLICATION_JSON);
    if (outcome.replayed()) {
      response.header("Idempotent-Replayed", "true");
    }
    return response.body(render.apply(outcome.response().body()));
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
