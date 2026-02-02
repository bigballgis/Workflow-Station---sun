package com.platform.common.exception;

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
    public ResponseEntity<ErrorResponse> handleAuthenticationException(
            AuthenticationException ex, WebRequest request) {
        String traceId = generateTraceId();
        log.warn("Authentication failed [{}]: {}", traceId, ex.getMessage());
        
        ErrorResponse response = ErrorResponse.builder()
                .code("AUTH_FAILED")
                .message("Authentication failed")
                .timestamp(Instant.now())
                .traceId(traceId)
                .path(getPath(request))
                .build();
        
        return new ResponseEntity<>(response, HttpStatus.UNAUTHORIZED);
    }

    @ExceptionHandler(BadCredentialsException.class)
    public ResponseEntity<ErrorResponse> handleBadCredentials(
            BadCredentialsException ex, WebRequest request) {
        String traceId = generateTraceId();
        log.warn("Bad credentials [{}]: {}", traceId, ex.getMessage());
        
        ErrorResponse response = ErrorResponse.builder()
                .code("AUTH_INVALID_CREDENTIALS")
                .message("Invalid username or password")
                .timestamp(Instant.now())
                .traceId(traceId)
                .path(getPath(request))
                .build();
        
        return new ResponseEntity<>(response, HttpStatus.UNAUTHORIZED);
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ErrorResponse> handleAccessDenied(
            AccessDeniedException ex, WebRequest request) {
        String traceId = generateTraceId();
        log.warn("Access denied [{}]: {}", traceId, ex.getMessage());
        
        ErrorResponse response = ErrorResponse.builder()
                .code("PERM_ACCESS_DENIED")
                .message("Access denied")
                .timestamp(Instant.now())
                .traceId(traceId)
                .path(getPath(request))
                .build();
        
        return new ResponseEntity<>(response, HttpStatus.FORBIDDEN);
    }

    // ==================== 验证异常 ====================
    
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidationException(
            MethodArgumentNotValidException ex, WebRequest request) {
        String traceId = generateTraceId();
        Map<String, Object> errors = new HashMap<>();
        
        ex.getBindingResult().getAllErrors().forEach(error -> {
            String fieldName = ((FieldError) error).getField();
            String errorMessage = error.getDefaultMessage();
            errors.put(fieldName, errorMessage);
        });
        
        log.warn("Validation failed [{}]: {}", traceId, errors);
        
        ErrorResponse response = ErrorResponse.builder()
                .code("VAL_INVALID_INPUT")
                .message("Validation failed")
                .details(errors)
                .timestamp(Instant.now())
                .traceId(traceId)
                .path(getPath(request))
                .build();
        
        return new ResponseEntity<>(response, HttpStatus.BAD_REQUEST);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ErrorResponse> handleConstraintViolationException(
            ConstraintViolationException ex, WebRequest request) {
        String traceId = generateTraceId();
        Map<String, Object> errors = new HashMap<>();
        
        ex.getConstraintViolations().forEach(violation -> {
            String fieldName = violation.getPropertyPath().toString();
            String errorMessage = violation.getMessage();
            errors.put(fieldName, errorMessage);
        });
        
        log.warn("Constraint violation [{}]: {}", traceId, errors);
        
        ErrorResponse response = ErrorResponse.builder()
                .code("VAL_CONSTRAINT_VIOLATION")
                .message("Constraint violation")
                .details(errors)
                .timestamp(Instant.now())
                .traceId(traceId)
                .path(getPath(request))
                .build();
        
        return new ResponseEntity<>(response, HttpStatus.BAD_REQUEST);
    }

    // ==================== 业务异常 ====================
    
    @ExceptionHandler(RuntimeException.class)
    public ResponseEntity<ErrorResponse> handleBusinessException(RuntimeException ex, WebRequest request) {
        String traceId = generateTraceId();
        
        // 检查是否是已知的业务异常类型
        String errorCode = "BIZ_ERROR";
        String message = "Business logic error occurred";
        HttpStatus status = HttpStatus.UNPROCESSABLE_ENTITY;
        
        // 根据异常类名判断类型
        String exceptionName = ex.getClass().getSimpleName();
        if (exceptionName.contains("Business")) {
            errorCode = "BIZ_BUSINESS_ERROR";
            message = ex.getMessage() != null ? ex.getMessage() : "Business rule violation";
        } else if (exceptionName.contains("Validation")) {
            errorCode = "VAL_VALIDATION_ERROR";
            message = ex.getMessage() != null ? ex.getMessage() : "Validation error";
            status = HttpStatus.BAD_REQUEST;
        } else if (exceptionName.contains("NotFound") || exceptionName.contains("Resource")) {
            errorCode = "RES_NOT_FOUND";
            message = ex.getMessage() != null ? ex.getMessage() : "Resource not found";
            status = HttpStatus.NOT_FOUND;
        }
        
        log.warn("Business exception [{}]: {} - {}", traceId, exceptionName, ex.getMessage(), ex);
        
        ErrorResponse response = ErrorResponse.builder()
                .code(errorCode)
                .message(message)
                .timestamp(Instant.now())
                .traceId(traceId)
                .path(getPath(request))
                .build();
        
        return new ResponseEntity<>(response, status);
    }

    // ==================== 系统异常 ====================
    
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleGenericException(
            Exception ex, WebRequest request) {
        String traceId = generateTraceId();
        log.error("Unexpected error [{}]: {}", traceId, ex.getMessage(), ex);
        
        ErrorResponse response = ErrorResponse.builder()
                .code("SYS_INTERNAL_ERROR")
                .message("An unexpected error occurred")
                .suggestion("Please try again later or contact support")
                .timestamp(Instant.now())
                .traceId(traceId)
                .path(getPath(request))
                .build();
        
        return new ResponseEntity<>(response, HttpStatus.INTERNAL_SERVER_ERROR);
    }

    // ==================== 工具方法 ====================
    
    private String generateTraceId() {
        return UUID.randomUUID().toString().substring(0, 8);
    }

    private String getPath(WebRequest request) {
        return request.getDescription(false).replace("uri=", "");
    }
}