package com.example.banking.support;

import com.example.banking.contracts.ApiError;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Instant;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalApiExceptionHandler {
    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<ApiError> validation(
            MethodArgumentNotValidException exception, HttpServletRequest request) {
        List<String> violations =
                exception.getBindingResult().getFieldErrors().stream()
                        .map(error -> error.getField() + ": " + error.getDefaultMessage())
                        .toList();
        return problem(
                HttpStatus.BAD_REQUEST,
                "Validation failed",
                "Request fields did not satisfy the API contract",
                request,
                violations);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    ResponseEntity<ApiError> malformed(
            HttpMessageNotReadableException exception, HttpServletRequest request) {
        return problem(
                HttpStatus.BAD_REQUEST,
                "Malformed request",
                "The request body could not be parsed",
                request,
                List.of());
    }

    protected ResponseEntity<ApiError> problem(
            HttpStatus status,
            String title,
            String detail,
            HttpServletRequest request,
            List<String> violations) {
        String correlationId = String.valueOf(request.getAttribute("correlationId"));
        ApiError body =
                new ApiError(
                        "about:blank",
                        title,
                        status.value(),
                        detail,
                        request.getRequestURI(),
                        correlationId,
                        Instant.now(),
                        violations);
        return ResponseEntity.status(status).body(body);
    }
}
