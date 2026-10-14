package com.jcaa.accounts.infrastructure;

import com.jcaa.accounts.domain.AccountNotFoundException;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ResponseStatusException;

@RestControllerAdvice
public class AccountErrorHandler {
  // Return the error directly: sendError dispatches to /error, which is intentionally protected.
  @ExceptionHandler({MethodArgumentNotValidException.class, HttpMessageNotReadableException.class})
  @ResponseStatus(HttpStatus.BAD_REQUEST)
  Map<String, Object> invalidRequest() {
    return Map.of("status", 400, "message", "Invalid account request.");
  }

  @ExceptionHandler(ResponseStatusException.class)
  ResponseEntity<Map<String, Object>> upstreamError(ResponseStatusException exception) {
    int status = exception.getStatusCode().value();
    return ResponseEntity.status(status)
        .body(Map.of("status", status, "message", "Account request could not be completed."));
  }

  @ExceptionHandler(AccountNotFoundException.class)
  @ResponseStatus(HttpStatus.NOT_FOUND)
  Map<String, Object> missing() {
    return Map.of("status", 404, "message", "Account not found.");
  }
}

