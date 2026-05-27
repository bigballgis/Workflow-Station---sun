package com.developer.exception;

import com.developer.security.FunctionUnitWorkspaceAccessDeniedException;
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
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@RestControllerAdvice
@Order(Ordered.HIGHEST_PRECEDENCE + 15)
@Slf4j
public class WorkspaceExceptionHandler {

    private static final Set<String> CONFLICT_ERROR_CODES = Set.of(
            "BINDING_EXISTS",
            "PRIMARY_BINDING_EXISTS",
            "CONFLICT_FORM_NAME_EXISTS",
            "CONFLICT_TABLE_NAME_EXISTS",
            "CONFLICT_ACTION_NAME_EXISTS",
            "CONFLICT_DECISION_KEY_EXISTS",
            "CONFLICT_NAME_EXISTS",
            "PROCESS_FORM_ALREADY_EXISTS"
    );

    @ExceptionHandler(FunctionUnitWorkspaceAccessDeniedException.class)
    public ResponseEntity<Map<String, Object>> onWorkspaceDenied(FunctionUnitWorkspaceAccessDeniedException ex) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(Map.of(
                        "error", "WORKSPACE_FORBIDDEN",
                        "message", ex.getMessage()));
    }

    /**
     * {@link DeveloperBusinessException} extends {@link RuntimeException}. If not handled separately,
     * it will be caught by {@link com.platform.common.exception.GlobalExceptionHandler} as an unhandled
     * runtime exception (500 / SYS_INTERNAL_ERROR), causing the frontend to only see "Operation failed"
     * without the real business rule reason.
     */
    @ExceptionHandler(DeveloperBusinessException.class)
    public ResponseEntity<ApiResponse<?>> handleDeveloperBusinessException(
            DeveloperBusinessException ex, WebRequest request) {
        String traceId = UUID.randomUUID().toString().substring(0, 8);
        HttpStatus status = CONFLICT_ERROR_CODES.contains(ex.getErrorCode())
                ? HttpStatus.CONFLICT
                : HttpStatus.BAD_REQUEST;
        String path = request.getDescription(false).replace("uri=", "");
        ErrorResponse errorResponse = ErrorResponse.builder()
                .code(ex.getErrorCode())
                .errorCode(ex.getErrorCode())
                .message(ex.getMessage())
                .timestamp(Instant.now())
                .traceId(traceId)
                .path(path)
                .build();
        return ResponseEntity.status(status).body(ApiResponse.error(errorResponse));
    }

    /**
     * {@link ResourceNotFoundException} extends {@link RuntimeException}. If not handled separately,
     * it will fall into the global {@code RuntimeException} handler and be mapped to
     * {@code SYS_INTERNAL_ERROR} (500), which does not match the real semantics.
     */
    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ApiResponse<?>> handleResourceNotFound(
            ResourceNotFoundException ex, WebRequest request) {
        String traceId = UUID.randomUUID().toString().substring(0, 8);
        String path = request.getDescription(false).replace("uri=", "");
        ErrorResponse errorResponse = ErrorResponse.builder()
                .code(ex.getErrorCode())
                .errorCode(ex.getErrorCode())
                .message(ex.getMessage())
                .timestamp(Instant.now())
                .traceId(traceId)
                .path(path)
                .build();
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(ApiResponse.error(errorResponse));
    }

    /**
     * Suppress harmless SSE async lifecycle exceptions.
     * They can surface as IllegalStateException/RuntimeException after response commit.
     */
    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<Void> handleSseIllegalState(IllegalStateException ex) {
        if (isSseCommittedException(ex)) {
            log.debug("Suppressing SSE IllegalStateException: {}", ex.getMessage());
            return ResponseEntity.ok().build();
        }
        throw ex;
    }

    @ExceptionHandler(RuntimeException.class)
    public ResponseEntity<Void> handleSseRuntime(RuntimeException ex) {
        if (isSseCommittedException(ex)) {
            log.debug("Suppressing SSE RuntimeException: {}", ex.getMessage());
            return ResponseEntity.ok().build();
        }
        throw ex;
    }

    private boolean isSseCommittedException(Throwable ex) {
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
