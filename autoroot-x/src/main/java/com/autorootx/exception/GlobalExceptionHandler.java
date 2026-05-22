package com.autorootx.exception;

import com.autorootx.model.ApiErrorResponse;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.stream.Collectors;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(ApiException.class)
    public ResponseEntity<ApiErrorResponse> handleApi(ApiException ex, HttpServletRequest request) {
        log.warn("event=api_error status={} path={} message={}", ex.getStatus().value(), request.getRequestURI(), ex.getMessage());
        ApiErrorResponse error = new ApiErrorResponse();
        error.status = ex.getStatus().value();
        error.error = ex.getStatus().getReasonPhrase();
        error.message = ex.getMessage();
        error.path = request.getRequestURI();
        return ResponseEntity.status(ex.getStatus()).body(error);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiErrorResponse> handleValidation(MethodArgumentNotValidException ex, HttpServletRequest request) {
        String message = ex.getBindingResult().getFieldErrors().stream()
                .map(FieldError::getDefaultMessage)
                .collect(Collectors.joining(", "));

        log.warn("event=api_validation_error status={} path={} message={}", HttpStatus.BAD_REQUEST.value(), request.getRequestURI(), message);
        ApiErrorResponse error = new ApiErrorResponse();
        error.status = HttpStatus.BAD_REQUEST.value();
        error.error = HttpStatus.BAD_REQUEST.getReasonPhrase();
        error.message = message;
        error.path = request.getRequestURI();
        return ResponseEntity.badRequest().body(error);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiErrorResponse> handleUnknown(Exception ex, HttpServletRequest request) {
        log.error("event=api_unknown_error status={} path={} error_class={} message={}",
            HttpStatus.INTERNAL_SERVER_ERROR.value(),
            request.getRequestURI(),
            ex.getClass().getSimpleName(),
            ex.getMessage(),
            ex);
        ApiErrorResponse error = new ApiErrorResponse();
        error.status = HttpStatus.INTERNAL_SERVER_ERROR.value();
        error.error = HttpStatus.INTERNAL_SERVER_ERROR.getReasonPhrase();
        error.message = "Unexpected server error";
        error.path = request.getRequestURI();
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(error);
    }
}
