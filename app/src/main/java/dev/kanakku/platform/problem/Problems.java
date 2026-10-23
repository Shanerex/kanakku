package dev.kanakku.platform.problem;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;

/** Builds RFC 9457 problem details whose {@code type} is a stable {@code urn:kanakku:problem:*}. */
public final class Problems {

  public static final String TYPE_PREFIX = "urn:kanakku:problem:";

  private Problems() {}

  public static ProblemDetail of(HttpStatus status, String slug, String title, String detail) {
    ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
    problem.setType(java.net.URI.create(TYPE_PREFIX + slug));
    problem.setTitle(title);
    return problem;
  }
}
