package dev.kanakku.support;

import static dev.kanakku.jooq.Tables.SIGN_IN_FAILURES;

import dev.kanakku.identity.service.SignUpService;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Map;
import org.jooq.DSLContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.server.LocalServerPort;

/**
 * Base for tests that need the real servlet container, because forwarded headers are handled there
 * and not in MockMvc. Sends sign-ins over HTTP from the loopback address.
 */
public abstract class RealServerSignIn extends PostgresIntegrationTest {

  @LocalServerPort int port;
  @Autowired protected DSLContext dsl;
  @Autowired protected SignUpService signUp;

  /** Posts a sign-in with a wrong password and returns the response. */
  protected HttpResponse<String> failedSignIn(String email, Map<String, String> headers)
      throws Exception {
    HttpRequest.Builder request =
        HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/api/v1/auth/sign-in"))
            .header("Content-Type", "application/json")
            .header("Cookie", "XSRF-TOKEN=t")
            .header("X-XSRF-TOKEN", "t")
            .POST(
                HttpRequest.BodyPublishers.ofString(
                    "{\"email\":\"" + email + "\",\"password\":\"wrong password\"}"));
    headers.forEach(request::header);
    return HttpClient.newHttpClient().send(request.build(), HttpResponse.BodyHandlers.ofString());
  }

  /** Posts a correct sign-in and returns the response. */
  protected HttpResponse<String> signIn(String email, String password, Map<String, String> headers)
      throws Exception {
    HttpRequest.Builder request =
        HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/api/v1/auth/sign-in"))
            .header("Content-Type", "application/json")
            .header("Cookie", "XSRF-TOKEN=t")
            .header("X-XSRF-TOKEN", "t")
            .POST(
                HttpRequest.BodyPublishers.ofString(
                    "{\"email\":\"" + email + "\",\"password\":\"" + password + "\"}"));
    headers.forEach(request::header);
    return HttpClient.newHttpClient().send(request.build(), HttpResponse.BodyHandlers.ofString());
  }

  /** The IP the throttle recorded for the email's one failure. */
  protected String recordedIp(String email) {
    return dsl.select(SIGN_IN_FAILURES.IP)
        .from(SIGN_IN_FAILURES)
        .where(SIGN_IN_FAILURES.EMAIL_KEY.eq(email))
        .fetchOne(SIGN_IN_FAILURES.IP)
        .address()
        .getHostAddress();
  }
}
