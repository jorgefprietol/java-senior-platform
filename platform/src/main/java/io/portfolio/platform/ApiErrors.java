package io.portfolio.platform;

import org.slf4j.LoggerFactory;
import org.springframework.http.*;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestControllerAdvice
public class ApiErrors {
  @ExceptionHandler({IllegalArgumentException.class, ArithmeticException.class})
  ProblemDetail invalid(RuntimeException ex) {
    return ProblemDetail.forStatusAndDetail(HttpStatus.UNPROCESSABLE_ENTITY, ex.getMessage());
  }

  @ExceptionHandler({
    MethodArgumentNotValidException.class,
    HttpMessageNotReadableException.class,
    org.springframework.web.bind.MissingRequestHeaderException.class,
    org.springframework.web.method.annotation.MethodArgumentTypeMismatchException.class
  })
  ProblemDetail badRequest(Exception ex) {
    return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, "Invalid request body");
  }

  @ExceptionHandler(ResponseStatusException.class)
  ProblemDetail status(ResponseStatusException ex) {
    return ProblemDetail.forStatusAndDetail(
        ex.getStatusCode(), ex.getReason() == null ? "Request rejected" : ex.getReason());
  }

  @ExceptionHandler(AccessDeniedException.class)
  ProblemDetail forbidden(AccessDeniedException ex) {
    return ProblemDetail.forStatusAndDetail(HttpStatus.FORBIDDEN, "Access denied");
  }

  @ExceptionHandler(Exception.class)
  ProblemDetail unexpected(Exception ex) {
    String id = java.util.UUID.randomUUID().toString();
    LoggerFactory.getLogger(ApiErrors.class).error("Unhandled incident {}", id, ex);
    return ProblemDetail.forStatusAndDetail(
        HttpStatus.INTERNAL_SERVER_ERROR, "Unexpected error; incident " + id);
  }
}
