package com.example.banking.dtm;

import com.example.banking.contracts.ApiError;
import com.example.banking.support.GlobalApiExceptionHandler;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class DtmExceptionHandler extends GlobalApiExceptionHandler {
    @ExceptionHandler(UnknownFlagException.class)
    ResponseEntity<ApiError> unknownFlag(
            UnknownFlagException exception, HttpServletRequest request) {
        return problem(
                HttpStatus.NOT_FOUND,
                "Unknown feature flag",
                exception.getMessage(),
                request,
                List.of("flagKey: not registered"));
    }

    @ExceptionHandler(IncorrectFlagTypeException.class)
    ResponseEntity<ApiError> incorrectType(
            IncorrectFlagTypeException exception, HttpServletRequest request) {
        return problem(
                HttpStatus.UNPROCESSABLE_ENTITY,
                "Incorrect flag type",
                exception.getMessage(),
                request,
                List.of("requestedType: does not match registry"));
    }
}
