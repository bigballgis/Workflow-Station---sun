package com.platform.common.exception;

import com.platform.common.dto.ApiResponse;
import com.platform.common.enums.ErrorCode;
import lombok.extern.slf4j.Slf4j;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import jakarta.validation.ConstraintViolationException;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Unified global exception handler.
 * Handles exceptions across all modules, avoiding Bean conflicts.
 */
@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    // ==================== Auth & Authorization Exceptions ====================
    
    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<ApiResponse<?>> handleAuthenticationException(
            AuthenticationException ex, WebRequest request) {
        String traceId = generateTraceId();
        log.warn("Authentication failed [{}]: {}", traceId, ex.getMessage());
        
        ErrorResponse errorResponse = ErrorResponse.builder()
                .code("AUTH_FAILED")
                .message("Authentication failed")
                .timestamp(Instant.now())
                .traceId(traceId)
                .path(getPath(request))
                .build();
        
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(ApiResponse.error(errorResponse));
    }

    @ExceptionHandler(BadCredentialsException.class)
    public ResponseEntity<ApiResponse<?>> handleBadCredentials(
            BadCredentialsException ex, WebRequest request) {
        String traceId = generateTraceId();
        log.warn("Bad credentials [{}]: {}", traceId, ex.getMessage());
        
        ErrorResponse errorResponse = ErrorResponse.builder()
                .code("AUTH_INVALID_CREDENTIALS")
                .message("Invalid username or password")
                .timestamp(Instant.now())
                .traceId(traceId)
                .path(getPath(request))
                .build();
        
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(ApiResponse.error(errorResponse));
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiResponse<?>> handleAccessDenied(
            AccessDeniedException ex, WebRequest request) {
        String traceId = generateTraceId();
        log.warn("Access denied [{}]: {}", traceId, ex.getMessage());
        
        ErrorResponse errorResponse = ErrorResponse.builder()
                .code("PERM_ACCESS_DENIED")
                .message("Access denied")
                .timestamp(Instant.now())
                .traceId(traceId)
                .path(getPath(request))
                .build();
        
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(ApiResponse.error(errorResponse));
    }

    // ==================== Permission Exceptions ====================

    @ExceptionHandler(PermissionDeniedException.class)
    public ResponseEntity<ApiResponse<?>> handlePermissionDeniedException(
            PermissionDeniedException ex, WebRequest request) {
        String traceId = generateTraceId();
        ErrorCode errorCode = ex.getErrorCode();
        log.warn("Permission denied [{}]: {}", traceId, ex.getMessage());
        
        ErrorResponse errorResponse = ErrorResponse.builder()
                .code(errorCode.getCode())
                .message(ex.getMessage())
                .details(ex.getDetails())
                .timestamp(Instant.now())
                .traceId(traceId)
                .path(getPath(request))
                .build();
        
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(ApiResponse.error(errorResponse));
    }

    // ==================== Validation Exceptions ====================
    
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<?>> handleValidationException(
            MethodArgumentNotValidException ex, WebRequest request) {
        String traceId = generateTraceId();
        Map<String, Object> errors = new HashMap<>();
        
        ex.getBindingResult().getAllErrors().forEach(error -> {
            if (error instanceof FieldError fieldError) {
                errors.put(fieldError.getField(), fieldError.getDefaultMessage());
            } else {
                errors.put(error.getObjectName(), error.getDefaultMessage());
            }
        });
        
        log.warn("Validation failed [{}]: {}", traceId, errors);
        
        ErrorResponse errorResponse = ErrorResponse.builder()
                .code("VAL_INVALID_INPUT")
                .message("Validation failed: " + errors)
                .details(errors)
                .timestamp(Instant.now())
                .traceId(traceId)
                .path(getPath(request))
                .build();
        
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ApiResponse.error(errorResponse));
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ApiResponse<?>> handleConstraintViolationException(
            ConstraintViolationException ex, WebRequest request) {
        String traceId = generateTraceId();
        Map<String, Object> errors = new HashMap<>();
        
        ex.getConstraintViolations().forEach(violation -> {
            String fieldName = violation.getPropertyPath().toString();
            String errorMessage = violation.getMessage();
            errors.put(fieldName, errorMessage);
        });
        
        log.warn("Constraint violation [{}]: {}", traceId, errors);
        
        ErrorResponse errorResponse = ErrorResponse.builder()
                .code("VAL_CONSTRAINT_VIOLATION")
                .message("Constraint violation")
                .details(errors)
                .timestamp(Instant.now())
                .traceId(traceId)
                .path(getPath(request))
                .build();
        
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ApiResponse.error(errorResponse));
    }

    // ==================== Platform / Business Exceptions ====================

    @ExceptionHandler(PlatformException.class)
    public ResponseEntity<ApiResponse<?>> handlePlatformException(
            PlatformException ex, WebRequest request) {
        String traceId = generateTraceId();
        ErrorCode errorCode = ex.getErrorCode();
        log.warn("Platform exception [{}]: {} - {}", traceId, errorCode.getCode(), ex.getMessage());
        
        ErrorResponse errorResponse = ErrorResponse.builder()
                .code(errorCode.getCode())
                .message(ex.getMessage())
                .details(ex.getDetails())
                .timestamp(Instant.now())
                .traceId(traceId)
                .path(getPath(request))
                .build();
        
        return ResponseEntity.status(errorCode.getHttpStatus()).body(ApiResponse.error(errorResponse));
    }

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ApiResponse<?>> handleBusinessException(
            BusinessException ex, WebRequest request) {
        String traceId = generateTraceId();
        ErrorCode errorCode = ex.getErrorCode();
        log.warn("Business exception [{}]: {} - {}", traceId, errorCode.getCode(), ex.getMessage());
        
        ErrorResponse errorResponse = ErrorResponse.builder()
                .code(errorCode.getCode())
                .message(ex.getMessage())
                .timestamp(Instant.now())
                .traceId(traceId)
                .path(getPath(request))
                .build();
        
        return ResponseEntity.status(errorCode.getHttpStatus()).body(ApiResponse.error(errorResponse));
    }

    // ==================== Parameter & Illegal State (avoid false-positive SYS_INTERNAL_ERROR) ====================

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ApiResponse<?>> handleMethodArgumentTypeMismatch(
            MethodArgumentTypeMismatchException ex, WebRequest request) {
        String traceId = generateTraceId();
        String param = ex.getName();
        Object value = ex.getValue();
        Class<?> requiredType = ex.getRequiredType();
        String required = requiredType != null ? requiredType.getSimpleName() : "unknown";
        String msg = String.format("Invalid value for parameter '%s': '%s' (expected %s)",
                param, value, required);
        log.warn("Type mismatch [{}]: {}", traceId, msg);

        ErrorResponse errorResponse = ErrorResponse.builder()
                .code("VAL_INVALID_PARAMETER")
                .message(msg)
                .timestamp(Instant.now())
                .traceId(traceId)
                .path(getPath(request))
                .build();

        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ApiResponse.error(errorResponse));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiResponse<?>> handleIllegalArgumentException(
            IllegalArgumentException ex, WebRequest request) {
        String traceId = generateTraceId();
        log.warn("Bad request [{}]: {}", traceId, ex.getMessage());

        ErrorResponse errorResponse = ErrorResponse.builder()
                .code("VAL_INVALID_ARGUMENT")
                .message(ex.getMessage() != null ? ex.getMessage() : "Invalid argument")
                .timestamp(Instant.now())
                .traceId(traceId)
                .path(getPath(request))
                .build();

        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ApiResponse.error(errorResponse));
    }

    // ==================== Fallback Exceptions ====================

    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<ApiResponse<?>> handleIllegalState(
            IllegalStateException ex, WebRequest request) {
        // SSE streaming commits the OutputStream — when Spring later tries to
        // finalize the async response, it hits "getOutputStream() has already been called".
        // This is a harmless lifecycle artifact; silently return 200.
        if (isSseResponseAlreadyCommitted(ex)) {
            log.debug("SSE response already committed, ignoring IllegalStateException: {}", ex.getMessage());
            return ResponseEntity.ok().build();
        }
        // For other IllegalStateExceptions, treat as 500
        String traceId = generateTraceId();
        log.error("IllegalStateException [{}]: {}", traceId, ex.getMessage(), ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiResponse.error(ErrorResponse.builder()
                        .code("SYS_INTERNAL_ERROR")
                        .message("Internal server error")
                        .timestamp(Instant.now())
                        .traceId(traceId)
                        .path(getPath(request))
                        .build()));
    }

    @ExceptionHandler(RuntimeException.class)
    public ResponseEntity<ApiResponse<?>> handleRuntimeException(
            RuntimeException ex, WebRequest request) {
        // Some servlet containers/framework wrappers rethrow SSE commit artifacts
        // as RuntimeException (cause chain contains IllegalStateException).
        if (isSseResponseAlreadyCommitted(ex)) {
            log.debug("SSE response already committed, ignoring RuntimeException: {}", ex.getMessage());
            return ResponseEntity.ok().build();
        }

        String traceId = generateTraceId();
        log.error("Unhandled RuntimeException [{}]: {}", traceId, ex.getMessage(), ex);
        
        ErrorResponse errorResponse = ErrorResponse.builder()
                .code("SYS_INTERNAL_ERROR")
                .message("Internal server error")
                .timestamp(Instant.now())
                .traceId(traceId)
                .path(getPath(request))
                .build();
        
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(ApiResponse.error(errorResponse));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<?>> handleGenericException(
            Exception ex, WebRequest request) {
        if (isSseResponseAlreadyCommitted(ex)) {
            log.debug("SSE response already committed, ignoring Exception: {}", ex.getMessage());
            return ResponseEntity.ok().build();
        }

        String traceId = generateTraceId();
        log.error("Unexpected error [{}]: {}", traceId, ex.getMessage(), ex);
        
        ErrorResponse errorResponse = ErrorResponse.builder()
                .code("SYS_INTERNAL_ERROR")
                .message("An unexpected error occurred")
                .suggestion("Please try again later or contact support")
                .timestamp(Instant.now())
                .traceId(traceId)
                .path(getPath(request))
                .build();
        
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(ApiResponse.error(errorResponse));
    }

    // ==================== Utility Methods ====================
    
    private String generateTraceId() {
        return UUID.randomUUID().toString().substring(0, 8);
    }

    private String getPath(WebRequest request) {
        return request.getDescription(false).replace("uri=", "");
    }

    /**
     * Detect SSE async response finalization artifacts in nested exception chains.
     */
    private boolean isSseResponseAlreadyCommitted(Throwable ex) {
        Throwable current = ex;
        while (current != null) {
            String message = current.getMessage();
            if (message != null && message.contains("getOutputStream() has already been called")) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }
}
