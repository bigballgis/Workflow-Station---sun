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
import jakarta.validation.ConstraintViolationException;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * 统一全局异常处理器
 * 处理所有模块的异常，避免 Bean 冲突
 */
@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    // ==================== 认证和授权异常 ====================
    
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

    // ==================== 权限异常 ====================

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

    // ==================== 验证异常 ====================
    
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
                .message("Validation failed")
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

    // ==================== 平台/业务异常 ====================

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

    // ==================== 参数与非法状态（避免误报为 SYS_INTERNAL_ERROR）====================

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

    // ==================== 兜底异常 ====================

    @ExceptionHandler(RuntimeException.class)
    public ResponseEntity<ApiResponse<?>> handleRuntimeException(
            RuntimeException ex, WebRequest request) {
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

    // ==================== 工具方法 ====================
    
    private String generateTraceId() {
        return UUID.randomUUID().toString().substring(0, 8);
    }

    private String getPath(WebRequest request) {
        return request.getDescription(false).replace("uri=", "");
    }
}
