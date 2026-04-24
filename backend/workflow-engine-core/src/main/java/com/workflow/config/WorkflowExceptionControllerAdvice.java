package com.workflow.config;

import com.platform.common.dto.ApiResponse;
import com.platform.common.exception.ErrorResponse;
import com.workflow.exception.WorkflowBusinessException;
import com.workflow.exception.WorkflowValidationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * 工作流领域异常必须在 {@link com.platform.common.exception.GlobalExceptionHandler} 的
 * {@link RuntimeException} 兜底之前处理，否则会误报为 {@code SYS_INTERNAL_ERROR} 且丢失真实原因。
 */
@Slf4j
@RestControllerAdvice(basePackages = "com.workflow")
@Order(Ordered.HIGHEST_PRECEDENCE)
public class WorkflowExceptionControllerAdvice {

    @ExceptionHandler(WorkflowValidationException.class)
    public ResponseEntity<ApiResponse<Void>> handleWorkflowValidation(
            WorkflowValidationException ex, WebRequest request) {
        String traceId = shortTraceId();
        log.warn("Workflow validation [{}] {}: {}", traceId, getPath(request), ex.getMessage());

        Map<String, Object> details = null;
        if (ex.getValidationErrors() != null && !ex.getValidationErrors().isEmpty()) {
            details = new LinkedHashMap<>();
            for (WorkflowValidationException.ValidationError ve : ex.getValidationErrors()) {
                details.put(ve.getField(), ve.getMessage());
            }
        }

        ErrorResponse error = ErrorResponse.builder()
                .code("WORKFLOW_VALIDATION_ERROR")
                .errorCode("WORKFLOW_VALIDATION_ERROR")
                .message(ex.getMessage())
                .details(details)
                .traceId(traceId)
                .path(getPath(request))
                .timestamp(Instant.now())
                .build();

        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ApiResponse.error(error));
    }

    @ExceptionHandler(WorkflowBusinessException.class)
    public ResponseEntity<ApiResponse<Void>> handleWorkflowBusiness(
            WorkflowBusinessException ex, WebRequest request) {
        String traceId = shortTraceId();
        log.warn("Workflow business [{}] {}: [{}] {}",
                traceId, getPath(request), ex.getErrorCode(), ex.getMessage());

        ErrorResponse error = ErrorResponse.builder()
                .code(ex.getErrorCode())
                .errorCode(ex.getErrorCode())
                .message(ex.getMessage())
                .traceId(traceId)
                .path(getPath(request))
                .timestamp(Instant.now())
                .build();

        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ApiResponse.error(error));
    }

    private static String shortTraceId() {
        return UUID.randomUUID().toString().replace("-", "").substring(0, 8);
    }

    private static String getPath(WebRequest request) {
        return request.getDescription(false).replace("uri=", "");
    }
}
