package com.admin.exception;

import com.platform.common.dto.ApiResponse;
import com.platform.common.exception.ErrorResponse;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.Instant;
import java.util.UUID;

/**
 * Admin Center 业务异常统一映射为 ApiResponse，避免落入 GlobalExceptionHandler 的 RuntimeException → 500。
 */
@RestControllerAdvice
@Slf4j
@Order(100)
public class AdminApiExceptionHandler {

    @ExceptionHandler(AdminConflictException.class)
    public ResponseEntity<ApiResponse<Void>> handleConflict(AdminConflictException ex, HttpServletRequest request) {
        String traceId = shortTraceId();
        log.warn("Conflict [{}] {}: {}", traceId, ex.getErrorCode(), ex.getErrorMessage());
        ErrorResponse err = ErrorResponse.builder()
                .code(ex.getErrorCode())
                .errorCode(ex.getErrorCode())
                .message(ex.getErrorMessage())
                .timestamp(Instant.now())
                .traceId(traceId)
                .path(request.getRequestURI())
                .build();
        return ResponseEntity.status(HttpStatus.CONFLICT).body(ApiResponse.error(err));
    }

    @ExceptionHandler(AdminBusinessException.class)
    public ResponseEntity<ApiResponse<Void>> handleBusiness(AdminBusinessException ex, HttpServletRequest request) {
        String traceId = shortTraceId();
        log.warn("Business [{}] {}: {}", traceId, ex.getErrorCode(), ex.getErrorMessage());
        ErrorResponse err = ErrorResponse.builder()
                .code(ex.getErrorCode())
                .errorCode(ex.getErrorCode())
                .message(ex.getErrorMessage())
                .timestamp(Instant.now())
                .traceId(traceId)
                .path(request.getRequestURI())
                .build();
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ApiResponse.error(err));
    }

    private static String shortTraceId() {
        return UUID.randomUUID().toString().substring(0, 8);
    }
}
