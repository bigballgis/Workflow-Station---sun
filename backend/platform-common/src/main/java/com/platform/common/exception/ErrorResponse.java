package com.platform.common.exception;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * 统一错误响应格式
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ErrorResponse {
    
    /**
     * 错误代码
     */
    private String code;
    
    /**
     * 错误消息
     */
    private String message;
    
    /**
     * 错误详情
     */
    private Object details;
    
    /**
     * 建议
     */
    private String suggestion;
    
    /**
     * 时间戳
     */
    private Instant timestamp;
    
    /**
     * 请求路径
     */
    private String path;
    
    /**
     * 跟踪ID
     */
    private String traceId;
    
    /**
     * 错误代码（兼容性）
     */
    private String errorCode;
}