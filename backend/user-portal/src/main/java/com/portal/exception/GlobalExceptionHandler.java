package com.portal.exception;

import com.portal.dto.ApiResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.stream.Collectors;

/**
 * 全局异常处理器
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(PortalException.class)
    public ResponseEntity<ApiResponse<Void>> handlePortalException(PortalException e) {
        log.error("业务异常: {}", e.getMessage());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ApiResponse.error(e.getCode(), e.getMessage()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Void>> handleValidationException(MethodArgumentNotValidException e) {
        String message = e.getBindingResult().getFieldErrors().stream()
                .map(FieldError::getDefaultMessage)
                .collect(Collectors.joining(", "));
        log.error("参数验证失败: {}", message);
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ApiResponse.error("400", message));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiResponse<Void>> handleIllegalArgumentException(IllegalArgumentException e) {
        log.error("参数错误: {}", e.getMessage());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ApiResponse.error("400", e.getMessage()));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleException(Exception e) {
        // #region agent log
        try {
            java.io.FileWriter fw = new java.io.FileWriter("/Users/qiweige/Desktop/PROJECTXXXSUN/Workflow-Station---sun/.cursor/debug.log", true);
            fw.write(java.util.Map.of("sessionId", "debug-session", "runId", "run1", "hypothesisId", "E", "location", "GlobalExceptionHandler.java:46", "message", "Global exception handler caught exception", "data", java.util.Map.of("exceptionType", e.getClass().getName(), "exceptionMessage", e.getMessage(), "stackTrace", java.util.Arrays.toString(e.getStackTrace()).substring(0, Math.min(500, java.util.Arrays.toString(e.getStackTrace()).length()))), "timestamp", System.currentTimeMillis()).toString() + "\n");
            fw.close();
        } catch (Exception ex) {}
        // #endregion
        log.error("系统异常", e);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiResponse.error("500", "系统内部错误"));
    }
}
