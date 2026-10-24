package dev.kanakku.platform.advice;

import dev.kanakku.platform.problem.ProblemException;
import dev.kanakku.platform.problem.Problems;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

/**
 * Turns every error into an RFC 9457 problem detail. Field errors never echo the rejected value.
 * The catch-all handler here must come last, so a module's own advice, which has a higher
 * precedence, is asked first.
 */
@Order(Ordered.LOWEST_PRECEDENCE)
@RestControllerAdvice
class ProblemAdvice extends ResponseEntityExceptionHandler {

  private static final Logger log = LoggerFactory.getLogger(ProblemAdvice.class);

  @ExceptionHandler(ProblemException.class)
  ResponseEntity<ProblemDetail> problem(ProblemException e) {
    return ResponseEntity.status(e.problem().getStatus()).headers(e.headers()).body(e.problem());
  }

  @Override
  protected ResponseEntity<Object> handleMethodArgumentNotValid(
      MethodArgumentNotValidException ex,
      HttpHeaders headers,
      HttpStatusCode status,
      WebRequest request) {
    List<Map<String, String>> errors =
        ex.getBindingResult().getFieldErrors().stream()
            .map(
                f ->
                    Map.of(
                        "field",
                        f.getField(),
                        "message",
                        f.getDefaultMessage() == null ? "invalid" : f.getDefaultMessage()))
            .toList();
    ProblemDetail problem =
        Problems.of(
            HttpStatus.BAD_REQUEST,
            "validation",
            "Check the highlighted fields",
            "The request has invalid fields.");
    problem.setProperty("errors", errors);
    return ResponseEntity.badRequest().headers(headers).body(problem);
  }

  @ExceptionHandler(Exception.class)
  ResponseEntity<ProblemDetail> unexpected(Exception e) throws Exception {
    if (e instanceof AccessDeniedException || e instanceof AuthenticationException) {
      throw e;
    }
    log.error("Unhandled error", e);
    return ResponseEntity.internalServerError()
        .body(
            Problems.of(
                HttpStatus.INTERNAL_SERVER_ERROR,
                "internal",
                "Something went wrong",
                "The server could not complete the request."));
  }
}
