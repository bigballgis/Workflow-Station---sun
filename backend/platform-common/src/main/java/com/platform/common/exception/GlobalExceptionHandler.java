package com.platform.common.exception;

import com.platform.common.dto.ErrorResponse;
import com.platform.common.enums.ErrorCode;
import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Global exception handler for unified error response across all modules.
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ErrorResponse> handleBusinessException(BusinessException ex, WebRequest request) {
        String traceId = generateTraceId();
        log.warn("Business exception [{}]: {}", traceId, ex.getMessage());
        
        ErrorResponse response = ErrorResponse.builder()
                .errorCode(ex.getErrorCode().getCode())
                .message(ex.getMessage())
                .timestamp(LocalDateTime.now())
                .traceId(traceId)
                .path(getPath(request))
                .build();
        
        return new ResponseEntity<>(response, ex.getErrorCode().getHttpStatus());
    }

    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleResourceNotFoundException(ResourceNotFoundException ex, WebRequest request) {
        String traceId = generateTraceId();
        log.warn("Resource not found [{}]: {}", traceId, ex.getMessage());
        
        ErrorResponse response = ErrorResponse.builder()
                .errorCode(ErrorCode.RESOURCE_NOT_FOUND.getCode())
                .message(ex.getMessage())
                .timestamp(LocalDateTime.now())
                .traceId(traceId)
                .path(getPath(request))
                .build();
        
        return new ResponseEntity<>(response, HttpStatus.NOT_FOUND);
    }

    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<ErrorResponse> handleAuthenticationException(AuthenticationException ex, WebRequest request) {
        String traceId = generateTraceId();
        log.warn("Authentication failed [{}]: {}", traceId, ex.getMessage());
        
        ErrorResponse response = ErrorResponse.builder()
                .errorCode(ErrorCode.UNAUTHORIZED.getCode())
                .message("Authentication failed")
                .timestamp(LocalDateTime.now())
                .traceId(traceId)
                .path(getPath(request))
                .build();
        
        return new ResponseEntity<>(response, HttpStatus.UNAUTHORIZED);
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ErrorResponse> handleAccessDeniedException(AccessDeniedException ex, WebRequest request) {
        String traceId = generateTraceId();
        log.warn("Access denied [{}]: {}", traceId, ex.getMessage());
        
        ErrorResponse response = ErrorResponse.builder()
                .errorCode(ErrorCode.FORBIDDEN.getCode())
                .message("Access denied")
                .timestamp(LocalDateTime.now())
                .traceId(traceId)
                .path(getPath(request))
                .build();
        
        return new ResponseEntity<>(response, HttpStatus.FORBIDDEN);
    }

    @SuppressWarnings("unchecked")
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidationException(MethodArgumentNotValidException ex, WebRequest request) {
        String traceId = generateTraceId();
        Map<String, Object> errors = new HashMap<>();
        
        ex.getBindingResult().getAllErrors().forEach(error -> {
            String fieldName = ((FieldError) error).getField();
            String errorMessage = error.getDefaultMessage();
            errors.put(fieldName, errorMessage);
        });
        
        log.warn("Validation failed [{}]: {}", traceId, errors);
        
        ErrorResponse response = ErrorResponse.builder()
                .errorCode(ErrorCode.VALIDATION_ERROR.getCode())
                .message("Validation failed")
                .timestamp(LocalDateTime.now())
                .traceId(traceId)
                .path(getPath(request))
                .details(errors)
                .build();
        
        return new ResponseEntity<>(response, HttpStatus.BAD_REQUEST);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ErrorResponse> handleConstraintViolationException(ConstraintViolationException ex, WebRequest request) {
        String traceId = generateTraceId();
        Map<String, Object> errors = new HashMap<>();
        
        ex.getConstraintViolations().forEach(violation -> {
            String fieldName = violation.getPropertyPath().toString();
            String errorMessage = violation.getMessage();
            errors.put(fieldName, errorMessage);
        });
        
        log.warn("Constraint violation [{}]: {}", traceId, errors);
        
        ErrorResponse response = ErrorResponse.builder()
                .errorCode(ErrorCode.VALIDATION_ERROR.getCode())
                .message("Constraint violation")
                .timestamp(LocalDateTime.now())
                .traceId(traceId)
                .path(getPath(request))
                .details(errors)
                .build();
        
        return new ResponseEntity<>(response, HttpStatus.BAD_REQUEST);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleGenericException(Exception ex, WebRequest request) {
        String traceId = generateTraceId();
        log.error("Unexpected error [{}]: {}", traceId, ex.getMessage(), ex);
        
        ErrorResponse response = ErrorResponse.builder()
                .errorCode(ErrorCode.INTERNAL_ERROR.getCode())
                .message("An unexpected error occurred")
                .timestamp(LocalDateTime.now())
                .traceId(traceId)
                .path(getPath(request))
                .build();
        
        return new ResponseEntity<>(response, HttpStatus.INTERNAL_SERVER_ERROR);
    }

    private String generateTraceId() {
        return UUID.randomUUID().toString().substring(0, 8);
    }

    private String getPath(WebRequest request) {
        return request.getDescription(false).replace("uri=", "");
    }
}
