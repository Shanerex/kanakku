package dev.kanakku.identity.repository;

import dev.kanakku.identity.service.SmsGateway;

/** Used when no SMS webhook is configured: nothing is ever sent, so numbers stay unverified. */
public final class NoSmsGateway implements SmsGateway {

  @Override
  public boolean enabled() {
    return false;
  }

  @Override
  public boolean send(String toE164, String text) {
    return false;
  }
}
