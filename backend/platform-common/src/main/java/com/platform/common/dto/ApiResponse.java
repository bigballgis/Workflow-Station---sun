package com.platform.common.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Unified API response wrapper for all platform APIs.
 * @param <T> The type of data in the response
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ApiResponse<T> {
    
    /**
     * Whether the request was successful
     */
    private boolean success;
    
    /**
     * Response data (null for error responses)
     */
    private T data;
    
    /**
     * Error information (null for success responses)
     */
    private ErrorResponse error;
    
    /**
     * Response timestamp
     */
    private LocalDateTime timestamp;
    
    /**
     * Trace ID for request tracking
     */
    private String traceId;
    
    /**
     * Create a success response with data
     */
    public static <T> ApiResponse<T> success(T data) {
        return ApiResponse.<T>builder()
                .success(true)
                .data(data)
                .timestamp(LocalDateTime.now())
                .build();
    }
    
    /**
     * Create a success response with data and trace ID
     */
    public static <T> ApiResponse<T> success(T data, String traceId) {
        return ApiResponse.<T>builder()
                .success(true)
                .data(data)
                .traceId(traceId)
                .timestamp(LocalDateTime.now())
                .build();
    }
    
    /**
     * Create a success response without data
     */
    public static <T> ApiResponse<T> success() {
        return ApiResponse.<T>builder()
                .success(true)
                .timestamp(LocalDateTime.now())
                .build();
    }
    
    /**
     * Create an error response
     */
    public static <T> ApiResponse<T> error(ErrorResponse errorResponse) {
        return ApiResponse.<T>builder()
                .success(false)
                .error(errorResponse)
                .traceId(errorResponse.getTraceId())
                .timestamp(LocalDateTime.now())
                .build();
    }
    
    /**
     * Create an error response with code and message
     */
    public static <T> ApiResponse<T> error(String errorCode, String message) {
        return ApiResponse.<T>builder()
                .success(false)
                .error(ErrorResponse.of(errorCode, message))
                .timestamp(LocalDateTime.now())
                .build();
    }
    
    /**
     * Create an error response with code, message and trace ID
     */
    public static <T> ApiResponse<T> error(String errorCode, String message, String traceId) {
        return ApiResponse.<T>builder()
                .success(false)
                .error(ErrorResponse.of(errorCode, message, traceId))
                .traceId(traceId)
                .timestamp(LocalDateTime.now())
                .build();
    }
}
