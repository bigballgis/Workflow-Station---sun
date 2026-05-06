package com.developer.exception;

import com.developer.security.FunctionUnitWorkspaceAccessDeniedException;
import com.platform.common.dto.ApiResponse;
import com.platform.common.exception.ErrorResponse;
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
     * {@link DeveloperBusinessException} 继承 {@link RuntimeException}，若不单独处理会被
     * {@link com.platform.common.exception.GlobalExceptionHandler} 归为未处理运行时异常（500 /
     * SYS_INTERNAL_ERROR），导致前端只看到「Operation failed」，用户无法获知真实业务规则原因。
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
     * {@link ResourceNotFoundException} 继承 {@link RuntimeException}，若不单独处理会落入全局 {@code RuntimeException} 分支，
     * 被映射为 {@code SYS_INTERNAL_ERROR}（500），与真实语义不符。
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
}
