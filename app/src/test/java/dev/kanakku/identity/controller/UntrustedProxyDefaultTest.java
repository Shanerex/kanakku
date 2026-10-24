package dev.kanakku.identity.controller;

import static org.assertj.core.api.Assertions.assertThat;

import dev.kanakku.support.RealServerSignIn;
import dev.kanakku.support.TestData;
import java.net.http.HttpResponse;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * With no trusted proxy configured (the default) forwarded headers are ignored. The Secure-cookie
 * default is switched off here so the cookie flag shows only what the request headers did.
 */
@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = "kanakku.auth.secure-cookies=false")
class UntrustedProxyDefaultTest extends RealServerSignIn {

  @Test
  void aSpoofedForwardedForDoesNotChangeTheThrottledIp() throws Exception {
    String email = TestData.email();
    failedSignIn(email, Map.of("X-Forwarded-For", "203.0.113.7"));

    assertThat(recordedIp(email)).isIn("127.0.0.1", "0:0:0:0:0:0:0:1", "::1");
  }

  @Test
  void aSpoofedForwardedProtoDoesNotMakeTheCookieSecure() throws Exception {
    String email = TestData.email();
    signUp.signUp(email, "correct horse battery", "Asha");

    HttpResponse<String> response =
        signIn(email, "correct horse battery", Map.of("X-Forwarded-Proto", "https"));

    assertThat(response.statusCode()).isEqualTo(200);
    assertThat(response.headers().allValues("Set-Cookie"))
        .filteredOn(c -> c.startsWith("SESSION="))
        .singleElement()
        .satisfies(c -> assertThat(c).doesNotContain("Secure"));
  }
}
