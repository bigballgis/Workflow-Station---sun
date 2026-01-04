package com.workflow.dto.request;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 任务查询请求
 */
@Data
public class TaskQueryRequest {
    
    private String processDefinitionKey;
    
    private String processInstanceId;
    
    private String assignee;
    
    private String candidateUser;
    
    private String candidateGroup;
    
    private String taskDefinitionKey;
    
    private LocalDateTime createdAfter;
    
    private LocalDateTime createdBefore;
    
    private LocalDateTime dueAfter;
    
    private LocalDateTime dueBefore;
    
    private Integer priority;
    
    private String tenantId;
    
    // 分页参数
    private int page = 0;
    
    private int size = 20;
    
    // 排序参数
    private String sortBy = "createTime";
    
    private String sortDirection = "desc";
}