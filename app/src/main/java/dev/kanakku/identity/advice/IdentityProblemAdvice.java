package dev.kanakku.identity.advice;

import dev.kanakku.identity.exception.BusyException;
import dev.kanakku.identity.exception.EmailTakenException;
import dev.kanakku.identity.exception.InvalidCredentialsException;
import dev.kanakku.identity.exception.TooManyAttemptsException;
import dev.kanakku.identity.exception.VersionConflictException;
import dev.kanakku.platform.problem.Problems;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/** Maps identity's own failures to problem details. */
@Order(Ordered.HIGHEST_PRECEDENCE + 100)
@RestControllerAdvice(basePackages = "dev.kanakku.identity.controller")
public class IdentityProblemAdvice {

  @ExceptionHandler(InvalidCredentialsException.class)
  ResponseEntity<ProblemDetail> invalidCredentials() {
    return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
        .body(
            Problems.of(
                HttpStatus.UNAUTHORIZED,
                "invalid-credentials",
                "Sign-in failed",
                "That email and password don't match."));
  }

  @ExceptionHandler(TooManyAttemptsException.class)
  ResponseEntity<ProblemDetail> tooManyAttempts(TooManyAttemptsException e) {
    ProblemDetail problem =
        Problems.of(
            HttpStatus.TOO_MANY_REQUESTS,
            "too-many-attempts",
            "Too many tries",
            "Too many sign-in tries. Try again later.");
    problem.setProperty("retryAfterSeconds", e.retryAfterSeconds());
    return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
        .header(HttpHeaders.RETRY_AFTER, Long.toString(e.retryAfterSeconds()))
        .body(problem);
  }

  @ExceptionHandler(BusyException.class)
  ResponseEntity<ProblemDetail> busy() {
    ProblemDetail problem =
        Problems.of(
            HttpStatus.TOO_MANY_REQUESTS,
            "busy",
            "Busy right now",
            "Too many requests are being processed. Try again in a moment.");
    problem.setProperty("retryAfterSeconds", 1);
    return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
        .header(HttpHeaders.RETRY_AFTER, "1")
        .body(problem);
  }

  @ExceptionHandler(VersionConflictException.class)
  ResponseEntity<ProblemDetail> versionConflict() {
    return ResponseEntity.status(HttpStatus.CONFLICT)
        .body(
            Problems.of(
                HttpStatus.CONFLICT,
                "version-conflict",
                "Changed elsewhere",
                "Your profile was changed somewhere else. Reload it and try again."));
  }

  @ExceptionHandler(EmailTakenException.class)
  ResponseEntity<ProblemDetail> emailTaken() {
    return ResponseEntity.status(HttpStatus.CONFLICT)
        .body(
            Problems.of(
                HttpStatus.CONFLICT,
                "email-taken",
                "Email already registered",
                "An account with this email already exists. Sign in instead."));
  }
}
