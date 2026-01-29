package com.admin.exception;

import com.admin.dto.response.ErrorResponse;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

/**
 * 全局异常处理器
 */
@Slf4j
@ControllerAdvice
public class GlobalExceptionHandler {
    
    @ExceptionHandler(AdminBusinessException.class)
    public ResponseEntity<ErrorResponse> handleBusinessException(
            AdminBusinessException ex, HttpServletRequest request) {
        log.warn("Business exception: {} - {}", ex.getErrorCode(), ex.getErrorMessage());
        
        ErrorResponse error = ErrorResponse.builder()
                .code(ex.getErrorCode())
                .message(ex.getErrorMessage())
                .timestamp(Instant.now())
                .path(safePath(request))
                .build();
        
        return ResponseEntity.badRequest().body(error);
    }
    
    @ExceptionHandler(PermissionDeniedException.class)
    public ResponseEntity<ErrorResponse> handlePermissionDenied(
            PermissionDeniedException ex, HttpServletRequest request) {
        log.warn("Permission denied: {}", ex.getErrorMessage());
        
        ErrorResponse error = ErrorResponse.builder()
                .code(ex.getErrorCode())
                .message(ex.getErrorMessage())
                .timestamp(Instant.now())
                .path(safePath(request))
                .build();
        
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(error);
    }
    
    @ExceptionHandler(UserNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleUserNotFound(
            UserNotFoundException ex, HttpServletRequest request) {
        log.warn("User not found: {}", ex.getErrorMessage());
        
        ErrorResponse error = ErrorResponse.builder()
                .code(ex.getErrorCode())
                .message(ex.getErrorMessage())
                .timestamp(Instant.now())
                .path(safePath(request))
                .build();
        
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(error);
    }
    
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidationException(
            MethodArgumentNotValidException ex, HttpServletRequest request) {
        Map<String, String> errors = new HashMap<>();
        ex.getBindingResult().getAllErrors().forEach(error -> {
            String fieldName = ((FieldError) error).getField();
            String errorMessage = error.getDefaultMessage();
            errors.put(fieldName, errorMessage);
        });
        
        ErrorResponse error = ErrorResponse.builder()
                .code("VALIDATION_ERROR")
                .message("请求参数验证失败")
                .timestamp(Instant.now())
                .path(safePath(request))
                .details(errors)
                .build();
        
        return ResponseEntity.badRequest().body(error);
    }
    
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorResponse> handleMessageNotReadable(
            HttpMessageNotReadableException ex, HttpServletRequest request) {
        log.warn("Request body not readable: {}", ex.getMessage());
        ErrorResponse error = ErrorResponse.builder()
                .code("BAD_REQUEST")
                .message("请求体格式错误，请检查 JSON 格式")
                .timestamp(Instant.now())
                .path(safePath(request))
                .build();
        return ResponseEntity.badRequest().body(error);
    }
    
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleGenericException(
            Exception ex, HttpServletRequest request) {
        log.error("Unexpected error", ex);
        try {
            ErrorResponse error = ErrorResponse.builder()
                    .code("INTERNAL_ERROR")
                    .message("系统内部错误")
                    .timestamp(Instant.now())
                    .path(safePath(request))
                    .build();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(error);
        } catch (Exception fallback) {
            log.error("Failed to build error response", fallback);
            ErrorResponse minimal = ErrorResponse.builder()
                    .code("INTERNAL_ERROR")
                    .message("系统内部错误")
                    .timestamp(Instant.now())
                    .path("")
                    .build();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(minimal);
        }
    }

    private static String safePath(HttpServletRequest request) {
        try {
            return (request != null && request.getRequestURI() != null) ? request.getRequestURI() : "";
        } catch (Exception e) {
            return "";
        }
    }
}
