package dev.kanakku.identity.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** Talks to a throwaway loopback server only; nothing leaves the machine. */
class WebhookSmsGatewayTest {

  record Received(String auth, String contentType, String body) {}

  HttpServer server;
  final AtomicReference<Received> received = new AtomicReference<>();

  String start(int status) throws IOException {
    server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    server.createContext(
        "/sms",
        exchange -> {
          received.set(
              new Received(
                  exchange.getRequestHeaders().getFirst("Authorization"),
                  exchange.getRequestHeaders().getFirst("Content-Type"),
                  new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8)));
          exchange.sendResponseHeaders(status, -1);
          exchange.close();
        });
    server.start();
    return "http://127.0.0.1:" + server.getAddress().getPort() + "/sms";
  }

  @AfterEach
  void stop() {
    if (server != null) {
      server.stop(0);
    }
  }

  @Test
  void postsToAndTextAsJsonWithABearerToken() throws Exception {
    var gateway = new WebhookSmsGateway(start(200), "s3cret");

    assertThat(gateway.enabled()).isTrue();
    assertThat(gateway.send("+919876543210", "Your code is 123456")).isTrue();

    Received r = received.get();
    assertThat(r.auth()).isEqualTo("Bearer s3cret");
    assertThat(r.contentType()).startsWith("application/json");
    JsonNode body = JsonMapper.builder().build().readTree(r.body());
    assertThat(body.get("to").asString()).isEqualTo("+919876543210");
    assertThat(body.get("text").asString()).isEqualTo("Your code is 123456");
  }

  @Test
  void sendsNoAuthorizationHeaderWithoutAToken() throws Exception {
    var gateway = new WebhookSmsGateway(start(204), "");

    assertThat(gateway.send("+919876543210", "hi")).isTrue();
    assertThat(received.get().auth()).isNull();
  }

  @Test
  void aNon2xxAnswerIsAFailure() throws Exception {
    var gateway = new WebhookSmsGateway(start(500), "t");

    assertThat(gateway.send("+919876543210", "hi")).isFalse();
  }

  @Test
  void anUnreachableWebhookIsAFailureNotAnException() {
    var gateway = new WebhookSmsGateway("http://127.0.0.1:1/sms", "t");

    assertThat(gateway.send("+919876543210", "hi")).isFalse();
  }

  @Test
  void onlyHttpUrlsAreAccepted() {
    assertThatThrownBy(() -> new WebhookSmsGateway("file:///etc/passwd", ""))
        .isInstanceOf(IllegalStateException.class);
  }

  @Test
  void plainHttpToAPublicHostIsRefusedAtStartup() {
    assertThatThrownBy(() -> new WebhookSmsGateway("http://8.8.8.8/sms", "s3cret"))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("https")
        .hasMessageNotContaining("8.8.8.8")
        .hasMessageNotContaining("s3cret");
    assertThatThrownBy(() -> new WebhookSmsGateway("http://[2001:4860:4860::8888]/sms", ""))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("https");
  }

  @Test
  void plainHttpIsAllowedToLoopbackAndPrivateHosts() {
    for (String url :
        new String[] {
          "http://127.0.0.1:9/sms",
          "http://localhost:9/sms",
          "http://[::1]:9/sms",
          "http://10.1.2.3/sms",
          "http://172.18.0.5/sms",
          "http://192.168.1.20/sms",
          "http://[fd12:3456:789a::1]/sms",
        }) {
      assertThat(new WebhookSmsGateway(url, "").enabled()).as(url).isTrue();
    }
  }

  @Test
  void httpsIsAllowedToAnyHost() {
    assertThat(new WebhookSmsGateway("https://8.8.8.8/sms", "").enabled()).isTrue();
  }

  @Test
  void anHttpHostThatDoesNotResolveIsRefusedAtStartup() {
    assertThatThrownBy(() -> new WebhookSmsGateway("http://no-such-host.invalid/sms", ""))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("https");
  }

  @Test
  void plainHttpToANameWithDotsIsRefusedWithoutAnyLookup() {
    for (String url :
        new String[] {
          "http://sms.example.com/sms",
          "http://sms.internal/sms",
          "http://localhost.example.com/sms",
          "http://sms-gateway./sms",
        }) {
      assertThatThrownBy(() -> new WebhookSmsGateway(url, "s3cret"))
          .as(url)
          .isInstanceOf(IllegalStateException.class)
          .hasMessageContaining("https")
          .hasMessageNotContaining("example")
          .hasMessageNotContaining("sms-gateway")
          .hasMessageNotContaining("s3cret");
    }
  }

  @Test
  void plainHttpToADotlessNumberIsRefused() {
    assertThatThrownBy(() -> new WebhookSmsGateway("http://134744072/sms", ""))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("https");
  }

  @Test
  void plainHttpIsAllowedToADotlessServiceName() {
    assertThat(new WebhookSmsGateway("http://sms-gateway:8080/sms", "").enabled()).isTrue();
  }

  @Test
  void httpsIsAllowedToANameWithDots() {
    assertThat(new WebhookSmsGateway("https://sms.example.com/sms", "").enabled()).isTrue();
  }

  @Test
  void toStringHidesTheToken() {
    assertThat(new WebhookSmsGateway("http://127.0.0.1:1/sms", "s3cret").toString())
        .doesNotContain("s3cret");
  }

  @Test
  void noSmsGatewayIsDisabledAndSendsNothing() {
    var none = new NoSmsGateway();

    assertThat(none.enabled()).isFalse();
    assertThat(none.send("+919876543210", "hi")).isFalse();
  }
}
