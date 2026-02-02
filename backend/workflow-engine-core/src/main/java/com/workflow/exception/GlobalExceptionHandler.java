package com.workflow.exception;

import com.platform.security.exception.AuthenticationException;
import com.workflow.dto.response.ApiResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;

import java.util.List;
import java.util.stream.Collectors;

/**
 * 全局异常处理器
 * 将异常转换为适当的HTTP响应
 */
@Slf4j
@ControllerAdvice
public class GlobalExceptionHandler {

    /**
     * 处理验证异常
     * 如果是"不存在"类型的错误，返回404；否则返回400
     */
    @ExceptionHandler(WorkflowValidationException.class)
    public ResponseEntity<ApiResponse<Void>> handleValidationException(WorkflowValidationException e) {
        log.warn("Validation exception: {}", e.getMessage());
        
        // 检查是否是"不存在"类型的错误
        String message = e.getMessage();
        boolean isNotFound = message != null && (
            message.contains("不存在") || 
            message.contains("not found") || 
            message.contains("Not found")
        );
        
        if (isNotFound) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ApiResponse.error("NOT_FOUND", message));
        }
        
        // 其他验证错误返回400
        List<WorkflowValidationException.ValidationError> errors = e.getValidationErrors();
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
            .body(ApiResponse.error("VALIDATION_ERROR", message, errors));
    }

    /**
     * 处理业务异常
     */
    @ExceptionHandler(WorkflowBusinessException.class)
    public ResponseEntity<ApiResponse<Void>> handleBusinessException(WorkflowBusinessException e) {
        log.error("Business exception: {} - {}", e.getErrorCode(), e.getMessage());
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
            .body(ApiResponse.error(e.getErrorCode(), e.getMessage()));
    }

    /**
     * 处理系统异常
     */
    @ExceptionHandler(WorkflowSystemException.class)
    public ResponseEntity<ApiResponse<Void>> handleSystemException(WorkflowSystemException e) {
        log.error("System exception: {}", e.getMessage(), e);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
            .body(ApiResponse.error("SYSTEM_ERROR", e.getMessage()));
    }

    /**
     * 处理参数验证异常
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Void>> handleMethodArgumentNotValid(MethodArgumentNotValidException e) {
        String message = e.getBindingResult().getFieldErrors().stream()
            .map(error -> error.getField() + ": " + error.getDefaultMessage())
            .collect(Collectors.joining(", "));
        
        log.warn("Method argument validation failed: {}", message);
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
            .body(ApiResponse.error("VALIDATION_ERROR", message));
    }

    /**
     * 处理非法参数异常
     */
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiResponse<Void>> handleIllegalArgument(IllegalArgumentException e) {
        log.warn("Illegal argument: {}", e.getMessage());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
            .body(ApiResponse.error("INVALID_ARGUMENT", e.getMessage()));
    }

    /**
     * 处理认证异常（登录/登出/令牌无效等），返回 401/403 而非 500
     */
    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<ApiResponse<Void>> handleAuthenticationException(AuthenticationException e) {
        log.warn("Authentication failed: {} - {}", e.getCode(), e.getMessage());
        HttpStatus status = e.getHttpStatus() == 403 ? HttpStatus.FORBIDDEN : HttpStatus.UNAUTHORIZED;
        return ResponseEntity.status(status)
            .body(ApiResponse.error(e.getCode(), e.getMessage()));
    }

    /**
     * 处理所有其他异常
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleGenericException(Exception e) {
        log.error("Unexpected error occurred", e);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
            .body(ApiResponse.error("INTERNAL_ERROR", "服务器内部错误"));
    }
}
