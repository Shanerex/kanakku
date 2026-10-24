package dev.kanakku.identity.controller;

import static org.assertj.core.api.Assertions.assertThat;

import dev.kanakku.support.RealServerSignIn;
import dev.kanakku.support.TestData;
import java.net.http.HttpResponse;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

/** A client behind a trusted proxy is told apart by X-Forwarded-For, and the cookie is Secure. */
@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = {
      "KANAKKU_TRUSTED_PROXIES=127\\.0\\.0\\.1|0:0:0:0:0:0:0:1|::1",
      "kanakku.auth.secure-cookies=false"
    })
class TrustedProxyTest extends RealServerSignIn {

  @Test
  void theThrottleKeysOnTheForwardedClientIpFromATrustedProxy() throws Exception {
    String email = TestData.email();
    failedSignIn(email, Map.of("X-Forwarded-For", "203.0.113.7"));

    assertThat(recordedIp(email)).isEqualTo("203.0.113.7");
  }

  @Test
  void theSessionCookieIsSecureWhenTheTrustedProxyTerminatesTls() throws Exception {
    String email = TestData.email();
    signUp.signUp(email, "correct horse battery", "Asha");

    HttpResponse<String> response =
        signIn(
            email,
            "correct horse battery",
            Map.of("X-Forwarded-For", "203.0.113.8", "X-Forwarded-Proto", "https"));

    assertThat(response.statusCode()).isEqualTo(200);
    assertThat(response.headers().allValues("Set-Cookie"))
        .filteredOn(c -> c.startsWith("SESSION="))
        .singleElement()
        .satisfies(c -> assertThat(c).contains("Secure").contains("HttpOnly"));
  }
}
