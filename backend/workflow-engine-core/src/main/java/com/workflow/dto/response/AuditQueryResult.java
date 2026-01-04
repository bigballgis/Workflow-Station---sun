package com.workflow.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

/**
 * 审计日志查询结果
 * 
 * @author Workflow Engine
 * @version 1.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AuditQueryResult {
    
    /**
     * 操作是否成功
     */
    private boolean success;
    
    /**
     * 审计日志列表
     */
    private List<Map<String, Object>> auditLogs;
    
    /**
     * 总记录数
     */
    private long totalCount;
    
    /**
     * 当前页码
     */
    private int currentPage;
    
    /**
     * 页面大小
     */
    private int pageSize;
    
    /**
     * 结果消息
     */
    private String message;
    
    /**
     * 错误消息
     */
    private String errorMessage;
}