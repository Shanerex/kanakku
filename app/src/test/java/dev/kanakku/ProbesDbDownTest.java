package dev.kanakku;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(
    properties = {
      "spring.datasource.url=jdbc:postgresql://127.0.0.1:1/none",
      "spring.datasource.hikari.connection-timeout=1000",
      "spring.flyway.enabled=false"
    })
@AutoConfigureMockMvc
class ProbesDbDownTest {

  @Autowired MockMvc mvc;

  @Test
  void readinessIs503WhenPostgresIsDown() throws Exception {
    mvc.perform(get("/actuator/health/readiness")).andExpect(status().isServiceUnavailable());
  }

  @Test
  void livenessIs200WhenPostgresIsDown() throws Exception {
    mvc.perform(get("/actuator/health/liveness")).andExpect(status().isOk());
  }
}
