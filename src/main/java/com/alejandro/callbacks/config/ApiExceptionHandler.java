package com.alejandro.callbacks.config;

import com.alejandro.callbacks.event.application.EventConflictException;
import com.alejandro.callbacks.event.application.EventNotFoundException;
import java.sql.SQLException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

@Order(Ordered.HIGHEST_PRECEDENCE)
@RestControllerAdvice
public class ApiExceptionHandler extends ResponseEntityExceptionHandler {
  private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);

  @ExceptionHandler(EventNotFoundException.class)
  ProblemDetail notFound(EventNotFoundException exception) {
    return ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, exception.getMessage());
  }

  @ExceptionHandler(EventConflictException.class)
  ProblemDetail conflict(EventConflictException exception) {
    return ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, exception.getMessage());
  }

  @Override
  protected ResponseEntity<Object> handleMethodArgumentNotValid(
      MethodArgumentNotValidException exception,
      HttpHeaders headers,
      HttpStatusCode status,
      WebRequest request) {
    var problem = ProblemDetail.forStatusAndDetail(status, "Revisa los campos del evento.");
    problem.setProperty(
        "errors",
        exception.getBindingResult().getFieldErrors().stream()
            .map(error -> new FieldError(error.getField(), error.getDefaultMessage()))
            .toList());
    return handleExceptionInternal(exception, problem, headers, status, request);
  }

  @ExceptionHandler(DataAccessException.class)
  ResponseEntity<ProblemDetail> databaseUnavailable(DataAccessException exception) {
    for (Throwable cause = exception; cause != null; cause = cause.getCause()) {
      if (cause instanceof SQLException sql
          && sql.getSQLState() != null
          && sql.getSQLState().startsWith("22")) {
        return ResponseEntity.badRequest()
            .body(
                ProblemDetail.forStatusAndDetail(
                    HttpStatus.BAD_REQUEST,
                    "El evento contiene valores no admitidos por el almacenamiento."));
      }
    }
    log.error("Database operation failed category={}", exception.getClass().getSimpleName());
    return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
        .header("Retry-After", "5")
        .body(
            ProblemDetail.forStatusAndDetail(
                HttpStatus.SERVICE_UNAVAILABLE,
                "No se pudo confirmar el almacenamiento. Reintenta con el mismo eventId."));
  }

  @ExceptionHandler(Exception.class)
  ProblemDetail unexpected(Exception exception) {
    // Never expose SQL, request bodies or authentication data.
    log.error("Unexpected request failure category={}", exception.getClass().getSimpleName());
    return ProblemDetail.forStatusAndDetail(
        HttpStatus.INTERNAL_SERVER_ERROR, "Ocurrió un error interno.");
  }

  public record FieldError(String field, String message) {}
}
