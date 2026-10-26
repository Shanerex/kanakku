package dev.kanakku.support;

import dev.kanakku.identity.service.SmsGateway;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The SMS provider in tests: records every message instead of sending it, can be told to fail, and
 * hands back the last code sent to a number. No network.
 */
public class FakeSmsGateway implements SmsGateway {

  /** One message the app tried to send. */
  public record Sent(String to, String text, boolean accepted, boolean inTransaction) {}

  private static final Pattern CODE = Pattern.compile("\\b(\\d{6})\\b");

  private final List<Sent> sent = new CopyOnWriteArrayList<>();
  private volatile boolean failing;
  private volatile boolean enabled = true;

  @Override
  public boolean enabled() {
    return enabled;
  }

  @Override
  public boolean send(String toE164, String text) {
    boolean accepted = !failing;
    sent.add(
        new Sent(
            toE164,
            text,
            accepted,
            org.springframework.transaction.support.TransactionSynchronizationManager
                .isActualTransactionActive()));
    return accepted;
  }

  /** True when any message was handed over while a database transaction was open. */
  public boolean anySentInTransaction() {
    return sent.stream().anyMatch(Sent::inTransaction);
  }

  /** Makes every later send fail (or work again). */
  public void failing(boolean value) {
    this.failing = value;
  }

  public void enabled(boolean value) {
    this.enabled = value;
  }

  public List<Sent> sent() {
    return sent;
  }

  public List<Sent> sentTo(String e164) {
    return sent.stream().filter(s -> s.to().equals(e164)).toList();
  }

  /** The code in the last message sent to the number, or empty when none was. */
  public Optional<String> lastCode(String e164) {
    List<Sent> to = sentTo(e164);
    if (to.isEmpty()) {
      return Optional.empty();
    }
    Matcher m = CODE.matcher(to.get(to.size() - 1).text());
    return m.find() ? Optional.of(m.group(1)) : Optional.empty();
  }

  /** Forgets recorded messages and restores working defaults. */
  public void reset() {
    sent.clear();
    failing = false;
    enabled = true;
  }
}
