package dev.kanakku.identity.repository;

import dev.kanakku.identity.service.SmsGateway;
import java.io.IOException;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tools.jackson.databind.json.JsonMapper;

/**
 * Sends a message by POSTing {@code {"to": "+91...", "text": "..."}} as JSON to an admin-configured
 * URL, with {@code Authorization: Bearer <token>} when a token is set. Any 2xx counts as sent. It
 * logs only the HTTP status or the failure class: never the URL, token, number or text.
 *
 * <p>The URL must be https unless its host is a loopback or private IP literal (a sidecar on the
 * same machine or network) or a name with no dot, such as {@code localhost} or a compose service
 * name. Any other name could later resolve to a public address, so the host is judged by its
 * spelling and never looked up: a bearer token and every code must not cross the internet in clear
 * text. A violation stops startup; the message never repeats the URL.
 */
public final class WebhookSmsGateway implements SmsGateway {

  private static final Logger log = LoggerFactory.getLogger(WebhookSmsGateway.class);
  private static final Duration TIMEOUT = Duration.ofSeconds(5);

  private final URI url;
  private final String token;
  private final HttpClient client;
  private final JsonMapper json = JsonMapper.builder().build();

  public WebhookSmsGateway(String url, String token) {
    URI uri = URI.create(url.strip());
    String scheme = uri.getScheme();
    if (!"http".equalsIgnoreCase(scheme) && !"https".equalsIgnoreCase(scheme)) {
      throw new IllegalStateException("kanakku.sms.webhook-url must be an http or https URL");
    }
    if ("http".equalsIgnoreCase(scheme) && !isLocalHost(uri.getHost())) {
      throw new IllegalStateException(
          "kanakku.sms.webhook-url (KANAKKU_SMS_WEBHOOK_URL) must use https unless its host is a"
              + " loopback or private IP address, or a single-word name such as localhost or a"
              + " compose service name");
    }
    this.url = uri;
    this.token = token == null || token.isBlank() ? null : token.strip();
    this.client = HttpClient.newBuilder().connectTimeout(TIMEOUT).build();
  }

  /**
   * True for a loopback, private or link-local IP literal, or a name with no dot that starts with a
   * letter. Nothing is resolved, so a name cannot pass now and point at the internet later. A
   * dotless all-digit host (such as {@code 134744072}, which some resolvers read as an address) is
   * not a name and fails.
   */
  private static boolean isLocalHost(String host) {
    if (host == null || host.isBlank()) {
      return false;
    }
    String literal = host.startsWith("[") ? host.substring(1, host.length() - 1) : host;
    InetAddress address;
    try {
      address = InetAddress.ofLiteral(literal);
    } catch (IllegalArgumentException e) {
      return !literal.contains(".")
          && !literal.contains(":")
          && Character.isLetter(literal.charAt(0));
    }
    boolean uniqueLocal =
        address instanceof Inet6Address && (address.getAddress()[0] & 0xfe) == 0xfc;
    return address.isLoopbackAddress()
        || address.isSiteLocalAddress()
        || address.isLinkLocalAddress()
        || uniqueLocal;
  }

  @Override
  public boolean enabled() {
    return true;
  }

  @Override
  public boolean send(String toE164, String text) {
    try {
      HttpRequest.Builder request =
          HttpRequest.newBuilder(url)
              .timeout(TIMEOUT)
              .header("Content-Type", "application/json")
              .POST(
                  HttpRequest.BodyPublishers.ofString(
                      json.writeValueAsString(Map.of("to", toE164, "text", text))));
      if (token != null) {
        request.header("Authorization", "Bearer " + token);
      }
      HttpResponse<Void> response =
          client.send(request.build(), HttpResponse.BodyHandlers.discarding());
      int status = response.statusCode();
      boolean sent = status >= 200 && status < 300;
      if (!sent) {
        log.warn("SMS webhook answered HTTP {}", status);
      }
      return sent;
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      log.warn("SMS webhook call interrupted");
      return false;
    } catch (IOException | RuntimeException e) {
      log.warn("SMS webhook call failed: {}", e.getClass().getSimpleName());
      return false;
    }
  }

  @Override
  public String toString() {
    return "WebhookSmsGateway[token=****]";
  }
}
