package com.admin.exception;

import com.platform.common.dto.ApiResponse;
import com.platform.common.exception.ErrorResponse;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.Instant;
import java.util.UUID;

/**
 * Admin Center 业务异常统一映射为 {@code ApiResponse.error(ErrorResponse)}，
 * 避免落入 GlobalExceptionHandler 的 RuntimeException → 500。
 * <p>Relation Table 异常（均继承 {@link AdminBusinessException}）由更具体的 handler
 * 映射为 404/409/500；其余 {@link AdminBusinessException} → 400，
 * {@link AdminConflictException} → 409。
 */
@RestControllerAdvice
@Slf4j
@Order(Ordered.HIGHEST_PRECEDENCE)
public class AdminApiExceptionHandler {

    @ExceptionHandler(AdminConflictException.class)
    public ResponseEntity<ApiResponse<Void>> handleConflict(AdminConflictException ex, HttpServletRequest request) {
        String traceId = shortTraceId();
        log.warn("Conflict [{}] {}: {}", traceId, ex.getErrorCode(), ex.getErrorMessage());
        return respond(HttpStatus.CONFLICT, ex.getErrorCode(), ex.getErrorMessage(), traceId, request);
    }

    @ExceptionHandler(AdminBusinessException.class)
    public ResponseEntity<ApiResponse<Void>> handleBusiness(AdminBusinessException ex, HttpServletRequest request) {
        String traceId = shortTraceId();
        log.warn("Business [{}] {}: {}", traceId, ex.getErrorCode(), ex.getErrorMessage());
        return respond(HttpStatus.BAD_REQUEST, ex.getErrorCode(), ex.getErrorMessage(), traceId, request);
    }

    @ExceptionHandler(RelationTableNotFoundException.class)
    public ResponseEntity<ApiResponse<Void>> handleRelationTableNotFound(
            RelationTableNotFoundException ex, HttpServletRequest request) {
        String traceId = shortTraceId();
        log.warn("Relation table not found [{}]: {}", traceId, ex.getMessage());
        return respond(HttpStatus.NOT_FOUND, ex.getErrorCode(), ex.getErrorMessage(), traceId, request);
    }

    @ExceptionHandler(RelationTableNameDuplicateException.class)
    public ResponseEntity<ApiResponse<Void>> handleRelationTableDuplicate(
            RelationTableNameDuplicateException ex, HttpServletRequest request) {
        String traceId = shortTraceId();
        log.warn("Relation table name duplicate [{}]: {}", traceId, ex.getMessage());
        return respond(HttpStatus.CONFLICT, ex.getErrorCode(), ex.getErrorMessage(), traceId, request);
    }

    @ExceptionHandler(RelationTableDeploymentException.class)
    public ResponseEntity<ApiResponse<Void>> handleRelationTableDeploymentError(
            RelationTableDeploymentException ex, HttpServletRequest request) {
        String traceId = shortTraceId();
        log.error("Relation table deployment failed [{}]: {}", traceId, ex.getMessage(), ex);
        return respond(HttpStatus.INTERNAL_SERVER_ERROR, ex.getErrorCode(), ex.getErrorMessage(), traceId, request);
    }

    @ExceptionHandler(RelationTableBindingExistsException.class)
    public ResponseEntity<ApiResponse<Void>> handleRelationTableBindingExists(
            RelationTableBindingExistsException ex, HttpServletRequest request) {
        String traceId = shortTraceId();
        log.warn("Relation table binding exists [{}]: {}", traceId, ex.getMessage());
        return respond(HttpStatus.CONFLICT, ex.getErrorCode(), ex.getErrorMessage(), traceId, request);
    }

    private static ResponseEntity<ApiResponse<Void>> respond(
            HttpStatus status, String code, String message, String traceId, HttpServletRequest request) {
        ErrorResponse err = ErrorResponse.builder()
                .code(code)
                .errorCode(code)
                .message(message)
                .timestamp(Instant.now())
                .traceId(traceId)
                .path(request.getRequestURI())
                .build();
        return ResponseEntity.status(status).body(ApiResponse.error(err));
    }

    private static String shortTraceId() {
        return UUID.randomUUID().toString().substring(0, 8);
    }
}
