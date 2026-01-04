package com.workflow.dto;

import com.workflow.entity.ExceptionRecord.ExceptionSeverity;
import com.workflow.entity.ExceptionRecord.ExceptionStatus;

import java.time.LocalDateTime;

/**
 * 异常查询请求DTO
 */
public class ExceptionQueryRequest {
    
    private String processInstanceId;
    private String processDefinitionKey;
    private String taskId;
    private String exceptionType;
    private ExceptionSeverity severity;
    private ExceptionStatus status;
    private Boolean resolved;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private String tenantId;
    private String keyword;
    private Integer page = 0;
    private Integer size = 20;
    private String sortBy = "occurredTime";
    private String sortDirection = "DESC";
    
    // Getters and Setters
    public String getProcessInstanceId() { return processInstanceId; }
    public void setProcessInstanceId(String processInstanceId) { this.processInstanceId = processInstanceId; }
    
    public String getProcessDefinitionKey() { return processDefinitionKey; }
    public void setProcessDefinitionKey(String processDefinitionKey) { this.processDefinitionKey = processDefinitionKey; }
    
    public String getTaskId() { return taskId; }
    public void setTaskId(String taskId) { this.taskId = taskId; }
    
    public String getExceptionType() { return exceptionType; }
    public void setExceptionType(String exceptionType) { this.exceptionType = exceptionType; }
    
    public ExceptionSeverity getSeverity() { return severity; }
    public void setSeverity(ExceptionSeverity severity) { this.severity = severity; }
    
    public ExceptionStatus getStatus() { return status; }
    public void setStatus(ExceptionStatus status) { this.status = status; }
    
    public Boolean getResolved() { return resolved; }
    public void setResolved(Boolean resolved) { this.resolved = resolved; }
    
    public LocalDateTime getStartTime() { return startTime; }
    public void setStartTime(LocalDateTime startTime) { this.startTime = startTime; }
    
    public LocalDateTime getEndTime() { return endTime; }
    public void setEndTime(LocalDateTime endTime) { this.endTime = endTime; }
    
    public String getTenantId() { return tenantId; }
    public void setTenantId(String tenantId) { this.tenantId = tenantId; }
    
    public String getKeyword() { return keyword; }
    public void setKeyword(String keyword) { this.keyword = keyword; }
    
    public Integer getPage() { return page; }
    public void setPage(Integer page) { this.page = page; }
    
    public Integer getSize() { return size; }
    public void setSize(Integer size) { this.size = size; }
    
    public String getSortBy() { return sortBy; }
    public void setSortBy(String sortBy) { this.sortBy = sortBy; }
    
    public String getSortDirection() { return sortDirection; }
    public void setSortDirection(String sortDirection) { this.sortDirection = sortDirection; }
}
