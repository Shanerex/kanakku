package dev.kanakku;

import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Health indicators for MinIO and SMTP; each is active only when its property is set. */
@Configuration
class InfraHealthIndicators {

  private static final Duration TIMEOUT = Duration.ofSeconds(2);

  @Bean("minio")
  @ConditionalOnProperty("kanakku.minio.endpoint")
  HealthIndicator minio(@Value("${kanakku.minio.endpoint}") String endpoint) {
    return new Minio(endpoint);
  }

  @Bean("smtp")
  @ConditionalOnProperty("kanakku.smtp.host")
  HealthIndicator smtp(
      @Value("${kanakku.smtp.host}") String host, @Value("${kanakku.smtp.port:1025}") int port) {
    return new Smtp(host, port);
  }

  static final class Minio implements HealthIndicator {
    private final String endpoint;

    Minio(String endpoint) {
      this.endpoint = endpoint;
    }

    @Override
    public Health health() {
      try {
        HttpRequest request =
            HttpRequest.newBuilder(
                    URI.create(endpoint.replaceAll("/+$", "") + "/minio/health/live"))
                .timeout(TIMEOUT)
                .GET()
                .build();
        HttpResponse<Void> response =
            HttpClient.newBuilder()
                .connectTimeout(TIMEOUT)
                .build()
                .send(request, HttpResponse.BodyHandlers.discarding());
        return response.statusCode() == 200 ? Health.up().build() : Health.down().build();
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
        return Health.down().build();
      } catch (Exception e) {
        return Health.down().build();
      }
    }
  }

  static final class Smtp implements HealthIndicator {
    private final String host;
    private final int port;

    Smtp(String host, int port) {
      this.host = host;
      this.port = port;
    }

    @Override
    public Health health() {
      try (Socket socket = new Socket()) {
        socket.connect(new InetSocketAddress(host, port), (int) TIMEOUT.toMillis());
        return Health.up().build();
      } catch (Exception e) {
        return Health.down().build();
      }
    }
  }
}
