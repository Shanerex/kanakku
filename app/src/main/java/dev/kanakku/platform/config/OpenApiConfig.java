package dev.kanakku.platform.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.servers.Server;
import java.util.List;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Top-level OpenAPI metadata. The version is the API version, not the build version. */
@Configuration
class OpenApiConfig {

  @Bean
  OpenAPI kanakkuOpenApi() {
    return new OpenAPI()
        .info(
            new Info()
                .title("Kanakku API")
                .version("v1")
                .description("Expense splitting for friends, trips and flatmates."))
        .servers(List.of(new Server().url("/")));
  }
}
