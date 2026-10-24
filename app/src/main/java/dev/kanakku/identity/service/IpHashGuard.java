package dev.kanakku.identity.service;

import dev.kanakku.identity.util.ThrottleIp;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Caps how many sign-in and sign-up hashes one client address may have in flight, the ones still
 * waiting for a hash slot included. Without it a single IP could park every request thread behind
 * the global slots. An IPv6 client counts by its /64, as in the sign-in throttle. The cap does not
 * look at the email, so a refusal says nothing about whether an account exists; it never waits, so
 * a refused request holds no thread.
 */
@Component
public class IpHashGuard {

  private final int max;
  private final Map<String, Integer> inFlight = new ConcurrentHashMap<>();

  IpHashGuard(@Value("${kanakku.auth.max-hashes-per-ip:3}") int max) {
    this.max = Math.max(0, max);
  }

  /** A spot held for one IP; close it when the work is over. Closing twice is harmless. */
  public final class Permit implements AutoCloseable {
    private final String key;
    private final AtomicBoolean open = new AtomicBoolean(true);

    private Permit(String key) {
      this.key = key;
    }

    @Override
    public void close() {
      if (open.compareAndSet(true, false)) {
        inFlight.computeIfPresent(key, (k, n) -> n <= 1 ? null : n - 1);
      }
    }
  }

  /** Takes a spot for {@code ip}, or returns empty at once when the IP is at its cap. */
  public Optional<Permit> tryEnter(String ip) {
    String key = ThrottleIp.of(ip);
    boolean[] admitted = {false};
    inFlight.compute(
        key,
        (k, n) -> {
          int current = n == null ? 0 : n;
          if (current >= max) {
            return n;
          }
          admitted[0] = true;
          return current + 1;
        });
    return admitted[0] ? Optional.of(new Permit(key)) : Optional.empty();
  }

  /** How many IPs currently hold a spot; for tests. */
  int trackedIps() {
    return inFlight.size();
  }
}
