package dev.kanakku.identity.advice;

import dev.kanakku.identity.exception.EmailTakenException;
import dev.kanakku.identity.exception.InvalidCredentialsException;
import dev.kanakku.identity.exception.SignInBusyException;
import dev.kanakku.identity.exception.TooManyAttemptsException;
import dev.kanakku.identity.exception.VersionConflictException;
import dev.kanakku.platform.problem.Problems;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/** Maps identity's own failures to problem details. */
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

  @ExceptionHandler(SignInBusyException.class)
  ResponseEntity<ProblemDetail> signInBusy() {
    return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
        .header(HttpHeaders.RETRY_AFTER, "1")
        .body(
            Problems.of(
                HttpStatus.SERVICE_UNAVAILABLE,
                "sign-in-busy",
                "Sign-in is busy",
                "Sign-in is busy right now. Try again in a moment."));
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
