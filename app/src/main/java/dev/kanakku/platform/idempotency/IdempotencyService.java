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
   * Runs {@code action} once for this key, or replays its stored response.
   *
   * @throws ProblemException 422 when the key was used before with a different request hash
   */
  public Outcome execute(
      String scope, String key, byte[] requestHash, Supplier<StoredResponse> action) {
    return transaction.execute(status -> executeInTransaction(scope, key, requestHash, action));
  }

  private Outcome executeInTransaction(
      String scope, String key, byte[] requestHash, Supplier<StoredResponse> action) {
    OffsetDateTime now = OffsetDateTime.ofInstant(clock.instant(), ZoneOffset.UTC);
    keys.deleteCreatedBefore(now.minus(TTL));

    if (!keys.claim(scope, key, requestHash, now)) {
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
