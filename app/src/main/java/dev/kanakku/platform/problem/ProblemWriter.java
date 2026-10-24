package dev.kanakku.platform.problem;

import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

/** Writes a problem detail straight to a servlet response, for filters that run before MVC. */
@Component
public class ProblemWriter {

  private final JsonMapper jsonMapper;

  ProblemWriter(JsonMapper jsonMapper) {
    this.jsonMapper = jsonMapper;
  }

  public void write(HttpServletResponse response, ProblemDetail problem) throws IOException {
    response.setStatus(problem.getStatus());
    response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
    response.setCharacterEncoding("UTF-8");
    response.getOutputStream().write(jsonMapper.writeValueAsBytes(problem));
  }
}
