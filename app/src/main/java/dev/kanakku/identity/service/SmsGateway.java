package dev.kanakku.identity.service;

/**
 * Sends one text message through whatever SMS provider an admin configured. Kanakku bundles none:
 * the production adapter posts to a generic HTTP webhook, tests use a fake.
 */
public interface SmsGateway {

  /** False when no provider is configured, so numbers stay unverified. */
  boolean enabled();

  /**
   * Sends {@code text} to the E.164 number. Never throws for a provider failure and never logs the
   * number or the text.
   *
   * @return true when the provider accepted the message
   */
  boolean send(String toE164, String text);
}
