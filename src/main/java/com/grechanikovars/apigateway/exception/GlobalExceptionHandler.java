package com.grechanikovars.apigateway.exception;

import com.grechanikovars.apigateway.dto.response.FieldViolation;
import java.util.Comparator;
import java.util.List;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.bind.support.WebExchangeBindException;

/**
 * Answers the errors of the controllers of the Gateway as RFC 9457 problems.
 *
 * <p>Only the validation of a request body is handled here, because it needs the list of the
 * invalid fields. Every other error (an unreadable body, a wrong content type, a failed call to a
 * service) is answered by {@link ProblemDetailErrorHandler} in the same format.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

  private static final Logger LOGGER = LogManager.getLogger(GlobalExceptionHandler.class);

  /**
   * Handles a request body that breaks a rule of its fields.
   *
   * <p>The answer lists the field and the rule, sorted by field, and never the rejected value.
   *
   * @param ex the validation failure
   * @return 400 with the property {@code errors}
   */
  @ExceptionHandler(WebExchangeBindException.class)
  public ResponseEntity<ProblemDetail> handleValidation(WebExchangeBindException ex) {
    List<FieldViolation> errors = ex.getFieldErrors().stream()
        .map(error -> new FieldViolation(error.getField(), error.getDefaultMessage()))
        .sorted(Comparator.comparing(FieldViolation::field)
            .thenComparing(FieldViolation::message))
        .toList();
    LOGGER.info("Request rejected with 400: {} invalid fields", errors.size());
    ProblemDetail problem =
        ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, "Validation failed");
    problem.setProperty("errors", errors);
    return ResponseEntity.badRequest().body(problem);
  }
}
