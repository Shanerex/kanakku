package dev.kanakku;

import static org.assertj.core.api.Assertions.assertThat;

import com.sun.net.httpserver.HttpServer;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import org.junit.jupiter.api.Test;
import org.springframework.boot.health.contributor.Status;

class InfraHealthIndicatorsTest {

  private static HttpServer minioStub(int code) throws Exception {
    HttpServer server =
        HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
    server.createContext(
        "/minio/health/live",
        exchange -> {
          exchange.sendResponseHeaders(code, -1);
          exchange.close();
        });
    server.start();
    return server;
  }

  private static String url(HttpServer server) {
    return "http://127.0.0.1:" + server.getAddress().getPort();
  }

  @Test
  void minioUpWhenLiveEndpointReturns200() throws Exception {
    HttpServer server = minioStub(200);
    try {
      assertThat(new InfraHealthIndicators.Minio(url(server)).health().getStatus())
          .isEqualTo(Status.UP);
    } finally {
      server.stop(0);
    }
  }

  @Test
  void minioDownWhenLiveEndpointReturns503() throws Exception {
    HttpServer server = minioStub(503);
    try {
      assertThat(new InfraHealthIndicators.Minio(url(server)).health().getStatus())
          .isEqualTo(Status.DOWN);
    } finally {
      server.stop(0);
    }
  }

  @Test
  void minioDownWhenUnreachable() throws Exception {
    HttpServer server = minioStub(200);
    String url = url(server);
    server.stop(0);
    assertThat(new InfraHealthIndicators.Minio(url).health().getStatus()).isEqualTo(Status.DOWN);
  }

  @Test
  void smtpUpWhenPortAccepts() throws Exception {
    try (ServerSocket socket = new ServerSocket(0, 1, InetAddress.getLoopbackAddress())) {
      assertThat(
              new InfraHealthIndicators.Smtp("127.0.0.1", socket.getLocalPort())
                  .health()
                  .getStatus())
          .isEqualTo(Status.UP);
    }
  }

  @Test
  void smtpDownWhenPortClosed() throws Exception {
    int port;
    try (ServerSocket socket = new ServerSocket(0, 1, InetAddress.getLoopbackAddress())) {
      port = socket.getLocalPort();
    }
    assertThat(new InfraHealthIndicators.Smtp("127.0.0.1", port).health().getStatus())
        .isEqualTo(Status.DOWN);
  }
}
