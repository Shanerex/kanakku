package dev.kanakku.platform.idempotency;

import dev.kanakku.platform.problem.ProblemException;
import dev.kanakku.platform.problem.Problems;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.function.Supplier;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Makes a create happen once per {@code (scope, key)}. The key row and the work share one
 * transaction, so a repeat either replays the stored response or, when the first attempt is still
 * running, waits on the primary key and then replays it. A failed action stores nothing.
 */
@Service
public class IdempotencyService {

  /** How long a key is remembered. */
  public static final Duration TTL = Duration.ofHours(24);

  /** A response to replay: HTTP status and a JSON body. */
  public record StoredResponse(int status, String body) {}

  /**
   * A response decided inside the key's transaction, plus a step to run after it commits (a slow
   * call such as an SMS provider). {@code finish} returns the response to send; when it differs
   * from {@code stored} the stored response is replaced, so a replay matches what was sent.
   */
  public record Staged(StoredResponse stored, Supplier<StoredResponse> finish) {}

  /** The response, and whether it came from an earlier request with the same key. */
  public record Outcome(StoredResponse response, boolean replayed) {}

  private final IdempotencyKeyRepository keys;
  private final TransactionTemplate transaction;
  private final Clock clock;

  IdempotencyService(
      IdempotencyKeyRepository keys, PlatformTransactionManager transactions, Clock clock) {
    this.keys = keys;
    this.transaction = new TransactionTemplate(transactions);
    this.clock = clock;
  }

  /**
   * Fingerprint of the request fields that define "the same request". Callers must leave secrets
   * such as passwords out of {@code parts}.
   */
  public static byte[] hash(String... parts) {
    try {
      MessageDigest digest = MessageDigest.getInstance("SHA-256");
      for (String part : parts) {
        byte[] bytes = part.getBytes(StandardCharsets.UTF_8);
        digest.update(Integer.toString(bytes.length).getBytes(StandardCharsets.US_ASCII));
        digest.update((byte) ':');
        digest.update(bytes);
      }
      return digest.digest();
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException(e);
    }
  }

  /**
   * Deletes keys past their TTL and returns how many went. Housekeeping for the scheduled cleanup
   * job: requests never run it, and an expired key that is still stored is replaced on claim.
   */
  public int purgeExpired() {
    OffsetDateTime now = OffsetDateTime.ofInstant(clock.instant(), ZoneOffset.UTC);
    return keys.deleteCreatedBefore(now.minus(TTL));
  }

  /**
   * Runs {@code action} once for this key, or replays its stored response.
   *
   * @throws ProblemException 422 when the key was used before with a different request hash
   */
  public Outcome execute(
      String scope, String key, byte[] requestHash, Supplier<StoredResponse> action) {
    return transaction.execute(status -> executeInTransaction(scope, key, requestHash, action));
  }

  /**
   * As {@link #execute}, but {@code action} also returns a step that runs once the key's
   * transaction has committed, so no connection or lock is held while it works. A replay runs
   * neither the action nor the step. If the step fails, the stored response stays as decided.
   */
  public Outcome executeStaged(
      String scope, String key, byte[] requestHash, Supplier<Staged> action) {
    java.util.concurrent.atomic.AtomicReference<Supplier<StoredResponse>> finish =
        new java.util.concurrent.atomic.AtomicReference<>();
    Outcome outcome =
        execute(
            scope,
            key,
            requestHash,
            () -> {
              Staged staged = action.get();
              finish.set(staged.finish());
              return staged.stored();
            });
    if (outcome.replayed() || finish.get() == null) {
      return outcome;
    }
    StoredResponse sent = finish.get().get();
    if (sent.equals(outcome.response())) {
      return outcome;
    }
    transaction.executeWithoutResult(
        status -> keys.storeResponse(scope, key, sent.status(), sent.body()));
    return new Outcome(sent, false);
  }

  private Outcome executeInTransaction(
      String scope, String key, byte[] requestHash, Supplier<StoredResponse> action) {
    OffsetDateTime now = OffsetDateTime.ofInstant(clock.instant(), ZoneOffset.UTC);

    if (!keys.claim(scope, key, requestHash, now, now.minus(TTL))) {
      var stored =
          keys.find(scope, key)
              .orElseThrow(() -> new IllegalStateException("idempotency key vanished"));
      if (!Arrays.equals(stored.requestHash(), requestHash)) {
        throw new ProblemException(
            Problems.of(
                HttpStatus.UNPROCESSABLE_ENTITY,
                "idempotency-key-reuse",
                "Idempotency key already used",
                "This Idempotency-Key was already used with a different request."));
      }
      return new Outcome(new StoredResponse(stored.status(), stored.body()), true);
    }

    StoredResponse response = action.get();
    keys.storeResponse(scope, key, response.status(), response.body());
    return new Outcome(response, false);
  }
}
