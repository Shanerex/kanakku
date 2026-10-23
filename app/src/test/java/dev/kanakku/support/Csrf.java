package dev.kanakku.support;

import jakarta.servlet.http.Cookie;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** Does the real CSRF round trip: fetch the token, then send the cookie and header back. */
public final class Csrf {

  private Csrf() {}

  public static RequestPostProcessor token(MockMvc mvc) throws Exception {
    MvcResult result =
        mvc.perform(
                org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get(
                    "/api/v1/auth/csrf"))
            .andReturn();
    JsonNode body =
        JsonMapper.builder().build().readTree(result.getResponse().getContentAsString());
    Cookie cookie = result.getResponse().getCookie("XSRF-TOKEN");
    String header = body.get("headerName").asString();
    String token = body.get("token").asString();
    return request -> {
      request.setCookies(mergeCookies(request.getCookies(), cookie));
      request.addHeader(header, token);
      return request;
    };
  }

  private static Cookie[] mergeCookies(Cookie[] existing, Cookie added) {
    if (existing == null || existing.length == 0) {
      return new Cookie[] {added};
    }
    Cookie[] merged = new Cookie[existing.length + 1];
    System.arraycopy(existing, 0, merged, 0, existing.length);
    merged[existing.length] = added;
    return merged;
  }
}
