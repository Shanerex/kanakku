package dev.kanakku.platform.problem;

import org.springframework.http.HttpHeaders;
import org.springframework.http.ProblemDetail;

/** Thrown by any module to answer with a problem detail (and optional headers). */
public class ProblemException extends RuntimeException {

  private final transient ProblemDetail problem;
  private final transient HttpHeaders headers;

  public ProblemException(ProblemDetail problem) {
    this(problem, new HttpHeaders());
  }

  public ProblemException(ProblemDetail problem, HttpHeaders headers) {
    super(problem.getDetail());
    this.problem = problem;
    this.headers = headers;
  }

  public ProblemDetail problem() {
    return problem;
  }

  public HttpHeaders headers() {
    return headers;
  }
}
