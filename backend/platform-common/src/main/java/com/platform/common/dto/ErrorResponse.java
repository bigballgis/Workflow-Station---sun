package com.platform.common.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * Unified error response format for all platform APIs.
 * Validates: Requirements 5.4
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ErrorResponse {
    
    /**
     * Error code following platform error code convention
     */
    private String errorCode;
    
    /**
     * Human-readable error message (supports i18n)
     */
    private String message;
    
    /**
     * Distributed trace ID for request tracking
     */
    private String traceId;
    
    /**
     * Timestamp when the error occurred
     */
    private LocalDateTime timestamp;
    
    /**
     * Request path that caused the error
     */
    private String path;
    
    /**
     * Additional error details (field errors, context info)
     */
    private Map<String, Object> details;
    
    /**
     * Create a simple error response
     */
    public static ErrorResponse of(String errorCode, String message) {
        return ErrorResponse.builder()
                .errorCode(errorCode)
                .message(message)
                .timestamp(LocalDateTime.now())
                .build();
    }
    
    /**
     * Create an error response with trace ID
     */
    public static ErrorResponse of(String errorCode, String message, String traceId) {
        return ErrorResponse.builder()
                .errorCode(errorCode)
                .message(message)
                .traceId(traceId)
                .timestamp(LocalDateTime.now())
                .build();
    }
    
    /**
     * Create a detailed error response
     */
    public static ErrorResponse of(String errorCode, String message, String traceId, 
                                   String path, Map<String, Object> details) {
        return ErrorResponse.builder()
                .errorCode(errorCode)
                .message(message)
                .traceId(traceId)
                .timestamp(LocalDateTime.now())
                .path(path)
                .details(details)
                .build();
    }
}
