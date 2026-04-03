package com.admin.exception;

import com.platform.common.dto.ApiResponse;
import com.platform.common.exception.ErrorResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;

import java.time.Instant;
import java.util.UUID;

/**
 * Relation Table 异常处理器
 * 处理 RelationTableNotFoundException（404）、RelationTableNameDuplicateException（409）、
 * RelationTableDeploymentException（500）、RelationTableBindingExistsException（409）
 * 响应体与 {@link com.platform.common.exception.GlobalExceptionHandler} 一致：{@code ApiResponse.error(ErrorResponse)}。
 * 使用 @Order(Ordered.HIGHEST_PRECEDENCE) 确保优先于 GlobalExceptionHandler 中的通用 RuntimeException handler
 */
@RestControllerAdvice
@Slf4j
@Order(Ordered.HIGHEST_PRECEDENCE)
public class RelationTableExceptionHandler {

    @ExceptionHandler(RelationTableNotFoundException.class)
    public ResponseEntity<ApiResponse<Void>> handleNotFound(
            RelationTableNotFoundException ex, WebRequest request) {
        String traceId = generateTraceId();
        log.warn("Relation table not found [{}]: {}", traceId, ex.getMessage());

        ErrorResponse response = ErrorResponse.builder()
                .code(ex.getErrorCode())
                .message(ex.getErrorMessage())
                .timestamp(Instant.now())
                .traceId(traceId)
                .path(getPath(request))
                .build();

        return new ResponseEntity<>(ApiResponse.error(response), HttpStatus.NOT_FOUND);
    }

    @ExceptionHandler(RelationTableNameDuplicateException.class)
    public ResponseEntity<ApiResponse<Void>> handleDuplicate(
            RelationTableNameDuplicateException ex, WebRequest request) {
        String traceId = generateTraceId();
        log.warn("Relation table name duplicate [{}]: {}", traceId, ex.getMessage());

        ErrorResponse response = ErrorResponse.builder()
                .code(ex.getErrorCode())
                .message(ex.getErrorMessage())
                .timestamp(Instant.now())
                .traceId(traceId)
                .path(getPath(request))
                .build();

        return new ResponseEntity<>(ApiResponse.error(response), HttpStatus.CONFLICT);
    }

    @ExceptionHandler(RelationTableDeploymentException.class)
    public ResponseEntity<ApiResponse<Void>> handleDeploymentError(
            RelationTableDeploymentException ex, WebRequest request) {
        String traceId = generateTraceId();
        log.error("Relation table deployment failed [{}]: {}", traceId, ex.getMessage(), ex);

        ErrorResponse response = ErrorResponse.builder()
                .code(ex.getErrorCode())
                .message(ex.getErrorMessage())
                .timestamp(Instant.now())
                .traceId(traceId)
                .path(getPath(request))
                .build();

        return new ResponseEntity<>(ApiResponse.error(response), HttpStatus.INTERNAL_SERVER_ERROR);
    }

    @ExceptionHandler(RelationTableBindingExistsException.class)
    public ResponseEntity<ApiResponse<Void>> handleBindingExists(
            RelationTableBindingExistsException ex, WebRequest request) {
        String traceId = generateTraceId();
        log.warn("Relation table binding exists [{}]: {}", traceId, ex.getMessage());

        ErrorResponse response = ErrorResponse.builder()
                .code(ex.getErrorCode())
                .message(ex.getErrorMessage())
                .timestamp(Instant.now())
                .traceId(traceId)
                .path(getPath(request))
                .build();

        return new ResponseEntity<>(ApiResponse.error(response), HttpStatus.CONFLICT);
    }

    private String generateTraceId() {
        return UUID.randomUUID().toString().substring(0, 8);
    }

    private String getPath(WebRequest request) {
        return request.getDescription(false).replace("uri=", "");
    }
}
