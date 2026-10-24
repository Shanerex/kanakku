package dev.kanakku.identity.service;

import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/** Caps how many sign-up password hashes (and replay checks) run at once. */
@Component
public class SignUpHashLimiter extends HashSlots {

  SignUpHashLimiter(
      @Value("${kanakku.auth.max-concurrent-sign-up-hashes:4}") int maxConcurrent,
      @Value("${kanakku.auth.sign-up-hash-wait:300ms}") Duration wait) {
    super(maxConcurrent, wait);
  }
}
