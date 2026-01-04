package com.workflow.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 审计日志记录结果
 * 
 * @author Workflow Engine
 * @version 1.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AuditLogResult {
    
    /**
     * 操作是否成功
     */
    private boolean success;
    
    /**
     * 日志ID
     */
    private String logId;
    
    /**
     * 记录时间
     */
    private LocalDateTime timestamp;
    
    /**
     * 结果消息
     */
    private String message;
    
    /**
     * 错误消息
     */
    private String errorMessage;
}