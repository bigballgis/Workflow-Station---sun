package com.developer.exception;

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
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * AI 相关异常处理器
 * 处理 AiLockConflictException（409）、AiValidationFailedException（422）、AiGenerationException（500）
 * 使用 @Order(Ordered.HIGHEST_PRECEDENCE) 确保优先于 GlobalExceptionHandler 中的通用 RuntimeException handler
 */
@RestControllerAdvice
@Slf4j
@Order(Ordered.HIGHEST_PRECEDENCE)
public class AiExceptionHandler {

    @ExceptionHandler(AiLockConflictException.class)
    public ResponseEntity<ErrorResponse> handleAiLockConflict(
            AiLockConflictException ex, WebRequest request) {
        String traceId = generateTraceId();
        log.warn("AI lock conflict [{}]: {}", traceId, ex.getMessage());

        Map<String, Object> details = new HashMap<>();
        details.put("lockInfo", ex.getLockInfo());

        ErrorResponse response = ErrorResponse.builder()
                .code("AI_LOCK_CONFLICT")
                .message(ex.getMessage())
                .details(details)
                .timestamp(Instant.now())
                .traceId(traceId)
                .path(getPath(request))
                .build();

        return new ResponseEntity<>(response, HttpStatus.CONFLICT);
    }

    @ExceptionHandler(AiValidationFailedException.class)
    public ResponseEntity<ErrorResponse> handleAiValidationFailed(
            AiValidationFailedException ex, WebRequest request) {
        String traceId = generateTraceId();
        log.warn("AI validation failed [{}]: {} errors", traceId, ex.getErrors().size());

        Map<String, Object> details = new HashMap<>();
        details.put("errors", ex.getErrors());

        ErrorResponse response = ErrorResponse.builder()
                .code("AI_VALIDATION_FAILED")
                .message(ex.getMessage())
                .details(details)
                .timestamp(Instant.now())
                .traceId(traceId)
                .path(getPath(request))
                .build();

        return new ResponseEntity<>(response, HttpStatus.UNPROCESSABLE_ENTITY);
    }

    @ExceptionHandler(AiGenerationException.class)
    public ResponseEntity<ErrorResponse> handleAiGeneration(
            AiGenerationException ex, WebRequest request) {
        String traceId = generateTraceId();
        log.error("AI generation error [{}]: {}", traceId, ex.getMessage());

        ErrorResponse response = ErrorResponse.builder()
                .code(ex.getErrorCode() != null ? ex.getErrorCode() : "AI_ERROR")
                .message(ex.getMessage())
                .timestamp(Instant.now())
                .traceId(traceId)
                .path(getPath(request))
                .build();

        HttpStatus status = determineHttpStatus(ex.getErrorCode());
        return new ResponseEntity<>(response, status);
    }

    private HttpStatus determineHttpStatus(String errorCode) {
        if (errorCode == null) return HttpStatus.INTERNAL_SERVER_ERROR;
        return switch (errorCode) {
            case "AI_SESSION_NOT_FOUND", "AI_FUNCTION_UNIT_NOT_FOUND", "AI_DOCUMENT_NOT_FOUND" -> HttpStatus.NOT_FOUND;
            case "AI_CONTEXT_TOO_LARGE" -> HttpStatus.PAYLOAD_TOO_LARGE;
            case "AI_N8N_TIMEOUT" -> HttpStatus.GATEWAY_TIMEOUT;
            case "AI_N8N_CALL_FAILED", "AI_N8N_EMPTY_RESPONSE" -> HttpStatus.BAD_GATEWAY;
            default -> HttpStatus.INTERNAL_SERVER_ERROR;
        };
    }

    private String generateTraceId() {
        return UUID.randomUUID().toString().substring(0, 8);
    }

    private String getPath(WebRequest request) {
        return request.getDescription(false).replace("uri=", "");
    }
}
