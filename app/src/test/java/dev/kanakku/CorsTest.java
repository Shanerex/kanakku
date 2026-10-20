package dev.kanakku;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@SpringBootTest(
    properties = {
      "kanakku.cors.allowed-origins=http://localhost:3000",
      "spring.datasource.url=jdbc:postgresql://127.0.0.1:1/none"
    })
@AutoConfigureMockMvc
@Import(CorsTest.PingConfig.class)
class CorsTest {

  @TestConfiguration
  @RestController
  static class PingConfig {
    @GetMapping("/api/ping")
    String ping() {
      return "pong";
    }
  }

  @Autowired MockMvc mvc;

  @Test
  void healthAllowsConfiguredOrigin() throws Exception {
    mvc.perform(get("/actuator/health").header("Origin", "http://localhost:3000"))
        .andExpect(header().string("Access-Control-Allow-Origin", "http://localhost:3000"));
  }

  @Test
  void healthRefusesOtherOrigin() throws Exception {
    mvc.perform(get("/actuator/health").header("Origin", "http://evil.example"))
        .andExpect(status().isForbidden());
  }

  @Test
  void apiAllowsConfiguredOrigin() throws Exception {
    mvc.perform(get("/api/ping").header("Origin", "http://localhost:3000"))
        .andExpect(header().string("Access-Control-Allow-Origin", "http://localhost:3000"));
  }

  @Test
  void apiRefusesOtherOrigin() throws Exception {
    mvc.perform(get("/api/ping").header("Origin", "http://evil.example"))
        .andExpect(status().isForbidden());
  }
}
