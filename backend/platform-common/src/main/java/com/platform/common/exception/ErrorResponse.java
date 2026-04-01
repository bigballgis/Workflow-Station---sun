package com.platform.common.exception;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.Map;

/**
 * 统一错误响应格式（标准版）。
 * 所有模块的异常处理应使用此类，而非 {@code com.platform.common.dto.ErrorResponse}。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ErrorResponse {
    
    private String code;
    
    private String message;
    
    private Object details;
    
    private String suggestion;
    
    private Instant timestamp;
    
    private String path;
    
    private String traceId;

    /**
     * 兼容 dto.ErrorResponse 的字段名。新代码请使用 {@link #code}。
     */
    private String errorCode;

    public static ErrorResponse of(String errorCode, String message) {
        return ErrorResponse.builder()
                .code(errorCode)
                .errorCode(errorCode)
                .message(message)
                .timestamp(Instant.now())
                .build();
    }

    public static ErrorResponse of(String errorCode, String message, String traceId) {
        return ErrorResponse.builder()
                .code(errorCode)
                .errorCode(errorCode)
                .message(message)
                .traceId(traceId)
                .timestamp(Instant.now())
                .build();
    }

    public static ErrorResponse of(String errorCode, String message, String traceId,
                                   String path, Map<String, Object> details) {
        return ErrorResponse.builder()
                .code(errorCode)
                .errorCode(errorCode)
                .message(message)
                .traceId(traceId)
                .timestamp(Instant.now())
                .path(path)
                .details(details)
                .build();
    }
}