package com.workflow.dto.request;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 审计日志查询请求
 * 
 * @author Workflow Engine
 * @version 1.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AuditQueryRequest {
    
    /**
     * 操作类型
     */
    private String operationType;
    
    /**
     * 操作目标
     */
    private String operationTarget;
    
    /**
     * 目标ID
     */
    private String targetId;
    
    /**
     * 用户ID
     */
    private String userId;
    
    /**
     * 用户角色
     */
    private String userRole;
    
    /**
     * 操作结果
     */
    private String result;
    
    /**
     * 开始时间
     */
    private LocalDateTime startTime;
    
    /**
     * 结束时间
     */
    private LocalDateTime endTime;
    
    /**
     * 分页偏移量
     */
    private Integer offset;
    
    /**
     * 分页大小
     */
    private Integer limit;
}