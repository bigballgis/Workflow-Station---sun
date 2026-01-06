package com.platform.security.exception;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.Instant;
import java.util.Map;

/**
 * Exception handler for authentication errors.
 * Validates: Requirements 2.2, 2.3, 2.4
 */
@Slf4j
@RestControllerAdvice
public class AuthExceptionHandler {

    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<Map<String, Object>> handleAuthenticationException(
            AuthenticationException ex) {
        
        log.debug("Authentication error: {} - {}", ex.getCode(), ex.getMessage());
        
        Map<String, Object> body = Map.of(
                "code", ex.getCode(),
                "message", ex.getMessage(),
                "timestamp", Instant.now().toString()
        );
        
        return ResponseEntity
                .status(ex.getHttpStatus())
                .body(body);
    }
}
