package dev.kanakku.identity.service;

import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Caps how many dummy Argon2 hashes (sign-ins for an unknown email) run at once. The per-(email,
 * IP) throttle cannot stop an attacker who rotates random emails, and each hash costs memory and
 * CPU. A caller past the cap waits for a slot up to a timeout, so the answer is always the same
 * generic 401 and never a distinct status that would reveal the email is unknown. Known emails are
 * never limited here, so real members can still sign in during such a flood.
 */
@Component
public class UnknownEmailHashLimiter extends HashSlots {

  UnknownEmailHashLimiter(
      @Value("${kanakku.auth.max-concurrent-unknown-hashes:4}") int maxConcurrent,
      @Value("${kanakku.auth.unknown-hash-wait:300ms}") Duration wait) {
    super(maxConcurrent, wait);
  }
}
